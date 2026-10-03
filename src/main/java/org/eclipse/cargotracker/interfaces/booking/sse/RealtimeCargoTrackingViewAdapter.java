package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.Map;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/** View adapter for displaying a cargo in a realtime tracking context. */
public class RealtimeCargoTrackingViewAdapter {

  // cz-java-0070 (line 12): replaced static in-process local cache
  // (Map<RoutingStatus, String> routingStatusLabels) with Amazon ElastiCache (Redis).
  // cz-java-0070 (line 14): replaced static in-process local cache
  // (Map<TransportStatus, String> transportStatusLabels) with Amazon ElastiCache (Redis).
  // Connection details are supplied via Kubernetes ConfigMap / Secret environment variables
  // (REDIS_HOST, REDIS_PORT).
  private static final JedisPool JEDIS_POOL;

  private static final String ROUTING_STATUS_KEY_PREFIX  = "routingStatusLabel:";
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "transportStatusLabel:";

  private final Cargo cargo;

  static {
    String redisHost = System.getenv("REDIS_HOST") != null
        ? System.getenv("REDIS_HOST") : "localhost";
    int redisPort = System.getenv("REDIS_PORT") != null
        ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);
    JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort);

    // Seed label data into ElastiCache on first initialisation.
    // Only writes if the key is not already present (supports multi-instance deployments).
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      seedIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.NOT_ROUTED.name(),  "Not routed");
      seedIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.ROUTED.name(),      "Routed");
      seedIfAbsent(jedis, ROUTING_STATUS_KEY_PREFIX + RoutingStatus.MISROUTED.name(),   "Misrouted");

      seedIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.NOT_RECEIVED.name(),    "Not received");
      seedIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.IN_PORT.name(),         "In port");
      seedIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.ONBOARD_CARRIER.name(), "Onboard carrier");
      seedIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.CLAIMED.name(),         "Claimed");
      seedIfAbsent(jedis, TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.UNKNOWN.name(),         "Unknown");
    }
  }

  private static void seedIfAbsent(Jedis jedis, String key, String value) {
    if (!jedis.exists(key)) {
      jedis.set(key, value);
    }
  }

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(ROUTING_STATUS_KEY_PREFIX + cargo.getDelivery().getRoutingStatus().name());
    }
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      return jedis.get(TRANSPORT_STATUS_KEY_PREFIX + cargo.getDelivery().getTransportStatus().name());
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
