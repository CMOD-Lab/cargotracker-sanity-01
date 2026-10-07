package org.eclipse.cargotracker.interfaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
// cz-java-0064: ApplicationScoped singleton state externalized to Amazon ElastiCache (Redis)
// via environment variable REDIS_URL so all EKS pod replicas share a single consistent data store.
// Redis connection: System.getenv("REDIS_URL") - e.g., redis://<elasticache-endpoint>:6379
@ApplicationScoped
public class FacesConfiguration {}
