# Warehouse Inventory Reservation System

This is a production-ready implementation of **Challenge 1 — Warehouse Inventory Reservation System** built using **Java 17**, **Spring Boot 3.3.0**, **PostgreSQL**, **Liquibase**, and **Testcontainers**.

---

## 1. Challenge Choice & Rationale

I chose **Challenge 1: Warehouse Inventory Reservation System** because handling concurrency, double-reservation prevention, and state transitions are core backend engineering challenges. Managing stock consistency under load is critical for any high-volume e-commerce or logistics platform. Solving this using pessimistic locking and the State design pattern represents a robust, industrial-strength approach to transactional consistency.

---

## 2. Architecture Overview

The system follows a clean, layered architecture separating concerns into distinct layers:

```
[Client / API Consumers]
          │ (JSON over HTTP)
          ▼
┌────────────────────────┐
│    Controller Layer    │ <─── Handles REST routing & Exception mapping (GlobalExceptionHandler)
└────────────────────────┘
          │ (DTOs)
          ▼
┌────────────────────────┐
│     Service Layer      │ <─── Business rules, transactional boundaries & design patterns
└────────────────────────┘
          │ (Domain Models)
          ▼
┌────────────────────────┐
│   Data Access Layer    │ <─── JPA Repositories (locks and queries)
└────────────────────────┘
          │ (SQL / JDBC)
          ▼
┌────────────────────────┐
│     Database (PG)      │ <─── Structured tables managed by Liquibase
└────────────────────────┘
```

- **Domain Models (`com.warehouse.inventory.model`)**: Rich domain models mapping to the database tables. They encapsulate business rules and lifecycle logic.
- **DTOs (`com.warehouse.inventory.dto`)**: Immutable Java 17 records used for request payloads and structured API response envelopes.
- **Repositories (`com.warehouse.inventory.repository`)**: JPA Repositories mapping database access. Leverages Pessimistic Locking to solve concurrent writes.
- **Services (`com.warehouse.inventory.service`)**: Orchestrates operations and manages transaction boundaries (`@Transactional`). Incorporates patterns.
- **Controllers (`com.warehouse.inventory.controller`)**: Exposes REST endpoints, performs strict input validation using **Jakarta Validation** (`@Valid`, `@NotBlank`, `@Min`), and delegates to service layer. Returns standard envelope.

---

## 3. Design Patterns Applied

### 1. State Pattern (Reservation Lifecycle Transitions)
The reservation lifecycle state transitions are handled dynamically using the State Pattern.
- **Interface**: `ReservationState` (declares `confirm` and `cancel` transitions).
- **Concrete States**: 
  - `PendingState`: Transitions `PENDING` -> `CONFIRMED` or `PENDING` -> `CANCELLED` and adjusts inventory levels accordingly.
  - `ConfirmedState` (Terminal): Throws `InvalidStateTransitionException` if any transition is attempted.
  - `CancelledState` (Terminal): Throws `InvalidStateTransitionException` if any transition is attempted.
- **Usage**: The `Reservation` entity resolves its active state wrapper at runtime via `getReservationState()`, delegating transition operations to it in `ReservationService`.

### 2. Factory Pattern (Reservation Creation)
The `ReservationFactory` encapsulates the logic required to build a new reservation:
- **Factory Class**: `ReservationFactory` (Implemented as a Spring `@Component` to support strict Dependency Injection).
- **Method**: `createPendingReservation(ReservationRequest)`
- **Usage**: Instantiates the UUID, sets status to `PENDING`, captures timestamp, maps DTO request items to JPA children `ReservationItem`, and returns the fully assembled entity ready to be persisted.

### 3. Singleton Pattern (Memory Optimization)
- **State Caching**: Instead of instantiating new `PendingState` or `ConfirmedState` objects on every transition (which causes garbage collection overhead), these stateless objects are statically cached as singletons within the `Reservation` model.

---

## 4. SOLID Principles Applied

- **Single Responsibility Principle (SRP)**: Classes have one reason to change. `ReservationFactory` handles only the creation of entities, `ReservationMapper` isolates DTO-to-Entity mapping, `GlobalExceptionHandler` handles only REST API errors, and Services orchestrate business logic.
- **Open/Closed Principle (OCP)**: The State Pattern implementation (`ReservationState`) allows introducing new states (like `ShippedState`) without modifying existing states or the core `ReservationService` logic.
- **Liskov Substitution Principle (LSP)**: The `ReservationService` interacts with the `ReservationState` interface. Any concrete state class (`PendingState`, `ConfirmedState`, etc.) can be substituted without altering the correctness of the service's execution.
- **Dependency Inversion Principle (DIP)**: High-level business logic in `ReservationService` depends on Spring Data JPA repository abstractions (`ReservationRepository`, `InventoryRepository`) and service interfaces injected via constructor, not concrete implementations.
- **Interface Segregation Principle (ISP)**: Interfaces are kept small and focused. For instance, `ReservationState` enforces only `confirm()` and `cancel()` methods.

---

## 5. Database Design Decisions

Structured PostgreSQL tables configured using **Liquibase** migrations:

1. **`products`**: Primary SKU directory mapping product identifiers to details.
2. **`inventory`**: Tracks stock quantities. It separates total, available, and reserved stocks:
   - `total_stock`: Total physical stock in warehouse.
   - `available_stock`: Stock available to purchase (`total_stock` - `reserved_stock`).
   - `reserved_stock`: Stock held in pending reservations.
3. **`reservations`**: Top-level details of order reservations. Includes a `UNIQUE(order_id)` constraint to guarantee idempotency and prevent double-processing at the database level. Also includes an `updated_at` audit column.
4. **`reservation_items`**: Junction table mapping multiple items/SKUs to a single reservation.

### Concurrency Strategy & Performance Optimizations
1. **Pessimistic Locking for Inventory**: To prevent overselling stock, we use a **Pessimistic Write Lock** (`SELECT ... FOR UPDATE`) on the `inventory` table.
   - **Deadlock Avoidance**: When querying `inventory` for multiple SKUs, the list is sorted alphabetically (ASC). Thus, all threads lock database records in the exact same sequence (e.g. always lock `A100` before `B200`), eliminating deadlock potential.
2. **Pessimistic Locking for State Transitions**: To prevent "Lost Update" race conditions where two concurrent requests attempt to `confirm()` and `cancel()` the same reservation simultaneously, we also apply a Pessimistic Write Lock when fetching the `Reservation` entity.
3. **N+1 Query Prevention**: We use `@EntityGraph(attributePaths = {"items"})` in `ReservationRepository` to eagerly fetch related items in a single `LEFT JOIN` SQL query, completely avoiding the N+1 query performance bottleneck.
4. **Database Indexes**: Proper indexes are defined on `reservations(order_id)` and `reservations(status)` to ensure fast queries for partner lookups and background cronjobs without triggering full table scans.

### Multi-Instance Safety & Statelessness (Horizontal Scaling)
- The Spring Boot application is completely **stateless**. It does not keep any reservation state in local memory or local caches.
- Because concurrency control is handled using database-level locks (`SELECT ... FOR UPDATE` inside PostgreSQL), the safety guarantees apply across **multiple running instances** of the application. 
- If instance 1 and instance 2 receive requests for the same SKU at the same millisecond, the database engine guarantees that only one transaction acquires the row lock, while the other blocks and waits. This makes the service naturally ready to scale horizontally behind a Load Balancer (e.g. Nginx, AWS ALB) with zero configuration changes.

---

## 6. How to Run the System

Simply run:
```bash
docker compose up --build
```
This builds the Spring Boot app from the multi-stage `Dockerfile`, starts a PostgreSQL database, and links them. The app is accessible at `http://localhost:8080`.

### REST Endpoints
- **Reserve Inventory**: `POST http://localhost:8080/api/v1/reservations`
  ```json
  {
    "orderId": "ORD-1001",
    "items": [
      { "sku": "A100", "quantity": 5 },
      { "sku": "B200", "quantity": 3 }
    ]
  }
  ```
- **Confirm Reservation**: `POST http://localhost:8080/api/v1/reservations/{id}/confirm`
- **Cancel Reservation**: `POST http://localhost:8080/api/v1/reservations/{id}/cancel`
- **Get Reservation**: `GET http://localhost:8080/api/v1/reservations/{id}`
- **Get Stock Status**: `GET http://localhost:8080/api/v1/inventory/{sku}`

---

## 7. How to Run the Tests

To run the unit and integration tests (which spin up a real PostgreSQL instance using Testcontainers):
```bash
mvn clean test
```

---

## 8. Trade-offs & Future Improvements

- **Trade-off: Pessimistic Locking vs. Eventual Consistency**: I chose pessimistic locking (`SELECT ... FOR UPDATE`) in Postgres to guarantee absolute stock consistency and prevent race conditions. The trade-off is reduced write throughput under extreme contention for a single SKU. An alternative would be eventual consistency using message queues, but that significantly increases architectural complexity.
- **Improvement: Distributed Caching**: With more time, I would introduce a Redis caching layer for stock read requests to reduce load on the primary PostgreSQL database.
- **Improvement: Observability**: Add distributed tracing (e.g., OpenTelemetry, Jaeger) and metrics (Prometheus/Grafana) to monitor transaction times and lock wait times.

---

## 9. Scalability Bottlenecks & Fixes

**What would break at scale?**
At massive scale (e.g., flash sales with thousands of concurrent requests for the exact same item), PostgreSQL row-level locks will become a bottleneck. Connection pools will exhaust as threads wait for locks, leading to cascading timeouts and high latency.

**How to fix it?**
1. **Redis Atomic Operations**: Offload inventory counters from PostgreSQL to Redis. Use Redis `DECR` and Lua scripts to atomically check and deduct stock entirely in memory, which is orders of magnitude faster than relational DB row locks.
2. **Event-Driven Architecture**: Introduce an asynchronous queue (e.g., Kafka or RabbitMQ). Instead of processing reservations synchronously via REST, requests are published to a topic. A single-threaded consumer per SKU (or a partitioned consumer group) processes writes sequentially, completely eliminating database deadlocks and lock contention.
3. **Database Sharding**: Shard the `inventory` database by `sku` so that read/write loads are distributed across multiple database clusters.
