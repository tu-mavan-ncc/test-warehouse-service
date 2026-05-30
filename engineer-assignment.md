# Senior Software Engineer — Take-Home Assignment

**Time allowed:** 1 day (6–8 hours)
**Submission:** Push everything to a public GitHub repository and share the link

---

## Goal

Choose **ONE** of the two challenges below and build a working service.

Your solution should demonstrate:
- Clean architecture and SOLID principles
- At least 2 design patterns (name them in your README)
- A working REST API with proper error handling
- Database modelling with migrations
- Unit tests
- Concurrency safety — your solution must be correct when multiple requests arrive simultaneously
- A clear README

---

## Challenge 1 — Warehouse Inventory Reservation System

### The Scenario

Multiple clients try to reserve warehouse inventory at the same time for different orders. Your service must ensure no item is oversold, reservations are tracked, and stock stays consistent under concurrent load.

### Functional Requirements

**Reserve inventory**
```
POST /api/v1/reservations
{
  "orderId": "ORD-1001",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ]
}
```
- Check available stock for each SKU
- If all items are available, create the reservation and reduce available stock
- If any item is unavailable, reject the entire reservation with a clear error
- Two simultaneous requests for the same SKU must not both succeed if stock is insufficient

**Confirm a reservation**
```
POST /api/v1/reservations/{id}/confirm
```
- Moves reservation from `PENDING` to `CONFIRMED`
- Only a `PENDING` reservation can be confirmed

**Cancel a reservation**
```
POST /api/v1/reservations/{id}/cancel
```
- Moves reservation from `PENDING` to `CANCELLED`
- Returns the reserved quantity back to available stock
- A `CONFIRMED` reservation cannot be cancelled — return a clear error

**Get a reservation**
```
GET /api/v1/reservations/{id}
```

**Get current stock for a SKU**
```
GET /api/v1/inventory/{sku}
```

### Reservation Lifecycle

```
PENDING  →  CONFIRMED
PENDING  →  CANCELLED
```

`CONFIRMED` is a terminal state — no further changes allowed.

### Example Stock Scenario

```
SKU A100: total stock = 100, available = 100

Request 1: reserve 30  →  success,  available = 70
Request 2: reserve 40  →  success,  available = 30
Request 3: reserve 50  →  REJECTED — only 30 available
```

### Database Tables

Design and create these tables (you decide the exact columns and types):

- `products` — SKU, name, description
- `inventory` — SKU, total stock, available stock, reserved stock
- `reservations` — id, order ID, status, created at
- `reservation_items` — reservation id, SKU, quantity

### Design Patterns to Apply

Name these in your README and show where they appear in the code:
- **State Pattern** — reservation lifecycle transitions
- **Factory Pattern** — reservation creation logic

---

## Challenge 2 — Order Pricing & Promotion Engine

### The Scenario

An e-commerce platform must calculate the final price of an order after applying multiple promotion rules. Rules can be combined and new rules must be easy to add without changing existing code.

### Functional Requirements

**Calculate order price**
```
POST /api/v1/orders/calculate
{
  "customerType": "VIP",
  "items": [
    { "sku": "A100", "price": 100, "quantity": 2 },
    { "sku": "B200", "price": 50,  "quantity": 1 }
  ],
  "couponCode": "SUMMER10"
}
```

**Expected response:**
```json
{
  "data": {
    "subtotal": 250,
    "discounts": [
      { "type": "PERCENTAGE_DISCOUNT", "amount": 25.00 },
      { "type": "VIP_DISCOUNT",        "amount": 12.50 },
      { "type": "COUPON_SUMMER10",     "amount": 10.00 },
      { "type": "BUY2_GET1_FREE",      "amount": 100.00 }
    ],
    "totalDiscount": 147.50,
    "finalPrice": 102.50
  },
  "error": null
}
```

**Promotion rules to implement:**

| Rule | Logic |
|---|---|
| Percentage Discount | 10% off the order total |
| Buy 2 Get 1 Free | For every 2 units of the same SKU, 1 unit is free |
| VIP Customer Discount | VIP customers get an extra 5% off |
| Coupon Code | `SUMMER10` = $10 off; `SAVE20` = $20 off |

All applicable rules must be applied. Adding a new rule must not require changing existing rule classes.

**List active promotions**
```
GET /api/v1/promotions
```

**Create a promotion**
```
POST /api/v1/promotions
{
  "type": "PERCENTAGE_DISCOUNT",
  "value": 10,
  "active": true
}
```

### Database Tables

Design and create these tables:

- `products` — SKU, name, price
- `promotions` — id, type, value, active, created at
- `coupons` — code, discount amount, active, expiry date
- `orders` — id, customer type, subtotal, total discount, final price, created at
- `order_items` — order id, SKU, price, quantity

### Design Patterns to Apply

Name these in your README and show where they appear in the code:
- **Strategy Pattern** — each promotion rule is a separate strategy
- **Chain of Responsibility** — rules applied in sequence as a pipeline

---

## Response Format (required for both challenges)

Every API response — success or error — must use this envelope:

**Success:**
```json
{
  "data": { ... },
  "error": null
}
```

**Error:**
```json
{
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "SKU A100 has only 30 units available, 50 were requested"
  }
}
```

---

## Tech Stack

- Java 17
- Spring Boot 3.x
- PostgreSQL (run via Docker)
- Spring Data JPA
- Liquibase for all schema changes — **SQL changesets only** (`.sql` files, not XML)
- Maven or Gradle
- JUnit 5 + Mockito

---

## Tests

### Unit Tests
- Test every business rule in the **service layer** — mock the repository, do not load a Spring context
- Challenge 1: test insufficient stock rejection; test each valid and invalid state transition
- Challenge 2: test each promotion rule in isolation; test that combined rules produce the correct final price

### Integration Test
- Use **Testcontainers** with a real PostgreSQL container
- Challenge 1: send two concurrent reservation requests for the same SKU where combined quantity exceeds stock — assert exactly one succeeds and one is rejected
- Challenge 2: test the full calculate endpoint with promotions loaded from the real database — assert the correct final price

---

## Running the System

Provide a `docker-compose.yml` that starts PostgreSQL and the Spring Boot service. A reviewer must be able to run:

```bash
docker compose up
```

and call the API at `http://localhost:8080` with no manual steps.

---

## README

Your README must cover all of the following:

1. Which challenge you chose and why
2. Architecture overview — how the code is structured and why
3. Which design patterns you used and exactly where in the code
4. Which SOLID principles are visible and where
5. Database design decisions
6. How to run the system
7. How to run the tests
8. Trade-offs you made and what you would improve with more time
9. What would break at scale and how you would fix it

---

## Submission

Push everything to a single public GitHub repository and share the link.


