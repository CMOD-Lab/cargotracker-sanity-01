package org.eclipse.cargotracker.interfaces.rest;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

/**
 * Health check endpoint for container orchestration platforms (e.g., Kubernetes on Amazon EKS).
 *
 * <p>Exposes a simple liveness probe at {@code GET /rest/health} that returns HTTP 200 with a
 * JSON body when the application is running. Container orchestrators (Kubernetes liveness /
 * readiness probes, AWS ALB health checks, etc.) can poll this endpoint to determine whether
 * the instance is healthy.
 *
 * <p>Example response:
 * <pre>
 * {
 *   "status": "UP",
 *   "timestamp": "2024-06-01T12:00:00Z"
 * }
 * </pre>
 */
@Path("health")
public class HealthResource {

  /**
   * Returns the application health status.
   *
   * @return HTTP 200 with {@code {"status":"UP","timestamp":"<ISO-8601>"}}
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    JsonObject body = Json.createObjectBuilder()
        .add("status", "UP")
        .add("timestamp", Instant.now().toString())
        .build();
    return Response.ok(body).build();
  }
}
