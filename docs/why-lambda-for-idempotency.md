# Why Pass Domain Work as a Lambda

Lesson 11 put the idempotency key and the Order write in one transaction. The key is inserted already as `COMPLETED`. There is no `IN_PROGRESS` row. In that lesson, `IdempotencyService` called `OrderService.create` / `addLine` directly inside `runOrReplay` overloads. Lesson 12 keeps the single-transaction COMPLETED-only design and makes the call shape deliberate: the Order work is a lambda passed into the key method.

## What the key method owns

`IdempotencyService.runOrReplay` is the key method. It opens the transaction. It hashes `userId + path + rawBody`. It inserts the key, handles a primary-key race (replay a completed response or reject a hash mismatch), stores the response body, and commits or rolls back. That is the key lifecycle. It does not create Orders or add lines by name.

## What the caller supplies

The caller passes a work callback — a lambda such as `() -> orderService.create(request)` or `() -> orderService.addLine(id, request)`. The key method invokes that lambda inside the open transaction. Create and add-line share the same claim, race, replay, and hash rules without duplicating them.

## One transaction boundary

Because the lambda runs inside the key method's `@Transactional` box, a success commits the `COMPLETED` key and the Order rows together. A failure under the lambda rolls the flushed key and any Order work back together. A retry finds no key and can start fresh. Nested `@Transactional` on Order service methods joins that box under default `REQUIRED` propagation.

## Why this helps later

Complicated domain work stays outside the key method. A request that mixes adds and updates can pass one lambda that performs all of those writes. Different endpoints can use different exception policies inside their own work without changing how the key is claimed or replayed. A new idempotent endpoint passes a new lambda; it does not copy `runOrReplay` wiring.

## Tradeoff

The call site is slightly harder to read at first. The Order write is no longer a direct statement inside the key method; it is a callback. That indirection is the cost of keeping key lifecycle and domain work separate so the single-transaction path stays reusable.
