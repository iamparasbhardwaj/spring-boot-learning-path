# The N+1 Select Problem, and Its Three Fixes

## Overview
The N+1 problem is the single most common JPA/Hibernate performance bug, and one of the highest-ROI things to be able to explain fluently in an interview — bonus points if you can say you've actually watched it happen in a query log, not just recited the definition.

---

## 1. What It Is
`Project.tasks` is a `@OneToMany(mappedBy = "project", fetch = FetchType.LAZY)`. Load a list of `N` projects, then touch `.getTasks()` on each one in a loop:

```java
List<Project> projects = projectRepository.findAll();   // query #1: SELECT * FROM project
for (Project p : projects) {
    p.getTasks().size();                                  // query #2..N+1: SELECT * FROM task WHERE project_id = ?
}
```

That's **1** query to load the projects, plus **1 query per project** to lazily initialize its `tasks` collection — **1 + N** total round trips to the database for what should conceptually be a single request. With 100 projects, that's 101 queries where 1 or 2 would do. This scales with data size in the worst possible way: it gets slower every time someone adds a row, not every time someone adds a feature.

The project's own repository names this explicitly as "the N+1 demo":
```java
// ProjectRepository.java
/**
 * THE N+1 DEMO.
 * Call findAll(), then loop and call p.getTasks().size().
 * Turn on logging.level.org.hibernate.SQL=DEBUG and count the queries: 1 + N.
 */
```

**How to actually see it:** set `logging.level.org.hibernate.SQL=DEBUG` (already on in `application.yml`), call the broken path, and count the `SELECT` statements in the console. Then swap to one of the fixes below and count again. That before/after — "I watched the query count go from 6 to 1" — is a far stronger answer than reciting the definition.

---

## 2. Fix #1 — Fetch Join (JPQL)
Force the association to load in the **same** SQL query, using a `JOIN FETCH`:

```java
// ProjectRepository.java
@Query("select distinct p from Project p left join fetch p.tasks")
List<Project> findAllWithTasksFetchJoin();
```
- `left join fetch` (not just `left join`) tells Hibernate to populate the `tasks` collection from the joined rows, instead of just filtering by them.
- `distinct` is required here: a `JOIN` between `project` and `task` produces one result row **per task**, so a project with 3 tasks would otherwise appear 3 times in the raw SQL result set before Hibernate deduplicates the Java objects.
- **Limitation:** you cannot fetch-join more than one collection association in the same query (a Hibernate restriction — it would produce a Cartesian product), and fetch joins don't compose well with `Pageable` (pagination on the *root* entity becomes ambiguous once the join has multiplied the row count).

## 3. Fix #2 — `@EntityGraph`
Declare which associations to eagerly load for a specific repository method, without hand-writing JPQL:

```java
// ProjectRepository.java
@EntityGraph(attributePaths = {"tasks"})
List<Project> findAll();
```
Behind the scenes this generates roughly the same fetch-join SQL, but reads more like a Spring Data annotation than a hand-rolled query — the preferred approach when you don't otherwise need custom JPQL. `@EntityGraph` composes better with derived query methods and (mostly) with pagination than a manual fetch join does.

## 4. Fix #3 — Batch Size
Instead of eliminating the extra queries, shrink their *number* by fetching lazy collections in **batches** rather than one row at a time:

```java
@OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
@BatchSize(size = 20)
private List<Task> tasks;
```
or globally via `spring.jpa.properties.hibernate.default_batch_fetch_size=20`. Instead of 1 query per project, Hibernate issues 1 query per **batch of 20** projects (`WHERE project_id IN (?, ?, ..., ?)`) — turning 1 + 100 queries into 1 + 5. This is the right fix when you genuinely need lazy loading in most code paths but want to blunt the N+1 cost for the few paths that iterate a whole collection, without committing to eager-loading everywhere.

---

## 5. Choosing Between Them
| Fix | Best when |
|---|---|
| Fetch join | One specific query needs the association, and only one collection |
| `@EntityGraph` | Same as above, but you prefer declarative Spring Data style, or need to reuse a derived query |
| Batch size | Many different code paths lazily touch the same association and a full fetch join everywhere isn't practical |

The one fix that is **not** on this list: switching the association to `FetchType.EAGER`. That eliminates N+1 for this one code path by paying the join cost on **every** load of `Project`, everywhere in the codebase, whether or not the caller needed `tasks` — trading a visible, fixable problem for an invisible, permanent one.

---

## Interview Answer, Compressed
> "N+1 happens when you load a list of parents with one query, then lazily touch each parent's lazy collection in a loop, firing one extra query per parent. I've watched this in a Hibernate SQL log — go from a handful of queries to N+1 by touching `.getTasks()` on each project. The fix is never 'switch to EAGER,' since that just makes the extra join happen on every load instead of the one that needed it. Instead: a JPQL fetch join with `distinct` for a single query path, `@EntityGraph` for the same result in a more Spring Data-native style, or `@BatchSize` when many different code paths touch the same lazy collection and you want batched `IN (...)` queries instead of one-per-row."
