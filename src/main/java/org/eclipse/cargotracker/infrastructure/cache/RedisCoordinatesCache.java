package org.eclipse.cargotracker.infrastructure.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.interfaces.Coordinates;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Amazon ElastiCache for Redis-backed coordinates cache.
 *
 * <p>Replaces the static in-memory {@code COORDINATES_MAP} in {@link
 * org.eclipse.cargotracker.interfaces.CoordinatesFactory} with a distributed, TTL-controlled cache
 * backed by Amazon ElastiCache for Redis. This prevents unbounded memory growth and ensures
 * consistent coordinate data across all application instances in a multi-instance cloud deployment.
 *
 * <p>Redis connection is configured via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} - ElastiCache endpoint (default: localhost)</li>
 *   <li>{@code REDIS_PORT} - ElastiCache port (default: 6379)</li>
 *   <li>{@code REDIS_COORDINATES_TTL_SECONDS} - TTL for coordinate entries in seconds
 *       (default: 86400, i.e. 24 hours)</li>
 *   <li>{@code REDIS_COORDINATES_KEY_PREFIX} - Key prefix for coordinate entries
 *       (default: coordinates:)</li>
 * </ul>
 */
@ApplicationScoped
public class RedisCoordinatesCache {

  private static final Logger logger = Logger.getLogger(RedisCoordinatesCache.class.getName());

  private static final String DEFAULT_REDIS_HOST = "localhost";
  private static final int DEFAULT_REDIS_PORT = 6379;
  /** Default TTL: 24 hours — prevents indefinite memory growth and stale data. */
  private static final int DEFAULT_TTL_SECONDS = 86400;
  private static final String DEFAULT_KEY_PREFIX = "coordinates:";

  /** Separator used when serialising a {@link Coordinates} pair to a Redis string value. */
  private static final String COORD_SEPARATOR = ",";

  private JedisPool jedisPool;
  private int ttlSeconds;
  private String keyPrefix;

  @PostConstruct
  public void initialize() {
    String redisHost =
        System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : DEFAULT_REDIS_HOST;

    int redisPort = DEFAULT_REDIS_PORT;
    String redisPortEnv = System.getenv("REDIS_PORT");
    if (redisPortEnv != null && !redisPortEnv.isEmpty()) {
      try {
        redisPort = Integer.parseInt(redisPortEnv);
      } catch (NumberFormatException e) {
        logger.warning("Invalid REDIS_PORT value, using default: " + DEFAULT_REDIS_PORT);
      }
    }

    ttlSeconds = DEFAULT_TTL_SECONDS;
    String ttlEnv = System.getenv("REDIS_COORDINATES_TTL_SECONDS");
    if (ttlEnv != null && !ttlEnv.isEmpty()) {
      try {
        ttlSeconds = Integer.parseInt(ttlEnv);
      } catch (NumberFormatException e) {
        logger.warning(
            "Invalid REDIS_COORDINATES_TTL_SECONDS value, using default: " + DEFAULT_TTL_SECONDS);
      }
    }

    keyPrefix =
        System.getenv("REDIS_COORDINATES_KEY_PREFIX") != null
            ? System.getenv("REDIS_COORDINATES_KEY_PREFIX")
            : DEFAULT_KEY_PREFIX;

    try {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(10);
      poolConfig.setMaxIdle(5);
      poolConfig.setMinIdle(1);
      poolConfig.setTestOnBorrow(true);
      jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
      logger.info(
          "RedisCoordinatesCache initialized with ElastiCache endpoint: "
              + redisHost
              + ":"
              + redisPort
              + ", TTL="
              + ttlSeconds
              + "s");
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to initialize Redis connection pool for coordinates cache: " + e.getMessage(),
          e);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (jedisPool != null && !jedisPool.isClosed()) {
      jedisPool.close();
      logger.info("RedisCoordinatesCache connection pool closed.");
    }
  }

  /**
   * Stores a {@link Coordinates} entry in the Redis cache with the configured TTL.
   *
   * <p>Falls back silently if Redis is unavailable — the caller is responsible for maintaining a
   * local fallback map for the startup seed data.
   *
   * @param unLocode the UN/LOCODE string key
   * @param coordinates the coordinates to cache
   */
  public void put(String unLocode, Coordinates coordinates) {
    if (unLocode == null || coordinates == null) {
      return;
    }
    if (jedisPool == null || jedisPool.isClosed()) {
      logger.fine("Redis unavailable; skipping put for coordinates key: " + unLocode);
      return;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      String value = coordinates.getLatitude() + COORD_SEPARATOR + coordinates.getLongitude();
      jedis.setex(keyPrefix + unLocode, ttlSeconds, value);
      logger.fine("Cached coordinates in Redis with TTL=" + ttlSeconds + "s for key: " + unLocode);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to cache coordinates in Redis for key " + unLocode + ": " + e.getMessage(),
          e);
    }
  }

  /**
   * Retrieves a {@link Coordinates} entry from the Redis cache.
   *
   * @param unLocode the UN/LOCODE string key
   * @return the cached {@link Coordinates}, or {@code null} if not found or Redis is unavailable
   */
  public Coordinates get(String unLocode) {
    if (unLocode == null) {
      return null;
    }
    if (jedisPool == null || jedisPool.isClosed()) {
      return null;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      String value = jedis.get(keyPrefix + unLocode);
      if (value == null) {
        return null;
      }
      String[] parts = value.split(COORD_SEPARATOR, 2);
      if (parts.length != 2) {
        logger.warning("Malformed coordinates value in Redis for key: " + unLocode);
        return null;
      }
      double latitude = Double.parseDouble(parts[0]);
      double longitude = Double.parseDouble(parts[1]);
      return new Coordinates(latitude, longitude);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to retrieve coordinates from Redis for key " + unLocode + ": " + e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * Returns {@code true} if the Redis connection pool is available and open.
   *
   * @return {@code true} when Redis is reachable
   */
  public boolean isAvailable() {
    return jedisPool != null && !jedisPool.isClosed();
  }
}
