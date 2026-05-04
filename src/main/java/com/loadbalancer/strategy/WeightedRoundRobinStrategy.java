package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;

/**
 * <b>Weighted Round-Robin (Smooth)</b> — NGINX-style smooth weighted round-robin.
 *
 * <p>Instead of bursting all weighted requests to high-weight servers,
 * this algorithm interleaves selections smoothly. For backends with weights
 * {A=5, B=1, C=1}, the selection sequence is approximately:
 * {@code A, A, B, A, C, A, A} rather than {@code A,A,A,A,A,B,C}.
 *
 * <h3>Algorithm (per request):</h3>
 * <ol>
 *   <li>For each backend: {@code currentWeight += effectiveWeight}</li>
 *   <li>Pick the backend with the highest {@code currentWeight}.</li>
 *   <li>Subtract {@code totalWeight} from the selected backend's {@code currentWeight}.</li>
 * </ol>
 *
 * <p><b>Note:</b> This implementation mutates the backend's
 * {@code currentWeight} / {@code effectiveWeight} fields, which are
 * declared as volatile in the {@link Backend} class. In a multi-threaded
 * environment, this is approximately correct — minor races in weight
 * tracking do not affect correctness, only slight fairness jitter.
 *
 * <p><b>Best for:</b> heterogeneous backends with known capacity differences.
 */
public class WeightedRoundRobinStrategy implements LoadBalancingStrategy {

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        int totalWeight = 0;
        Backend selected = null;

        for (Backend backend : healthyBackends) {
            // Step 1: increase each backend's current weight
            backend.setCurrentWeight(backend.getCurrentWeight() + backend.getEffectiveWeight());
            totalWeight += backend.getEffectiveWeight();

            // Step 2: pick the one with the highest current weight
            if (selected == null || backend.getCurrentWeight() > selected.getCurrentWeight()) {
                selected = backend;
            }
        }

        if (selected == null) {
            return healthyBackends.get(0);
        }

        // Step 3: reduce selected backend's current weight
        selected.setCurrentWeight(selected.getCurrentWeight() - totalWeight);
        return selected;
    }
}
