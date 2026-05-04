package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;

/**
 * <b>Least Connections</b> — routes to the backend with the fewest active connections.
 *
 * <p>Naturally adapts to runtime conditions:
 * <ul>
 *   <li>Slow backends accumulate connections → receive fewer new ones.</li>
 *   <li>Fast backends drain quickly → pick up more traffic.</li>
 *   <li>Handles variable request costs (heavy DB queries vs. light reads).</li>
 * </ul>
 *
 * <p>Tie-breaking: when two backends have the same connection count,
 * the one with the higher weight wins (effectively implementing
 * <em>Weighted Least Connections</em>).
 *
 * <p><b>Best for:</b> workloads with variable request processing times.
 */
public class LeastConnectionsStrategy implements LoadBalancingStrategy {

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        Backend selected = null;
        double lowestRatio = Double.MAX_VALUE;

        for (Backend backend : healthyBackends) {
            // Weighted least connections: connections / weight
            // Lower ratio = less loaded relative to capacity
            double ratio = (double) backend.getActiveConnections() / backend.getWeight();

            if (ratio < lowestRatio) {
                lowestRatio = ratio;
                selected = backend;
            } else if (ratio == lowestRatio && selected != null
                    && backend.getWeight() > selected.getWeight()) {
                // Tie-break: prefer higher-weight (more capable) server
                selected = backend;
            }
        }

        return (selected != null) ? selected : healthyBackends.get(0);
    }
}
