# DTOs vs Entities: Why You Never Serialize an Entity

## Overview
It is tempting to return a `@RestController` method's `@Entity` object directly — Jackson will happily turn it into JSON and the endpoint "works" in a demo. In any real codebase this is a mistake on at least four independent axes. This project always converts `Task`/`Project` into a `web/dto` record (see `TaskResponse.from(Task)`) before it reaches the controller.

---

## 1. `LazyInitializationException` at Serialization Time
`Task.project` is `@ManyToOne(fetch = FetchType.LAZY)`. Inside an open transaction, `task.getProject()` returns a Hibernate-generated proxy — a placeholder, not the real row.

If you serialize the entity **after** the persistence context/transaction has closed (which is exactly when the `HttpMessageConverter` runs, in the servlet container, after your `@Transactional` service method has already returned), Jackson calls `getProject().getName()` to fill in the JSON, the proxy tries to lazily fetch from the database, finds no active `Session`, and throws `LazyInitializationException`.

This project sidesteps the whole class of bug by mapping to `TaskResponse` **inside** the `@Transactional` service method, while the session is still open:

```java
// TaskService.findById — mapping happens before the transaction/session closes
public TaskResponse findById(Long id) {
    return taskRepository.findById(id)
            .map(TaskResponse::from)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

Some teams instead reach for `@JsonIgnoreProperties`, `Hibernate5Module`/`Jackson3Module`, or `spring.jackson.serialization.fail-on-empty-beans=false` to paper over this. All are worse than not serializing the entity in the first place.

---

## 2. Schema Coupling
An entity's shape **is** your database schema. If `@RestController` methods return entities directly:
- Renaming a column forces a client-facing JSON field rename in lockstep.
- Adding an internal-only column (an audit flag, an internal FK) immediately becomes public API surface, whether you meant it to or not.
- You lose the ability to evolve persistence and API independently — a classic sign of missing separation of concerns.

A DTO is a deliberate, versioned **contract**. `TaskResponse` currently exposes `projectName` (a derived, flattened value) instead of a nested `Project` object — a shape decision that has nothing to do with how `Task` is mapped in the database and everything to do with what the API consumer needs.

---

## 3. Over-exposure and Security
Entities often carry fields that must never leave the server: password hashes, internal notes, soft-delete flags, other entities' full object graphs via bidirectional relationships. Returning the entity means trusting every future field addition to remember to `@JsonIgnore` it — an easy thing to forget under deadline pressure. A DTO is opt-in by construction: a field only appears in the response if someone explicitly put it in the record.

---

## 4. Bidirectional Relationships and Infinite Recursion
`Project` has `@OneToMany(mappedBy = "project") List<Task> tasks`, and `Task` has `@ManyToOne Project project`. Serialize a `Task` entity directly and Jackson serializes its `project`, which serializes its `tasks`, which serializes each task's `project` again — either infinite recursion (`StackOverflowError`) or, with `@JsonManagedReference`/`@JsonBackReference` band-aids, a fragile ownership dance baked into your domain model purely to satisfy the serializer. DTOs never have this problem because you write the flattening by hand: `TaskResponse.projectName` is a `String`, not a nested object graph.

---

## Where Mapping Should Live
For a project this size, a static factory method on the record (`TaskResponse.from(Task)`) is the right amount of ceremony. On a larger team, the same mapping logic typically moves to:
- A dedicated `TaskMapper` class, or
- **MapStruct**, which generates the mapping code at compile time (no reflection cost, and compile errors instead of runtime `NullPointerException`s when a field is renamed).

Either way, the principle is unchanged: **the entity never crosses the controller boundary.**

---

## Interview Answer, Compressed
> "I never return `@Entity` classes from a controller. Lazy associations throw `LazyInitializationException` once the session closes, the entity's shape is my database schema rather than a deliberate API contract, and bidirectional JPA relationships recurse infinitely under a naive serializer. I map to a response record inside the transactional service method, while the session is still open, and only the record crosses the web boundary."
