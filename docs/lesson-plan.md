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

## Lesson 6: User Id in the Hash and Key Reuse Rejection

The hash must include the user id. A key that belongs to one user and one body must not be accepted for a different body. The same key with different content throws `IdempotencyKeyReuseException`, which maps to 409.

### Learning Objectives

- Include the user id in the request hash.
- Reject a key that is reused with different content.
- Keep the in-progress 409 behaviour unchanged.

### Key Concepts

The hash is now `userId + path + rawBody`. Two requests that share a key but differ in user or body produce different hashes. When a completed or in-progress key is found and the computed hash does not match the stored hash, `IdempotencyKeyReuseException` is thrown. The exception maps to HTTP 409. The front end is expected to send one request at a time; the server check is a backstop.

### What You Will Build

- Updated `IdempotencyService.claimOrReplay` that accepts a user id and includes it in the hash.
- `IdempotencyKeyReuseException`.
- Updated controller call that supplies a user id.
- A unit test that verifies the reuse rejection.

### Deliberate Design Decisions

The user id is part of the hash so that one key cannot be used for two different users or two different bodies. For this lesson the user id is a fixed value. A later lesson can replace it with the authenticated principal. The 409 for an in-progress request still returns the running key.

### Implementation Outline

1. Change the hash method to include the user id.
2. Change `claimOrReplay` to accept the user id and to throw `IdempotencyKeyReuseException` on hash mismatch.
3. Update the controller to pass a user id.
4. Update existing tests and add `createWithSameKeyAndDifferentContentThrows`.

### Verify Your Work

```
mvn -Dtest=OrderControllerIdempotencyTest test
```

The following methods must pass (names on `main` after Lessons 11–12):

- `createWithNewKeyRunOrReplayCompletes`
- `createWithCompletedKeyReplaysStoredResponse`
- `createWithSameKeyAndDifferentContentThrows`

The earlier tests continue to pass:

```
mvn -Dtest=OrderOptimisticLockTest,OrderServiceTransactionTest,OrderControllerTest test
```

## Lesson 7: User Id Header and 409 Body with the Running Key

The fixed user id is replaced by an `X-User-Id` request header. The 409 response for an in-progress key includes the running key in the body so the client can retry with it.

### Learning Objectives

- Obtain the user id from the `X-User-Id` header.
- Return the running key in the body of a 409 for an in-progress request.
- Keep the hash and the reuse rejection unchanged.

### Key Concepts

The controller reads the user id from the `X-User-Id` header. When the header is absent the value falls back to `anonymous`. The `IdempotencyExceptionHandler` catches `IdempotencyInProgressException` and returns HTTP 409 with a body that contains the key. The client can then retry with the same key. The hash remains `userId + path + rawBody`. The front end is expected to send one request at a time; the server check is a backstop.

### What You Will Build

- An `X-User-Id` header parameter on `OrderController.create`.
- `IdempotencyConflictResponse` under `lab.lesson.idem` and `IdempotencyExceptionHandler` under `lab.lesson.common`.
- A unit test that verifies an in-progress key throws with the key present.

### Deliberate Design Decisions

The user id comes from a header for this lesson. A later lesson can replace the header with the authenticated principal. The 409 body contains the key so the client can retry with it. No change is made to the hash algorithm. (Lesson 11 removes the in-progress path entirely.)

### Implementation Outline

1. Add the `X-User-Id` header parameter to the controller.
2. Add `IdempotencyConflictResponse` and `IdempotencyExceptionHandler`.
3. Update the controller tests to pass the user id.
4. Add `createWithInProgressKeyThrowsWithKey`.

### Verify Your Work

When implementing claim-then-complete at this stage:

```
mvn -Dtest=OrderControllerIdempotencyTest test
```

Methods that must pass at this stage:

- `createWithNewKeyClaimsAndCompletes`
- `createWithCompletedKeyReplaysStoredResponse`
- `createWithSameKeyAndDifferentContentThrows`
- `createWithInProgressKeyThrowsWithKey`

On `main` after Lesson 11 there is no in-progress path; the reuse and replay tests remain.

The earlier tests continue to pass:

```
mvn -Dtest=OrderOptimisticLockTest,OrderServiceTransactionTest,OrderControllerTest test
```

## Lesson 8: Authenticated Principal as the User Id

The `X-User-Id` header is removed. The user id now comes from the authenticated principal in the Spring Security context. The hash, the claim/replay path, and the 409 behaviour remain unchanged.

### Learning Objectives

- Obtain the user id from `SecurityContextHolder`.
- Configure a minimal Spring Security filter chain that permits all requests.
- Keep the rest of the idempotency behaviour unchanged.

### Key Concepts

The controller calls `SecurityContextHolder.getContext().getAuthentication()`. When the authentication is present and is not the anonymous principal, `authentication.getName()` supplies the user id. When no authentication is present the value falls back to `anonymous`. The hash remains `userId + path + rawBody`. The front end is expected to send one request at a time; the server check is a backstop.

### What You Will Build

- The `spring-boot-starter-security` dependency.
- `SecurityConfig` under `lab.lesson.common` that permits all requests and disables CSRF.
- A private `currentUserId` method on `OrderController`.
- Updated unit tests that set a `UsernamePasswordAuthenticationToken` in the security context.

### Deliberate Design Decisions

Spring Security is introduced only to supply the principal. No authentication filter or password encoder is configured. All endpoints remain open. The user id is taken from the security context so that a later lesson can add real authentication without changing the hash.

### Implementation Outline

1. Add `spring-boot-starter-security` to the pom.
2. Add `SecurityConfig` that permits all requests.
3. Replace the `X-User-Id` header parameter with a call to `currentUserId`.
4. Update the controller tests to set the security context.

### Verify Your Work

```
mvn -Dtest=OrderControllerIdempotencyTest,OrderControllerTest test
```

All methods in both classes must pass.

The earlier tests continue to pass:

```
mvn -Dtest=OrderOptimisticLockTest,OrderServiceTransactionTest test
```

## Lesson 9: Handle the Race on the Primary Key

**Scope note.** This lesson's teaching outcome — catch the primary-key race and land on an `IN_PROGRESS` 409 — applies only to the **claim-and-replay** approach of Lessons 5–10. After Lessons 11–12 switch to single-transaction / `COMPLETED`-only (`runOrReplay`), there is no `IN_PROGRESS` status and therefore no in-progress race outcome. A concurrent insert race still exists in a different form: the loser reloads a `COMPLETED` key and either replays the stored response or rejects a hash mismatch. Lesson 11 updates the race test accordingly.

The `key_value` column is already the primary key, so a second insert of the same key fails. Two concurrent requests that both see no existing row will both attempt the insert. The second insert throws `DataIntegrityViolationException`. The service catches the exception, reloads the key, and either replays the stored result or returns 409 if the first request is still in progress.

### Learning Objectives

- Recognise that the primary key already enforces uniqueness.
- Catch `DataIntegrityViolationException` on the claim insert.
- Reload the key after the conflict and apply the same replay or in-progress rules (claim-and-replay only).
- Know that Lessons 11–12 keep the catch/reload race but drop the `IN_PROGRESS` outcome.

### Key Concepts

The first request inserts the key with status `IN_PROGRESS`. A concurrent request that also finds no row attempts the same insert. The database rejects the second insert because of the primary key. The service catches the violation, loads the row that the first request inserted, and continues with the normal claim-and-replay rules: hash mismatch throws `IdempotencyKeyReuseException`, `IN_PROGRESS` throws `IdempotencyInProgressException`, and `COMPLETED` returns the stored response. The front end is expected to send one request at a time; the server check is a backstop.

**After Lessons 11–12.** The same primary-key race still happens under `runOrReplay`, but the raced row is already `COMPLETED` (or not yet visible until the winner commits). There is no `IN_PROGRESS` outcome to return: the loser reloads and either replays the completed response or throws `IdempotencyKeyReuseException` on a hash mismatch. Lesson 9's in-progress 409 path is therefore claim-and-replay-specific.

### What the Client Sees Without This Change

Without the try/catch, the second request receives an HTTP 500. The `DataIntegrityViolationException` leaves the service uncaught and becomes a server error. The client does not receive a 409 with the running key, and it does not receive the stored response. The client cannot distinguish a concurrent retry from a true server failure, and it has no key to retry with.

### What You Will Build

- A try/catch around `saveAndFlush` in `IdempotencyService.claimOrReplay`.
- A reload of the key after the constraint violation.
- A unit test that simulates the concurrent claim of an in-progress key.

### Deliberate Design Decisions

No additional unique constraint is added. The primary key is sufficient. The race is handled by catching the constraint violation rather than by a lock. The `IN_PROGRESS` outcome of that race is claim-and-replay-specific; Lessons 11–12 keep the same catch/reload pattern but only replay `COMPLETED` or reject reuse.

### Implementation Outline

1. Change the claim insert to `saveAndFlush` inside a try/catch for `DataIntegrityViolationException`.
2. On the exception, reload the key and apply the existing hash and status checks.
3. Add `IdempotencyServiceRaceTest`.

### Verify Your Work

When implementing claim-then-complete at this stage:

```
mvn -Dtest=IdempotencyServiceRaceTest test
```

The method `concurrentClaimOfInProgressKeyThrows` must pass.

On `main` after Lessons 11–12 (single-TX / `COMPLETED`-only), there is no `IN_PROGRESS` race outcome. The race test asserts completed-replay / hash-mismatch instead (`concurrentClaimOfSameKeyReplaysCompletedResponse`, `concurrentClaimWithDifferentHashThrowsReuse`).

The earlier tests continue to pass:

```
mvn -Dtest=OrderControllerIdempotencyTest,OrderControllerTest,OrderOptimisticLockTest,OrderServiceTransactionTest test
```

