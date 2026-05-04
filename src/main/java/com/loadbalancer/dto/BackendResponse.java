package com.loadbalancer.dto;

import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendStatus;

import java.time.Instant;

/**
 * Response payload representing a backend server.
 */
public record BackendResponse(
        String id,
        String address,
        int port,
        int weight,
        BackendStatus status,
        int activeConnections,
        long totalRequests,
        long totalFailures,
        String healthCheckPath,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Factory method to project a {@link Backend} domain entity into a response DTO.
     */
    public static BackendResponse from(Backend b) {
        return new BackendResponse(
                b.getId(),
                b.getAddress(),
                b.getPort(),
                b.getWeight(),
                b.getStatus(),
                b.getActiveConnections(),
                b.getTotalRequests(),
                b.getTotalFailures(),
                b.getHealthCheckPath(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
