package org.eclipse.cargotracker.interfaces.booking.sse;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.cargo.Cargo;
import org.eclipse.cargotracker.domain.model.cargo.RoutingStatus;
import org.eclipse.cargotracker.domain.model.cargo.TransportStatus;
import org.eclipse.cargotracker.infrastructure.cache.RedisLabelCache;
import org.eclipse.cargotracker.interfaces.CoordinatesFactory;

/**
 * View adapter for displaying a cargo in a realtime tracking context.
 *
 * <p>Previously this class held two static {@code EnumMap} fields ({@code routingStatusLabels} and
 * {@code transportStatusLabels}) without any TTL or expiration policy (rule cr-java-0067). Those
 * maps have been replaced by {@link RedisLabelCache} which enforces a configurable TTL (default
 * 24 h) via the {@code REDIS_LABEL_TTL_SECONDS} environment variable, preventing unbounded
 * in-memory growth and ensuring consistent label data across all cloud instances.
 *
 * <p>Local seed maps are retained as a fallback when Redis is temporarily unavailable and to
 * perform write-through population of the Redis cache on first access.
 */
public class RealtimeCargoTrackingViewAdapter {

  private static final Logger logger =
      Logger.getLogger(RealtimeCargoTrackingViewAdapter.class.getName());

  /** Namespace key used in {@link RedisLabelCache} for routing-status labels. */
  private static final String ROUTING_NS = "routing";

  /** Namespace key used in {@link RedisLabelCache} for transport-status labels. */
  private static final String TRANSPORT_NS = "transport";

  /**
   * Local seed map for routing-status labels — used as a fallback when Redis is unavailable and
   * to seed the Redis cache on first access. NOT used as the primary cache.
   */
  private static final Map<RoutingStatus, String> ROUTING_STATUS_SEED =
      new EnumMap<>(RoutingStatus.class);

  /**
   * Local seed map for transport-status labels — used as a fallback when Redis is unavailable and
   * to seed the Redis cache on first access. NOT used as the primary cache.
   */
  private static final Map<TransportStatus, String> TRANSPORT_STATUS_SEED =
      new EnumMap<>(TransportStatus.class);

  static {
    ROUTING_STATUS_SEED.put(RoutingStatus.NOT_ROUTED, "Not routed");
    ROUTING_STATUS_SEED.put(RoutingStatus.ROUTED, "Routed");
    ROUTING_STATUS_SEED.put(RoutingStatus.MISROUTED, "Misrouted");

    TRANSPORT_STATUS_SEED.put(TransportStatus.NOT_RECEIVED, "Not received");
    TRANSPORT_STATUS_SEED.put(TransportStatus.IN_PORT, "In port");
    TRANSPORT_STATUS_SEED.put(TransportStatus.ONBOARD_CARRIER, "Onboard carrier");
    TRANSPORT_STATUS_SEED.put(TransportStatus.CLAIMED, "Claimed");
    TRANSPORT_STATUS_SEED.put(TransportStatus.UNKNOWN, "Unknown");
  }

  private final Cargo cargo;
  private final RedisLabelCache redisLabelCache;
  private final CoordinatesFactory coordinatesFactory;

  /**
   * Constructs a new adapter backed by the provided caches.
   *
   * @param cargo              the cargo domain object to adapt
   * @param redisLabelCache    the Redis-backed label cache (Amazon ElastiCache for Redis)
   * @param coordinatesFactory the Redis-backed coordinates factory (Amazon ElastiCache for Redis)
   */
  public RealtimeCargoTrackingViewAdapter(
      Cargo cargo, RedisLabelCache redisLabelCache, CoordinatesFactory coordinatesFactory) {
    this.cargo = cargo;
    this.redisLabelCache = redisLabelCache;
    this.coordinatesFactory = coordinatesFactory;
  }

  public String getTrackingId() {
    return cargo.getTrackingId().getIdString();
  }

  /**
   * Returns the human-readable routing status label.
   *
   * <p>Lookup order:
   * <ol>
   *   <li>Amazon ElastiCache for Redis (TTL-controlled)</li>
   *   <li>Local seed map (fallback when Redis is unavailable)</li>
   * </ol>
   */
  public String getRoutingStatus() {
    RoutingStatus status = cargo.getDelivery().getRoutingStatus();
    return resolveLabel(ROUTING_NS, status.name(), ROUTING_STATUS_SEED.get(status));
  }

  public boolean isMisdirected() {
    return cargo.getDelivery().isMisdirected();
  }

  /**
   * Returns the human-readable transport status label.
   *
   * <p>Lookup order:
   * <ol>
   *   <li>Amazon ElastiCache for Redis (TTL-controlled)</li>
   *   <li>Local seed map (fallback when Redis is unavailable)</li>
   * </ol>
   */
  public String getTransportStatus() {
    TransportStatus status = cargo.getDelivery().getTransportStatus();
    return resolveLabel(TRANSPORT_NS, status.name(), TRANSPORT_STATUS_SEED.get(status));
  }

  public boolean isAtDestination() {
    return cargo.getDelivery().isUnloadedAtDestination();
  }

  public LocationViewAdapter getOrigin() {
    return new LocationViewAdapter(cargo.getOrigin(), coordinatesFactory);
  }

  public LocationViewAdapter getLastKnownLocation() {
    return new LocationViewAdapter(cargo.getDelivery().getLastKnownLocation(), coordinatesFactory);
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

  /**
   * Resolves a label by first checking the Redis cache, then falling back to the provided seed
   * value. On a cache miss, the seed value is written back to Redis (write-through).
   *
   * @param namespace  the cache namespace (e.g. "routing", "transport")
   * @param enumKey    the enum constant name used as the cache key
   * @param seedValue  the fallback label from the local seed map
   * @return the resolved label, or {@code null} if not found anywhere
   */
  private String resolveLabel(String namespace, String enumKey, String seedValue) {
    // 1. Try Redis cache first (TTL-controlled, distributed)
    String cached = redisLabelCache.get(namespace, enumKey);
    if (cached != null) {
      return cached;
    }

    // 2. Fall back to seed map
    if (seedValue != null && redisLabelCache.isAvailable()) {
      // Write-through: populate Redis so future lookups are served from the distributed cache
      redisLabelCache.put(namespace, enumKey, seedValue);
      logger.fine(
          "Write-through: seeded Redis label cache for " + namespace + ":" + enumKey);
    }
    return seedValue;
  }
}
