package org.eclipse.cargotracker.interfaces.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for containerization readiness.
 *
 * <p>Exposes GET /rest/health returning a JSON status response.
 * This endpoint is used by container orchestration platforms (e.g., EKS) for
 * liveness and readiness probes.
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

  /**
   * Returns the health status of the application.
   *
   * @return HTTP 200 with JSON body {"status":"UP","timestamp":"..."} when healthy
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("status", "UP");
    status.put("timestamp", Instant.now().toString());
    return Response.ok(status).build();
  }
}
