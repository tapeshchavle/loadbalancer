package com.loadbalancer.dto;

import java.util.Map;

/**
 * Response payload wrapping the forwarded response from a backend.
 */
public record ForwardResponse(
        int statusCode,
        Map<String, String> headers,
        String body,
        String servedBy
) {}
