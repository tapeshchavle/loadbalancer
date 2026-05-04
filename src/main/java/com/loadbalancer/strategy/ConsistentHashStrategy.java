package com.loadbalancer.strategy;

import com.loadbalancer.model.Backend;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <b>Consistent Hash</b> — virtual-node hash ring for minimal disruption.
 *
 * <p>When backends are added or removed, only {@code 1/n} of requests get
 * remapped (unlike IP Hash where most requests remap). This is achieved by
 * mapping each backend to many <em>virtual nodes</em> on a hash ring.
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>Each backend is placed at {@value #VIRTUAL_NODES} positions on a ring [0, 2^31).</li>
 *   <li>For each request, hash the client IP and find the next clockwise node on the ring.</li>
 *   <li>That node's backend handles the request.</li>
 * </ol>
 *
 * <h3>Ring rebuild:</h3>
 * <p>The ring is lazily rebuilt from the current healthy backends list on each call.
 * In production, you would rebuild only when the backend topology changes.
 * This implementation is simple and correct; the rebuild cost is negligible
 * compared to network I/O.
 *
 * <p><b>Best for:</b> dynamic scaling, cache servers, and systems where backend
 * churn must not invalidate most sessions/caches.
 */
public class ConsistentHashStrategy implements LoadBalancingStrategy {

    private static final int VIRTUAL_NODES = 150;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final TreeMap<Integer, Backend> ring = new TreeMap<>();
    private int lastBackendHashCode = 0;

    @Override
    public Backend selectBackend(List<Backend> healthyBackends, String clientIp) {
        rebuildRingIfNeeded(healthyBackends);

        int hash = hash(clientIp);

        lock.readLock().lock();
        try {
            // Find the first node clockwise from the hash position
            SortedMap<Integer, Backend> tail = ring.tailMap(hash);
            int nodeHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
            return ring.get(nodeHash);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Rebuilds the hash ring only when the backend list changes.
     * Uses the hash code of the backend-id list as a cheap change-detection mechanism.
     */
    private void rebuildRingIfNeeded(List<Backend> healthyBackends) {
        int currentHash = healthyBackends.stream()
                .map(Backend::getId)
                .toList()
                .hashCode();

        if (currentHash == lastBackendHashCode) {
            return; // no change
        }

        lock.writeLock().lock();
        try {
            // Double-check after acquiring write lock
            if (currentHash == lastBackendHashCode) {
                return;
            }

            ring.clear();
            for (Backend backend : healthyBackends) {
                for (int i = 0; i < VIRTUAL_NODES; i++) {
                    int vnodeHash = hash(backend.getId() + "#" + i);
                    ring.put(vnodeHash, backend);
                }
            }
            lastBackendHashCode = currentHash;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * FNV-1a hash — fast, good distribution, widely used in hash ring implementations.
     */
    private int hash(String key) {
        int hash = 0x811c9dc5; // FNV offset basis
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x01000193; // FNV prime
        }
        return hash & Integer.MAX_VALUE; // ensure positive
    }
}
