# Transaction Propagation & Isolation Levels

## 1. Propagation: What Happens When a Transactional Method Calls Another
Propagation answers: *"a transaction is already running — what should this new `@Transactional` method do about it?"*

### `REQUIRED` (the default)
Join the existing transaction if one is active; otherwise start a new one. This is what every method in `TaskService` uses implicitly. If `create()` calls another `@Transactional(propagation = REQUIRED)` method (through a different bean, so the proxy still applies), both run inside **one** physical transaction — either both commit, or both roll back together.

### `REQUIRES_NEW`
Suspend the caller's transaction (if any) and start a brand-new, independent one, with its own commit/rollback outcome.

**Canonical use case:** audit logging that must survive even if the surrounding business transaction rolls back.

```java
@Transactional
public TaskResponse create(CreateTaskRequest request) {
    auditLogger.recordAttempt(request);   // REQUIRES_NEW — commits independently
    // ... business logic that might throw and roll back ...
}

@Service
class AuditLogger {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(CreateTaskRequest request) { ... }
}
```
If `create()`'s business logic later throws `IllegalStateException` (project full), the outer transaction rolls back the `Task` insert — but the audit row, having already committed independently in its own transaction, survives. Note this only works across a **separate bean** — the self-invocation trap from `@Transactional` applies here too.

### Other propagation values (know they exist, rarely reached for)
| Value | Behavior |
|---|---|
| `SUPPORTS` | Join if a transaction exists, otherwise run non-transactionally |
| `NOT_SUPPORTED` | Suspend any existing transaction, run non-transactionally |
| `MANDATORY` | Must already be inside a transaction, or throw an exception |
| `NEVER` | Must **not** be inside a transaction, or throw an exception |
| `NESTED` | Runs inside a savepoint of the outer transaction — can roll back to that savepoint without killing the whole outer transaction (JDBC only, not all drivers support it) |

---

## 2. Isolation Levels: What One Transaction Can See of Another's Uncommitted Work
Isolation controls concurrency anomalies between simultaneous transactions. From weakest (fastest, least safe) to strongest:

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| `READ_UNCOMMITTED` | possible | possible | possible |
| `READ_COMMITTED` | prevented | possible | possible |
| `REPEATABLE_READ` | prevented | prevented | possible |
| `SERIALIZABLE` | prevented | prevented | prevented |

- **Dirty read:** reading a row another transaction wrote but hasn't committed yet (and might roll back).
- **Non-repeatable read:** re-reading the same row twice in one transaction and getting different values because another transaction committed an update in between.
- **Phantom read:** re-running the same range query twice and getting a different *set of rows* because another transaction inserted/deleted matching rows in between.

Spring's default is `Isolation.DEFAULT` — defer to whatever the underlying database's default is (Postgres and MySQL/InnoDB both default to `READ_COMMITTED`; H2's default is close to this too). You override it per-method only when a specific race condition needs closing:

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transferBudget(Long fromProjectId, Long toProjectId, int amount) { ... }
```

The trade-off is always the same direction: stronger isolation prevents more anomalies, at the cost of more locking/blocking and lower throughput. `SERIALIZABLE` is the correct answer for "this must be airtight" (financial transfers), not the default answer for every service method.

---

## Interview Answer, Compressed
> "Propagation decides how a `@Transactional` method behaves relative to a transaction already in progress — `REQUIRED` joins it (the default), `REQUIRES_NEW` suspends it and starts an independent one, useful for an audit log that must commit even if the caller rolls back. Isolation decides what one transaction can see of another's uncommitted changes — `READ_COMMITTED` is the sane default most databases ship with; I'd only reach for `SERIALIZABLE` on something like a financial transfer where a race condition is unacceptable."
