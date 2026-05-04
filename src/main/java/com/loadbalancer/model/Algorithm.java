package com.loadbalancer.model;

/**
 * Supported load-balancing algorithms.
 *
 * <p>Each constant maps 1-to-1 to a {@link com.loadbalancer.strategy.LoadBalancingStrategy}
 * implementation via {@link com.loadbalancer.strategy.StrategyFactory}.
 */
public enum Algorithm {

    /** Distributes requests sequentially across servers in rotation. */
    ROUND_ROBIN,

    /** Round-robin with per-backend weights (NGINX smooth-weighted style). */
    WEIGHTED_ROUND_ROBIN,

    /** Selects a random healthy backend for each request. */
    RANDOM,

    /** Routes to the backend with the fewest active connections. */
    LEAST_CONNECTIONS,

    /** Deterministic routing based on MurmurHash3 of the client IP. */
    IP_HASH,

    /** Virtual-node hash ring for minimal disruption on topology changes. */
    CONSISTENT_HASH
}
