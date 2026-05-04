package com.loadbalancer.dto;

import com.loadbalancer.model.Algorithm;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to change the active load-balancing algorithm.
 *
 * @param algorithm      the algorithm to switch to
 * @param stickySessions whether to enable cookie-based sticky sessions
 * @param stickyTtlSeconds TTL for sticky session mappings
 */
public record ConfigureAlgorithmRequest(

        @NotNull(message = "algorithm is required")
        Algorithm algorithm,

        Boolean stickySessions,

        Integer stickyTtlSeconds
) {
    public ConfigureAlgorithmRequest {
        if (stickySessions == null) stickySessions = false;
        if (stickyTtlSeconds == null || stickyTtlSeconds <= 0) stickyTtlSeconds = 3600;
    }
}

