package org.eclipse.cargotracker.application.util;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Jakarta REST configuration.
 *
 * <p>Configured as a stateless, container-neutral JAX-RS Application suitable for
 * deployment on Amazon EKS or any Jakarta EE-compliant runtime (e.g., embedded Tomcat
 * via Spring Boot, Payara Micro, Open Liberty). GlassFish/Jersey-specific properties
 * have been removed to ensure portability across container runtimes.
 *
 * <p>Bean Validation error propagation to REST responses is handled by the Jakarta EE
 * runtime's standard exception mapping mechanism (ExceptionMapper) rather than a
 * GlassFish/Jersey vendor-specific server property.
 */
@ApplicationPath("rest")
public class RestConfiguration extends Application {
  // No GlassFish/Jersey-specific properties. The application is now portable
  // across any Jakarta EE 10-compliant container runtime and suitable for
  // containerized deployment on Amazon EKS.
}
