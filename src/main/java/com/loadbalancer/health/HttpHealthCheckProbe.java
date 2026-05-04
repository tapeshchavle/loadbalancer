package com.loadbalancer.health;

import com.loadbalancer.model.Backend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP health-check probe.
 *
 * <p>Sends a {@code GET} request to {@code http://{address}:{port}{healthCheckPath}}
 * and expects a 2xx response. Any non-2xx status, timeout, or connection error
 * is treated as a failure.
 *
 * <p>Uses Spring's {@link RestClient} (Spring Boot 4 idiomatic HTTP client).
 */
public class HttpHealthCheckProbe implements HealthCheckProbe {

    private static final Logger log = LoggerFactory.getLogger(HttpHealthCheckProbe.class);

    private final RestClient restClient;

    public HttpHealthCheckProbe(int timeoutMs) {
        this.restClient = RestClient.builder()
                .defaultHeaders(headers -> headers.set("User-Agent", "LoadBalancer-HealthChecker/1.0"))
                .build();
    }

    @Override
    public boolean check(Backend backend) {
        String url = backend.getUrl() + backend.getHealthCheckPath();
        try {
            var response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            boolean healthy = response.getStatusCode().is2xxSuccessful();
            log.trace("HTTP health check {} → {}", url, healthy ? "OK" : "FAIL");
            return healthy;
        } catch (Exception e) {
            log.debug("HTTP health check {} failed: {}", url, e.getMessage());
            return false;
        }
    }
}
