package org.eclipse.cargotracker.interfaces.booking.sse;

import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.interfaces.Coordinates;
import org.eclipse.cargotracker.interfaces.CoordinatesFactory;

/** View adapter for displaying a location in a real-time tracking context. */
public class LocationViewAdapter {

  private final Location location;
  private final CoordinatesFactory coordinatesFactory;

  /**
   * Constructs a new adapter backed by the provided {@link CoordinatesFactory}.
   *
   * @param location           the location domain object to adapt
   * @param coordinatesFactory the Redis-backed coordinates factory (Amazon ElastiCache for Redis)
   */
  public LocationViewAdapter(Location location, CoordinatesFactory coordinatesFactory) {
    this.location = location;
    this.coordinatesFactory = coordinatesFactory;
  }

  public String getUnLocode() {
    return location.getUnLocode().getIdString();
  }

  public String getName() {
    return location.getName();
  }

  public Coordinates getCoordinates() {
    return coordinatesFactory.find(location);
  }
}
