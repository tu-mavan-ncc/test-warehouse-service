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
- **Controllers (`com.warehouse.inventory.controller`)**: Exposes REST endpoints, validates inputs, and delegates to service layer. Returns standard envelope.

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
- **Factory Class**: `ReservationFactory`
- **Method**: `createPendingReservation(ReservationRequest)`
- **Usage**: Instantiates the UUID, sets status to `PENDING`, captures timestamp, maps DTO request items to JPA children `ReservationItem`, and returns the fully assembled entity ready to be persisted.

---

## 4. Database Design Decisions

Structured PostgreSQL tables configured using **Liquibase** migrations:

1. **`products`**: Primary SKU directory mapping product identifiers to details.
2. **`inventory`**: Tracks stock quantities. It separates total, available, and reserved stocks:
   - `total_stock`: Total physical stock in warehouse.
   - `available_stock`: Stock available to purchase (`total_stock` - `reserved_stock`).
   - `reserved_stock`: Stock held in pending reservations.
3. **`reservations`**: Top-level details of order reservations.
4. **`reservation_items`**: Junction table mapping multiple items/SKUs to a single reservation.

### Concurrency Strategy (Pessimistic Locking)
To prevent overselling stock (two concurrent requests acquiring the last item), we use a **Pessimistic Write Lock** (`SELECT ... FOR UPDATE`).
- **Deadlock Avoidance**: When querying `inventory` for multiple SKUs in a single reservation, the list of SKUs is sorted alphabetically (ASC) before querying. Thus, all threads lock database records in the exact same sequence (e.g. always lock `A100` before `B200`), eliminating deadlock potential.
- **Multi-Instance Safety & Statelessness (Horizontal Scaling)**:
  - The Spring Boot application is completely **stateless**. It does not keep any reservation state in local memory or local caches.
  - Because concurrency control is handled using database-level locks (`SELECT ... FOR UPDATE` inside PostgreSQL), the safety guarantees apply across **multiple running instances** of the application. 
  - If instance 1 and instance 2 receive requests for the same SKU at the same millisecond, the database engine guarantees that only one transaction acquires the row lock, while the other blocks and waits. This makes the service naturally ready to scale horizontally behind a Load Balancer (e.g. Nginx, AWS ALB) with zero configuration changes.

---

## 5. How to Run the System

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

## 6. How to Run the Tests

To run the unit and integration tests (which spin up a real PostgreSQL instance using Testcontainers):
```bash
mvn clean test
```
