package org.eclipse.cargotracker.interfaces.booking.sse;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Status label mappings are stored in Amazon ElastiCache (Redis) to support horizontal
 * scaling on EKS. Connection details are injected via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: {@code 6379})</li>
 * </ul>
 */
public class RealtimeCargoTrackingViewAdapter {

  private static final Logger LOGGER =
      Logger.getLogger(RealtimeCargoTrackingViewAdapter.class.getName());

  /** Redis key prefix for routing-status labels. */
  private static final String ROUTING_STATUS_KEY_PREFIX = "label:routing:";

  /** Redis key prefix for transport-status labels. */
  private static final String TRANSPORT_STATUS_KEY_PREFIX = "label:transport:";

  /** Lazily-initialised Redis client backed by Amazon ElastiCache. */
  private static volatile RedisClient redisClient;
  private static volatile StatefulRedisConnection<String, String> redisConnection;

  private final Cargo cargo;

  public RealtimeCargoTrackingViewAdapter(Cargo cargo) {
    this.cargo = cargo;
  }

  // ---------------------------------------------------------------------------
  // Redis initialisation
  // ---------------------------------------------------------------------------

  private static RedisCommands<String, String> getRedisCommands() {
    if (redisConnection == null || !redisConnection.isOpen()) {
      synchronized (RealtimeCargoTrackingViewAdapter.class) {
        if (redisConnection == null || !redisConnection.isOpen()) {
          String redisHost = System.getenv("REDIS_HOST") != null
              ? System.getenv("REDIS_HOST") : "localhost";
          int redisPort = 6379;
          String redisPortEnv = System.getenv("REDIS_PORT");
          if (redisPortEnv != null && !redisPortEnv.isEmpty()) {
            try {
              redisPort = Integer.parseInt(redisPortEnv);
            } catch (NumberFormatException e) {
              LOGGER.log(Level.WARNING, "Invalid REDIS_PORT value, using default 6379", e);
            }
          }
          RedisURI redisUri = RedisURI.builder()
              .withHost(redisHost)
              .withPort(redisPort)
              .build();
          redisClient = RedisClient.create(redisUri);
          redisConnection = redisClient.connect();
          // Seed label mappings on first connection
          seedLabels(redisConnection.sync());
        }
      }
    }
    return redisConnection.sync();
  }

  /**
   * Seeds the Redis cache with routing-status and transport-status label mappings.
   * Uses SETNX so externally-managed values are not overwritten.
   */
  private static void seedLabels(RedisCommands<String, String> commands) {
    // Routing status labels
    commands.setnx(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.NOT_ROUTED.name(), "Not routed");
    commands.setnx(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.ROUTED.name(), "Routed");
    commands.setnx(ROUTING_STATUS_KEY_PREFIX + RoutingStatus.MISROUTED.name(), "Misrouted");

    // Transport status labels
    commands.setnx(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.NOT_RECEIVED.name(), "Not received");
    commands.setnx(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.IN_PORT.name(), "In port");
    commands.setnx(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.ONBOARD_CARRIER.name(), "Onboard carrier");
    commands.setnx(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.CLAIMED.name(), "Claimed");
    commands.setnx(TRANSPORT_STATUS_KEY_PREFIX + TransportStatus.UNKNOWN.name(), "Unknown");
  }

  // ---------------------------------------------------------------------------
  // Label lookup helpers
  // ---------------------------------------------------------------------------

  private static String getRoutingStatusLabel(RoutingStatus status) {
    try {
      RedisCommands<String, String> commands = getRedisCommands();
      return commands.get(ROUTING_STATUS_KEY_PREFIX + status.name());
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to retrieve routing status label from Redis", e);
      return status.name();
    }
  }

  private static String getTransportStatusLabel(TransportStatus status) {
    try {
      RedisCommands<String, String> commands = getRedisCommands();
      return commands.get(TRANSPORT_STATUS_KEY_PREFIX + status.name());
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to retrieve transport status label from Redis", e);
      return status.name();
    }
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

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
}
