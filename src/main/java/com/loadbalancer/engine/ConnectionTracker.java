package com.loadbalancer.engine;

import com.loadbalancer.model.Backend;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized, thread-safe tracker for active connections per backend.
 *
 * <p>While the {@link Backend} entity itself tracks its own connection count,
 * this component provides an independent, aggregate view useful for:
 * <ul>
 *   <li>Monitoring dashboards (total active connections across all backends).</li>
 *   <li>Connection drain verification (is the backend fully drained?).</li>
 *   <li>Rate limiting (global connection cap).</li>
 * </ul>
 *
 * <p>All operations are lock-free using {@link AtomicInteger}.
 */
public class ConnectionTracker {

    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    /**
     * Increments the connection count for a backend.
     *
     * @param backendId the backend's unique ID
     * @return the new connection count for this backend
     */
    public int increment(String backendId) {
        totalConnections.incrementAndGet();
        return connectionCounts
                .computeIfAbsent(backendId, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * Decrements the connection count for a backend.
     *
     * @param backendId the backend's unique ID
     * @return the new connection count for this backend
     */
    public int decrement(String backendId) {
        totalConnections.updateAndGet(c -> Math.max(0, c - 1));
        AtomicInteger counter = connectionCounts.get(backendId);
        if (counter != null) {
            return counter.updateAndGet(c -> Math.max(0, c - 1));
        }
        return 0;
    }

    /**
     * Returns the active connection count for a specific backend.
     */
    public int getCount(String backendId) {
        AtomicInteger counter = connectionCounts.get(backendId);
        return (counter != null) ? counter.get() : 0;
    }

    /**
     * Returns the total number of active connections across all backends.
     */
    public int getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * Removes tracking for a backend (called after full removal).
     */
    public void removeBackend(String backendId) {
        connectionCounts.remove(backendId);
    }
}
