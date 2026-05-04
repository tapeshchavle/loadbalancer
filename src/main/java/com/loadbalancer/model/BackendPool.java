package com.loadbalancer.model;

import com.loadbalancer.exception.BackendAlreadyExistsException;
import com.loadbalancer.exception.BackendNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe container for all registered backend servers.
 *
 * <p>Backed by a {@link ConcurrentHashMap} so reads on the data-plane
 * hot path never block. Mutations (register / remove) are rare control-plane
 * operations and are individually thread-safe.
 */
@Component
public class BackendPool {

    /** Primary index: backend-id → Backend */
    private final ConcurrentHashMap<String, Backend> backends = new ConcurrentHashMap<>();

    /** Secondary index to detect duplicates: "address:port" → backend-id */
    private final ConcurrentHashMap<String, String> addressIndex = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────
    // Mutators (control-plane)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Registers a new backend server.
     *
     * @throws BackendAlreadyExistsException if a backend with the same address:port exists
     */
    public Backend register(String address, int port, int weight, String healthCheckPath) {
        String key = address + ":" + port;
        if (addressIndex.containsKey(key)) {
            throw new BackendAlreadyExistsException(
                    "Backend already registered at " + key);
        }

        Backend backend = new Backend(address, port, weight, healthCheckPath);
        backends.put(backend.getId(), backend);
        addressIndex.put(key, backend.getId());
        return backend;
    }

    /**
     * Marks a backend as DRAINING and returns it.
     * The caller is responsible for waiting on connection drain before full removal.
     *
     * @return the backend being drained
     * @throws BackendNotFoundException if no backend with the given id exists
     */
    public Backend startDraining(String backendId) {
        Backend backend = getById(backendId);
        backend.markDraining();
        return backend;
    }

    /**
     * Fully removes a backend from the pool (called after drain completes).
     */
    public Backend remove(String backendId) {
        Backend backend = backends.remove(backendId);
        if (backend == null) {
            throw new BackendNotFoundException("Backend not found: " + backendId);
        }
        addressIndex.remove(backend.getAddress() + ":" + backend.getPort());
        return backend;
    }

    // ──────────────────────────────────────────────────────────────────
    // Queries (data-plane — hot path)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns all backends whose status is {@link BackendStatus#HEALTHY}.
     * This is the primary query used on every incoming request.
     */
    public List<Backend> getHealthyBackends() {
        return backends.values().stream()
                .filter(Backend::isAvailable)
                .toList();
    }

    /**
     * Returns all registered backends regardless of status.
     */
    public Collection<Backend> getAll() {
        return backends.values();
    }

    /**
     * Looks up a backend by its unique ID.
     *
     * @throws BackendNotFoundException if no backend with the given id exists
     */
    public Backend getById(String backendId) {
        Backend backend = backends.get(backendId);
        if (backend == null) {
            throw new BackendNotFoundException("Backend not found: " + backendId);
        }
        return backend;
    }

    /**
     * Returns the number of registered backends.
     */
    public int size() {
        return backends.size();
    }
}
