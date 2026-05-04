package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;

/**
 * Abstraction for load-balancing algorithms.
 *
 * <p>Implementations are stateless singletons (except those that need
 * per-request bookkeeping like weighted round-robin). They receive the
 * list of <em>healthy</em> backends and the client IP, and return the
 * single backend that should serve the request.
 *
 * <p>Adding a new algorithm requires only:
 * <ol>
 *   <li>Implement this interface.</li>
 *   <li>Register the implementation in {@link StrategyFactory}.</li>
 *   <li>Add a constant to {@link com.loadbalancer.model.Algorithm}.</li>
 * </ol>
 *
 * <p><strong>Open/Closed Principle:</strong> existing code is never modified
 * when a new algorithm is added.
 */
public interface LoadBalancingStrategy {

    /**
     * Selects a backend from the healthy pool.
     *
     * @param healthyBackends non-empty list of healthy backends
     * @param clientIp        the client's IP address (used by hash-based strategies)
     * @return the selected backend
     */
    Backend selectBackend(List<Backend> healthyBackends, String clientIp);
}
