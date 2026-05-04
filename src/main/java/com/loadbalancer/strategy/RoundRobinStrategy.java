package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>Round-Robin</b> — distributes requests sequentially across servers.
 *
 * <pre>
 *   Request 1 → Backend 0
 *   Request 2 → Backend 1
 *   Request 3 → Backend 2
 *   Request 4 → Backend 0  (wraps around)
 * </pre>
 *
 * <p>Uses an {@link AtomicInteger} counter for lock-free, thread-safe rotation.
 * The counter is allowed to overflow — the modulo operation handles it correctly
 * with {@code Math.floorMod}.
 *
 * <p><b>Best for:</b> homogeneous backends with uniform request cost.
 */
public class RoundRobinStrategy implements LoadBalancingStrategy {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        int index = Math.floorMod(counter.getAndIncrement(), healthyBackends.size());
        return healthyBackends.get(index);
    }
}
