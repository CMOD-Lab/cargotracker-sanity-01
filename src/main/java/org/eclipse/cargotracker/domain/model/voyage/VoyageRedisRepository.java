package org.eclipse.cargotracker.domain.model.voyage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis-backed repository for {@link Voyage} objects.
 *
 * <p><strong>Cloud Readiness (cr-java-0066):</strong> This class replaces the static mutable
 * {@code HashMap} that was previously held in {@link SampleVoyages#ALL}. By delegating all
 * shared mutable state to Amazon ElastiCache for Redis, every application instance in a
 * multi-instance AWS deployment reads from and writes to the same distributed store, eliminating
 * the data-inconsistency problem that arose when each JVM maintained its own independent copy.
 *
 * <h2>Connection configuration</h2>
 * The Redis endpoint is resolved from the environment variable
 * {@code ELASTICACHE_REDIS_ENDPOINT} (format: {@code host:port}). If the variable is absent
 * the repository falls back to a local in-memory map so that the application can still start
 * in non-cloud environments (e.g., developer workstations, unit-test runs).
 *
 * <h2>Serialisation</h2>
 * {@link Voyage} objects are serialised to/from JSON using a lightweight hand-rolled converter
 * so that no additional JSON library dependency is required beyond what Jakarta EE already
 * provides. Production deployments may replace this with a proper Jackson or Gson serialiser.
 */
public class VoyageRedisRepository {

  private static final Logger LOGGER = Logger.getLogger(VoyageRedisRepository.class.getName());

  /** Environment variable that carries the ElastiCache Redis endpoint ({@code host:port}). */
  private static final String REDIS_ENDPOINT_ENV = "ELASTICACHE_REDIS_ENDPOINT";

  /** Redis key prefix used for all voyage entries. */
  private static final String KEY_PREFIX = "voyage:";

  /** Redis key that holds the set of all known voyage-number strings. */
  private static final String INDEX_KEY = "voyage:index";

  // -------------------------------------------------------------------------
  // Internal state
  // -------------------------------------------------------------------------

  /**
   * Fallback in-memory store used when no Redis endpoint is configured.
   * This is intentionally NOT static so that each repository instance has its own
   * isolated fallback map (avoids re-introducing a static mutable variable).
   */
  private final Map<VoyageNumber, Voyage> fallbackStore = new HashMap<>();

  /** Whether a live Redis connection is available. */
  private final boolean redisAvailable;

  /** Resolved Redis host (null when Redis is unavailable). */
  private final String redisHost;

  /** Resolved Redis port (0 when Redis is unavailable). */
  private final int redisPort;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  /**
   * Creates a new repository instance.
   *
   * <p>Reads {@code ELASTICACHE_REDIS_ENDPOINT} from the environment. If the variable is set
   * and the endpoint is reachable the repository operates in Redis mode; otherwise it falls
   * back to an in-memory map and logs a warning.
   */
  public VoyageRedisRepository() {
    String endpoint = System.getenv(REDIS_ENDPOINT_ENV);
    if (endpoint != null && !endpoint.isBlank()) {
      String[] parts = endpoint.split(":", 2);
      String host = parts[0];
      int port = parts.length > 1 ? parsePort(parts[1]) : 6379;
      boolean available = probeConnection(host, port);
      this.redisHost = available ? host : null;
      this.redisPort = available ? port : 0;
      this.redisAvailable = available;
      if (!available) {
        LOGGER.warning(
            "VoyageRedisRepository: ElastiCache endpoint '"
                + endpoint
                + "' is not reachable. Falling back to in-memory store. "
                + "Shared state will NOT be consistent across multiple instances.");
      }
    } else {
      LOGGER.warning(
          "VoyageRedisRepository: Environment variable '"
              + REDIS_ENDPOINT_ENV
              + "' is not set. Falling back to in-memory store. "
              + "Shared state will NOT be consistent across multiple instances.");
      this.redisHost = null;
      this.redisPort = 0;
      this.redisAvailable = false;
    }
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Seeds the Redis store with the supplied voyages if they are not already present.
   *
   * <p>Uses SET NX (set-if-not-exists) semantics so that the first application instance to
   * start wins and subsequent instances do not overwrite data that may have been updated at
   * runtime.
   *
   * @param voyages the seed map (voyage number → voyage)
   */
  public void seedIfAbsent(Map<VoyageNumber, Voyage> voyages) {
    if (redisAvailable) {
      seedRedis(voyages);
    } else {
      // Populate the fallback store only if it is empty (first-writer-wins).
      if (fallbackStore.isEmpty()) {
        fallbackStore.putAll(voyages);
      }
    }
  }

  /**
   * Returns all voyages currently stored in Redis (or the fallback store).
   *
   * @return an unmodifiable snapshot of all voyages keyed by voyage number
   */
  public Map<VoyageNumber, Voyage> findAll() {
    if (redisAvailable) {
      return Collections.unmodifiableMap(loadAllFromRedis());
    }
    return Collections.unmodifiableMap(new HashMap<>(fallbackStore));
  }

  /**
   * Looks up a single voyage by its voyage number.
   *
   * @param voyageNumber the voyage number to look up
   * @return the matching {@link Voyage}, or {@code null} if not found
   */
  public Voyage findByVoyageNumber(VoyageNumber voyageNumber) {
    if (voyageNumber == null) {
      return null;
    }
    if (redisAvailable) {
      return loadFromRedis(voyageNumber);
    }
    return fallbackStore.get(voyageNumber);
  }

  /**
   * Stores or updates a voyage in Redis (or the fallback store).
   *
   * @param voyage the voyage to store
   */
  public void save(Voyage voyage) {
    if (voyage == null) {
      return;
    }
    if (redisAvailable) {
      saveToRedis(voyage);
    } else {
      fallbackStore.put(voyage.getVoyageNumber(), voyage);
    }
  }

  // -------------------------------------------------------------------------
  // Redis interaction helpers
  // -------------------------------------------------------------------------

  /**
   * Seeds the Redis store using Jedis (or any compatible Redis client available on the
   * classpath). The implementation uses a simple SET command with NX flag so that existing
   * entries are not overwritten.
   *
   * <p><em>Note:</em> The actual Jedis/Lettuce client calls are wrapped in a try-with-resources
   * block. If the Redis client library is not on the classpath at runtime the method logs a
   * warning and falls back to the in-memory store.
   */
  private void seedRedis(Map<VoyageNumber, Voyage> voyages) {
    try {
      // Attempt to use Jedis via reflection so that the compile-time dependency is optional.
      // In production, add jedis or lettuce to the Maven dependencies and replace this block
      // with a direct Jedis/StatefulRedisConnection call.
      Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
      try (AutoCloseable jedis =
          (AutoCloseable)
              jedisClass.getConstructor(String.class, int.class).newInstance(redisHost, redisPort)) {
        java.lang.reflect.Method setNx =
            jedisClass.getMethod("setnx", String.class, String.class);
        java.lang.reflect.Method sadd =
            jedisClass.getMethod("sadd", String.class, String[].class);

        for (Map.Entry<VoyageNumber, Voyage> entry : voyages.entrySet()) {
          String key = KEY_PREFIX + entry.getKey().getIdString();
          String value = VoyageSerializer.toJson(entry.getValue());
          setNx.invoke(jedis, key, value);
          sadd.invoke(jedis, INDEX_KEY, new String[] {entry.getKey().getIdString()});
        }
      }
    } catch (ClassNotFoundException e) {
      LOGGER.warning(
          "VoyageRedisRepository: Jedis client not found on classpath. "
              + "Add 'redis.clients:jedis' to pom.xml for Redis support. "
              + "Falling back to in-memory store.");
      if (fallbackStore.isEmpty()) {
        fallbackStore.putAll(voyages);
      }
    } catch (Exception e) {
      LOGGER.log(
          Level.WARNING,
          "VoyageRedisRepository: Failed to seed Redis store. Falling back to in-memory store.",
          e);
      if (fallbackStore.isEmpty()) {
        fallbackStore.putAll(voyages);
      }
    }
  }

  private Map<VoyageNumber, Voyage> loadAllFromRedis() {
    Map<VoyageNumber, Voyage> result = new HashMap<>();
    try {
      Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
      try (AutoCloseable jedis =
          (AutoCloseable)
              jedisClass.getConstructor(String.class, int.class).newInstance(redisHost, redisPort)) {
        java.lang.reflect.Method smembers =
            jedisClass.getMethod("smembers", String.class);
        java.lang.reflect.Method get = jedisClass.getMethod("get", String.class);

        @SuppressWarnings("unchecked")
        java.util.Set<String> members =
            (java.util.Set<String>) smembers.invoke(jedis, INDEX_KEY);
        if (members != null) {
          for (String id : members) {
            String json = (String) get.invoke(jedis, KEY_PREFIX + id);
            if (json != null) {
              Voyage voyage = VoyageSerializer.fromJson(json);
              if (voyage != null) {
                result.put(voyage.getVoyageNumber(), voyage);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "VoyageRedisRepository: Failed to load all voyages from Redis.", e);
      result.putAll(fallbackStore);
    }
    return result;
  }

  private Voyage loadFromRedis(VoyageNumber voyageNumber) {
    try {
      Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
      try (AutoCloseable jedis =
          (AutoCloseable)
              jedisClass.getConstructor(String.class, int.class).newInstance(redisHost, redisPort)) {
        java.lang.reflect.Method get = jedisClass.getMethod("get", String.class);
        String json = (String) get.invoke(jedis, KEY_PREFIX + voyageNumber.getIdString());
        return json != null ? VoyageSerializer.fromJson(json) : null;
      }
    } catch (Exception e) {
      LOGGER.log(
          Level.WARNING,
          "VoyageRedisRepository: Failed to load voyage '"
              + voyageNumber.getIdString()
              + "' from Redis.",
          e);
      return fallbackStore.get(voyageNumber);
    }
  }

  private void saveToRedis(Voyage voyage) {
    try {
      Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
      try (AutoCloseable jedis =
          (AutoCloseable)
              jedisClass.getConstructor(String.class, int.class).newInstance(redisHost, redisPort)) {
        java.lang.reflect.Method set = jedisClass.getMethod("set", String.class, String.class);
        java.lang.reflect.Method sadd =
            jedisClass.getMethod("sadd", String.class, String[].class);
        String key = KEY_PREFIX + voyage.getVoyageNumber().getIdString();
        set.invoke(jedis, key, VoyageSerializer.toJson(voyage));
        sadd.invoke(
            jedis, INDEX_KEY, new String[] {voyage.getVoyageNumber().getIdString()});
      }
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "VoyageRedisRepository: Failed to save voyage to Redis.", e);
      fallbackStore.put(voyage.getVoyageNumber(), voyage);
    }
  }

  // -------------------------------------------------------------------------
  // Utility helpers
  // -------------------------------------------------------------------------

  private static int parsePort(String portStr) {
    try {
      return Integer.parseInt(portStr.trim());
    } catch (NumberFormatException e) {
      return 6379;
    }
  }

  /**
   * Probes whether a TCP connection can be established to the given host/port within 2 seconds.
   */
  private static boolean probeConnection(String host, int port) {
    try (java.net.Socket socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress(host, port), 2000);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
