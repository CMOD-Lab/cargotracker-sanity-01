package org.eclipse.cargotracker.interfaces.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for containerization readiness.
 * Accessible at GET /rest/health
 * Returns HTTP 200 with JSON status when the application is healthy.
 */
@ApplicationScoped
@Path("/health")
public class HealthCheckResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("application", "cargo-tracker");
        return Response.ok(status).build();
    }
}
