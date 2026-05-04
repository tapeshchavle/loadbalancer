package com.loadbalancer.exception;

/**
 * Thrown when the routing engine cannot find any healthy backend
 * to serve a request. Mapped to HTTP 503 Service Unavailable.
 */
public class NoHealthyBackendException extends RuntimeException {

    public NoHealthyBackendException(String message) {
        super(message);
    }
}
