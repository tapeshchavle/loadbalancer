package com.loadbalancer.controller;

import com.loadbalancer.dto.*;
import com.loadbalancer.engine.RoutingEngine;
import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendPool;
import com.loadbalancer.session.SessionManager;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Control-plane REST API for managing the load balancer.
 *
 * <p>All endpoints are under {@code /api/lb/**} so the
 * {@link com.loadbalancer.proxy.ReverseProxyFilter} can distinguish
 * control-plane traffic from data-plane (proxied) traffic.
 *
 * <h3>Endpoints:</h3>
 * <table>
 *   <tr><td>POST</td>   <td>/api/lb/backends</td>            <td>Register backend</td></tr>
 *   <tr><td>DELETE</td>  <td>/api/lb/backends/{id}</td>       <td>Remove backend</td></tr>
 *   <tr><td>GET</td>     <td>/api/lb/backends</td>            <td>List all backends</td></tr>
 *   <tr><td>GET</td>     <td>/api/lb/backends/{id}/health</td><td>Get health status</td></tr>
 *   <tr><td>PUT</td>     <td>/api/lb/config/algorithm</td>    <td>Change algorithm</td></tr>
 *   <tr><td>GET</td>     <td>/api/lb/config</td>              <td>Get current config</td></tr>
 *   <tr><td>GET</td>     <td>/api/lb/stats</td>               <td>Get aggregate stats</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/lb")
public class LoadBalancerController {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerController.class);

    private final BackendPool backendPool;
    private final RoutingEngine routingEngine;
    private final SessionManager sessionManager;

    public LoadBalancerController(BackendPool backendPool,
                                  RoutingEngine routingEngine,
                                  SessionManager sessionManager) {
        this.backendPool = backendPool;
        this.routingEngine = routingEngine;
        this.sessionManager = sessionManager;
    }

    // ──────────────────────────────────────────────────────────────────
    // Backend Management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Register a new backend server.
     *
     * <p>The backend starts in {@code UNKNOWN} status and transitions
     * to {@code HEALTHY} or {@code UNHEALTHY} after the first health check cycle.
     */
    @PostMapping("/backends")
    public ResponseEntity<BackendResponse> registerBackend(
            @Valid @RequestBody RegisterBackendRequest request) {

        log.info("Registering backend: {}:{} (weight={})",
                request.address(), request.port(), request.weight());

        Backend backend = backendPool.register(
                request.address(),
                request.port(),
                request.weight(),
                request.healthCheckPath()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BackendResponse.from(backend));
    }

    /**
     * Remove a backend server.
     *
     * <p>The backend is marked as DRAINING, then removed from the pool.
     * Active connections are allowed to complete gracefully.
     */
    @DeleteMapping("/backends/{id}")
    public ResponseEntity<BackendResponse> removeBackend(@PathVariable String id) {
        log.info("Removing backend: {}", id);

        // Start draining (stops new traffic)
        Backend backend = backendPool.startDraining(id);
        int drainedConnections = backend.getActiveConnections();

        // Remove from pool
        backendPool.remove(id);

        // Invalidate sticky sessions
        sessionManager.invalidateBackend(id);

        // Clean up connection tracker
        routingEngine.getConnectionTracker().removeBackend(id);

        log.info("Backend {} removed (drained {} connections)", id, drainedConnections);
        return ResponseEntity.ok(BackendResponse.from(backend));
    }

    /**
     * List all registered backends with their current status.
     */
    @GetMapping("/backends")
    public ResponseEntity<List<BackendResponse>> listBackends() {
        List<BackendResponse> backends = backendPool.getAll().stream()
                .map(BackendResponse::from)
                .toList();
        return ResponseEntity.ok(backends);
    }

    /**
     * Get detailed health status for a specific backend.
     */
    @GetMapping("/backends/{id}/health")
    public ResponseEntity<HealthStatusResponse> getHealthStatus(@PathVariable String id) {
        Backend backend = backendPool.getById(id);
        return ResponseEntity.ok(HealthStatusResponse.from(backend));
    }

    // ──────────────────────────────────────────────────────────────────
    // Configuration
    // ──────────────────────────────────────────────────────────────────

    /**
     * Change the active load-balancing algorithm at runtime.
     * Takes effect immediately for new connections.
     */
    @PutMapping("/config/algorithm")
    public ResponseEntity<ConfigureAlgorithmResponse> configureAlgorithm(
            @Valid @RequestBody ConfigureAlgorithmRequest request) {

        log.info("Changing algorithm to: {} (sticky={}, ttl={}s)",
                request.algorithm(), request.stickySessions(), request.stickyTtlSeconds());

        // Update algorithm
        routingEngine.setAlgorithm(request.algorithm());

        // Update sticky session config
        sessionManager.setEnabled(request.stickySessions());
        sessionManager.setTtl(Duration.ofSeconds(request.stickyTtlSeconds()));

        return ResponseEntity.ok(new ConfigureAlgorithmResponse(
                request.algorithm(),
                request.stickySessions(),
                request.stickyTtlSeconds(),
                "Algorithm updated successfully"
        ));
    }

    /**
     * Get the current load balancer configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("algorithm", routingEngine.getActiveAlgorithm());
        config.put("stickySessions", sessionManager.isEnabled());
        config.put("stickyTtlSeconds", sessionManager.getTtl().toSeconds());
        config.put("totalBackends", backendPool.size());
        config.put("healthyBackends", backendPool.getHealthyBackends().size());
        return ResponseEntity.ok(config);
    }

    // ──────────────────────────────────────────────────────────────────
    // Statistics
    // ──────────────────────────────────────────────────────────────────

    /**
     * Get aggregate load balancer statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Collection<Backend> allBackends = backendPool.getAll();

        long totalRequests = allBackends.stream().mapToLong(Backend::getTotalRequests).sum();
        long totalFailures = allBackends.stream().mapToLong(Backend::getTotalFailures).sum();
        int totalActiveConnections = routingEngine.getConnectionTracker().getTotalConnections();

        long healthyCount = allBackends.stream().filter(Backend::isAvailable).count();
        long unhealthyCount = allBackends.stream()
                .filter(b -> !b.isAvailable())
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("algorithm", routingEngine.getActiveAlgorithm());
        stats.put("totalBackends", allBackends.size());
        stats.put("healthyBackends", healthyCount);
        stats.put("unhealthyBackends", unhealthyCount);
        stats.put("totalRequests", totalRequests);
        stats.put("totalFailures", totalFailures);
        stats.put("activeConnections", totalActiveConnections);
        stats.put("stickySessions", sessionManager.isEnabled());

        // Per-backend breakdown
        List<Map<String, Object>> backendStats = allBackends.stream()
                .map(b -> {
                    Map<String, Object> bs = new LinkedHashMap<>();
                    bs.put("id", b.getId());
                    bs.put("address", b.getAddress() + ":" + b.getPort());
                    bs.put("status", b.getStatus());
                    bs.put("weight", b.getWeight());
                    bs.put("activeConnections", b.getActiveConnections());
                    bs.put("totalRequests", b.getTotalRequests());
                    bs.put("totalFailures", b.getTotalFailures());
                    return bs;
                })
                .toList();
        stats.put("backends", backendStats);

        return ResponseEntity.ok(stats);
    }
}
