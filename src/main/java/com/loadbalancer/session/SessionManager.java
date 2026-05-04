package com.loadbalancer.session;

import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Manages cookie-based sticky sessions.
 *
 * <p>When sticky sessions are enabled:
 * <ol>
 *   <li>On first request: the routing engine selects a backend normally,
 *       then this manager stores the mapping {@code clientId → backendId}.</li>
 *   <li>On subsequent requests: this manager looks up the stored mapping
 *       and returns the same backend (if it is still healthy).</li>
 * </ol>
 *
 * <p>If the sticky backend is no longer healthy, the mapping is invalidated
 * and normal algorithm-based routing resumes.
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    public static final String STICKY_COOKIE_NAME = "LB_BACKEND_ID";

    private final SessionStore sessionStore;
    private final BackendPool backendPool;

    private volatile boolean enabled = false;
    private volatile Duration ttl = Duration.ofSeconds(3600);

    public SessionManager(SessionStore sessionStore, BackendPool backendPool) {
        this.sessionStore = sessionStore;
        this.backendPool = backendPool;
    }

    // ──────────────────────────────────────────────────────────────────
    // Configuration
    // ──────────────────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Sticky sessions {}", enabled ? "ENABLED" : "DISABLED");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getTtl() {
        return ttl;
    }

    // ──────────────────────────────────────────────────────────────────
    // Core operations
    // ──────────────────────────────────────────────────────────────────

    /**
     * Attempts to resolve a sticky backend for the given client.
     *
     * @param clientIdentifier typically the sticky cookie value
     * @return the sticky backend if found and healthy, otherwise empty
     */
    public Optional<Backend> resolveSticky(String clientIdentifier) {
        if (!enabled || clientIdentifier == null || clientIdentifier.isBlank()) {
            return Optional.empty();
        }

        Optional<String> backendId = sessionStore.getBackendId(clientIdentifier);
        if (backendId.isEmpty()) {
            return Optional.empty();
        }

        try {
            Backend backend = backendPool.getById(backendId.get());
            if (backend.isAvailable()) {
                log.trace("Sticky session hit: {} → {}", clientIdentifier, backend.getId());
                return Optional.of(backend);
            } else {
                // Backend is no longer healthy — invalidate the mapping
                log.debug("Sticky backend {} is {}, invalidating session",
                        backend.getId(), backend.getStatus());
                sessionStore.remove(clientIdentifier);
                return Optional.empty();
            }
        } catch (Exception e) {
            // Backend was removed entirely
            sessionStore.remove(clientIdentifier);
            return Optional.empty();
        }
    }

    /**
     * Stores a new sticky-session mapping after a backend is selected.
     *
     * @param clientIdentifier the client's identifier (cookie value)
     * @param backend          the selected backend
     */
    public void storeSession(String clientIdentifier, Backend backend) {
        if (!enabled || clientIdentifier == null || clientIdentifier.isBlank()) {
            return;
        }
        sessionStore.store(clientIdentifier, backend.getId(), ttl);
    }

    /**
     * Invalidates all sessions pointing to a specific backend.
     * Called by {@link com.loadbalancer.event.BackendHealthEventListener}
     * when a backend becomes unhealthy.
     */
    public void invalidateBackend(String backendId) {
        sessionStore.invalidateBackend(backendId);
    }

    /**
     * Periodic cleanup of expired session entries.
     */
    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        sessionStore.evictExpired();
    }
}
