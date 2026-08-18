# `@Transactional`: Proxy-Based AOP, Self-Invocation, and Rollback Rules

## Overview
`@Transactional` looks like a declarative annotation but there is no magic reflection happening inside `TaskService` itself. Spring wraps your bean in a **proxy**, and the proxy is what actually manages the transaction. Understanding that one fact explains every "gotcha" people hit with this annotation.

---

## 1. How the Proxy Is Built
At startup, `AnnotationAwareAspectJAutoProxyCreator` (a `BeanPostProcessor`) inspects every bean. If it finds `@Transactional` — at class level (`TaskService` uses this) or method level — it replaces the bean in the `ApplicationContext` with a proxy:

- **JDK dynamic proxy** if the bean implements at least one interface.
- **CGLIB subclass proxy** otherwise (the default in Spring Boot; `TaskService` is a concrete class with no interface, so this is what gets generated for it).

Whatever `TaskController` gets injected as `TaskService` is **not** the raw `TaskService` instance — it is the proxy.

```java
@Service
@Transactional(readOnly = true)   // class-level default
public class TaskService {
    @Transactional
    public TaskResponse create(CreateTaskRequest request) { ... }
}
```

Call sequence for `taskService.create(...)` from the controller:
1. Controller calls a method on the **proxy**.
2. Proxy's interceptor (`TransactionInterceptor`) sees `@Transactional`, asks the `PlatformTransactionManager` to begin a transaction, binds a `Connection` to the current thread (`TransactionSynchronizationManager`).
3. Proxy invokes the **real** `TaskService.create(...)`.
4. On normal return: proxy commits. On a matching exception: proxy rolls back.

---

## 2. The Classic Trap: Self-Invocation
Because the interception only happens **through the proxy**, calling a `@Transactional` method **from another method on the same object** (`this.someOtherMethod()`) bypasses the proxy entirely — it's a plain Java method call on `this`, not a call routed through Spring's interceptor.

```java
@Service
public class TaskService {
    public void doWork() {
        this.create(request);   // BYPASSES the proxy - @Transactional on create() is silently ignored
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) { ... }
}
```

**Fixes**, in order of how commonly they're used:
- Inject `TaskService` into itself (via `@Lazy` to avoid a circular-dependency error) and call through that self-reference — forces the call through the proxy.
- Split the transactional method into a separate collaborator bean and call it from there.
- `AopContext.currentProxy()` with `exposeProxy=true` — works, but signals the design should probably be restructured instead.

The same underlying rule silently defeats `@Transactional` on:
- **`private` methods** — CGLIB can't override a private method to intercept it.
- **`final` methods or classes** — can't be subclassed, so CGLIB has nothing to proxy.
- **Static methods** — proxies only intercept instance method calls.

---

## 3. Rollback Rules
By default, Spring's transaction interceptor rolls back on **unchecked exceptions** (`RuntimeException` and `Error`) and commits through **checked exceptions**. This trips people coming from a "any exception should roll back" mental model.

```java
@Transactional
public void riskyMethod() throws IOException {
    // throws a checked IOException -> transaction STILL COMMITS by default
}
```

Override the default explicitly when you need to:
```java
@Transactional(rollbackFor = IOException.class)
@Transactional(noRollbackFor = SpecificRuntimeException.class)
```

`TaskService.create` relies on the default: `IllegalArgumentException` (unknown project) and `IllegalStateException` (project full) are both unchecked, so a failed `create()` call correctly rolls back — no partially-created `Task` survives.

Another sharp edge: an exception caught and swallowed **inside** the transactional method never reaches the interceptor, so nothing rolls back even though a `RuntimeException` was technically thrown internally. If you catch-and-log, the transaction has no way to know something went wrong unless you rethrow or call `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.

---

## 4. `readOnly = true` — What It Actually Does
`TaskService` sets `@Transactional(readOnly = true)` at class level and overrides it per write method. `readOnly` is a **hint**, not an enforced restriction at the Spring level:
- Hibernate uses it to skip dirty-checking overhead for entities loaded in that transaction (no need to track changes you're never going to flush).
- Some JDBC drivers use it to route to a read replica or optimize the connection.
- It does **not** stop you from calling `save()` inside a read-only transaction at the Java level — some databases will reject the write, others won't.

This is why `TaskService.create`, `.update`, and `.delete` each carry their own `@Transactional` (no `readOnly`) — the more specific method-level annotation wins over the class-level default.

---

## Interview Answer, Compressed
> "`@Transactional` works via a CGLIB or JDK dynamic proxy that Spring wraps around the bean. The proxy opens a transaction, invokes the real method, and commits or rolls back afterward. Because interception happens at the proxy boundary, a self-invocation — calling `this.otherMethod()` from inside the same bean — never goes through the proxy, so the annotation is silently ignored. Same reason it does nothing on `private` or `final` methods. Rollback defaults to unchecked exceptions only; checked exceptions commit unless you set `rollbackFor` explicitly."
