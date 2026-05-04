package com.loadbalancer.strategy;

import com.loadbalancer.model.Algorithm;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory that maps {@link Algorithm} enum values to concrete
 * {@link LoadBalancingStrategy} singleton instances.
 *
 * <p>Adding a new algorithm requires:
 * <ol>
 *   <li>Add a constant to {@link Algorithm}.</li>
 *   <li>Create a class implementing {@link LoadBalancingStrategy}.</li>
 *   <li>Register it in the {@code strategies} map below.</li>
 * </ol>
 *
 * <p>This factory is used by the {@link com.loadbalancer.engine.RoutingEngine}
 * to obtain the active strategy when the algorithm is changed at runtime.
 */
public class StrategyFactory {

    private final Map<Algorithm, LoadBalancingStrategy> strategies;

    public StrategyFactory() {
        strategies = new EnumMap<>(Algorithm.class);
        strategies.put(Algorithm.ROUND_ROBIN, new RoundRobinStrategy());
        strategies.put(Algorithm.WEIGHTED_ROUND_ROBIN, new WeightedRoundRobinStrategy());
        strategies.put(Algorithm.RANDOM, new RandomStrategy());
        strategies.put(Algorithm.LEAST_CONNECTIONS, new LeastConnectionsStrategy());
        strategies.put(Algorithm.IP_HASH, new IpHashStrategy());
        strategies.put(Algorithm.CONSISTENT_HASH, new ConsistentHashStrategy());
    }

    /**
     * Returns the strategy for the given algorithm.
     *
     * @throws IllegalArgumentException if the algorithm is not registered
     */
    public LoadBalancingStrategy getStrategy(Algorithm algorithm) {
        LoadBalancingStrategy strategy = strategies.get(algorithm);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
        return strategy;
    }
}
