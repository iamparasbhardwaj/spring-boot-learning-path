# Connection Pooling with HikariCP: Pool Size as a Failure Mode

## Why Pool at All
Opening a raw TCP connection to a database, authenticating, and negotiating a session is expensive — tens of milliseconds you do not want to pay on every single request. A connection pool opens a fixed set of connections once and hands them out to threads on demand, returning them to the pool when the thread is done instead of closing them.

**HikariCP** is Spring Boot's default connection pool (has been since Boot 2.0) — chosen for being the fastest and lowest-overhead pool available on the JVM, benchmarked extensively against alternatives like Apache DBCP2 and Tomcat's pool.

## The Core Settings
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10   # default is 10
      minimum-idle: 10        # defaults to maximum-pool-size
      connection-timeout: 30000   # ms to wait for a connection before giving up
```

`maximum-pool-size` is the one setting people misuse most: the instinct is "more traffic, bigger pool," but that instinct is backwards for most workloads.

## Why Bigger Isn't Better
A database connection maps to real, finite resources on the database server — a backend process (Postgres) or thread (MySQL), memory, and CPU context. Every additional connection is contention for the **same underlying CPU cores** doing the actual query execution; past a certain point, more concurrent connections just means more threads fighting over the same cores, with added context-switching overhead and no more actual throughput.

HikariCP's own sizing guidance follows a formula close to:
```
connections = ((core_count * 2) + effective_spindle_count)
```
For a typical modern server, that lands surprisingly small — often 10-20 total connections, even under heavy load — because a handful of connections running back-to-back is more efficient than dozens fighting over the same cores.

## Pool Exhaustion — the Actual Failure Mode
The realistic production incident isn't "the pool is too small so we're slow" — it's **pool exhaustion**: every connection is checked out and none are coming back, so new requests block on `connection-timeout` and then fail outright with `SQLTransientConnectionException`.

Classic causes:
- A slow downstream query holding a connection far longer than expected (a missing index, a lock wait) — under load, connections back up faster than they're returned.
- A leaked connection — code that gets a connection outside `try`-with-resources / outside a properly managed `@Transactional` boundary and never releases it back to the pool.
- The application's own thread pool (Tomcat's request-handling threads) sized much larger than the connection pool, so under load, threads pile up waiting for a connection that only 10 threads can hold at once.

**Diagnostic signal:** HikariCP exposes pool metrics via Micrometer/Actuator (`hikaricp_connections_active`, `hikaricp_connections_pending`) — a sustained high `pending` count under load is the tell that the pool, not the database itself, is the bottleneck.

## The Actual Fix Is Rarely "Increase Pool Size"
Increasing `maximum-pool-size` to "fix" exhaustion just moves the same contention further down the stack — the database's own connection or CPU limits become the new ceiling, often with worse per-query latency along the way. The fix is almost always to find *why* connections are held longer than expected (missing index, N+1 queries multiplying connection hold time, a runaway transaction) rather than to hand out more connections to the same underlying bottleneck.

---

## Interview Answer, Compressed
> "HikariCP is Boot's default pool because it's the lowest-overhead one on the JVM. The counterintuitive part is that a bigger pool isn't automatically faster — every connection contends for the same finite CPU cores on the database server, so oversizing the pool just adds context-switch overhead without adding real throughput. The real production failure mode is pool exhaustion — connections held too long by a slow query or a leak, until new requests time out waiting for one. The fix is almost never 'raise `maximum-pool-size`'; it's finding why connections are held longer than expected."
