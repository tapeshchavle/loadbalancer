package com.loadbalancer.exception;

/**
 * Thrown when attempting to register a backend with an address:port
 * combination that already exists in the pool.
 */
public class BackendAlreadyExistsException extends RuntimeException {

    public BackendAlreadyExistsException(String message) {
        super(message);
    }
}
