package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;

/**
 * <b>IP Hash</b> — deterministic routing based on the client IP address.
 *
 * <p>Uses a MurmurHash3-inspired mixing function to produce a well-distributed
 * hash from the client IP. The same IP always maps to the same backend (as long
 * as the backend pool does not change).
 *
 * <p><b>Provides session persistence at Layer 4</b> without storing any state.
 * Every LB node can independently compute the same hash.
 *
 * <h3>Limitations:</h3>
 * <ul>
 *   <li>Corporate NAT — thousands of users behind one IP all hit the same backend.</li>
 *   <li>Adding/removing backends causes massive reshuffling ({@code hash % n} changes).</li>
 *   <li>Use {@link ConsistentHashStrategy} to minimize reshuffling.</li>
 * </ul>
 *
 * <p><b>Best for:</b> basic session persistence without cookies or shared state.
 */
public class IpHashStrategy implements LoadBalancingStrategy {

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        int hash = murmurHash3(clientIp);
        int index = Math.floorMod(hash, healthyBackends.size());
        return healthyBackends.get(index);
    }

    /**
     * MurmurHash3 32-bit finalizer — high-quality mixing for short keys.
     *
     * <p>This is the same mixing function used in Guava's {@code Hashing.murmur3_32()}.
     * It provides excellent distribution and avalanche properties.
     */
    private int murmurHash3(String key) {
        int h = key.hashCode();
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }
}
