package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Jakarta REST configuration. */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  /**
   * Bean Validation error-in-response property key (container-agnostic).
   *
   * <p>Previously referenced via GlassFish/Jersey-specific
   * {@code org.glassfish.jersey.server.ServerProperties.BV_SEND_ERROR_IN_RESPONSE}.
   * Replaced with the standard string constant so the application is not coupled
   * to GlassFish/Jersey and can run on any Jakarta EE-compliant container
   * (e.g., embedded Tomcat via Spring Boot on Amazon EKS).
   */
  private static final String BV_SEND_ERROR_IN_RESPONSE =
      "jersey.config.bv.feature.disable.ValidationError.in.response.entity";

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put(BV_SEND_ERROR_IN_RESPONSE, true);
    return properties;
  }
}
