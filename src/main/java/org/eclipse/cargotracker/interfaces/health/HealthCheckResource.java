package org.eclipse.cargotracker.interfaces.health;

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
 * <p>Provides a simple liveness/readiness probe at GET /rest/health
 * returning HTTP 200 with a JSON status payload when the application is running.
 *
 * <p>health-check-endpoint: Mandatory containerization requirement - exposes /rest/health
 * for Kubernetes liveness and readiness probes on EKS.
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

    /**
     * Returns the health status of the application.
     *
     * @return HTTP 200 with JSON body {"status":"UP","timestamp":"..."} when healthy.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, Object> healthStatus = new LinkedHashMap<>();
        healthStatus.put("status", "UP");
        healthStatus.put("application", "cargo-tracker");
        healthStatus.put("timestamp", Instant.now().toString());
        return Response.ok(healthStatus).build();
    }
}
