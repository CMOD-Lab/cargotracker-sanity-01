package org.eclipse.cargotracker.application.util;

import java.util.HashMap;
import java.util.Map;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Jakarta REST configuration. */
@ApplicationPath("rest")
public class RestConfiguration extends Application {

  @Override
  public Map<String, Object> getProperties() {
    Map<String, Object> properties = new HashMap<String, Object>();
    // Replaced GlassFish/Jersey-specific ServerProperties.BV_SEND_ERROR_IN_RESPONSE
    // with the portable standard property string for container-agnostic deployment (EKS/embedded Tomcat).
    properties.put("jersey.config.bv.sendErrorInResponse", true);
    return properties;
  }
}
