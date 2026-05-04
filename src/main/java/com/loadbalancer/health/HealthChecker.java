package com.loadbalancer.health;

import com.loadbalancer.event.BackendHealthEvent;
import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendPool;
import com.loadbalancer.model.BackendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled health checker that continuously monitors all registered backends.
 *
 * <p>Runs at a fixed interval (configurable via {@code lb.health.interval}).
 * For each backend:
 * <ol>
 *   <li>Runs the configured {@link HealthCheckProbe}.</li>
 *   <li>Tracks consecutive successes / failures.</li>
 *   <li>Transitions the backend status based on thresholds.</li>
 *   <li>Publishes a {@link BackendHealthEvent} on status change.</li>
 * </ol>
 *
 * <h3>State machine thresholds:</h3>
 * <ul>
 *   <li>Mark UNHEALTHY after {@code unhealthyThreshold} consecutive failures.</li>
 *   <li>Mark HEALTHY after {@code healthyThreshold} consecutive successes.</li>
 * </ul>
 */
@Component
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final BackendPool backendPool;
    private final HealthCheckProbe probe;
    private final ApplicationEventPublisher eventPublisher;
    private final int healthyThreshold;
    private final int unhealthyThreshold;

    public HealthChecker(BackendPool backendPool,
                         HealthCheckProbe probe,
                         ApplicationEventPublisher eventPublisher,
                         int healthyThreshold,
                         int unhealthyThreshold) {
        this.backendPool = backendPool;
        this.probe = probe;
        this.eventPublisher = eventPublisher;
        this.healthyThreshold = healthyThreshold;
        this.unhealthyThreshold = unhealthyThreshold;
    }

    /**
     * Periodic health-check loop. Iterates all backends and probes each one.
     * Backends in DRAINING status are skipped (they are being removed).
     */
    @Scheduled(fixedRateString = "${lb.health.interval:5000}")
    public void checkAll() {
        for (Backend backend : backendPool.getAll()) {
            if (backend.getStatus() == BackendStatus.DRAINING) {
                continue; // skip backends being drained
            }

            boolean healthy = false;
            try {
                healthy = probe.check(backend);
            } catch (Exception e) {
                log.warn("Health check threw exception for {}: {}", backend.getId(), e.getMessage());
            }

            if (healthy) {
                onSuccess(backend);
            } else {
                onFailure(backend);
            }
        }
    }

    private void onSuccess(Backend backend) {
        backend.recordHealthCheckSuccess();

        if (backend.getConsecutiveSuccesses() >= healthyThreshold
                && backend.getStatus() != BackendStatus.HEALTHY) {

            BackendStatus previous = backend.markHealthy();
            if (previous != null) {
                log.info("Backend {} transitioned {} → HEALTHY", backend.getId(), previous);
                eventPublisher.publishEvent(
                        new BackendHealthEvent(this, backend.getId(), previous, BackendStatus.HEALTHY));
            }
        }
    }

    private void onFailure(Backend backend) {
        backend.recordHealthCheckFailure();

        if (backend.getConsecutiveFailures() >= unhealthyThreshold
                && backend.getStatus() != BackendStatus.UNHEALTHY) {

            BackendStatus previous = backend.markUnhealthy();
            if (previous != null) {
                log.warn("Backend {} transitioned {} → UNHEALTHY", backend.getId(), previous);
                eventPublisher.publishEvent(
                        new BackendHealthEvent(this, backend.getId(), previous, BackendStatus.UNHEALTHY));
            }
        }
    }
}
