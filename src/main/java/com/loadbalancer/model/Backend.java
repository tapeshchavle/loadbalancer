package com.loadbalancer.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe domain entity representing a single backend server
 * registered with the load balancer.
 *
 * <p>All mutable state uses atomic types so the data-plane can read/write
 * concurrently without locks on the hot path.
 */
public class Backend {

    // ── Identity ──────────────────────────────────────────────────────
    private final String id;
    private final String address;
    private final int port;
    private final String healthCheckPath;
    private final Instant createdAt;

    // ── Configurable ──────────────────────────────────────────────────
    private volatile int weight;
    private volatile int maxConnections; // 0 = unlimited

    // ── Runtime state (atomic for lock-free hot-path access) ──────────
    private final AtomicReference<BackendStatus> status;
    private final AtomicInteger activeConnections;
    private final AtomicLong totalRequests;
    private final AtomicLong totalFailures;
    private final AtomicInteger consecutiveSuccesses;
    private final AtomicInteger consecutiveFailures;
    private volatile Instant updatedAt;
    private volatile Instant lastHealthCheck;

    // ── Weighted round-robin bookkeeping ──────────────────────────────
    private volatile int currentWeight;
    private volatile int effectiveWeight;

    // ──────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────

    public Backend(String address, int port, int weight, String healthCheckPath) {
        this.id = UUID.randomUUID().toString();
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.port = port;
        this.weight = weight;
        this.healthCheckPath = (healthCheckPath != null) ? healthCheckPath : "/health";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;

        this.status = new AtomicReference<>(BackendStatus.UNKNOWN);
        this.activeConnections = new AtomicInteger(0);
        this.totalRequests = new AtomicLong(0);
        this.totalFailures = new AtomicLong(0);
        this.consecutiveSuccesses = new AtomicInteger(0);
        this.consecutiveFailures = new AtomicInteger(0);

        this.maxConnections = 0;
        this.currentWeight = 0;
        this.effectiveWeight = weight;
    }

    // ──────────────────────────────────────────────────────────────────
    // Connection tracking
    // ──────────────────────────────────────────────────────────────────

    public int incrementConnections() {
        totalRequests.incrementAndGet();
        return activeConnections.incrementAndGet();
    }

    public int decrementConnections() {
        return activeConnections.updateAndGet(current -> Math.max(0, current - 1));
    }

    // ──────────────────────────────────────────────────────────────────
    // Health-state transitions
    // ──────────────────────────────────────────────────────────────────

    /**
     * Records a successful health check. Resets consecutive failures and
     * increments consecutive successes.
     */
    public void recordHealthCheckSuccess() {
        consecutiveFailures.set(0);
        consecutiveSuccesses.incrementAndGet();
        lastHealthCheck = Instant.now();
    }

    /**
     * Records a failed health check. Resets consecutive successes and
     * increments consecutive failures.
     */
    public void recordHealthCheckFailure() {
        consecutiveSuccesses.set(0);
        consecutiveFailures.incrementAndGet();
        totalFailures.incrementAndGet();
        lastHealthCheck = Instant.now();
    }

    /**
     * Transition to HEALTHY. Only succeeds if current status allows it.
     *
     * @return the previous status, or {@code null} if already HEALTHY.
     */
    public BackendStatus markHealthy() {
        BackendStatus previous;
        do {
            previous = status.get();
            if (previous == BackendStatus.HEALTHY || previous == BackendStatus.DRAINING) {
                return null; // no transition
            }
        } while (!status.compareAndSet(previous, BackendStatus.HEALTHY));
        updatedAt = Instant.now();
        return previous;
    }

    /**
     * Transition to UNHEALTHY.
     *
     * @return the previous status, or {@code null} if already UNHEALTHY.
     */
    public BackendStatus markUnhealthy() {
        BackendStatus previous;
        do {
            previous = status.get();
            if (previous == BackendStatus.UNHEALTHY || previous == BackendStatus.DRAINING) {
                return null;
            }
        } while (!status.compareAndSet(previous, BackendStatus.UNHEALTHY));
        updatedAt = Instant.now();
        return previous;
    }

    /**
     * Transition to DRAINING (used during graceful removal).
     *
     * @return the previous status, or {@code null} if already DRAINING.
     */
    public BackendStatus markDraining() {
        BackendStatus previous;
        do {
            previous = status.get();
            if (previous == BackendStatus.DRAINING) {
                return null;
            }
        } while (!status.compareAndSet(previous, BackendStatus.DRAINING));
        updatedAt = Instant.now();
        return previous;
    }

    /**
     * A backend is available for new traffic only when HEALTHY.
     */
    public boolean isAvailable() {
        return status.get() == BackendStatus.HEALTHY;
    }

    // ──────────────────────────────────────────────────────────────────
    // Weighted round-robin helpers
    // ──────────────────────────────────────────────────────────────────

    public int getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(int currentWeight) {
        this.currentWeight = currentWeight;
    }

    public int getEffectiveWeight() {
        return effectiveWeight;
    }

    public void setEffectiveWeight(int effectiveWeight) {
        this.effectiveWeight = effectiveWeight;
    }

    // ──────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getAddress() { return address; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; this.effectiveWeight = weight; this.updatedAt = Instant.now(); }
    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public BackendStatus getStatus() { return status.get(); }
    public int getActiveConnections() { return activeConnections.get(); }
    public long getTotalRequests() { return totalRequests.get(); }
    public long getTotalFailures() { return totalFailures.get(); }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses.get(); }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastHealthCheck() { return lastHealthCheck; }

    /**
     * Returns the full URL base for this backend, e.g. {@code http://192.168.1.10:8080}.
     */
    public String getUrl() {
        return "http://" + address + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Backend that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Backend{id='%s', address='%s', port=%d, status=%s, active=%d}"
                .formatted(id, address, port, status.get(), activeConnections.get());
    }
}
