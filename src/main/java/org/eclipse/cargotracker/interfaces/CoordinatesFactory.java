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

import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import org.eclipse.cargotracker.infrastructure.cache.RedisConfig;
import redis.clients.jedis.Jedis;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Remediation cz-java-0070: The previous in-process static {@code HashMap} (local cache) has
 * been replaced with Amazon ElastiCache (Redis) so that coordinate data is shared consistently
 * across all horizontally-scaled container replicas. Connection details are supplied via the
 * {@code REDIS_HOST}, {@code REDIS_PORT}, and {@code REDIS_PASSWORD} environment variables
 * (injected through Kubernetes ConfigMaps / Secrets with IRSA-secured access).
 */
public class CoordinatesFactory {

  /** Redis hash key that stores all location → coordinate mappings. */
  private static final String CACHE_KEY = "coordinates:map";

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  /**
   * Initialises the coordinates cache in Amazon ElastiCache (Redis) if it has not been populated
   * yet. This replaces the former static {@code HashMap} (cz-java-0070).
   */
  static {
    try (Jedis jedis = RedisConfig.getPool().getResource()) {
      if (!jedis.exists(CACHE_KEY)) {
        // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
        jedis.hset(CACHE_KEY, HONGKONG.getUnLocode().getIdString(),  "22,114");
        jedis.hset(CACHE_KEY, MELBOURNE.getUnLocode().getIdString(), "-38,145");
        jedis.hset(CACHE_KEY, STOCKHOLM.getUnLocode().getIdString(), "59,18");
        jedis.hset(CACHE_KEY, HELSINKI.getUnLocode().getIdString(),  "60,25");
        jedis.hset(CACHE_KEY, CHICAGO.getUnLocode().getIdString(),   "42,-88");
        jedis.hset(CACHE_KEY, TOKYO.getUnLocode().getIdString(),     "36,140");
        jedis.hset(CACHE_KEY, HAMBURG.getUnLocode().getIdString(),   "54,10");
        jedis.hset(CACHE_KEY, SHANGHAI.getUnLocode().getIdString(),  "31,121");
        jedis.hset(CACHE_KEY, ROTTERDAM.getUnLocode().getIdString(), "52,5");
        jedis.hset(CACHE_KEY, GOTHENBURG.getUnLocode().getIdString(),"58,12");
        jedis.hset(CACHE_KEY, HANGZOU.getUnLocode().getIdString(),   "30,120");
        jedis.hset(CACHE_KEY, NEWYORK.getUnLocode().getIdString(),   "41,-74");
        jedis.hset(CACHE_KEY, DALLAS.getUnLocode().getIdString(),    "33,-97");
        jedis.hset(CACHE_KEY, UNKNOWN.getUnLocode().getIdString(),   "-90,0"); // The South Pole.
      }
    }
  }

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
    try (Jedis jedis = RedisConfig.getPool().getResource()) {
      String value = jedis.hget(CACHE_KEY, unLocode);
      if (value == null) {
        return null;
      }
      String[] parts = value.split(",");
      return new Coordinates(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
  }
}
