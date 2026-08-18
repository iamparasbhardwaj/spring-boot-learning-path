# Optimistic Locking (`@Version`) vs Pessimistic Locking

## The Problem Both Solve
Two transactions read the same row, both decide to update it based on what they read, and one of them commits over the other's changes without either transaction ever knowing a conflict happened — a classic **lost update**. Neither JPA nor a plain `@Transactional` boundary prevents this by default; you have to opt into a concurrency control strategy.

---

## 1. Optimistic Locking — `@Version`
**Bet:** conflicts are rare, so don't pay a locking cost up front — just detect a conflict at commit time and fail loudly if one occurred.

```java
@Entity
public class Task {
    @Version
    private Long version;
    // ...
}
```
Hibernate adds `version` to every `UPDATE`'s `WHERE` clause and increments it on write:
```sql
UPDATE task SET title = ?, version = 6 WHERE id = ? AND version = 5
```
If another transaction already bumped `version` to 6 in between, this `UPDATE` matches **zero rows**. Hibernate detects that and throws `OptimisticLockException` (wrapped as `ObjectOptimisticLockingFailureException` in Spring), rather than silently overwriting the other transaction's change.

**Trade-offs:**
- No database locks held — reads and writes from other transactions proceed freely until commit time.
- Requires the caller to catch the exception and decide what to do (retry with a fresh read, surface a 409 Conflict to the client, merge fields).
- Best fit for **low-contention** scenarios: e.g., two admins rarely edit the exact same `Task` at the same instant, so paying for a lock on every read would be pure waste for a conflict that almost never happens.

## 2. Pessimistic Locking
**Bet:** conflicts are likely enough (or costly enough if they happen) that it's worth blocking other transactions from touching the row at all until you're done.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select t from Task t where t.id = :id")
Optional<Task> findByIdForUpdate(@Param("id") Long id);
```
This maps to a database-level row lock — typically `SELECT ... FOR UPDATE`. Any other transaction trying to read (with a competing lock mode) or write that row **blocks** until the lock-holding transaction commits or rolls back.

**Trade-offs:**
- Guarantees no lost update, with no retry logic needed on the caller's side.
- Reduces concurrency: other transactions queue up waiting for the lock.
- Risk of deadlocks if two transactions lock rows in different orders — needs careful lock ordering discipline across the codebase.
- Fit for **high-contention, high-stakes** scenarios — e.g., decrementing a limited inventory count or a financial balance, where a lost update is unacceptable and the lock is held only briefly.

---

## 3. Choosing Between Them
| | Optimistic (`@Version`) | Pessimistic (`FOR UPDATE`) |
|---|---|---|
| Cost when uncontended | Near zero | DB lock overhead even with no conflict |
| Behavior on conflict | Exception at commit — caller retries | Blocks until lock released |
| Best for | Low contention, UI-editable records | High contention, short critical sections |
| Failure mode | Livelock/retry storms under heavy contention | Deadlocks under bad lock ordering |

Default to optimistic locking unless you have a specific, identified hot path where conflicts are frequent enough that repeated retries would themselves become a performance problem.

---

## Interview Answer, Compressed
> "Optimistic locking adds a `@Version` column that's checked and incremented on every `UPDATE`'s `WHERE` clause — if another transaction already bumped it, the update matches zero rows and Hibernate throws an exception instead of silently overwriting. No locks held, so it's cheap, but the caller has to handle the conflict, usually by retrying. Pessimistic locking takes a real database row lock (`SELECT FOR UPDATE`) up front so no one else can touch the row until you're done — safer under contention, but it serializes access and risks deadlocks if lock ordering isn't consistent. I'd default to optimistic unless I have a specific high-contention hot path."
