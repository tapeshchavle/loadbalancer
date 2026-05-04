package com.loadbalancer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for registering a new backend server.
 *
 * @param address        IP address or hostname of the backend
 * @param port           port the backend listens on (1–65535)
 * @param weight         relative weight for weighted algorithms (default: 1)
 * @param healthCheckPath HTTP path for health checks (default: /health)
 */
public record RegisterBackendRequest(

        @NotBlank(message = "address is required")
        String address,

        @Min(value = 1, message = "port must be between 1 and 65535")
        @Max(value = 65535, message = "port must be between 1 and 65535")
        int port,

        @Min(value = 1, message = "weight must be at least 1")
        int weight,

        String healthCheckPath
) {
    /**
     * Compact constructor – applies defaults for optional fields.
     */
    public RegisterBackendRequest {
        if (weight == 0) weight = 1;
        if (healthCheckPath == null || healthCheckPath.isBlank()) healthCheckPath = "/health";
    }
}
