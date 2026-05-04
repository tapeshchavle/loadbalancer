package com.loadbalancer.proxy;

import com.loadbalancer.engine.RoutingEngine;
import com.loadbalancer.exception.NoHealthyBackendException;
import com.loadbalancer.model.Backend;
import com.loadbalancer.session.SessionManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Data-plane reverse proxy filter.
 *
 * <p>This is the hot path — every non-control-plane request flows through here.
 * For each request:
 * <ol>
 *   <li>Extract client IP and sticky cookie.</li>
 *   <li>Ask {@link RoutingEngine} to select a backend.</li>
 *   <li>Forward the request to the selected backend using {@link RestClient}.</li>
 *   <li>Copy the response (status, headers, body) back to the client.</li>
 *   <li>Release the connection in a {@code finally} block.</li>
 *   <li>Add diagnostic headers ({@code X-Backend-Server}, {@code X-Request-ID}).</li>
 * </ol>
 *
 * <p>Control-plane paths ({@code /api/lb/**}) bypass this filter entirely.
 */
public class ReverseProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyFilter.class);
    private static final String CONTROL_PLANE_PREFIX = "/api/lb";

    private final RoutingEngine routingEngine;
    private final RestClient restClient;

    public ReverseProxyFilter(RoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "LoadBalancer-Proxy/1.0")
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Control-plane requests bypass the proxy
        return request.getRequestURI().startsWith(CONTROL_PLANE_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = extractClientIp(request);
        String stickyId = extractStickyCookie(request);
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        Backend backend = null;
        try {
            // 1. Route to a backend
            backend = routingEngine.route(clientIp, stickyId);
            final Backend selectedBackend = backend;

            // 2. Build the target URL
            String targetUrl = selectedBackend.getUrl() + request.getRequestURI();
            if (request.getQueryString() != null) {
                targetUrl += "?" + request.getQueryString();
            }

            log.debug("[{}] Proxying {} {} → {}", requestId, request.getMethod(), request.getRequestURI(), targetUrl);

            // 3. Forward the request
            final String url = targetUrl;
            byte[] requestBody = request.getInputStream().readAllBytes();

            restClient.method(HttpMethod.valueOf(request.getMethod()))
                    .uri(url)
                    .headers(headers -> copyRequestHeaders(request, headers))
                    .header("X-Forwarded-For", clientIp)
                    .header("X-Forwarded-Proto", request.getScheme())
                    .header("X-Request-ID", requestId)
                    .body(requestBody)
                    .exchange((req, res) -> {
                        // 4. Copy response status
                        response.setStatus(res.getStatusCode().value());

                        // 5. Copy response headers
                        res.getHeaders().forEach((name, values) -> {
                            for (String value : values) {
                                if (!name.equalsIgnoreCase("Transfer-Encoding")) {
                                    response.addHeader(name, value);
                                }
                            }
                        });

                        // 6. Add diagnostic headers
                        response.setHeader("X-Backend-Server",
                                selectedBackend.getAddress() + ":" + selectedBackend.getPort());
                        response.setHeader("X-Request-ID", requestId);
                        response.setHeader("X-LB-Algorithm",
                                routingEngine.getActiveAlgorithm().name());

                        // 7. Set sticky cookie if sessions are enabled
                        if (stickyId == null || stickyId.isBlank()) {
                            Cookie cookie = new Cookie(
                                    SessionManager.STICKY_COOKIE_NAME,
                                    selectedBackend.getId());
                            cookie.setPath("/");
                            cookie.setHttpOnly(true);
                            cookie.setMaxAge(3600);
                            response.addCookie(cookie);
                        }

                        // 8. Copy response body
                        byte[] body = res.getBody().readAllBytes();
                        response.getOutputStream().write(body);
                        response.getOutputStream().flush();

                        return null;
                    });

        } catch (NoHealthyBackendException e) {
            log.error("[{}] No healthy backends available", requestId);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error": "Service Unavailable", "message": "No healthy backends available", "requestId": "%s"}
                    """.formatted(requestId));
        } catch (Exception e) {
            log.error("[{}] Proxy error: {}", requestId, e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"error": "Bad Gateway", "message": "Failed to reach backend server", "requestId": "%s"}
                    """.formatted(requestId));
        } finally {
            // 9. Always release the connection
            if (backend != null) {
                routingEngine.releaseConnection(backend);
            }
        }
    }

    /**
     * Extracts the real client IP, respecting X-Forwarded-For if present.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Extracts the sticky session cookie value, if present.
     */
    private String extractStickyCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (SessionManager.STICKY_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Copies relevant headers from the client request to the backend request.
     * Excludes hop-by-hop headers that should not be forwarded.
     */
    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            // Skip hop-by-hop headers
            if (name.equalsIgnoreCase("Host")
                    || name.equalsIgnoreCase("Connection")
                    || name.equalsIgnoreCase("Transfer-Encoding")
                    || name.equalsIgnoreCase("Cookie")) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
    }
}
