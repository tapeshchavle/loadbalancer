package com.loadbalancer.health;

import com.loadbalancer.model.Backend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP health-check probe.
 *
 * <p>Opens a TCP connection to the backend and immediately closes it.
 * If the connection succeeds within the timeout, the backend is considered healthy.
 *
 * <p>This verifies network connectivity and that something is listening on the port,
 * but does <b>not</b> verify the application is actually working (use
 * {@link HttpHealthCheckProbe} for that).
 *
 * <p><b>Best for:</b> non-HTTP backends (databases, game servers, custom protocols).
 */
public class TcpHealthCheckProbe implements HealthCheckProbe {

    private static final Logger log = LoggerFactory.getLogger(TcpHealthCheckProbe.class);

    private final int timeoutMs;

    public TcpHealthCheckProbe(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public boolean check(Backend backend) {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(backend.getAddress(), backend.getPort()),
                    timeoutMs
            );
            log.trace("TCP health check {}:{} → OK", backend.getAddress(), backend.getPort());
            return true;
        } catch (Exception e) {
            log.debug("TCP health check {}:{} failed: {}",
                    backend.getAddress(), backend.getPort(), e.getMessage());
            return false;
        }
    }
}
