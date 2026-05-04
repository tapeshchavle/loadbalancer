package com.loadbalancer.engine;

import com.loadbalancer.exception.NoHealthyBackendException;
import com.loadbalancer.model.Algorithm;
import com.loadbalancer.model.Backend;
import com.loadbalancer.model.BackendPool;
import com.loadbalancer.session.SessionManager;
import com.loadbalancer.strategy.LoadBalancingStrategy;
import com.loadbalancer.strategy.StrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Core routing engine — the "brain" of the load balancer.
 *
 * <p>Orchestrates the routing decision for each incoming request:
 * <ol>
 *   <li><b>Sticky session check:</b> If enabled and a valid cookie exists,
 *       route to the stored backend (if it's healthy).</li>
 *   <li><b>Algorithm selection:</b> Get healthy backends from the pool and
 *       delegate to the configured {@link LoadBalancingStrategy}.</li>
 *   <li><b>Connection tracking:</b> Increment the active connection count
 *       on the selected backend.</li>
 *   <li><b>Session storage:</b> If sticky sessions are enabled, store the
 *       mapping for subsequent requests.</li>
 * </ol>
 *
 * <p>The engine supports <b>runtime algorithm switching</b> — calling
 * {@link #setAlgorithm(Algorithm)} swaps the strategy without restarting.
 *
 * <p><b>Thread safety:</b> The strategy reference is volatile so swaps are
 * immediately visible to all request-handling threads.
 */
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    private final BackendPool backendPool;
    private final SessionManager sessionManager;
    private final ConnectionTracker connectionTracker;
    private final StrategyFactory strategyFactory;

    private volatile LoadBalancingStrategy activeStrategy;
    private volatile Algorithm activeAlgorithm;

    public RoutingEngine(BackendPool backendPool,
                         SessionManager sessionManager,
                         ConnectionTracker connectionTracker,
                         StrategyFactory strategyFactory,
                         Algorithm initialAlgorithm) {
        this.backendPool = backendPool;
        this.sessionManager = sessionManager;
        this.connectionTracker = connectionTracker;
        this.strategyFactory = strategyFactory;
        setAlgorithm(initialAlgorithm);
    }

    // ──────────────────────────────────────────────────────────────────
    // Core routing
    // ──────────────────────────────────────────────────────────────────

    /**
     * Selects a backend for the given client request.
     *
     * @param clientIp           the client's IP address
     * @param stickyIdentifier   the sticky cookie value (may be null)
     * @return the selected backend
     * @throws NoHealthyBackendException if no healthy backends are available
     */
    public Backend route(String clientIp, String stickyIdentifier) {
        // 1. Check sticky session
        Optional<Backend> sticky = sessionManager.resolveSticky(stickyIdentifier);
        if (sticky.isPresent()) {
            Backend backend = sticky.get();
            backend.incrementConnections();
            connectionTracker.increment(backend.getId());
            log.debug("Sticky routed {} → {}", clientIp, backend.getId());
            return backend;
        }

        // 2. Get healthy backends
        List<Backend> healthyBackends = backendPool.getHealthyBackends();
        if (healthyBackends.isEmpty()) {
            throw new NoHealthyBackendException(
                    "No healthy backends available to serve the request");
        }

        // 3. Delegate to strategy
        Backend selected = activeStrategy.selectBackend(healthyBackends, clientIp);

        // 4. Track connections
        selected.incrementConnections();
        connectionTracker.increment(selected.getId());

        // 5. Store sticky session
        if (stickyIdentifier != null) {
            sessionManager.storeSession(stickyIdentifier, selected);
        }

        log.debug("Routed {} → {} [algo={}]", clientIp, selected.getId(), activeAlgorithm);
        return selected;
    }

    /**
     * Releases a connection after the proxied request completes.
     * Must be called in a {@code finally} block by the reverse proxy.
     */
    public void releaseConnection(Backend backend) {
        backend.decrementConnections();
        connectionTracker.decrement(backend.getId());
    }

    // ──────────────────────────────────────────────────────────────────
    // Algorithm management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Switches the active load-balancing algorithm at runtime.
     * Takes effect immediately for new requests.
     */
    public void setAlgorithm(Algorithm algorithm) {
        this.activeStrategy = strategyFactory.getStrategy(algorithm);
        this.activeAlgorithm = algorithm;
        log.info("Load balancing algorithm set to: {}", algorithm);
    }

    public Algorithm getActiveAlgorithm() {
        return activeAlgorithm;
    }

    // ──────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────

    public ConnectionTracker getConnectionTracker() {
        return connectionTracker;
    }
}
