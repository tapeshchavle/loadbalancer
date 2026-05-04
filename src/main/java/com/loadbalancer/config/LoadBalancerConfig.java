package com.loadbalancer.config;

import com.loadbalancer.engine.ConnectionTracker;
import com.loadbalancer.engine.RoutingEngine;
import com.loadbalancer.health.HealthCheckProbe;
import com.loadbalancer.health.HealthChecker;
import com.loadbalancer.health.HttpHealthCheckProbe;
import com.loadbalancer.health.TcpHealthCheckProbe;
import com.loadbalancer.model.Algorithm;
import com.loadbalancer.model.BackendPool;
import com.loadbalancer.proxy.ReverseProxyFilter;
import com.loadbalancer.session.SessionManager;
import com.loadbalancer.strategy.StrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Central configuration class wiring all load balancer components.
 *
 * <p>Reads externalized properties from {@code application.properties}
 * and constructs the full component graph:
 * <ul>
 *   <li>Strategy factory + routing engine</li>
 *   <li>Health checker with configurable probe type</li>
 *   <li>Session manager with configurable TTL</li>
 *   <li>Reverse proxy filter</li>
 * </ul>
 *
 * <p>All values are overridable via properties, environment variables, or command-line args.
 */
@Configuration
public class LoadBalancerConfig {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerConfig.class);

    @Value("${lb.algorithm:ROUND_ROBIN}")
    private String defaultAlgorithm;

    @Value("${lb.sticky-sessions.enabled:false}")
    private boolean stickyEnabled;

    @Value("${lb.sticky-sessions.ttl:3600}")
    private int stickyTtlSeconds;

    @Value("${lb.health.timeout:3000}")
    private int healthTimeoutMs;

    @Value("${lb.health.healthy-threshold:2}")
    private int healthyThreshold;

    @Value("${lb.health.unhealthy-threshold:3}")
    private int unhealthyThreshold;

    @Value("${lb.health.check-type:HTTP}")
    private String healthCheckType;

    // ──────────────────────────────────────────────────────────────────
    // Strategy
    // ──────────────────────────────────────────────────────────────────

    @Bean
    public StrategyFactory strategyFactory() {
        return new StrategyFactory();
    }

    @Bean
    public ConnectionTracker connectionTracker() {
        return new ConnectionTracker();
    }

    // ──────────────────────────────────────────────────────────────────
    // Routing Engine
    // ──────────────────────────────────────────────────────────────────

    @Bean
    public RoutingEngine routingEngine(BackendPool backendPool,
                                       SessionManager sessionManager,
                                       ConnectionTracker connectionTracker,
                                       StrategyFactory strategyFactory) {
        Algorithm algorithm = Algorithm.valueOf(defaultAlgorithm.toUpperCase());
        log.info("Initializing routing engine with algorithm: {}", algorithm);

        // Configure session manager from properties
        sessionManager.setEnabled(stickyEnabled);
        sessionManager.setTtl(Duration.ofSeconds(stickyTtlSeconds));

        return new RoutingEngine(backendPool, sessionManager, connectionTracker,
                strategyFactory, algorithm);
    }

    // ──────────────────────────────────────────────────────────────────
    // Health Checking
    // ──────────────────────────────────────────────────────────────────

    @Bean
    public HealthCheckProbe healthCheckProbe() {
        return switch (healthCheckType.toUpperCase()) {
            case "TCP" -> {
                log.info("Using TCP health check probe (timeout={}ms)", healthTimeoutMs);
                yield new TcpHealthCheckProbe(healthTimeoutMs);
            }
            default -> {
                log.info("Using HTTP health check probe (timeout={}ms)", healthTimeoutMs);
                yield new HttpHealthCheckProbe(healthTimeoutMs);
            }
        };
    }

    @Bean
    public HealthChecker healthChecker(BackendPool backendPool,
                                       HealthCheckProbe probe,
                                       ApplicationEventPublisher eventPublisher) {
        log.info("Health checker: healthyThreshold={}, unhealthyThreshold={}",
                healthyThreshold, unhealthyThreshold);
        return new HealthChecker(backendPool, probe, eventPublisher,
                healthyThreshold, unhealthyThreshold);
    }

    // ──────────────────────────────────────────────────────────────────
    // Reverse Proxy Filter
    // ──────────────────────────────────────────────────────────────────

    @Bean
    public FilterRegistrationBean<ReverseProxyFilter> reverseProxyFilterRegistration(
            RoutingEngine routingEngine) {
        FilterRegistrationBean<ReverseProxyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ReverseProxyFilter(routingEngine));
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // run after Spring Security (if present)
        registration.setName("reverseProxyFilter");
        return registration;
    }
}
