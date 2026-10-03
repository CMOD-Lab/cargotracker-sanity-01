package org.eclipse.cargotracker.interfaces;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;

// cz-java-0064: @ApplicationScoped CDI bean confirmed stateless - no mutable singleton state held.
// Any shared state should be externalized to Amazon ElastiCache (Redis) on EKS.
// Redis endpoint configured via REDIS_HOST environment variable for horizontal scaling consistency.
/** Jakarta Faces configuration. * */
@FacesConfig()
@ApplicationScoped
public class FacesConfiguration {}
