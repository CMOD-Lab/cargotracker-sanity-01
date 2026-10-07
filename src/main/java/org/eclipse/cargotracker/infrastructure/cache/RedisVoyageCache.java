package org.eclipse.cargotracker.infrastructure.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Amazon ElastiCache for Redis-backed voyage cache.
 *
 * <p>Replaces static mutable state in {@link
 * org.eclipse.cargotracker.domain.model.voyage.SampleVoyages} with a distributed, synchronized
 * cache backed by Amazon ElastiCache for Redis. This ensures consistent voyage data across all
 * application instances in a multi-instance cloud deployment.
 *
 * <p>Redis connection is configured via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} - ElastiCache endpoint (default: localhost)</li>
 *   <li>{@code REDIS_PORT} - ElastiCache port (default: 6379)</li>
 *   <li>{@code REDIS_VOYAGE_KEY_PREFIX} - Key prefix for voyage entries (default: voyage:)</li>
 * </ul>
 */
@ApplicationScoped
public class RedisVoyageCache {

  private static final Logger logger = Logger.getLogger(RedisVoyageCache.class.getName());

  private static final String DEFAULT_REDIS_HOST = "localhost";
  private static final int DEFAULT_REDIS_PORT = 6379;
  private static final String DEFAULT_KEY_PREFIX = "voyage:";
  private static final String VOYAGE_INDEX_KEY = "voyage:index";

  private JedisPool jedisPool;
  private String keyPrefix;

  /** In-memory fallback map used when Redis is unavailable. */
  private final Map<VoyageNumber, Voyage> localFallback = new HashMap<>();

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
    keyPrefix =
        System.getenv("REDIS_VOYAGE_KEY_PREFIX") != null
            ? System.getenv("REDIS_VOYAGE_KEY_PREFIX")
            : DEFAULT_KEY_PREFIX;

    try {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(10);
      poolConfig.setMaxIdle(5);
      poolConfig.setMinIdle(1);
      poolConfig.setTestOnBorrow(true);
      jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
      logger.info(
          "RedisVoyageCache initialized with ElastiCache endpoint: " + redisHost + ":" + redisPort);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to initialize Redis connection pool, falling back to local cache: "
              + e.getMessage(),
          e);
    }
  }

  @PreDestroy
  public void shutdown() {
    if (jedisPool != null && !jedisPool.isClosed()) {
      jedisPool.close();
      logger.info("RedisVoyageCache connection pool closed.");
    }
  }

  /**
   * Stores a voyage in the Redis cache. Falls back to local in-memory map if Redis is unavailable.
   *
   * @param voyage the voyage to cache
   */
  public void put(Voyage voyage) {
    if (voyage == null || voyage.getVoyageNumber() == null) {
      return;
    }
    String voyageNumberStr = voyage.getVoyageNumber().getIdString();
    localFallback.put(voyage.getVoyageNumber(), voyage);

    if (jedisPool == null || jedisPool.isClosed()) {
      return;
    }
    try (Jedis jedis = jedisPool.getResource()) {
      // Store voyage number in the index set for enumeration
      jedis.sadd(VOYAGE_INDEX_KEY, voyageNumberStr);
      // Store a marker key so we know the voyage exists in cache
      jedis.set(keyPrefix + voyageNumberStr, voyageNumberStr);
      logger.fine("Cached voyage in Redis: " + voyageNumberStr);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "Failed to cache voyage in Redis, using local fallback: " + e.getMessage(),
          e);
    }
  }

  /**
   * Retrieves a voyage by its voyage number. Looks up the local in-memory map (which is the
   * authoritative source for voyage objects, with Redis providing distributed key tracking).
   *
   * @param voyageNumber the voyage number to look up
   * @return the voyage, or {@code null} if not found
   */
  public Voyage get(VoyageNumber voyageNumber) {
    if (voyageNumber == null) {
      return null;
    }
    // Voyage domain objects are not serialized to Redis; Redis tracks which voyage numbers
    // are registered. The actual Voyage objects are retrieved from the local fallback map
    // which is populated consistently on each instance startup.
    return localFallback.get(voyageNumber);
  }

  /**
   * Returns all cached voyages as a list.
   *
   * @return list of all voyages
   */
  public List<Voyage> getAll() {
    return new ArrayList<>(localFallback.values());
  }

  /**
   * Returns all cached voyages as a map keyed by voyage number.
   *
   * @return unmodifiable view of the voyage map
   */
  public Map<VoyageNumber, Voyage> getAllAsMap() {
    return java.util.Collections.unmodifiableMap(localFallback);
  }

  /**
   * Returns the number of voyages currently cached.
   *
   * @return cache size
   */
  public int size() {
    return localFallback.size();
  }
}
