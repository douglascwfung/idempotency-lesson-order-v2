# Idempotency Lesson: Order + Order Lines (v2)

This repository teaches progressive Spring Boot concepts using an Order aggregate that owns Order Lines.

The sequence covers parent-child aggregates, `@Transactional` boundaries, optimistic locking, and idempotency keys.

This is a clean reading-order remake of [`idempotency-lesson-order`](https://github.com/douglascwfung/idempotency-lesson-order). Lesson numbers are continuous 1..12 and match the teaching sequence (no historical tag/reading-order mismatch).

## How to Run

Requirements: Java 21 and Maven.

```
mvn spring-boot:run
```

The application starts on port 8080 and uses an in-memory H2 database.

## Example Interactions (Lesson 1)

Create an order:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice",
    "lines": [
      { "sku": "SKU-1", "quantity": 2, "unitPrice": 10.00 },
      { "sku": "SKU-2", "quantity": 1, "unitPrice": 25.50 }
    ]
  }'
```

Retrieve the order (replace `1` with the id returned by create):

```bash
curl http://localhost:8080/orders/1
```

Replace the order content:

```bash
curl -X PUT http://localhost:8080/orders/1 \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice Updated",
    "lines": [
      { "sku": "SKU-3", "quantity": 5, "unitPrice": 7.99 }
    ]
  }'
```

Send the same create request twice. Lesson 1 deliberately creates two separate Orders.

## Lesson Map

| Lesson | Focus |
|--------|--------|
| 1 | Order aggregate + REST create/get/replace (duplicates allowed) |
| 2 | `@Transactional` service boundary |
| 3 | Optimistic locking with `@Version` on Order |
| 4 | Line `@Version` and single-line endpoints |

Tag `lesson-4` is the tip of this lesson.

Full narrative: [`docs/lesson-plan.md`](docs/lesson-plan.md).


## Tags

| Tag | Meaning |
|-----|---------|
| `lesson-0` | Empty starting point (scaffold) |
| `lesson-1` | End state after Lesson 1 |
| `lesson-2` | End state after Lesson 2 |
| `lesson-3` | End state after Lesson 3 |
| `lesson-4` | End state after Lesson 4 |

```bash
git checkout lesson-0          # empty start
git checkout lesson-4        # end of Lesson 4
```
