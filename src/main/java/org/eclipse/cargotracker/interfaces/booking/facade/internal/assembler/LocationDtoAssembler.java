package org.eclipse.cargotracker.interfaces.booking.facade.internal.assembler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.cargotracker.domain.model.location.Location;

/**
 * CZ-JAVA-0064 (Singleton State Storage): This CDI @ApplicationScoped bean acts as a singleton.
 * For horizontal scaling on EKS, any mutable singleton state should be externalized to
 * Amazon ElastiCache (Redis) so all pod replicas share a single consistent data store.
 * Configure the Redis connection via environment variables:
 *   REDIS_HOST - ElastiCache Redis endpoint (e.g., ${REDIS_HOST})
 *   REDIS_PORT - Redis port (e.g., ${REDIS_PORT:6379})
 */
@ApplicationScoped
public class LocationDtoAssembler {

  public org.eclipse.cargotracker.interfaces.booking.facade.dto.Location toDto(Location location) {
    return new org.eclipse.cargotracker.interfaces.booking.facade.dto.Location(
        location.getUnLocode().getIdString(), location.getName());
  }

  public List<org.eclipse.cargotracker.interfaces.booking.facade.dto.Location> toDtoList(
      List<Location> allLocations) {
    List<org.eclipse.cargotracker.interfaces.booking.facade.dto.Location> dtoList =
        allLocations
            .stream()
            .map(this::toDto)
            .sorted(
                Comparator.comparing(
                    org.eclipse.cargotracker.interfaces.booking.facade.dto.Location::getUnLocode))
            .collect(Collectors.toList());
    return dtoList;
  }
}
