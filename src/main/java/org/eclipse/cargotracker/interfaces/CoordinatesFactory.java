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

import java.util.HashMap;
import java.util.Map;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.UnLocode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>Coordinates are cached in Amazon ElastiCache (Redis) to support horizontal scaling in
 * containerised/EKS deployments. Connection details are injected via environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} – ElastiCache primary endpoint (default: {@code localhost})</li>
 *   <li>{@code REDIS_PORT} – ElastiCache port (default: {@code 6379})</li>
 * </ul>
 */
public class CoordinatesFactory {

  // cz-java-0070: replaced static in-process local cache (Map<String, Coordinates> COORDINATES_MAP)
  // with Amazon ElastiCache (Redis) backed cache. Connection details are supplied via
  // Kubernetes ConfigMap / Secret environment variables (REDIS_HOST, REDIS_PORT).
  private static final JedisPool JEDIS_POOL;

  private static final String COORDINATES_KEY_PREFIX = "coordinates:";

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  static {
    String redisHost = System.getenv("REDIS_HOST") != null
        ? System.getenv("REDIS_HOST") : "localhost";
    int redisPort = System.getenv("REDIS_PORT") != null
        ? Integer.parseInt(System.getenv("REDIS_PORT")) : 6379;

    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(10);
    poolConfig.setMaxIdle(5);
    poolConfig.setMinIdle(1);
    JEDIS_POOL = new JedisPool(poolConfig, redisHost, redisPort);

    // Populate ElastiCache with coordinate data on first initialisation.
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    Map<String, Coordinates> seedData = new HashMap<>();
    seedData.put(HONGKONG.getUnLocode().getIdString(), new Coordinates(22, 114));
    seedData.put(MELBOURNE.getUnLocode().getIdString(), new Coordinates(-38, 145));
    seedData.put(STOCKHOLM.getUnLocode().getIdString(), new Coordinates(59, 18));
    seedData.put(HELSINKI.getUnLocode().getIdString(), new Coordinates(60, 25));
    seedData.put(CHICAGO.getUnLocode().getIdString(), new Coordinates(42, -88));
    seedData.put(TOKYO.getUnLocode().getIdString(), new Coordinates(36, 140));
    seedData.put(HAMBURG.getUnLocode().getIdString(), new Coordinates(54, 10));
    seedData.put(SHANGHAI.getUnLocode().getIdString(), new Coordinates(31, 121));
    seedData.put(ROTTERDAM.getUnLocode().getIdString(), new Coordinates(52, 5));
    seedData.put(GOTHENBURG.getUnLocode().getIdString(), new Coordinates(58, 12));
    seedData.put(HANGZOU.getUnLocode().getIdString(), new Coordinates(30, 120));
    seedData.put(NEWYORK.getUnLocode().getIdString(), new Coordinates(41, -74));
    seedData.put(DALLAS.getUnLocode().getIdString(), new Coordinates(33, -97));
    seedData.put(UNKNOWN.getUnLocode().getIdString(), new Coordinates(-90, 0)); // The South Pole.

    try (Jedis jedis = JEDIS_POOL.getResource()) {
      for (Map.Entry<String, Coordinates> entry : seedData.entrySet()) {
        String redisKey = COORDINATES_KEY_PREFIX + entry.getKey();
        // Only seed if not already present (supports multi-instance deployments).
        if (!jedis.exists(redisKey)) {
          Coordinates c = entry.getValue();
          jedis.hset(redisKey, "lat", String.valueOf(c.getLatitude()));
          jedis.hset(redisKey, "lon", String.valueOf(c.getLongitude()));
        }
      }
    }
  }

  public static Coordinates find(Location location) {
    return find(location.getUnLocode());
  }

  public static Coordinates find(UnLocode unLocode) {
    return find(unLocode.getIdString());
  }

  public static Coordinates find(String unLocode) {
    try (Jedis jedis = JEDIS_POOL.getResource()) {
      String redisKey = COORDINATES_KEY_PREFIX + unLocode;
      Map<String, String> fields = jedis.hgetAll(redisKey);
      if (fields == null || fields.isEmpty()) {
        return null;
      }
      double lat = Double.parseDouble(fields.get("lat"));
      double lon = Double.parseDouble(fields.get("lon"));
      return new Coordinates(lat, lon);
    }
  }
}
