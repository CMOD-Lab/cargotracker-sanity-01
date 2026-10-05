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

import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * At the moment, coordinates are produced by a simple factory backed by Amazon ElastiCache for
 * Redis. The Redis-backed store replaces the previous unbounded static in-memory map, providing
 * TTL-controlled expiration, consistent data across multiple instances, and centralized cache
 * management (cr-java-0067).
 */
public class CoordinatesFactory {

  /** TTL for coordinate entries in Redis (seconds). 24 hours. */
  private static final int COORDINATES_TTL_SECONDS = 86400;

  /** Redis key prefix for coordinate cache entries. */
  private static final String REDIS_KEY_PREFIX = "coordinates:";

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

    // Pre-populate the Redis cache with the known coordinate data.
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      putCoordinate(jedis, HONGKONG.getUnLocode().getIdString(), 22, 114);
      putCoordinate(jedis, MELBOURNE.getUnLocode().getIdString(), -38, 145);
      putCoordinate(jedis, STOCKHOLM.getUnLocode().getIdString(), 59, 18);
      putCoordinate(jedis, HELSINKI.getUnLocode().getIdString(), 60, 25);
      putCoordinate(jedis, CHICAGO.getUnLocode().getIdString(), 42, -88);
      putCoordinate(jedis, TOKYO.getUnLocode().getIdString(), 36, 140);
      putCoordinate(jedis, HAMBURG.getUnLocode().getIdString(), 54, 10);
      putCoordinate(jedis, SHANGHAI.getUnLocode().getIdString(), 31, 121);
      putCoordinate(jedis, ROTTERDAM.getUnLocode().getIdString(), 52, 5);
      putCoordinate(jedis, GOTHENBURG.getUnLocode().getIdString(), 58, 12);
      putCoordinate(jedis, HANGZOU.getUnLocode().getIdString(), 30, 120);
      putCoordinate(jedis, NEWYORK.getUnLocode().getIdString(), 41, -74);
      putCoordinate(jedis, DALLAS.getUnLocode().getIdString(), 33, -97);
      putCoordinate(jedis, UNKNOWN.getUnLocode().getIdString(), -90, 0); // The South Pole.
    }
  }

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  /**
   * Stores a coordinate entry in Redis with the configured TTL.
   *
   * @param jedis  active Jedis connection
   * @param unLocode  UN/LOCODE string key
   * @param latitude  latitude value
   * @param longitude longitude value
   */
  private static void putCoordinate(Jedis jedis, String unLocode, double latitude, double longitude) {
    String key = REDIS_KEY_PREFIX + unLocode;
    // Store as "latitude,longitude" string; set TTL so entries expire and are refreshed.
    jedis.setex(key, COORDINATES_TTL_SECONDS, latitude + "," + longitude);
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  /**
   * Looks up coordinates for the given UN/LOCODE from Amazon ElastiCache for Redis.
   * Returns {@code null} if the entry is not found or has expired.
   *
   * @param unLocode UN/LOCODE string
   * @return Coordinates or null
   */
  public static Coordinates find(String unLocode) {
    String key = REDIS_KEY_PREFIX + unLocode;
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      String value = jedis.get(key);
      if (value == null) {
        return null;
      }
      String[] parts = value.split(",");
      double latitude = Double.parseDouble(parts[0]);
      double longitude = Double.parseDouble(parts[1]);
      return new Coordinates(latitude, longitude);
    }
  }
}
