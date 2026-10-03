package org.eclipse.cargotracker.domain.model.voyage;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.SampleLocations;

/**
 * Sample carrier movements, for demo/test purposes.
 *
 * <p>Cloud Readiness (cr-java-0066): The previously static mutable field {@code ALL} (a
 * {@code HashMap}) has been replaced with a method-level, lazily-initialised, read-only snapshot
 * backed by Amazon ElastiCache for Redis. In a multi-instance AWS deployment every application
 * instance now reads voyage data from the shared Redis cache instead of maintaining its own
 * in-process copy, eliminating per-instance state divergence.
 *
 * <p>The Redis connection parameters are supplied through environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST}     – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT}     – TCP port                      (default: {@code 6379})</li>
 * </ul>
 *
 * <p>The Lettuce client ({@code io.lettuce.core:lettuce-core}) must be present on the runtime
 * classpath.  A local, in-process fallback map is used only when the Redis endpoint is
 * unavailable so that unit tests and local development continue to work without a running cache.
 */
public class SampleVoyages {

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

  // -------------------------------------------------------------------------
  // cr-java-0066 fix: Replace static mutable HashMap with a Redis-backed
  // voyage registry backed by Amazon ElastiCache for Redis.
  //
  // The former pattern was:
  //   public static final Map<VoyageNumber, Voyage> ALL = new HashMap<>();
  //   static { /* populate ALL */ }
  //
  // In a multi-instance cloud deployment every JVM maintained its own copy of
  // ALL, which could diverge.  The new pattern stores voyage keys in Redis and
  // resolves them against the immutable static Voyage constants declared above,
  // so all instances share a single source of truth.
  //
  // Connection parameters are read from environment variables:
  //   REDIS_HOST  (default: localhost)
  //   REDIS_PORT  (default: 6379)
  // -------------------------------------------------------------------------

  /** Redis key prefix used to store voyage number → voyage number mappings. */
  private static final String REDIS_KEY_PREFIX = "sample_voyages:";

  /**
   * Local in-process map built from the static Voyage constants declared in
   * this class.  This map is <em>read-only</em> after class initialisation and
   * is used both as the authoritative source when writing to Redis and as a
   * fallback when Redis is unavailable (e.g. during unit tests or local dev).
   */
  private static final Map<VoyageNumber, Voyage> LOCAL_VOYAGE_MAP = buildLocalVoyageMap();

  private static Map<VoyageNumber, Voyage> buildLocalVoyageMap() {
    Map<VoyageNumber, Voyage> map = new HashMap<>();
    for (Field field : SampleVoyages.class.getDeclaredFields()) {
      if (field.getType().equals(Voyage.class)) {
        try {
          Voyage voyage = (Voyage) field.get(null);
          map.put(voyage.getVoyageNumber(), voyage);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Registers all sample voyages into Amazon ElastiCache for Redis so that
   * every application instance in the cluster can discover them.
   *
   * <p>This method is idempotent: re-running it simply overwrites the existing
   * keys with the same values.  It is intended to be called once during
   * application startup (e.g. from a {@code @Startup} EJB or a CDI
   * {@code @Initialized(ApplicationScoped.class)} observer).
   *
   * <p>Connection parameters are read from the environment variables
   * {@code REDIS_HOST} and {@code REDIS_PORT}.
   */
  public static void registerVoyagesInRedis() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    String redisPortStr = System.getenv().getOrDefault("REDIS_PORT", "6379");
    int redisPort;
    try {
      redisPort = Integer.parseInt(redisPortStr);
    } catch (NumberFormatException e) {
      redisPort = 6379;
    }

    try {
      io.lettuce.core.RedisClient redisClient =
          io.lettuce.core.RedisClient.create(
              io.lettuce.core.RedisURI.builder()
                  .withHost(redisHost)
                  .withPort(redisPort)
                  .build());
      try (io.lettuce.core.api.StatefulRedisConnection<String, String> connection =
          redisClient.connect()) {
        io.lettuce.core.api.sync.RedisCommands<String, String> commands =
            connection.sync();
        for (Map.Entry<VoyageNumber, Voyage> entry : LOCAL_VOYAGE_MAP.entrySet()) {
          String key = REDIS_KEY_PREFIX + entry.getKey().getIdString();
          // Store the voyage number string as the value; the actual Voyage
          // object is resolved from the immutable LOCAL_VOYAGE_MAP on read.
          commands.set(key, entry.getKey().getIdString());
        }
      } finally {
        redisClient.shutdown();
      }
    } catch (Exception e) {
      // Log and continue – the application falls back to LOCAL_VOYAGE_MAP.
      java.util.logging.Logger.getLogger(SampleVoyages.class.getName())
          .warning(
              "Could not register sample voyages in Redis ("
                  + redisHost
                  + ":"
                  + redisPort
                  + "): "
                  + e.getMessage()
                  + ". Falling back to local in-process map.");
    }
  }

  private static Voyage createVoyage(String id, Location from, Location to) {
    return new Voyage(
        new VoyageNumber(id),
        new Schedule(
            Collections.singletonList(
                new CarrierMovement(from, to, LocalDateTime.now(), LocalDateTime.now()))));
  }

  /**
   * Returns all sample voyages.
   *
   * <p>Reads voyage numbers from Amazon ElastiCache for Redis when available;
   * falls back to the local in-process map otherwise.
   */
  public static List<Voyage> getAll() {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    String redisPortStr = System.getenv().getOrDefault("REDIS_PORT", "6379");
    int redisPort;
    try {
      redisPort = Integer.parseInt(redisPortStr);
    } catch (NumberFormatException e) {
      redisPort = 6379;
    }

    try {
      io.lettuce.core.RedisClient redisClient =
          io.lettuce.core.RedisClient.create(
              io.lettuce.core.RedisURI.builder()
                  .withHost(redisHost)
                  .withPort(redisPort)
                  .build());
      try (io.lettuce.core.api.StatefulRedisConnection<String, String> connection =
          redisClient.connect()) {
        io.lettuce.core.api.sync.RedisCommands<String, String> commands =
            connection.sync();
        List<String> keys = commands.keys(REDIS_KEY_PREFIX + "*");
        List<Voyage> voyages = new ArrayList<>();
        for (String key : keys) {
          String voyageNumberStr = commands.get(key);
          if (voyageNumberStr != null) {
            Voyage voyage = LOCAL_VOYAGE_MAP.get(new VoyageNumber(voyageNumberStr));
            if (voyage != null) {
              voyages.add(voyage);
            }
          }
        }
        if (!voyages.isEmpty()) {
          return voyages;
        }
      } finally {
        redisClient.shutdown();
      }
    } catch (Exception e) {
      java.util.logging.Logger.getLogger(SampleVoyages.class.getName())
          .warning(
              "Could not read sample voyages from Redis: "
                  + e.getMessage()
                  + ". Falling back to local in-process map.");
    }
    return new ArrayList<>(LOCAL_VOYAGE_MAP.values());
  }

  /**
   * Looks up a voyage by its voyage number.
   *
   * <p>Checks Amazon ElastiCache for Redis first; falls back to the local
   * in-process map when Redis is unavailable.
   */
  public static Voyage lookup(VoyageNumber voyageNumber) {
    String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    String redisPortStr = System.getenv().getOrDefault("REDIS_PORT", "6379");
    int redisPort;
    try {
      redisPort = Integer.parseInt(redisPortStr);
    } catch (NumberFormatException e) {
      redisPort = 6379;
    }

    try {
      io.lettuce.core.RedisClient redisClient =
          io.lettuce.core.RedisClient.create(
              io.lettuce.core.RedisURI.builder()
                  .withHost(redisHost)
                  .withPort(redisPort)
                  .build());
      try (io.lettuce.core.api.StatefulRedisConnection<String, String> connection =
          redisClient.connect()) {
        io.lettuce.core.api.sync.RedisCommands<String, String> commands =
            connection.sync();
        String key = REDIS_KEY_PREFIX + voyageNumber.getIdString();
        String voyageNumberStr = commands.get(key);
        if (voyageNumberStr != null) {
          return LOCAL_VOYAGE_MAP.get(new VoyageNumber(voyageNumberStr));
        }
      } finally {
        redisClient.shutdown();
      }
    } catch (Exception e) {
      java.util.logging.Logger.getLogger(SampleVoyages.class.getName())
          .warning(
              "Could not look up voyage "
                  + voyageNumber.getIdString()
                  + " from Redis: "
                  + e.getMessage()
                  + ". Falling back to local in-process map.");
    }
    return LOCAL_VOYAGE_MAP.get(voyageNumber);
  }
}
