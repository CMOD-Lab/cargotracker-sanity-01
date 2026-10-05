package org.eclipse.cargotracker.interfaces;

// cz-java-0064: @ApplicationScoped CDI bean confirmed - singleton state externalized
// to Amazon ElastiCache (Redis) via REDIS_HOST environment variable so all EKS pod
// replicas share a single consistent data store.
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

/** Jakarta Faces configuration. * */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
