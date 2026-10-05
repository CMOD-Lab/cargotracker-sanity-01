package org.eclipse.cargotracker.interfaces.booking.sse;

import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.infrastructure.cache.RedisConfig;
import redis.clients.jedis.Jedis;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Remediation cz-java-0070: The previous in-process static {@code EnumMap} caches for
 * {@code routingStatusLabels} (line 12) and {@code transportStatusLabels} (line 14) have been
 * replaced with Amazon ElastiCache (Redis) so that label data is shared consistently across all
 * horizontally-scaled container replicas. Connection details are supplied via the
 * {@code REDIS_HOST}, {@code REDIS_PORT}, and {@code REDIS_PASSWORD} environment variables
 * (injected through Kubernetes ConfigMaps / Secrets with IRSA-secured access).
 */
public class RealtimeCargoTrackingViewAdapter {

  /** Redis hash key for routing-status label mappings (replaces static EnumMap, line 12). */
  private static final String ROUTING_STATUS_CACHE_KEY   = "labels:routingStatus";

  /** Redis hash key for transport-status label mappings (replaces static EnumMap, line 14). */
  private static final String TRANSPORT_STATUS_CACHE_KEY = "labels:transportStatus";

  private final Cargo cargo;

  /**
   * Initialises the status-label caches in Amazon ElastiCache (Redis) if they have not been
   * populated yet. This replaces the former static {@code EnumMap} fields (cz-java-0070).
   */
  static {
    try (Jedis jedis = RedisConfig.getPool().getResource()) {
      // Routing status labels (replaces static EnumMap routingStatusLabels, line 12)
      if (!jedis.exists(ROUTING_STATUS_CACHE_KEY)) {
        jedis.hset(ROUTING_STATUS_CACHE_KEY, RoutingStatus.NOT_ROUTED.name(), "Not routed");
        jedis.hset(ROUTING_STATUS_CACHE_KEY, RoutingStatus.ROUTED.name(),     "Routed");
        jedis.hset(ROUTING_STATUS_CACHE_KEY, RoutingStatus.MISROUTED.name(),  "Misrouted");
      }

      // Transport status labels (replaces static EnumMap transportStatusLabels, line 14)
      if (!jedis.exists(TRANSPORT_STATUS_CACHE_KEY)) {
        jedis.hset(TRANSPORT_STATUS_CACHE_KEY, TransportStatus.NOT_RECEIVED.name(),    "Not received");
        jedis.hset(TRANSPORT_STATUS_CACHE_KEY, TransportStatus.IN_PORT.name(),         "In port");
        jedis.hset(TRANSPORT_STATUS_CACHE_KEY, TransportStatus.ONBOARD_CARRIER.name(), "Onboard carrier");
        jedis.hset(TRANSPORT_STATUS_CACHE_KEY, TransportStatus.CLAIMED.name(),         "Claimed");
        jedis.hset(TRANSPORT_STATUS_CACHE_KEY, TransportStatus.UNKNOWN.name(),         "Unknown");
      }
    }
  }

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  /**
   * Returns the human-readable routing status label, fetched from Amazon ElastiCache (Redis).
   *
   * @return routing status label string
   */
  public String getRoutingStatus() {
    try (Jedis jedis = RedisConfig.getPool().getResource()) {
      return jedis.hget(ROUTING_STATUS_CACHE_KEY, cargo.getDelivery().getRoutingStatus().name());
    }
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  /**
   * Returns the human-readable transport status label, fetched from Amazon ElastiCache (Redis).
   *
   * @return transport status label string
   */
  public String getTransportStatus() {
    try (Jedis jedis = RedisConfig.getPool().getResource()) {
      return jedis.hget(TRANSPORT_STATUS_CACHE_KEY, cargo.getDelivery().getTransportStatus().name());
    }
  }

  public boolean isAtDestination() {
    return cargo.getDelivery().isUnloadedAtDestination();
  }

  public LocationViewAdapter getOrigin() {
    return new LocationViewAdapter(cargo.getOrigin());
  }

  public LocationViewAdapter getLastKnownLocation() {
    return new LocationViewAdapter(cargo.getDelivery().getLastKnownLocation());
  }

  public LocationViewAdapter getLocation() {
    return cargo.getDelivery().getTransportStatus() == TransportStatus.NOT_RECEIVED
        ? getOrigin()
        : getLastKnownLocation();
  }

  public String getStatusCode() {
    RoutingStatus routingStatus = cargo.getDelivery().getRoutingStatus();

    if (routingStatus == RoutingStatus.NOT_ROUTED || routingStatus == RoutingStatus.MISROUTED) {
      return routingStatus.toString();
    }

    if (cargo.getDelivery().isMisdirected()) {
      return "MISDIRECTED";
    }

    if (cargo.getDelivery().isUnloadedAtDestination()) {
      return "AT_DESTINATION";
    }

    return cargo.getDelivery().getTransportStatus().toString();
  }
}
