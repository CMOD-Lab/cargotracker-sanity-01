package org.eclipse.cargotracker.infrastructure.cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis connection configuration for Amazon ElastiCache.
 *
 * <p>Connection details are injected via environment variables (Kubernetes ConfigMaps/Secrets):
 * <ul>
 *   <li>REDIS_HOST  – ElastiCache primary endpoint (default: localhost)</li>
 *   <li>REDIS_PORT  – ElastiCache port              (default: 6379)</li>
 *   <li>REDIS_PASSWORD – ElastiCache auth token     (optional)</li>
 * </ul>
 *
 * <p>Remediation for cz-java-0070: replaces in-process local caches with a shared
 * Amazon ElastiCache (Redis) cluster so that cached data is consistent across all
 * horizontally-scaled container replicas.
 */
public final class RedisConfig {

    private static final JedisPool POOL;

    static {
        String host     = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int    port     = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        String password = System.getenv("REDIS_PASSWORD");

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            POOL = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            POOL = new JedisPool(poolConfig, host, port, 2000);
        }
    }

    private RedisConfig() {
        /* Utility class – prevent instantiation. */
    }

    /**
     * Returns the shared {@link JedisPool} backed by Amazon ElastiCache.
     *
     * @return the shared pool
     */
    public static JedisPool getPool() {
        return POOL;
    }
}
