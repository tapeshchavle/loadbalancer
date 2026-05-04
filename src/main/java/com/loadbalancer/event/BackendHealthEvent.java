package com.loadbalancer.event;

import com.loadbalancer.model.BackendStatus;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a backend's health status transitions.
 *
 * <p>Subscribers (e.g., logging, session invalidation) react without
 * coupling to the health checker — classic Observer pattern via Spring Events.
 */
public class BackendHealthEvent extends ApplicationEvent {

    private final String backendId;
    private final BackendStatus previousStatus;
    private final BackendStatus newStatus;

    public BackendHealthEvent(Object source, String backendId,
                              BackendStatus previousStatus, BackendStatus newStatus) {
        super(source);
        this.backendId = backendId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public String getBackendId() { return backendId; }
    public BackendStatus getPreviousStatus() { return previousStatus; }
    public BackendStatus getNewStatus() { return newStatus; }

    @Override
    public String toString() {
        return "BackendHealthEvent{backendId='%s', %s → %s}"
                .formatted(backendId, previousStatus, newStatus);
    }
}
