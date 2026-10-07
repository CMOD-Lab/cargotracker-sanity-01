package org.eclipse.cargotracker.application.util;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Jakarta REST configuration. */
@ApplicationPath("rest")
public class RestConfiguration extends Application {
  // GlassFish/Jersey-specific ServerProperties removed for container portability.
  // Standard Jakarta RS Application subclass with no server-specific properties,
  // compatible with any Jakarta EE-compliant container (Payara, OpenLiberty, Tomcat, EKS).
}
