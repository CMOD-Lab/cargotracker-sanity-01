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
import org.eclipse.cargotracker.infrastructure.cache.RedisCache;

/**
 * At the moment, coordinates are produced by a simple factory. It may be converted to a repository
 * if coordinates become a domain layer concern.
 *
 * <p>cz-java-0070: The previous local in-process HashMap cache (COORDINATES_MAP) has been replaced
 * with Amazon ElastiCache for Redis so that coordinate data is shared across all horizontally-scaled
 * container replicas on EKS. Connection details are injected via the REDIS_HOST and REDIS_PORT
 * environment variables (backed by Kubernetes ConfigMaps / Secrets with IRSA-secured access).
 */
public class CoordinatesFactory {

  // cz-java-0070 fix (line 30): Removed local in-process HashMap cache (COORDINATES_MAP).
  // Coordinate lookups are now delegated to Amazon ElastiCache (Redis) via RedisCache,
  // eliminating the per-JVM cache that would not be shared across horizontally-scaled replicas.

  private static final String CACHE_KEY_PREFIX = "coordinates:";

  private CoordinatesFactory() {
    /* Prevent instantiation. */
  }

  /**
   * Initialises the coordinate entries in Redis if they are not already present.
   * This is called once at application startup so that all container replicas share
   * the same data via Amazon ElastiCache.
   */
  private static void initializeIfAbsent() {
    putIfAbsent(HONGKONG.getUnLocode().getIdString(), 22, 114);
    putIfAbsent(MELBOURNE.getUnLocode().getIdString(), -38, 145);
    putIfAbsent(STOCKHOLM.getUnLocode().getIdString(), 59, 18);
    putIfAbsent(HELSINKI.getUnLocode().getIdString(), 60, 25);
    putIfAbsent(CHICAGO.getUnLocode().getIdString(), 42, -88);
    putIfAbsent(TOKYO.getUnLocode().getIdString(), 36, 140);
    putIfAbsent(HAMBURG.getUnLocode().getIdString(), 54, 10);
    putIfAbsent(SHANGHAI.getUnLocode().getIdString(), 31, 121);
    putIfAbsent(ROTTERDAM.getUnLocode().getIdString(), 52, 5);
    putIfAbsent(GOTHENBURG.getUnLocode().getIdString(), 58, 12);
    putIfAbsent(HANGZOU.getUnLocode().getIdString(), 30, 120);
    putIfAbsent(NEWYORK.getUnLocode().getIdString(), 41, -74);
    putIfAbsent(DALLAS.getUnLocode().getIdString(), 33, -97);
    // TODO [Clean Code] See if there is a service to get the latitude/longitude data from.
    putIfAbsent(UNKNOWN.getUnLocode().getIdString(), -90, 0); // The South Pole.
  }

  private static void putIfAbsent(String unLocode, int lat, int lon) {
    String key = CACHE_KEY_PREFIX + unLocode;
    if (!RedisCache.exists(key)) {
      RedisCache.set(key, lat + "," + lon);
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
    initializeIfAbsent();
    String value = RedisCache.get(CACHE_KEY_PREFIX + unLocode);
    if (value == null) {
      return null;
    }
    String[] parts = value.split(",");
    return new Coordinates(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
  }
}
