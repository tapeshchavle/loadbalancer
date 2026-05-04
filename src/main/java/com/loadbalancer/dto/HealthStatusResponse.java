package com.loadbalancer.dto;

import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendStatus;

import java.time.Instant;

/**
 * Detailed health status response for a single backend.
 */
public record HealthStatusResponse(
        String backendId,
        BackendStatus status,
        int activeConnections,
        long totalRequests,
        long totalFailures,
        int consecutiveSuccesses,
        int consecutiveFailures,
        Instant lastHealthCheck
) {
    public static HealthStatusResponse from(Backend b) {
        return new HealthStatusResponse(
                b.getId(),
                b.getStatus(),
                b.getActiveConnections(),
                b.getTotalRequests(),
                b.getTotalFailures(),
                b.getConsecutiveSuccesses(),
                b.getConsecutiveFailures(),
                b.getLastHealthCheck()
        );
    }
}
