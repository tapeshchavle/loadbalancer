package com.loadbalancer.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store for sticky-session mappings.
 *
 * <p>Maps a client identifier (e.g., cookie value or IP) to the backend ID
 * that should serve subsequent requests. Entries expire after a configurable TTL.
 *
 * <p>In a multi-node active-active deployment, this would be swapped for a
 * Redis-backed implementation sharing the same interface.
 *
 * <h3>Eviction:</h3>
 * <p>Expired entries are lazily evicted on read and periodically purged
 * by the {@link #evictExpired()} method (called from a scheduled task).
 */
@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    /**
     * Internal record holding a backend mapping with its expiration time.
     */
    private record SessionEntry(String backendId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────
    // Core operations
    // ──────────────────────────────────────────────────────────────────

    /**
     * Looks up the backend ID for a client identifier.
     *
     * @param clientIdentifier the sticky cookie value or client IP
     * @return the backend ID if a valid (non-expired) mapping exists
     */
    public Optional<String> getBackendId(String clientIdentifier) {
        SessionEntry entry = sessions.get(clientIdentifier);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            sessions.remove(clientIdentifier);
            return Optional.empty();
        }
        return Optional.of(entry.backendId());
    }

    /**
     * Stores a sticky-session mapping.
     *
     * @param clientIdentifier the sticky cookie value or client IP
     * @param backendId        the backend that should serve this client
     * @param ttl              how long the mapping should live
     */
    public void store(String clientIdentifier, String backendId, Duration ttl) {
        sessions.put(clientIdentifier,
                new SessionEntry(backendId, Instant.now().plus(ttl)));
        log.trace("Sticky session stored: {} → {} (TTL={}s)", clientIdentifier, backendId, ttl.toSeconds());
    }

    /**
     * Removes the mapping for a specific client.
     */
    public void remove(String clientIdentifier) {
        sessions.remove(clientIdentifier);
    }

    /**
     * Invalidates all sessions pointing to a specific backend.
     * Called when a backend becomes unhealthy.
     */
    public void invalidateBackend(String backendId) {
        int removed = 0;
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionEntry> entry = iterator.next();
            if (entry.getValue().backendId().equals(backendId)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Invalidated {} sticky sessions for backend {}", removed, backendId);
        }
    }

    /**
     * Purges all expired entries. Called periodically by a scheduled task.
     */
    public void evictExpired() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
        int evicted = before - sessions.size();
        if (evicted > 0) {
            log.debug("Evicted {} expired sticky sessions", evicted);
        }
    }

    /**
     * Returns the number of active session mappings.
     */
    public int size() {
        return sessions.size();
    }
}
