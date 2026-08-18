# `@DataJpaTest`, `TestEntityManager`, and Automatic Rollback

## Overview
`@DataJpaTest` is the persistence-layer counterpart to `@WebMvcTest` — a slice that boots exactly what's needed to test repositories and JPA mappings, and nothing else. `TaskRepositoryTest` is the reference example in this project.

```java
@DataJpaTest
class TaskRepositoryTest {
    @Autowired TaskRepository taskRepository;
    @Autowired TestEntityManager em;
    ...
}
```

---

## 1. What Actually Boots
`@DataJpaTest` auto-configures:
- An embedded, in-memory database (H2 here, since it's already on the classpath) — **by default it substitutes any embedded DB it finds for whatever `DataSource` your app config specifies**, unless you explicitly disable that with `@AutoConfigureTestDatabase(replace = Replace.NONE)` (used when you want the test to hit the real configured database, e.g. via Testcontainers).
- Hibernate/JPA itself, and every `@Repository` interface — `TaskRepository` and `ProjectRepository` are both real, working beans here.
- `TestEntityManager` — a test-only wrapper around the JPA `EntityManager` with convenience methods for setting up fixture data.

It does **not** boot `@Service`, `@Controller`, or the web layer — this slice is purely about "does my repository/entity mapping do the right thing against a real (if embedded) database."

## 2. `TestEntityManager` — Precise Control Over Persistence State
`taskRepository.save(...)` alone isn't always enough to set up a meaningful test, because `save()` doesn't necessarily force data to actually hit the database or clear Hibernate's first-level cache. `TestEntityManager` gives direct control over both:

```java
Project project = em.persist(new Project("Prep"));
em.persist(new Task("Late", null, LocalDate.now().minusDays(3), project));
em.flush();   // force pending INSERTs to actually run against the DB
em.clear();   // evict everything from the persistence context's first-level cache
```
- **`.persist(...)`** inserts fixture data directly, bypassing your repository under test (so the test isn't accidentally exercising the same method it's trying to verify).
- **`.flush()`** forces Hibernate to actually execute pending SQL rather than leaving it batched in memory, so the state genuinely exists in the (embedded) database before you query it.
- **`.clear()`** evicts the persistence context's first-level cache, which matters specifically for testing lazy loading and N+1 fixes correctly:

```java
// TaskRepositoryTest.fetchJoin_avoidsNPlusOne
em.flush();
em.clear();   // force reads to hit the DB rather than the first-level cache

List<Task> tasks = taskRepository.findByStatusWithProject(TaskStatus.TODO);
// Project is already initialised - no extra query, no LazyInitializationException.
assertThat(tasks).allSatisfy(t -> assertThat(t.getProject().getName()).isEqualTo("Prep"));
```
Without `em.clear()`, the entities from `.persist(...)` might still be sitting in the first-level cache when the repository query runs, and the test could pass even if the fetch join were broken — because Hibernate would silently return the cached, already-initialized instances instead of actually exercising the fetch join's SQL.

## 3. Automatic Transaction Rollback
Every `@DataJpaTest` method runs wrapped in a transaction that is **rolled back** at the end of the test — this is the default behavior (`@Transactional` is implicitly applied by `@DataJpaTest`, unless explicitly overridden). Practically:
- Each test starts from a clean slate — data inserted in `derivedQuery_findsByStatus` never leaks into `jpqlQuery_findsOverdue`.
- No manual `@AfterEach` cleanup of inserted rows is needed.
- This is exactly the same proxy-and-rollback mechanism covered in the `@Transactional` note — the test method itself is the transactional boundary here.

## 4. The Honest Limitation: H2 Lies About Your Real Database
```java
/**
 * INTERVIEW: "Is H2 good enough for testing?"
 * It catches mapping errors but lies about dialect-specific SQL, so real teams use
 * Testcontainers to run the actual Postgres/MySQL image.
 */
```
H2's SQL dialect is close to, but not identical to, Postgres or MySQL — a native query using Postgres JSONB operators, or subtle constraint/locking behavior differences, can pass against H2 and fail against the real production database. `@DataJpaTest` with H2 is excellent for fast feedback on entity mappings, derived queries, and JPQL correctness; it is not a substitute for testing dialect-specific native SQL against the real engine (see the Testcontainers note for that answer).

---

## Interview Answer, Compressed
> "`@DataJpaTest` boots an embedded database, Hibernate, and my repository beans — nothing else — and wraps every test method in a transaction that's rolled back afterward, so tests never pollute each other. `TestEntityManager` lets me set up fixture data directly and, critically, `flush()` and `clear()` it so I'm actually testing against the database and a clean persistence context rather than accidentally passing because of the first-level cache — that matters a lot when the thing under test is a fetch join or an N+1 fix. The honest caveat: H2 catches mapping bugs but doesn't reproduce dialect-specific SQL, so for anything Postgres/MySQL-specific I'd reach for Testcontainers instead."
