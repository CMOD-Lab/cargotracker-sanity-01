package org.eclipse.cargotracker.infrastructure.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Provides a singleton JedisPool for connecting to Amazon ElastiCache for Redis.
 *
 * <p>Connection parameters are read from environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: {@code 6379})</li>
 * </ul>
 *
 * <p>This class replaces unbounded static in-memory caches with a centralised,
 * TTL-aware Redis cache that is consistent across all application instances.
 */
public final class RedisConnectionProvider {

  private static final String REDIS_HOST =
      System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";

  private static final int REDIS_PORT;

  static {
    String portEnv = System.getenv("REDIS_PORT");
    int port = 6379;
    if (portEnv != null && !portEnv.isEmpty()) {
      try {
        port = Integer.parseInt(portEnv);
      } catch (NumberFormatException ignored) {
        // fall back to default
      }
    }
    REDIS_PORT = port;
  }

  private static final JedisPool POOL;

  static {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(16);
    poolConfig.setMaxIdle(8);
    poolConfig.setMinIdle(2);
    poolConfig.setTestOnBorrow(true);
    poolConfig.setTestOnReturn(true);
    poolConfig.setTestWhileIdle(true);
    POOL = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
  }

  private RedisConnectionProvider() {
    /* Prevent instantiation. */
  }

  /**
   * Returns the shared {@link JedisPool} instance.
   *
   * @return the JedisPool connected to Amazon ElastiCache for Redis
   */
  public static JedisPool getPool() {
    return POOL;
  }
}
