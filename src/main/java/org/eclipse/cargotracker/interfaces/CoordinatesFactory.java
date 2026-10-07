package org.eclipse.cargotracker.interfaces;

import static org.eclipse.cargotracker.domain.model.location.Location.UNKNOWN;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.CHICAGO;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.DALLAS;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.GOTHENBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HAMBURG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HANGZOU;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HELSINKI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.HONGKONG;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.MELBOURNE;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.NEWYORK;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.ROTTERDAM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.SHANGHAI;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.STOCKHOLM;
import static org.eclipse.cargotracker.domain.model.location.SampleLocations.TOKYO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.infrastructure.cache.RedisCoordinatesCache;

/**
 * Coordinates factory backed by Amazon ElastiCache for Redis.
 *
 * <p>Coordinate lookups are served from the Redis cache (with TTL-based expiration) to prevent
 * unbounded in-memory growth and to ensure consistent data across multiple cloud instances.
 * A local seed map is used to populate Redis on first access and as a fallback when Redis is
 * temporarily unavailable.
 *
 * <p>Previously this class held a static {@code COORDINATES_MAP} without any TTL or expiration
 * policy (rule cr-java-0067). That map has been replaced by {@link RedisCoordinatesCache} which
 * enforces a configurable TTL (default 24 h) via the {@code REDIS_COORDINATES_TTL_SECONDS}
 * environment variable.
 */
@ApplicationScoped
public class CoordinatesFactory {

  private static final Logger logger = Logger.getLogger(CoordinatesFactory.class.getName());

  /**
   * Seed map used to populate the Redis cache on startup and as a local fallback when Redis is
   * temporarily unavailable. This map is intentionally read-only and is NOT used as the primary
   * cache — all lookups go through {@link RedisCoordinatesCache} first.
   */
  private static final Map<String, Coordinates> SEED_MAP;

  static {
    Map<String, Coordinates> map = new HashMap<>();

    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    map.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    map.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    map.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    map.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    map.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    map.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    map.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    map.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    map.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    map.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    map.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    map.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    map.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    map.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.

    SEED_MAP = Collections.unmodifiableMap(map);
  }

  @Inject
  private RedisCoordinatesCache redisCoordinatesCache;

  /**
   * Looks up coordinates for the given {@link Location}.
   *
   * @param location the location whose coordinates are required
   * @return the {@link Coordinates}, or {@code null} if not found
   */
  public Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  /**
   * Looks up coordinates for the given {@link UnLocode}.
   *
   * @param unLocode the UN/LOCODE whose coordinates are required
   * @return the {@link Coordinates}, or {@code null} if not found
   */
  public Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  /**
   * Looks up coordinates for the given UN/LOCODE string.
   *
   * <p>Lookup order:
   * <ol>
   *   <li>Amazon ElastiCache for Redis (with TTL-based expiration)</li>
   *   <li>Local seed map (fallback when Redis is unavailable)</li>
   * </ol>
   * When a cache miss occurs in Redis but the entry exists in the seed map, the entry is
   * written back to Redis so that subsequent lookups are served from the distributed cache.
   *
   * @param unLocode the UN/LOCODE string
   * @return the {@link Coordinates}, or {@code null} if not found
   */
  public Coordinates find(String unLocode) {
    // 1. Try Redis cache first (TTL-controlled, distributed)
    Coordinates cached = redisCoordinatesCache.get(unLocode);
    if (cached != null) {
      return cached;
    }

    // 2. Fall back to seed map
    Coordinates seedCoordinates = SEED_MAP.get(unLocode);
    if (seedCoordinates != null && redisCoordinatesCache.isAvailable()) {
      // Write-through: populate Redis so future lookups are served from the distributed cache
      redisCoordinatesCache.put(unLocode, seedCoordinates);
      logger.fine("Write-through: seeded Redis coordinates cache for UN/LOCODE: " + unLocode);
    }
    return seedCoordinates;
  }
}
