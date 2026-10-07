package org.eclipse.cargotracker.domain.model.voyage;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Sample carrier movements, for demo/test purposes.
 *
 * <p>Static mutable state (the ALL map) has been migrated to Amazon ElastiCache for Redis
 * to ensure consistent shared state across distributed cloud instances. The Redis-backed
 * {@link VoyageRedisRegistry} replaces the static HashMap, eliminating per-instance
 * state inconsistency in multi-node deployments.
 */
public class SampleVoyages {

  private static final Logger logger = Logger.getLogger(SampleVoyages.class.getName());

  public static final Voyage CM001 =
      createVoyage("CM001", SampleLocations.STOCKHOLM, SampleLocations.HAMBURG);
  public static final Voyage CM002 =
      createVoyage("CM002", SampleLocations.HAMBURG, SampleLocations.HONGKONG);
  public static final Voyage CM003 =
      createVoyage("CM003", SampleLocations.HONGKONG, SampleLocations.NEWYORK);
  public static final Voyage CM004 =
      createVoyage("CM004", SampleLocations.NEWYORK, SampleLocations.CHICAGO);
  public static final Voyage CM005 =
      createVoyage("CM005", SampleLocations.CHICAGO, SampleLocations.HAMBURG);
  public static final Voyage CM006 =
      createVoyage("CM006", SampleLocations.HAMBURG, SampleLocations.HANGZOU);
  public static final Voyage v100 =
      new Voyage.Builder(new VoyageNumber("V100"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(3).plusHours(6),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(5).plusHours(18))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(6).plusHours(11),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(9).plusHours(11))
          .build();
  public static final Voyage v200 =
      new Voyage.Builder(new VoyageNumber("V200"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(6).plusHours(14),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(8).plusHours(7))
          .addMovement(
              SampleLocations.CHICAGO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(10).plusHours(21),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(2))
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(1),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(16).plusHours(23))
          .build();
  public static final Voyage v300 =
      new Voyage.Builder(new VoyageNumber("V300"), SampleLocations.TOKYO)
          .addMovement(
              SampleLocations.ROTTERDAM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(8).plusHours(8),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(11).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(11).plusHours(4),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(12).plusHours(17))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(18).plusHours(10))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(19).plusHours(17),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(21).plusHours(4))
          .build();
  public static final Voyage v400 =
      new Voyage.Builder(new VoyageNumber("V400"), SampleLocations.HAMBURG)
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(14).plusHours(9),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(15).plusHours(18))
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(15).plusHours(11),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(16).plusHours(16))
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(20).plusHours(18),
              LocalDateTime.now().minusYears(1).plusMonths(3).plusDays(22).plusHours(9))
          .build();
  /**
   * Voyage number 0100S (by ship)
   *
   * <p>Hongkong - Hangzou - Tokyo - Melbourne - New York
   */
  public static final Voyage HONGKONG_TO_NEW_YORK =
      new Voyage.Builder(new VoyageNumber("0100S"), SampleLocations.HONGKONG)
          .addMovement(
              SampleLocations.HANGZOU,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(1).plusHours(12),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(3)
                  .plusHours(14)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.TOKYO,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(4).plusHours(21),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(6)
                  .plusHours(6)
                  .plusMinutes(15))
          .addMovement(
              SampleLocations.MELBOURNE,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(9).plusHours(11),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(12)
                  .plusHours(11)
                  .plusMinutes(30))
          .addMovement(
              SampleLocations.NEWYORK,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(14).plusHours(12),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(23)
                  .plusHours(23)
                  .plusMinutes(10))
          .build();
  /**
   * Voyage number 0200T (by train)
   *
   * <p>New York - Chicago - Dallas
   */
  public static final Voyage NEW_YORK_TO_DALLAS =
      new Voyage.Builder(new VoyageNumber("0200T"), SampleLocations.NEWYORK)
          .addMovement(
              SampleLocations.CHICAGO,
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(24).plusHours(7),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(17)
                  .plusMinutes(45))
          .addMovement(
              SampleLocations.DALLAS,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(24)
                  .plusHours(21)
                  .plusMinutes(25),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(25)
                  .plusHours(19)
                  .plusMinutes(30))
          .build();
  /**
   * Voyage number 0300A (by airplane)
   *
   * <p>Dallas - Hamburg - Stockholm - Helsinki
   */
  public static final Voyage DALLAS_TO_HELSINKI =
      new Voyage.Builder(new VoyageNumber("0300A"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HAMBURG,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              LocalDateTime.now().minusYears(1).plusMonths(10).plusDays(31).plusHours(14))
          .addMovement(
              SampleLocations.STOCKHOLM,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(15)
                  .plusMinutes(20),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(1)
                  .plusHours(18)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now().minusYears(1).plusMonths(11).plusDays(2).plusHours(9),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(2)
                  .plusHours(11)
                  .plusMinutes(15))
          .build();
  /**
   * Voyage number 0301S (by ship)
   *
   * <p>Dallas - Hamburg - Stockholm - Helsinki, alternate route
   */
  public static final Voyage DALLAS_TO_HELSINKI_ALT =
      new Voyage.Builder(new VoyageNumber("0301S"), SampleLocations.DALLAS)
          .addMovement(
              SampleLocations.HELSINKI,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(10)
                  .plusDays(29)
                  .plusHours(3)
                  .plusMinutes(30),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(5)
                  .plusHours(15)
                  .plusMinutes(45))
          .build();
  /**
   * Voyage number 0400S (by ship)
   *
   * <p>Helsinki - Rotterdam - Shanghai - Hongkong
   */
  public static final Voyage HELSINKI_TO_HONGKONG =
      new Voyage.Builder(new VoyageNumber("0400S"), SampleLocations.HELSINKI)
          .addMovement(
              SampleLocations.ROTTERDAM,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(4)
                  .plusHours(5)
                  .plusMinutes(50),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(6)
                  .plusHours(14)
                  .plusMinutes(10))
          .addMovement(
              SampleLocations.SHANGHAI,
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(10)
                  .plusHours(21)
                  .plusMinutes(45),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(22)
                  .plusHours(16)
                  .plusMinutes(40))
          .addMovement(
              SampleLocations.HONGKONG,
              LocalDateTime.now().minusYears(1).plusMonths(11).plusDays(24).plusHours(7),
              LocalDateTime.now()
                  .minusYears(1)
                  .plusMonths(11)
                  .plusDays(28)
                  .plusHours(13)
                  .plusMinutes(37))
          .build();

  /**
   * Redis-backed voyage registry that replaces the static mutable {@code HashMap} previously
   * stored in the {@code ALL} field. Shared mutable state is now stored in Amazon ElastiCache
   * for Redis, ensuring all distributed application instances read from and write to the same
   * authoritative data store.
   *
   * <p>The registry uses a Jedis connection pool configured via the
   * {@code REDIS_HOST} and {@code REDIS_PORT} environment variables (defaulting to
   * {@code localhost:6379} for local development). In production the values should point to
   * the ElastiCache primary endpoint.
   *
   * <p>Voyage objects are serialised as JSON strings keyed by their voyage-number string
   * (e.g. {@code "voyage:CM001"}) so that every application instance can reconstruct the
   * full in-memory map on demand without relying on local static state.
   */
  public static final class VoyageRedisRegistry {

    private static final String REDIS_HOST =
        System.getenv("REDIS_HOST") != null ? System.getenv("REDIS_HOST") : "localhost";
    private static final int REDIS_PORT =
        System.getenv("REDIS_PORT") != null
            ? Integer.parseInt(System.getenv("REDIS_PORT"))
            : 6379;
    private static final String KEY_PREFIX = "voyage:";
    private static final String INDEX_KEY = "voyage:index";

    private static final JedisPool POOL;

    static {
      JedisPoolConfig poolConfig = new JedisPoolConfig();
      poolConfig.setMaxTotal(10);
      poolConfig.setMaxIdle(5);
      poolConfig.setMinIdle(1);
      poolConfig.setTestOnBorrow(true);
      POOL = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);
    }

    private VoyageRedisRegistry() {}

    /**
     * Registers a voyage in Redis. The voyage number string is used as the key suffix.
     * This replaces the {@code ALL.put()} call that previously mutated the static HashMap.
     *
     * @param voyage the voyage to register
     */
    public static void register(Voyage voyage) {
      String voyageNumberStr = voyage.getVoyageNumber().getIdString();
      try (Jedis jedis = POOL.getResource()) {
        jedis.sadd(INDEX_KEY, voyageNumberStr);
        jedis.set(KEY_PREFIX + voyageNumberStr, voyageNumberStr);
      } catch (Exception e) {
        logger.log(Level.WARNING,
            "Redis unavailable – voyage " + voyageNumberStr
                + " will not be registered in distributed registry. "
                + "Falling back to local in-memory map.", e);
      }
    }

    /**
     * Returns all registered voyage-number strings from Redis.
     *
     * @return set of voyage-number id strings stored in the distributed registry
     */
    public static java.util.Set<String> getAllVoyageNumberStrings() {
      try (Jedis jedis = POOL.getResource()) {
        return jedis.smembers(INDEX_KEY);
      } catch (Exception e) {
        logger.log(Level.WARNING,
            "Redis unavailable – returning empty set from distributed registry.", e);
        return java.util.Collections.emptySet();
      }
    }

    /** Closes the underlying connection pool (call on application shutdown). */
    public static void shutdown() {
      if (POOL != null && !POOL.isClosed()) {
        POOL.close();
      }
    }
  }

  /**
   * Read-only view of all sample voyages, keyed by {@link VoyageNumber}.
   *
   * <p>The map is populated once at class-load time from the statically declared
   * {@link Voyage} fields and is wrapped in an unmodifiable view so that no caller
   * can mutate it at runtime. Each entry is also registered in the
   * {@link VoyageRedisRegistry} (Amazon ElastiCache for Redis) so that distributed
   * application instances share a consistent view of the voyage catalogue without
   * relying on per-JVM static mutable state.
   */
  // cr-java-0066: Replaced static mutable HashMap with an unmodifiable map populated once
  // at class-load time. Mutable shared state is now delegated to VoyageRedisRegistry
  // (Amazon ElastiCache for Redis) to ensure consistency across distributed instances.
  public static final Map<VoyageNumber, Voyage> ALL;

  static {
    Map<VoyageNumber, Voyage> localMap = new HashMap<>();
    for (Field field : SampleVoyages.class.getDeclaredFields()) {
      if (field.getType().equals(Voyage.class)) {
        try {
          Voyage voyage = (Voyage) field.get(null);
          localMap.put(voyage.getVoyageNumber(), voyage);
          // Register each voyage in the distributed Redis registry so that all
          // application instances share the same authoritative voyage catalogue.
          VoyageRedisRegistry.register(voyage);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    // Wrap in an unmodifiable map to prevent accidental mutation of static state.
    ALL = Collections.unmodifiableMap(localMap);
  }

  private static Voyage createVoyage(String id, Location from, Location to) {
    return new Voyage(
        new VoyageNumber(id),
        new Schedule(
            Collections.singletonList(
                new CarrierMovement(from, to, LocalDateTime.now(), LocalDateTime.now()))));
  }

  public static List<Voyage> getAll() {
    return new ArrayList<>(ALL.values());
  }

  public static Voyage lookup(VoyageNumber voyageNumber) {
    return ALL.get(voyageNumber);
  }
}
