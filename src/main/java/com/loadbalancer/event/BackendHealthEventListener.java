package com.loadbalancer.event;

import com.loadbalancer.model.BackendStatus;
import com.loadbalancer.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link BackendHealthEvent}s and takes corrective actions.
 *
 * <p>When a backend becomes UNHEALTHY:
 * <ul>
 *   <li>Invalidates all sticky sessions pointing to that backend.</li>
 *   <li>Logs the transition for observability.</li>
 * </ul>
 *
 * <p>This decouples health monitoring from session management —
 * neither knows about the other's internals.
 */
@Component
public class BackendHealthEventListener {

    private static final Logger log = LoggerFactory.getLogger(BackendHealthEventListener.class);

    private final SessionManager sessionManager;

    public BackendHealthEventListener(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventListener
    public void onHealthChange(BackendHealthEvent event) {
        log.info("Health event: backend={} transition={} → {}",
                event.getBackendId(), event.getPreviousStatus(), event.getNewStatus());

        if (event.getNewStatus() == BackendStatus.UNHEALTHY
                || event.getNewStatus() == BackendStatus.DRAINING) {
            // Invalidate sticky sessions so clients get rerouted to healthy backends
            sessionManager.invalidateBackend(event.getBackendId());
            log.info("Invalidated sticky sessions for backend {}", event.getBackendId());
        }
    }
}
