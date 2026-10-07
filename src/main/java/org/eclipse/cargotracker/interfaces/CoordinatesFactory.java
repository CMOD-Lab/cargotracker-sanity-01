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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.infrastructure.cache.RedisConnectionProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Coordinates are cached in Amazon ElastiCache for Redis with a configurable TTL (default 3600
 * seconds) to prevent unbounded in-memory growth and to ensure consistency across multiple
 * application instances. A local fallback map is used when Redis is unavailable.
 */
public class CoordinatesFactory {

  private static final Logger LOGGER = Logger.getLogger(CoordinatesFactory.class.getName());

  /** Redis key prefix for coordinates cache entries. */
  private static final String CACHE_KEY_PREFIX = "coordinates:";

  /**
   * TTL in seconds for each coordinates cache entry in Redis.
   * Reads from the {@code COORDINATES_CACHE_TTL_SECONDS} environment variable;
   * defaults to 3600 (1 hour).
   */
  private static final int CACHE_TTL_SECONDS;

  static {
    String ttlEnv = System.getenv("COORDINATES_CACHE_TTL_SECONDS");
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

  /**
   * Static fallback map used when Redis is unavailable.
   * This map is immutable and never grows, so it does not cause unbounded memory growth.
   */
  private static final Map<String, Coordinates> FALLBACK_MAP;

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

    FALLBACK_MAP = Collections.unmodifiableMap(map);

    // Pre-warm Redis cache with all known coordinates on startup.
    warmRedisCache(map);
  }

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  /**
   * Looks up coordinates for the given UN/LOCODE string.
   *
   * <p>The lookup order is:
   * <ol>
   *   <li>Amazon ElastiCache for Redis (with TTL-based expiration)</li>
   *   <li>Local fallback map (used when Redis is unavailable)</li>
   * </ol>
   *
   * @param unLocode the UN/LOCODE identifier string
   * @return the {@link Coordinates} for the location, or {@code null} if not found
   */
  public static Coordinates find(String unLocode) {
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      String key = CACHE_KEY_PREFIX + unLocode;
      String cached = jedis.get(key);
      if (cached != null) {
        return deserializeCoordinates(cached);
      }
      // Cache miss: look up in fallback map and store in Redis with TTL.
      Coordinates coordinates = FALLBACK_MAP.get(unLocode);
      if (coordinates != null) {
        jedis.setex(key, CACHE_TTL_SECONDS, serializeCoordinates(coordinates));
      }
      return coordinates;
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable for coordinates lookup of ''{0}''; using fallback map. Error: {1}",
          new Object[] {unLocode, e.getMessage()});
      return FALLBACK_MAP.get(unLocode);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  /**
   * Pre-warms the Redis cache with all known coordinates so that the first request
   * for any location is served from Redis rather than the fallback map.
   */
  private static void warmRedisCache(Map<String, Coordinates> map) {
    try (Jedis jedis = RedisConnectionProvider.getPool().getResource()) {
      for (Map.Entry<String, Coordinates> entry : map.entrySet()) {
        String key = CACHE_KEY_PREFIX + entry.getKey();
        // Only set if not already present to avoid overwriting a valid cached value.
        jedis.setnx(key, serializeCoordinates(entry.getValue()));
        jedis.expire(key, CACHE_TTL_SECONDS);
      }
    } catch (JedisException e) {
      LOGGER.log(
          Level.WARNING,
          "Redis unavailable during cache warm-up; coordinates will be served from fallback map. Error: {0}",
          e.getMessage());
    }
  }

  /**
   * Serialises a {@link Coordinates} instance to a compact string {@code "lat,lon"}.
   *
   * @param coordinates the coordinates to serialise
   * @return a string representation suitable for storage in Redis
   */
  private static String serializeCoordinates(Coordinates coordinates) {
    return coordinates.getLatitude() + "," + coordinates.getLongitude();
  }

  /**
   * Deserialises a {@link Coordinates} instance from the compact string {@code "lat,lon"}.
   *
   * @param value the string value retrieved from Redis
   * @return the deserialised {@link Coordinates}, or {@code null} if parsing fails
   */
  private static Coordinates deserializeCoordinates(String value) {
    try {
      String[] parts = value.split(",");
      if (parts.length == 2) {
        double lat = Double.parseDouble(parts[0]);
        double lon = Double.parseDouble(parts[1]);
        return new Coordinates(lat, lon);
      }
    } catch (NumberFormatException e) {
      LOGGER.log(Level.WARNING, "Failed to deserialize coordinates from Redis value: {0}", value);
    }
    return null;
  }
}
