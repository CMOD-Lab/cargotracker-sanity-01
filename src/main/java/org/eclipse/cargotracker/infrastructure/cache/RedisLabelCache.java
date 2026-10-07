package org.eclipse.cargotracker.infrastructure.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Amazon ElastiCache for Redis-backed label cache for enum-to-display-string mappings.
 *
 * <p>Replaces the static {@code EnumMap} fields in {@link
 * org.eclipse.cargotracker.interfaces.booking.sse.RealtimeCargoTrackingViewAdapter} (rule
 * cr-java-0067) with a distributed, TTL-controlled cache backed by Amazon ElastiCache for Redis.
 * This prevents unbounded in-memory growth and ensures consistent label data across all application
 * instances in a multi-instance cloud deployment.
 *
 * <p>Redis connection is configured via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} - ElastiCache endpoint (default: localhost)</li>
 *   <li>{@code REDIS_PORT} - ElastiCache port (default: 6379)</li>
 *   <li>{@code REDIS_LABEL_TTL_SECONDS} - TTL for label entries in seconds
 *       (default: 86400, i.e. 24 hours)</li>
 *   <li>{@code REDIS_LABEL_KEY_PREFIX} - Key prefix for label entries (default: label:)</li>
 * </ul>
 */
@ApplicationScoped
public class RedisLabelCache {

  private static final Logger logger = Logger.getLogger(RedisLabelCache.class.getName());

  private static final String DEFAULT_REDIS_HOST = "localhost";
  private static final int DEFAULT_REDIS_PORT = 6379;
  /** Default TTL: 24 hours — prevents indefinite memory growth and stale data. */
  private static final int DEFAULT_TTL_SECONDS = 86400;
  private static final String DEFAULT_KEY_PREFIX = "label:";

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
    String ttlEnv = System.getenv("REDIS_LABEL_TTL_SECONDS");
    if (ttlEnv != null && !ttlEnv.isEmpty()) {
      try {
        ttlSeconds = Integer.parseInt(ttlEnv);
      } catch (NumberFormatException e) {
        logger.warning(
            "Invalid REDIS_LABEL_TTL_SECONDS value, using default: " + DEFAULT_TTL_SECONDS);
      }
    }

    keyPrefix =
        System.getenv("REDIS_LABEL_KEY_PREFIX") != null
            ? System.getenv("REDIS_LABEL_KEY_PREFIX")
            : DEFAULT_KEY_PREFIX;

    try {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(10);
      poolConfig.setMaxIdle(5);
      poolConfig.setMinIdle(1);
      poolConfig.setTestOnBorrow(true);
      jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
      logger.info(
          "RedisLabelCache initialized with ElastiCache endpoint: "
              + redisHost
              + ":"
              + redisPort
              + ", TTL="
              + ttlSeconds
              + "s");
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to initialize Redis connection pool for label cache: " + e.getMessage(),
          e);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (jedisPool != null && !jedisPool.isClosed()) {
      jedisPool.close();
      logger.info("RedisLabelCache connection pool closed.");
    }
  }

  /**
   * Stores a label entry in the Redis cache with the configured TTL.
   *
   * <p>The {@code namespace} parameter is used to distinguish between different enum types (e.g.
   * {@code "routing"} vs {@code "transport"}) so that keys do not collide.
   *
   * @param namespace a short string identifying the enum type (e.g. "routing", "transport")
   * @param enumKey   the enum constant name (e.g. "ROUTED", "IN_PORT")
   * @param label     the human-readable display label
   */
  public void put(String namespace, String enumKey, String label) {
    if (namespace == null || enumKey == null || label == null) {
      return;
    }
    if (jedisPool == null || jedisPool.isClosed()) {
      logger.fine("Redis unavailable; skipping put for label key: " + namespace + ":" + enumKey);
      return;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.setex(keyPrefix + namespace + ":" + enumKey, ttlSeconds, label);
      logger.fine(
          "Cached label in Redis with TTL="
              + ttlSeconds
              + "s for key: "
              + namespace
              + ":"
              + enumKey);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to cache label in Redis for key "
              + namespace
              + ":"
              + enumKey
              + ": "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Retrieves a label entry from the Redis cache.
   *
   * @param namespace a short string identifying the enum type
   * @param enumKey   the enum constant name
   * @return the cached label, or {@code null} if not found or Redis is unavailable
   */
  public String get(String namespace, String enumKey) {
    if (namespace == null || enumKey == null) {
      return null;
    }
    if (jedisPool == null || jedisPool.isClosed()) {
      return null;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      return jedis.get(keyPrefix + namespace + ":" + enumKey);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to retrieve label from Redis for key "
              + namespace
              + ":"
              + enumKey
              + ": "
              + e.getMessage(),
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
