package com.loadbalancer.exception;

/**
 * Thrown when an operation references a backend ID that does not exist in the pool.
 */
public class BackendNotFoundException extends RuntimeException {

    public BackendNotFoundException(String message) {
        super(message);
    }
}
