package com.loadbalancer.model;

/**
 * Represents the lifecycle states of a backend server.
 *
 * <p>State transitions:
 * <pre>
 *   UNKNOWN ──► HEALTHY ──► UNHEALTHY ──► HEALTHY  (recovery)
 *                  │                         │
 *                  └──► DRAINING ◄───────────┘
 * </pre>
 *
 * <ul>
 *   <li>{@code UNKNOWN}   — Initial state after registration, before first health check.</li>
 *   <li>{@code HEALTHY}   — Backend passed the healthy-threshold consecutive checks.</li>
 *   <li>{@code UNHEALTHY} — Backend failed the unhealthy-threshold consecutive checks.</li>
 *   <li>{@code DRAINING}  — Backend is being removed; existing connections are draining.</li>
 * </ul>
 */
public enum BackendStatus {

    UNKNOWN,
    HEALTHY,
    UNHEALTHY,
    DRAINING
}
