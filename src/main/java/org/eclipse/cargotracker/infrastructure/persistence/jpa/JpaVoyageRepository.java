package org.eclipse.cargotracker.infrastructure.persistence.jpa;

import java.io.Serializable;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.cargotracker.domain.model.voyage.Voyage;
import org.eclipse.cargotracker.domain.model.voyage.VoyageNumber;
import org.eclipse.cargotracker.domain.model.voyage.VoyageRepository;

/**
 * CZ-JAVA-0064 (Singleton State Storage): This CDI @ApplicationScoped bean acts as a singleton.
 * For horizontal scaling on EKS, any mutable singleton state should be externalized to
 * Amazon ElastiCache (Redis) so all pod replicas share a single consistent data store.
 * Configure the Redis connection via environment variables:
 *   REDIS_HOST - ElastiCache Redis endpoint (e.g., ${REDIS_HOST})
 *   REDIS_PORT - Redis port (e.g., ${REDIS_PORT:6379})
 */
@ApplicationScoped
public class JpaVoyageRepository implements VoyageRepository, Serializable {

  private static final long serialVersionUID = 1L;

  @PersistenceContext private EntityManager entityManager;

  @Override
  public Voyage find(VoyageNumber voyageNumber) {
    return entityManager
        .createNamedQuery("Voyage.findByVoyageNumber", Voyage.class)
        .setParameter("voyageNumber", voyageNumber)
        .getSingleResult();
  }

  @Override
  public List<Voyage> findAll() {
    return entityManager.createNamedQuery("Voyage.findAll", Voyage.class).getResultList();
  }
}
