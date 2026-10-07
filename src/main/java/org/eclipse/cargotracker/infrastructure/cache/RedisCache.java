package org.eclipse.cargotracker.infrastructure.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Shared Redis cache utility backed by Amazon ElastiCache.
 *
 * <p>Connection details are injected via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: {@code 6379})</li>
 * </ul>
 *
 * <p>cz-java-0070: Replaces local in-process caches (HashMap / EnumMap) with
 * Amazon ElastiCache for Redis so that cached data is shared across all
 * horizontally-scaled container replicas on EKS.
 */
public final class RedisCache {

    private static final JedisPool POOL;

    static {
        String host = System.getenv("REDIS_HOST") != null
                ? System.getenv("REDIS_HOST") : "localhost";
        int port = System.getenv("REDIS_PORT") != null
                ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);

        POOL = new JedisPool(poolConfig, host, port);
    }

    private RedisCache() {
        /* Prevent instantiation. */
    }

    /**
     * Retrieves a value from Redis by key.
     *
     * @param key the cache key
     * @return the cached value, or {@code null} if not present
     */
    public static String get(String key) {
        try (Jedis jedis = POOL.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * Stores a key-value pair in Redis.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    public static void set(String key, String value) {
        try (Jedis jedis = POOL.getResource()) {
            jedis.set(key, value);
        }
    }

    /**
     * Checks whether a key exists in Redis.
     *
     * @param key the cache key
     * @return {@code true} if the key exists
     */
    public static boolean exists(String key) {
        try (Jedis jedis = POOL.getResource()) {
            return jedis.exists(key);
        }
    }
}
