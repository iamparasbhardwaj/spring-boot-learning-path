# Persistence Context, Managed Entities & Dirty Checking

## Overview
`TaskService.update` never calls `taskRepository.save(task)`. That is not an oversight — it is the entire point of the demo:

```java
@Transactional
public TaskResponse update(Long id, UpdateTaskRequest request) {
    Task task = taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));

    // No explicit save() call needed: the entity is MANAGED inside the persistence
    // context, so Hibernate dirty-checks it and flushes an UPDATE at commit.
    task.update(request.title(), request.description(), request.status(), request.dueDate());
    return TaskResponse.from(task);
}
```

---

## 1. What the Persistence Context Actually Is
The **persistence context** (Hibernate's `Session`, wrapped by JPA's `EntityManager`) is a first-level cache scoped to the current transaction. Every entity loaded through it enters one of three states:

- **Transient** — a plain `new Task(...)`, not yet known to Hibernate.
- **Managed** — loaded via `findById`, `find`, or returned from `save()`. Hibernate is actively tracking it.
- **Detached** — was managed, but its persistence context (transaction) has since closed. Hibernate is no longer watching it.

`taskRepository.findById(id)` inside `@Transactional` hands back a **managed** entity, because it is loaded while the persistence context for this transaction is open.

---

## 2. Dirty Checking: How Changes Get Detected Without `save()`
For every managed entity, Hibernate snapshots its field values at load time. At **flush time** (just before commit, or explicitly via `em.flush()`), Hibernate compares the entity's current field values against that snapshot for every managed entity in the context. Any field that changed generates an `UPDATE` statement for exactly the changed columns (or the whole row, depending on dialect and `@DynamicUpdate` settings).

```
Load:   Task{id=5, title="Draft", status=TODO}   <- snapshot taken here
Mutate: task.update("Final", ..., DONE, ...)      <- plain field assignment, no Hibernate call
Flush:  Hibernate diffs current vs snapshot -> UPDATE tasks SET title='Final', status='DONE' WHERE id=5
```

`Task.update(...)` is a plain setter-style method with no persistence awareness whatsoever — it just assigns fields. The "save" is entirely implicit: it happens because the entity is managed and the transaction commits (which triggers a flush).

**When is `save()` still necessary?**
- **New entities** (`TaskService.create` calls `taskRepository.save(task)`): a transient entity has no row yet, so there is nothing for dirty checking to diff against — you must explicitly persist it once.
- **Detached entities**: if you loaded a `Task` in one transaction, held onto it, and want to re-attach and update it in a different transaction, you need `save()` (a merge) to reattach it.

---

## 3. Why This Matters for Correctness, Not Just Style
Calling `taskRepository.save(task)` after mutating an already-managed entity is **harmless but redundant** — `save()` on an entity whose ID is already set and already managed is a no-op merge; the actual `UPDATE` still comes from dirty checking at flush time either way. The real danger is the opposite mistake: mutating a **detached** entity (for example, one fetched in a `@Transactional(readOnly=true)` method, held in a field, and mutated after the method returns) and assuming it will be saved. It will not — there is no active persistence context tracking it, and no `save()` call means the change is silently lost.

---

## 4. Flush Timing
Flush is not the same moment as commit. By default (`FlushMode.AUTO`), Hibernate flushes:
- Right before commit.
- Right before executing a JPQL/HQL query, if that query might be affected by pending changes still in memory (keeps the query from returning stale data).

This is also the exact mechanism a bulk `@Modifying` JPQL update bypasses — bulk updates go straight to the database and skip dirty-checking/flush entirely, which is why entities already sitting in the persistence context can go stale relative to a bulk update run in the same transaction.

---

## Interview Answer, Compressed
> "Inside a `@Transactional` method, an entity loaded via the repository is *managed* by the persistence context. Hibernate snapshots its state at load time, and at flush — just before commit — it diffs the entity's current fields against that snapshot and issues an `UPDATE` for whatever changed. That's why `TaskService.update` never calls `save()`: mutating the managed entity is enough. `save()` is only required for a brand-new, transient entity, or to reattach a detached one from outside the current persistence context."
