package org.eclipse.cargotracker.interfaces.booking.sse;

import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>The static in-memory label maps (routingStatusLabels, transportStatusLabels) have been
 * replaced with Amazon ElastiCache for Redis-backed lookups with TTL expiration to prevent
 * unbounded memory growth and ensure consistency across multiple instances (cr-java-0067).
 */
public class RealtimeCargoTrackingViewAdapter {

  /** TTL for label entries in Redis (seconds). 24 hours. */
  private static final int LABEL_TTL_SECONDS = 86400;

  /** Redis key prefix for routing-status label entries. */
  private static final String ROUTING_STATUS_KEY_PREFIX = "label:routing:";

  /** Redis key prefix for transport-status label entries. */
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "label:transport:";

  /**
   * Shared JedisPool backed by Amazon ElastiCache for Redis.
   * The endpoint is read from the environment variable ELASTICACHE_REDIS_ENDPOINT
   * (format: host:port). Falls back to localhost:6379 for local development.
   */
  private static final JedisPool JEDIS_POOL;

  static {
    String redisEndpoint =
        System.getenv("ELASTICACHE_REDIS_ENDPOINT") != null
            ? System.getenv("ELASTICACHE_REDIS_ENDPOINT")
            : "localhost:6379";

    String[] parts = redisEndpoint.split(":");
    String host = parts[0];
    int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(16);
    poolConfig.setMaxIdle(8);
    poolConfig.setMinIdle(2);
    poolConfig.setTestOnBorrow(true);

    JEDIS_POOL = new JedisPool(poolConfig, host, port);

    // Pre-populate Redis with routing-status and transport-status labels.
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      // Routing status labels
      jedis.setex(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.NOT_ROUTED.name(),
          LABEL_TTL_SECONDS, "Not routed");
      jedis.setex(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.ROUTED.name(),
          LABEL_TTL_SECONDS, "Routed");
      jedis.setex(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.MISROUTED.name(),
          LABEL_TTL_SECONDS, "Misrouted");

      // Transport status labels
      jedis.setex(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.NOT_RECEIVED.name(),
          LABEL_TTL_SECONDS, "Not received");
      jedis.setex(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.IN_PORT.name(),
          LABEL_TTL_SECONDS, "In port");
      jedis.setex(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.ONBOARD_CARRIER.name(),
          LABEL_TTL_SECONDS, "Onboard carrier");
      jedis.setex(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.CLAIMED.name(),
          LABEL_TTL_SECONDS, "Claimed");
      jedis.setex(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.UNKNOWN.name(),
          LABEL_TTL_SECONDS, "Unknown");
    }
  }

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  /**
   * Returns the human-readable routing status label, fetched from Amazon ElastiCache for Redis.
   */
  public String getRoutingStatus() {
    String key = ROUTING_STATUS_KEY_PREFIX + cargo.getDelivery().getRoutingStatus().name();
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(key);
    }
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  /**
   * Returns the human-readable transport status label, fetched from Amazon ElastiCache for Redis.
   */
  public String getTransportStatus() {
    String key = TRANSPORT_STATUS_KEY_PREFIX + cargo.getDelivery().getTransportStatus().name();
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(key);
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
