package org.eclipse.cargotracker.interfaces.booking.sse;

import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.infrastructure.cache.RedisCache;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>cz-java-0070: The previous local in-process EnumMap caches for routing-status labels
 * (line 12) and transport-status labels (line 14) have been replaced with Amazon ElastiCache
 * for Redis so that label data is shared across all horizontally-scaled container replicas on EKS.
 * Connection details are injected via the REDIS_HOST and REDIS_PORT environment variables
 * (backed by Kubernetes ConfigMaps / Secrets with IRSA-secured access).
 */
public class RealtimeCargoTrackingViewAdapter {

  // cz-java-0070 fix (line 12): Removed local in-process EnumMap 'routingStatusLabels'.
  // cz-java-0070 fix (line 14): Removed local in-process EnumMap 'transportStatusLabels'.
  // Both label lookups are now delegated to Amazon ElastiCache (Redis) via RedisCache,
  // eliminating per-JVM caches that would not be shared across horizontally-scaled replicas.

  private static final String ROUTING_STATUS_KEY_PREFIX = "routing-status-label:";
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "transport-status-label:";

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
    initializeStatusLabelsIfAbsent();
  }

  /**
   * Initialises routing-status and transport-status label entries in Redis if they are not
   * already present. This ensures all container replicas share the same label data via
   * Amazon ElastiCache.
   */
  private static void initializeStatusLabelsIfAbsent() {
    // Routing status labels
    putRoutingLabelIfAbsent(RoutingStatus.NOT_ROUTED, "Not routed");
    putRoutingLabelIfAbsent(RoutingStatus.ROUTED, "Routed");
    putRoutingLabelIfAbsent(RoutingStatus.MISROUTED, "Misrouted");

    // Transport status labels
    putTransportLabelIfAbsent(TransportStatus.NOT_RECEIVED, "Not received");
    putTransportLabelIfAbsent(TransportStatus.IN_PORT, "In port");
    putTransportLabelIfAbsent(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    putTransportLabelIfAbsent(TransportStatus.CLAIMED, "Claimed");
    putTransportLabelIfAbsent(TransportStatus.UNKNOWN, "Unknown");
  }

  private static void putRoutingLabelIfAbsent(RoutingStatus status, String label) {
    String key = ROUTING_STATUS_KEY_PREFIX + status.name();
    if (!RedisCache.exists(key)) {
      RedisCache.set(key, label);
    }
  }

  private static void putTransportLabelIfAbsent(TransportStatus status, String label) {
    String key = TRANSPORT_STATUS_KEY_PREFIX + status.name();
    if (!RedisCache.exists(key)) {
      RedisCache.set(key, label);
    }
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    String key = ROUTING_STATUS_KEY_PREFIX + cargo.getDelivery().getRoutingStatus().name();
    return RedisCache.get(key);
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    String key = TRANSPORT_STATUS_KEY_PREFIX + cargo.getDelivery().getTransportStatus().name();
    return RedisCache.get(key);
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
