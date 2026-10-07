package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.infrastructure.cache.RedisConnectionProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Status label lookups are backed by Amazon ElastiCache for Redis with a configurable TTL
 * (default 3600 seconds) to prevent unbounded in-memory growth and to ensure consistency across
 * multiple application instances. Hard-coded fallback values are used when Redis is unavailable.
 */
public class RealtimeCargoTrackingViewAdapter {

  private static final Logger LOGGER =
      Logger.getLogger(RealtimeCargoTrackingViewAdapter.class.getName());

  /** Redis key prefix for routing-status label cache entries. */
  private static final String ROUTING_STATUS_KEY_PREFIX = "label:routing:";

  /** Redis key prefix for transport-status label cache entries. */
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "label:transport:";

  /**
   * TTL in seconds for each status-label cache entry in Redis.
   * Reads from the {@code STATUS_LABEL_CACHE_TTL_SECONDS} environment variable;
   * defaults to 3600 (1 hour).
   */
  private static final int CACHE_TTL_SECONDS;

  static {
    String ttlEnv = System.getenv("STATUS_LABEL_CACHE_TTL_SECONDS");
    int ttl = 3600;
    if (ttlEnv != null && !ttlEnv.isEmpty()) {
      try {
        ttl = Integer.parseInt(ttlEnv);
      } catch (NumberFormatException ignored) {
        // fall back to default
      }
    }
    CACHE_TTL_SECONDS = ttl;
  }

  static {
    // Pre-warm Redis with routing-status labels.
    warmRoutingStatusLabels();
    // Pre-warm Redis with transport-status labels.
    warmTransportStatusLabels();
  }

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  public String getRoutingStatus() {
    return getRoutingStatusLabel(cargo.getDelivery().getRoutingStatus());
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  public String getTransportStatus() {
    return getTransportStatusLabel(cargo.getDelivery().getTransportStatus());
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

  // ---------------------------------------------------------------------------
  // Private helpers – Redis-backed label lookups with TTL
  // ---------------------------------------------------------------------------

  /**
   * Retrieves the human-readable label for a {@link RoutingStatus} from Amazon ElastiCache for
   * Redis. Falls back to a hard-coded value when Redis is unavailable.
   *
   * @param status the routing status
   * @return the display label, or the enum name if not found
   */
  private static String getRoutingStatusLabel(RoutingStatus status) {
    String key = ROUTING_STATUS_KEY_PREFIX + status.name();
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      String label = jedis.get(key);
      if (label != null) {
        return label;
      }
      // Cache miss: store fallback value with TTL.
      String fallback = routingStatusFallback(status);
      jedis.setex(key, CACHE_TTL_SECONDS, fallback);
      return fallback;
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable for routing-status label lookup of ''{0}''; using fallback. Error: {1}",
          new Object[] {status, e.getMessage()});
      return routingStatusFallback(status);
    }
  }

  /**
   * Retrieves the human-readable label for a {@link TransportStatus} from Amazon ElastiCache for
   * Redis. Falls back to a hard-coded value when Redis is unavailable.
   *
   * @param status the transport status
   * @return the display label, or the enum name if not found
   */
  private static String getTransportStatusLabel(TransportStatus status) {
    String key = TRANSPORT_STATUS_KEY_PREFIX + status.name();
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      String label = jedis.get(key);
      if (label != null) {
        return label;
      }
      // Cache miss: store fallback value with TTL.
      String fallback = transportStatusFallback(status);
      jedis.setex(key, CACHE_TTL_SECONDS, fallback);
      return fallback;
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable for transport-status label lookup of ''{0}''; using fallback. Error: {1}",
          new Object[] {status, e.getMessage()});
      return transportStatusFallback(status);
    }
  }

  /** Returns the hard-coded fallback label for a {@link RoutingStatus}. */
  private static String routingStatusFallback(RoutingStatus status) {
    switch (status) {
      case NOT_ROUTED:
        return "Not routed";
      case ROUTED:
        return "Routed";
      case MISROUTED:
        return "Misrouted";
      default:
        return status.name();
    }
  }

  /** Returns the hard-coded fallback label for a {@link TransportStatus}. */
  private static String transportStatusFallback(TransportStatus status) {
    switch (status) {
      case NOT_RECEIVED:
        return "Not received";
      case IN_PORT:
        return "In port";
      case ONBOARD_CARRIER:
        return "Onboard carrier";
      case CLAIMED:
        return "Claimed";
      case UNKNOWN:
        return "Unknown";
      default:
        return status.name();
    }
  }

  /** Pre-warms Redis with all known routing-status labels. */
  private static void warmRoutingStatusLabels() {
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      for (RoutingStatus status : RoutingStatus.values()) {
        String key = ROUTING_STATUS_KEY_PREFIX + status.name();
        jedis.setnx(key, routingStatusFallback(status));
        jedis.expire(key, CACHE_TTL_SECONDS);
      }
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable during routing-status label warm-up; labels will be served from fallback. Error: {0}",
          e.getMessage());
    }
  }

  /** Pre-warms Redis with all known transport-status labels. */
  private static void warmTransportStatusLabels() {
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      for (TransportStatus status : TransportStatus.values()) {
        String key = TRANSPORT_STATUS_KEY_PREFIX + status.name();
        jedis.setnx(key, transportStatusFallback(status));
        jedis.expire(key, CACHE_TTL_SECONDS);
      }
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable during transport-status label warm-up; labels will be served from fallback. Error: {0}",
          e.getMessage());
    }
  }
}
