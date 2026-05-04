# Production-Grade Spring Boot Load Balancer

A high-performance, configurable, software-based Load Balancer built in Java using Spring Boot 4.0. It is designed to demonstrate production-ready patterns including lock-free hot paths, runtime configuration, pluggable routing strategies, and decoupled event-driven architectures.

---

## 🏛️ Architecture & Component Interaction

The architecture is strictly separated into two distinct planes:
1. **Data Plane**: The highly optimized "hot path" that intercepts client traffic and routes it to backend servers.
2. **Control Plane**: A REST API to dynamically manage the load balancer's state (add/remove backends, switch algorithms) without restarting the server.

### Component Interaction Diagram

```mermaid
graph TD
    Client[Client Request] --> Proxy[ReverseProxyFilter]
    
    subgraph Data Plane [Data Plane - Traffic Routing]
        Proxy -->|1. Request Backend| Engine[Routing Engine]
        Engine -->|2a. Check Sticky Session| SessionMgr[Session Manager]
        Engine -->|2b. Select via Algorithm| Strategy[Load Balancing Strategy]
        Engine -->|3. Update Counters| Tracker[Connection Tracker]
        Proxy -->|4. Forward HTTP Request| Backend[(Backend Server)]
    end

    subgraph Control Plane [Control Plane - Management]
        Controller[LoadBalancerController] -->|Register/Remove| Pool[Backend Pool]
        Controller -->|Switch Algorithm| Engine
        Controller -->|Enable/Disable| SessionMgr
    end

    subgraph Observability [Health & Observability]
        Health[Health Checker] -->|Probes (HTTP/TCP)| Pool
        Health -->|Emits| Event[Health Status Event]
        Event -.->|Invalidate Dead Sessions| SessionMgr
    end
    
    Pool -.->|Provides Healthy List| Strategy
```

### How a Request Flows
1. **Interception**: A client sends a request. The `ReverseProxyFilter` catches it.
2. **Routing Decision**: The filter asks the `RoutingEngine` for a backend.
3. **Session Check**: The engine checks the `SessionManager` for an existing sticky cookie.
4. **Strategy Execution**: If no session exists, the `RoutingEngine` queries the `BackendPool` for healthy servers and delegates to the active `LoadBalancingStrategy`.
5. **Connection Tracking**: The engine increments the active connection count lock-free (`AtomicInteger`).
6. **Forwarding**: The filter forwards the request to the target backend using Spring's `RestClient`, copies the response back to the client, and safely releases the connection.

---

## ✨ Features

### 1. Six Pluggable Routing Algorithms (Strategy Pattern)
Switch between these algorithms at runtime with zero downtime:
- **`ROUND_ROBIN`**: Sequential, even distribution.
- **`WEIGHTED_ROUND_ROBIN`**: Smooth NGINX-style weighting to prevent thundering herds on high-capacity servers.
- **`RANDOM`**: Fast, lock-free random selection.
- **`LEAST_CONNECTIONS`**: Dynamically routes to the server with the fewest active requests (weighted).
- **`IP_HASH`**: Deterministic routing based on the client's IP address using MurmurHash3.
- **`CONSISTENT_HASH`**: Ring-based hashing using 150 virtual nodes to minimize re-routing when backends are added or removed.

### 2. Active Health Checking
A background scheduled task continuously probes registered backends.
- **Probes**: Supports both **HTTP** (expects 2xx status) and **TCP** (socket connection) checks.
- **State Machine**: Transitions backends through `UNKNOWN` → `HEALTHY` ↔ `UNHEALTHY` based on configurable consecutive success/failure thresholds.
- **Event-Driven**: Emits Spring `ApplicationEvent`s when a backend dies, immediately triggering the `SessionManager` to invalidate dead sticky sessions.

### 3. Session Persistence (Sticky Sessions)
- Uses an `LB_BACKEND_ID` cookie to lock a client to a specific server.
- Supports configurable TTLs and lazy eviction.

### 4. Graceful Connection Draining
When a backend is removed via the API, it enters a `DRAINING` state. It immediately stops receiving new requests, but existing connections are allowed to finish processing gracefully.

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Running the Application
```bash
./mvnw clean spring-boot:run
```
The load balancer will start on `http://localhost:8080`.

### Configuration
You can configure defaults in `src/main/resources/application.properties`:
```properties
lb.algorithm=ROUND_ROBIN
lb.sticky-sessions.enabled=false
lb.health.interval=5000
lb.health.check-type=HTTP
```

---

## 🎮 Control Plane API Examples

Manage the load balancer via its REST API (bypasses the data plane).

### 1. Register a Backend Server
```bash
curl -X POST http://localhost:8080/api/lb/backends \
  -H "Content-Type: application/json" \
  -d '{
    "address": "127.0.0.1",
    "port": 9001,
    "weight": 3,
    "healthCheckPath": "/health"
  }'
```

### 2. Change Algorithm at Runtime
```bash
curl -X PUT http://localhost:8080/api/lb/config/algorithm \
  -H "Content-Type: application/json" \
  -d '{
    "algorithm": "LEAST_CONNECTIONS",
    "stickySessions": true,
    "stickyTtlSeconds": 3600
  }'
```

### 3. Get Real-Time Statistics
```bash
curl http://localhost:8080/api/lb/stats
```
*Returns a JSON payload with active connections, total failures, and per-backend metrics.*

### 4. Remove (Drain) a Backend
```bash
curl -X DELETE http://localhost:8080/api/lb/backends/{backend-id}
```

---

## 🏗️ Design Principles Applied
- **SOLID**: 
  - *Open/Closed*: New routing strategies can be added simply by implementing `LoadBalancingStrategy` and adding it to the `StrategyFactory` without touching core code.
  - *Single Responsibility*: Separation of Health Checking, Proxying, and Routing.
- **Concurrency**: Lock-free operations in the hot path. Uses `ConcurrentHashMap` and `AtomicInteger` to ensure nanosecond routing latencies.
- **Observer Pattern**: Decouples health monitoring from session management via Spring Events.
