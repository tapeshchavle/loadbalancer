package com.loadbalancer.health;

import com.loadbalancer.model.Backend;

/**
 * Abstraction for health-check probes.
 *
 * <p>Implementations define how a backend's liveness is verified.
 * The load balancer supports pluggable probe types (HTTP, TCP, custom)
 * to match different backend requirements.
 */
public interface HealthCheckProbe {

    /**
     * Probes the given backend.
     *
     * @param backend the backend to check
     * @return {@code true} if the backend is considered healthy
     */
    boolean check(Backend backend);
}
