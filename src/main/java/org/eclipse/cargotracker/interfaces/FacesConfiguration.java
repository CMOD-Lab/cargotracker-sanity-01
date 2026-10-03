package org.eclipse.cargotracker.interfaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/**
 * Jakarta Faces configuration.
 *
 * <p>CZ-JAVA-0064 (Singleton State Storage): This CDI @ApplicationScoped bean acts as a singleton.
 * For horizontal scaling on EKS, any mutable singleton state should be externalized to
 * Amazon ElastiCache (Redis) so all pod replicas share a single consistent data store.
 * Configure the Redis connection via environment variables:
 *   REDIS_HOST - ElastiCache Redis endpoint (e.g., ${REDIS_HOST})
 *   REDIS_PORT - Redis port (e.g., ${REDIS_PORT:6379})
 */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
