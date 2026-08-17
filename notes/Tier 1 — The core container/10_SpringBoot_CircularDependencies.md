# Spring Boot Circular Dependencies

## What is a Circular Dependency?
A circular dependency happens when two or more Spring Beans depend on each other to be created. 

Imagine a scenario where:
* **Bean A** needs **Bean B** to function.
* **Bean B** needs **Bean A** to function.

When Spring starts up, it tries to create Bean A, but sees it needs Bean B. So, it pauses to create Bean B, but sees Bean B needs Bean A. This creates an infinite loop—a classic "chicken or the egg" problem.

---

## Why Spring Boot Now "Fails Fast"
Historically, Spring tried to magically resolve these loops using internal caches and setter injection. However, starting in **Spring Boot 2.6**, circular dependencies are **prohibited by default**.

If Spring detects a loop, it will immediately crash the application on startup and throw a `BeanCurrentlyInCreationException`. 

**Why the change?**
* **Bad Architecture:** A circular dependency is almost always a sign of a design flaw. It means your classes are too tightly coupled and lack clear, one-way data flows.
* **Predictability:** Trying to magically resolve these loops sometimes led to unpredictable behavior, null pointers, or partially initialized beans. Failing fast forces developers to fix the underlying architectural issue immediately rather than letting it hide in the shadows.

---

## How to Fix Circular Dependencies

### 1. The Proper Fix: Redesign (Extract to a Third Bean)
The best and most permanent way to fix a loop is to break it. If Bean A and Bean B rely on each other, they are likely sharing a common responsibility that belongs somewhere else. 

**Solution:** Extract that shared logic into a brand new **Bean C**. Then, have both Bean A and Bean B inject Bean C. This changes the flow from a circle (A ↔ B) to a clean hierarchy (A → C ← B).

### 2. The Band-Aid Fix: `@Lazy` Injection
Sometimes, usually in legacy codebases, a major refactor is not immediately possible. In these cases, you can use the `@Lazy` annotation.

By placing `@Lazy` on one of the injected dependencies, you tell Spring: *"Do not fully create this bean right now. Just give me a dummy proxy version, and only create the real bean when I actually call a method on it."*

```java
@Service
public class ServiceA {
    private final ServiceB serviceB;

    // Using @Lazy breaks the initialization loop at startup
    @Autowired
    public ServiceA(@Lazy ServiceB serviceB) {
        this.serviceB = serviceB;
    }
}
```
