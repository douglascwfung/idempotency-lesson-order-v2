# Lesson Plan

Sections follow continuous reading order: Lessons 1 through 12. There is no Lesson 10 gap and no historical renumbering — numbers match the teaching sequence.

## Lesson 1: Basic Order Aggregate and REST Endpoints

A user places an Order that owns a collection of Order Lines. Each line records a sku, quantity, and unit price. The application persists the Order and its lines and exposes create, get, and full-replace operations over HTTP.

### Learning Objectives

- Model a parent-child aggregate with an Order that owns OrderDetail lines.
- Persist the aggregate with Spring Data JPA and Flyway.
- Expose a REST controller that creates an Order, retrieves an Order by id, and replaces an Order's content.
- Verify controller behaviour with unit tests that mock the service.

### Key Concepts

An Order is the root of the aggregate. An OrderDetail exists only as part of an Order. The repository saves the Order. Cascade and orphanRemoval ensure that lines are saved and removed with the Order. The controller receives a request body and delegates to the service. The service constructs the domain objects and calls the repository.

### What You Will Build

- `Order` and `OrderDetail` entities under `lab.lesson.order.domain`.
- `OrderRepository` under `lab.lesson.order.repository`.
- `OrderService` under `lab.lesson.order.service`.
- `OrderController` under `lab.lesson.order.web` with create, get, and replace methods.
- Request and response records under `lab.lesson.order.web.dto`.
- A Flyway migration that creates the `orders` and `order_details` tables.
- Unit tests for `OrderController` that mock `OrderService`.

### Deliberate Design Decisions

The create operation always inserts a new Order. Repeated identical requests produce duplicate Orders. This behaviour is intentional for Lesson 1. Later lessons will introduce an idempotency key to prevent duplicates. No `@Transactional` annotation appears yet. No version column appears yet.

### Implementation Outline

1. Add the `Order` entity with a customer name and a list of `OrderDetail` lines.
2. Add the `OrderDetail` entity with sku, quantity, and unitPrice.
3. Create the Flyway migration `V1__create_order_tables.sql`.
4. Add `OrderRepository`.
5. Add `OrderService` with create, get, and replace methods. The create method always constructs and saves a new Order.
6. Add request and response records.
7. Add `OrderController` that maps POST `/orders`, GET `/orders/{id}`, and PUT `/orders/{id}`.
8. Add `OrderControllerTest` that mocks the service.

### Verify Your Work

Run the controller unit tests:

```
mvn -Dtest=OrderControllerTest test
```

The following methods must pass:

- `createDelegatesToServiceAndReturnsCreated`
- `getDelegatesToService`
- `replaceDelegatesToService`

Start the application and send two identical POST requests to `/orders`. Two distinct Orders appear in the database.

Example create request:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice",
    "lines": [
      { "sku": "SKU-1", "quantity": 2, "unitPrice": 10.00 }
    ]
  }'
```

Repeat the same request. Both succeed and produce different ids.

## Lesson 2: Transaction Boundary with @Transactional

The Order and its OrderDetail lines form one unit of work. Without an explicit transaction, a failure after the Order is saved but before the lines are saved would leave an incomplete aggregate. The `@Transactional` annotation on the service methods creates the boundary.

### Learning Objectives

- Place `@Transactional` on the service methods that mutate or load the aggregate.
- Understand that the transaction starts when the annotated method is entered and commits when the method returns normally.
- Confirm the boundary with a Spring Boot integration test.

### Key Concepts

The `@Transactional` annotation on `OrderService.create` and `OrderService.replace` starts a transaction before the method body runs. The repository `save` calls participate in that transaction. When the method returns, the transaction commits. If an exception leaves the method, the transaction rolls back. The `get` method is marked `readOnly = true` because it does not mutate state.

### What You Will Build

- `@Transactional` on the mutating methods of `OrderService`.
- `@Transactional(readOnly = true)` on the read method.
- A Spring Boot test (`OrderServiceTransactionTest`) that verifies the Order and its lines are saved together.

### Deliberate Design Decisions

The transaction boundary is the service method. The controller does not start a transaction. The repository does not start a transaction. The service owns the unit of work for the aggregate. No version column is added yet. No idempotency key is added yet.

### Implementation Outline

1. Add `@Transactional` to `OrderService.create` and `OrderService.replace`.
2. Add `@Transactional(readOnly = true)` to `OrderService.get`.
3. Add `OrderServiceTransactionTest` that creates an Order and asserts both the Order and its lines are present after the call.

### Verify Your Work

Run the new integration test:

```
mvn -Dtest=OrderServiceTransactionTest test
```

The method `createSavesOrderAndLinesInOneTransaction` must pass.

The controller unit tests from Lesson 1 continue to pass:

```
mvn -Dtest=OrderControllerTest test
```

## Lesson 3: Optimistic Locking with @Version

Two concurrent updates of the same Order must not both succeed. The `@Version` column on the Order entity detects the conflict. The second update throws an exception when it tries to commit a stale version.

### Learning Objectives

- Add a version column to the Order entity with the `@Version` annotation.
- Observe that Hibernate increments the version on each successful update.
- Confirm that a second concurrent update of a stale entity throws `OptimisticLockingFailureException`.

### Key Concepts

The `@Version` annotation marks the field that Hibernate uses for optimistic locking. On each successful update the version value increases. When an entity is loaded, its version is recorded. If another transaction updates the same row and increments the version, the second transaction's update fails the version check and throws an exception. The exception is `OptimisticLockingFailureException` (or a subclass). No pessimistic lock is taken.

### What You Will Build

- A `version` field on the `Order` entity annotated with `@Version`.
- A Flyway migration `V2__add_order_version.sql` that adds the column.
- A Spring Boot test (`OrderOptimisticLockTest`) that loads the same Order twice, detaches the second instance, updates the first copy, and asserts that saving the second copy throws `OptimisticLockingFailureException`.

### Deliberate Design Decisions

Optimistic locking is chosen over pessimistic locking. The expected contention is low. A version conflict is reported to the caller rather than blocked. The version column is added only to the Order root. OrderDetail lines do not carry their own version yet (Lesson 4). No idempotency key is introduced yet.

### Implementation Outline

1. Add the `version` field to `Order` and annotate it with `@Version`.
2. Add the Flyway migration that creates the column with a default of 0.
3. Add `OrderOptimisticLockTest` that loads the Order twice, detaches the second instance so it carries the old version, updates the first, and asserts that saving the second throws `OptimisticLockingFailureException`.

### Verify Your Work

Run the optimistic-lock test:

```
mvn -Dtest=OrderOptimisticLockTest test
```

The method `concurrentReplaceThrowsOptimisticLockingFailure` must pass.

The earlier tests continue to pass:

```
mvn -Dtest=OrderServiceTransactionTest,OrderControllerTest test
```

## Lesson 4: Line Version and Single-Line Endpoints

An OrderDetail carries its own version. New endpoints add, update, or delete one line without loading the rest of the Order. An update of a line is guarded by the line version.

### Learning Objectives

- Add a version column to OrderDetail.
- Expose endpoints that operate on one line.
- Reject a stale line version before the write.

### Key Concepts

The `@Version` annotation on OrderDetail causes Hibernate to increment the version on each successful update of that line. The service loads only the named line by line id and Order id. It compares the version the client sent with the loaded version. A mismatch throws `ObjectOptimisticLockingFailureException`. The rest of the Order is not loaded. The whole-list replace from Lesson 1 remains available.

### What You Will Build

- A `version` field on `OrderDetail`.
- A Flyway migration `V4__add_order_detail_version.sql` that adds the column.
- `OrderDetailRepository` with `findByIdAndOrderId`.
- `OrderService` methods `addLine`, `updateLine`, and `deleteLine`.
- Controller endpoints `POST /orders/{id}/lines`, `PUT /orders/{id}/lines/{lineId}`, and `DELETE /orders/{id}/lines/{lineId}`.
- A unit test that rejects a stale line version.

### Deliberate Design Decisions

The line version is independent of the Order version. A change to one line does not require loading the other lines. No idempotency key is added to the line endpoints in this lesson (Lesson 10 adds a required key on add-line).

### Implementation Outline

1. Add `@Version` to `OrderDetail` and the migration.
2. Add `OrderDetailRepository`.
3. Add the three service methods.
4. Add the three controller endpoints.
5. Add `OrderLineServiceTest.updateLineRejectsStaleVersion`.

### Verify Your Work

Run the new line test:

```
mvn -Dtest=OrderLineServiceTest test
```

The method `updateLineRejectsStaleVersion` must pass.

The earlier tests continue to pass:

```
mvn -Dtest=OrderControllerTest,OrderOptimisticLockTest,OrderServiceTransactionTest test
```

## Lesson 5: Idempotency Key Claim and Replay

A client may retry the same create request. Without an idempotency key the retry creates a second Order. The key is claimed before the Order is created. A later request that carries the same key and the same content receives the stored response instead of creating another Order. An in-progress key returns 409 together with the key so the client can retry with it.

### Learning Objectives

- Store an idempotency key in its own table.
- Compute a hash of the path and the raw body.
- Claim the key before creating the Order.
- Replay the stored response when the same key and the same hash arrive again.
- Return 409 when the key is still in progress.

### Key Concepts

The client sends an `Idempotency-Key` header. The server computes a hash of the path and the raw body. On the first request the key is inserted with status `IN_PROGRESS`. After the Order is created the key is updated to `COMPLETED` and the response body is stored. A second request with the same key and the same hash finds the completed record and returns the stored body. A second request that arrives while the first is still running finds status `IN_PROGRESS` and throws `IdempotencyInProgressException`, which maps to 409. The hash includes the path and the raw body. Later lessons will add the user id to the hash.

This claim-then-complete design is the teaching path through Lesson 10. Lesson 11 replaces it with a single-transaction, COMPLETED-only design (`runOrReplay`).

### What You Will Build

- The `idempotency_keys` table and the `IdempotencyKey` entity under `lab.lesson.idem` (Flyway `V3__create_idempotency_keys.sql`).
- `IdempotencyService` with `claimOrReplay` and `complete`.
- Integration of the key into `OrderController.create`.
- Unit tests that verify claim-and-complete and replay.

### Deliberate Design Decisions

The key lives in its own table, not on the Order. The front end is expected to send one request at a time; the server check is a backstop. A 409 for an in-progress request returns the running key so the client can retry with it. No user id is included in the hash yet. Optimistic locking and the transaction boundary remain unchanged.

### Implementation Outline

1. Add the Flyway migration that creates `idempotency_keys`.
2. Add the `IdempotencyKey` entity and repository.
3. Add `IdempotencyService.claimOrReplay` and `complete`.
4. Change `OrderController.create` to accept the `Idempotency-Key` header and to call the service.
5. Add `OrderControllerIdempotencyTest`.

### Verify Your Work

When you implement this lesson (claim-then-complete), run:

```
mvn -Dtest=OrderControllerIdempotencyTest test
```

Methods that must pass at this stage:

- `createWithNewKeyClaimsAndCompletes`
- `createWithCompletedKeyReplaysStoredResponse`

On `main` (after Lessons 11–12), those paths become `runOrReplay` and the corresponding test method is `createWithNewKeyRunOrReplayCompletes`.

The earlier tests continue to pass:

```
mvn -Dtest=OrderOptimisticLockTest,OrderServiceTransactionTest,OrderControllerTest test
```

