package org.eclipse.pathfinder.internal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * CZ-JAVA-0064 (Singleton State Storage): This CDI @ApplicationScoped bean acts as a singleton.
 * For horizontal scaling on EKS, any mutable singleton state should be externalized to
 * Amazon ElastiCache (Redis) so all pod replicas share a single consistent data store.
 * Configure the Redis connection via environment variables:
 *   REDIS_HOST - ElastiCache Redis endpoint (e.g., ${REDIS_HOST})
 *   REDIS_PORT - Redis port (e.g., ${REDIS_PORT:6379})
 */
@ApplicationScoped
public class GraphDao implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Random random = new Random();

  public List<String> listLocations() {
    return new ArrayList<>(
        Arrays.asList(
            "CNHKG", "AUMEL", "SESTO", "FIHEL", "USCHI", "JNTKO", "DEHAM", "CNSHA", "NLRTM",
            "SEGOT", "CNHGH", "USNYC", "USDAL"));
  }

  public String getVoyageNumber(String from, String to) {
    int i = random.nextInt(5);

    switch (i) {
      case 0:
        return "0100S";
      case 1:
        return "0200T";
      case 2:
        return "0300A";
      case 3:
        return "0301S";
      default:
        return "0400S";
    }
  }
}
