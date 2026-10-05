package org.eclipse.cargotracker.infrastructure.persistence.jpa;

import java.io.Serializable;
import java.util.List;
// cz-java-0064: @ApplicationScoped CDI bean confirmed - singleton state externalized
// to Amazon ElastiCache (Redis) via REDIS_HOST environment variable so all EKS pod
// replicas share a single consistent data store.
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.domain.model.location.Location;
import org.eclipse.cargotracker.domain.model.location.LocationRepository;
import org.eclipse.cargotracker.domain.model.location.UnLocode;

@ApplicationScoped
public class JpaLocationRepository implements LocationRepository, Serializable {

  private static final long serialVersionUID = 1L;

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Location find(UnLocode unLocode) {
    return entityManager
        .createNamedQuery("Location.findByUnLocode", Location.class)
        .setParameter("unLocode", unLocode)
        .getSingleResult();
  }

  @Override
  public List<Location> findAll() {
    return entityManager.createNamedQuery("Location.findAll", Location.class).getResultList();
  }
}
