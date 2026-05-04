package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <b>Random</b> — selects a random healthy backend for each request.
 *
 * <p>Uses {@link ThreadLocalRandom} for high-performance, contention-free
 * random number generation across threads.
 *
 * <p>Statistically, over a large number of requests, this produces a roughly
 * uniform distribution identical to round-robin. The key difference is
 * non-determinism — useful when you explicitly do not want predictable patterns.
 *
 * <p><b>Best for:</b> scenarios where simplicity and zero shared state are priorities.
 */
public class RandomStrategy implements LoadBalancingStrategy {

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        int index = ThreadLocalRandom.current().nextInt(healthyBackends.size());
        return healthyBackends.get(index);
    }
}
