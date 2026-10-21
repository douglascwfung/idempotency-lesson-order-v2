# Claim-then-complete vs Single Transaction

This note compares the claim-then-complete design used through Lesson 10 with the single-transaction design introduced in Lesson 11. Both keep the same hash (`userId + path + rawBody`) and primary-key race handling on the key insert. Lesson 11 removes `IN_PROGRESS` and the in-progress 409 path.

## Claim-then-complete

The service inserts the key as `IN_PROGRESS` and commits. It then creates the Order in a separate unit of work. After the Order succeeds, it updates the key to `COMPLETED` and stores the response body.

**Crash after key insert.** The key remains `IN_PROGRESS` with no Order. A retry with the same key receives 409. The client cannot finish through a simple replay until something clears or completes that row. Recovery code (delete, expire, or lease the stuck claim) is required for production use of this pattern.

**Concurrent claim of the same key.** The primary key rejects the second insert. The service catches the violation, reloads the key, and returns 409 while the first request is still in progress, or replays when the first request has completed.

**Duplicate Order risk.** Low while the claim stays `IN_PROGRESS`, because the second request is rejected. The risk appears after a crash: an operator who deletes the stuck key and allows a retry can create an Order that the original request may also have created if the crash happened after the Order write but before `complete`. Coordinating recovery with the Order table is part of the operational cost.

**Recovery code required.** Yes. Stuck `IN_PROGRESS` rows must be expired, leased, or removed. Without that, a crash blocks the key forever from the client's point of view.

**When this is acceptable.** Claim-then-complete is a useful teaching model when the work after the claim cannot join the same database transaction (for example, an external call). It is not production-safe on its own: a crash can leave a stuck `IN_PROGRESS` key with no Order, so a lease, sweeper, or other recovery path is required before you rely on it in production. Prefer a single transaction when the key and Order share one database.

## Single transaction

The service inserts the key already as `COMPLETED`, writes the Order, and stores the response body inside one transaction. There is no `IN_PROGRESS` status at any point. Controllers call `runOrReplay`. Lesson 11 invoked `OrderService` directly inside that method; Lesson 12 passes Order work as a lambda; Lesson 13 uses that lambda for mixed add+update and may finish a key as `FAILED` when `PermanentRejection` is thrown before business writes. Flyway `V5` introduced COMPLETED-only; `V6` allows `COMPLETED` or `FAILED`.

**Crash after key insert.** There is no committed key insert to crash after in isolation. An uncommitted key and an uncommitted Order roll back together. A retry finds no row and starts fresh.

**Concurrent claim of the same key.** The insert still uses `saveAndFlush` so the primary key rejects a concurrent claim before the loser creates an Order. The losing request catches the violation, reloads, and either replays the completed response or rejects a hash mismatch. There is no in-progress 409.

**Duplicate Order risk.** Low for the happy path and for a crash before commit: neither the key nor the Order becomes visible. The main residual risk is work that cannot participate in the same transaction; that work must not be placed inside `runOrReplay` as if it were atomic with the key.

**Recovery code required.** Not for the single-transaction path. A crash does not leave a key row. There is no in-progress state to observe or match against.

**When this is acceptable.** Prefer the single transaction when the Order write and the key live in the same database and can share one commit. That is the common case for this lesson's create and add-line flows. It removes lease or sweeper logic on the happy path and makes retry after a crash safe without manual cleanup.

## Summary

| Concern | Claim-then-complete | Single transaction |
| --- | --- | --- |
| Key status on insert | `IN_PROGRESS`, then later `COMPLETED` | `COMPLETED` only |
| Crash after claim | Committed `IN_PROGRESS`, no Order | Both roll back |
| Concurrent same key | Primary key + reload → 409 or replay | Primary key + reload → replay or hash-mismatch 409 |
| Duplicate Order | Possible around recovery mistakes | Avoided when work shares the transaction |
| Recovery code | Required for stuck claims | Not required for this path |
| Best fit | External side effects after claim | Same-database Order + key |

## FAILED without IN_PROGRESS (Lesson 13)

A permanent early rejection (`PermanentRejection`) is stored as `FAILED` with the rejection status and body. That key is a finished rejection: no business rows were written, and there is still no `IN_PROGRESS`. A retry with the same key and hash replays the same rejection. A database optimistic-lock failure is different — it must leave `runOrReplay` so the transaction rolls back and no FAILED (or COMPLETED) key is kept for a half-applied change.
