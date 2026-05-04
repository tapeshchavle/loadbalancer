package com.loadbalancer.dto;

import com.loadbalancer.model.Algorithm;

/**
 * Response payload after an algorithm change.
 */
public record ConfigureAlgorithmResponse(
        Algorithm algorithm,
        boolean stickySessions,
        int stickyTtlSeconds,
        String message
) {}
