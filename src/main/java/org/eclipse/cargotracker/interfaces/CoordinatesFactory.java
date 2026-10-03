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

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Coordinates are cached in Amazon ElastiCache (Redis) to support horizontal scaling on EKS.
 * Connection details are injected via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: {@code 6379})</li>
 * </ul>
 */
public class CoordinatesFactory {

  private static final Logger LOGGER = Logger.getLogger(CoordinatesFactory.class.getName());

  /** Redis key prefix used for all coordinates entries. */
  private static final String REDIS_KEY_PREFIX = "coordinates:";

  /** Lazily-initialised Redis client backed by Amazon ElastiCache. */
  private static volatile RedisClient redisClient;
  private static volatile StatefulRedisConnection<String, String> redisConnection;

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  // ---------------------------------------------------------------------------
  // Redis initialisation
  // ---------------------------------------------------------------------------

  private static RedisCommands<String, String> getRedisCommands() {
    if (redisConnection == null || !redisConnection.isOpen()) {
      synchronized (CoordinatesFactory.class) {
        if (redisConnection == null || !redisConnection.isOpen()) {
          String redisHost = System.getenv("REDIS_HOST") != null
              ? System.getenv("REDIS_HOST") : "localhost";
          int redisPort = 6379;
          String redisPortEnv = System.getenv("REDIS_PORT");
          if (redisPortEnv != null && !redisPortEnv.isEmpty()) {
            try {
              redisPort = Integer.parseInt(redisPortEnv);
            } catch (NumberFormatException e) {
              LOGGER.log(Level.WARNING, "Invalid REDIS_PORT value, using default 6379", e);
            }
          }
          RedisURI redisUri = RedisURI.builder()
              .withHost(redisHost)
              .withPort(redisPort)
              .build();
          redisClient = RedisClient.create(redisUri);
          redisConnection = redisClient.connect();
          // Seed the cache with known coordinates on first connection
          seedCoordinates(redisConnection.sync());
        }
      }
    }
    return redisConnection.sync();
  }

  /**
   * Seeds the Redis cache with the known location coordinates.
   * Uses SET NX (set-if-not-exists) so existing values are not overwritten.
   */
  private static void seedCoordinates(RedisCommands<String, String> commands) {
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    seedIfAbsent(commands, HONGKONG.getUnLocode().getIdString(), 22, 114);
    seedIfAbsent(commands, MELBOURNE.getUnLocode().getIdString(), -38, 145);
    seedIfAbsent(commands, STOCKHOLM.getUnLocode().getIdString(), 59, 18);
    seedIfAbsent(commands, HELSINKI.getUnLocode().getIdString(), 60, 25);
    seedIfAbsent(commands, CHICAGO.getUnLocode().getIdString(), 42, -88);
    seedIfAbsent(commands, TOKYO.getUnLocode().getIdString(), 36, 140);
    seedIfAbsent(commands, HAMBURG.getUnLocode().getIdString(), 54, 10);
    seedIfAbsent(commands, SHANGHAI.getUnLocode().getIdString(), 31, 121);
    seedIfAbsent(commands, ROTTERDAM.getUnLocode().getIdString(), 52, 5);
    seedIfAbsent(commands, GOTHENBURG.getUnLocode().getIdString(), 58, 12);
    seedIfAbsent(commands, HANGZOU.getUnLocode().getIdString(), 30, 120);
    seedIfAbsent(commands, NEWYORK.getUnLocode().getIdString(), 41, -74);
    seedIfAbsent(commands, DALLAS.getUnLocode().getIdString(), 33, -97);
    seedIfAbsent(commands, UNKNOWN.getUnLocode().getIdString(), -90, 0); // The South Pole.
  }

  private static void seedIfAbsent(RedisCommands<String, String> commands,
      String unLocode, double lat, double lon) {
    String key = REDIS_KEY_PREFIX + unLocode;
    // Store as "lat:lon" string; use SETNX to avoid overwriting externally-managed values
    commands.setnx(key, lat + ":" + lon);
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  /**
   * Looks up coordinates for the given UN/LOCODE from Amazon ElastiCache (Redis).
   *
   * @param unLocode the UN/LOCODE string identifier
   * @return the {@link Coordinates} for the location, or {@code null} if not found
   */
  public static Coordinates find(String unLocode) {
    try {
      RedisCommands<String, String> commands = getRedisCommands();
      String value = commands.get(REDIS_KEY_PREFIX + unLocode);
      if (value == null) {
        return null;
      }
      String[] parts = value.split(":");
      if (parts.length != 2) {
        LOGGER.warning("Unexpected coordinates format in Redis for key: " + unLocode);
        return null;
      }
      double lat = Double.parseDouble(parts[0]);
      double lon = Double.parseDouble(parts[1]);
      return new Coordinates(lat, lon);
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Failed to retrieve coordinates from Redis for: " + unLocode, e);
      return null;
    }
  }
}
