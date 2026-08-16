**@Component / @Service / @Repository / @Controller**  
**and what @Repository uniquely adds (exception translation)**

These four annotations are collectively called **Spring stereotype annotations**.  
They all serve the same fundamental purpose — marking a class as a Spring-managed bean so it can be detected by `@ComponentScan` — but they carry different **semantic meaning** and, in one case, extra technical behavior.

---

### 1. The Foundation: @Component

```java
@Component
public class MyHelper {
    // generic Spring-managed bean
}
```

- The most generic stereotype.
- Tells Spring: “This is a candidate for component scanning.”
- All other stereotypes are **specializations** of `@Component` (they are meta-annotated with it).

Use `@Component` when the class doesn’t clearly fall into the service, persistence, or web layer.

---

### 2. @Service

```java
@Service
public class OrderService {
    // business logic
}
```

- Specialization of `@Component`.
- Indicates that the class holds **business logic** or **use-case** logic.
- Purely semantic — at runtime it behaves exactly like `@Component`.
- Helps developers and tools understand the role of the class.

---

### 3. @Controller / @RestController

```java
@Controller
public class OrderController {
    // returns views (traditional Spring MVC)
}

@RestController   // = @Controller + @ResponseBody
public class OrderRestController {
    // returns JSON/XML directly
}
```

- Specialization of `@Component`.
- Marks the class as a **web controller** (presentation layer).
- `@RestController` is itself a composed annotation: `@Controller` + `@ResponseBody`.
- Enables request mapping annotations (`@GetMapping`, `@PostMapping`, etc.) to work properly.

---

### 4. @Repository

```java
@Repository
public class OrderRepository {
    // data access logic
}
```

- Specialization of `@Component`.
- Indicates that the class is a **Data Access Object (DAO)** or **repository** (persistence layer).
- Like `@Service`, it is largely semantic…
- **But it adds one unique technical feature: Exception Translation.**

---

### What @Repository Uniquely Adds: Exception Translation

This is the key difference.

When you annotate a class with `@Repository`, Spring automatically registers a post-processor called:

```
PersistenceExceptionTranslationPostProcessor
```

This post-processor creates a proxy around your repository and translates **technology-specific exceptions** into Spring’s unified **DataAccessException** hierarchy.

#### Concrete example

Without `@Repository` (or without exception translation):

```java
// Using plain JPA / Hibernate
public void save(Order order) {
    entityManager.persist(order);   // might throw javax.persistence.PersistenceException
                                    // or org.hibernate.HibernateException
}
```

These checked/unchecked exceptions force you to either handle them or declare them, and they tightly couple your code to the persistence technology.

With `@Repository`:

```java
@Repository
public class JpaOrderRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public void save(Order order) {
        entityManager.persist(order);  // any PersistenceException is automatically translated
    }
}
```

Now, low-level exceptions such as:
- `PersistenceException`
- `SQLException`
- `HibernateException`
- ConstraintViolationException
- etc.

are translated into Spring’s hierarchy:

- `DataAccessException` (root)
    - `DataIntegrityViolationException`
    - `DuplicateKeyException`
    - `EmptyResultDataAccessException`
    - `CannotAcquireLockException`
    - …

**Benefits of exception translation**
- Your service layer only needs to deal with one consistent exception hierarchy.
- You are no longer tied to JPA, JDBC, Hibernate, MyBatis, etc.
- You can switch persistence technologies with far less impact on the rest of the application.
- Unchecked exceptions → no forced try-catch or throws declarations.

---

### Comparison Table

| Annotation       | Layer              | Semantic Meaning              | Extra Technical Behavior              | Common Usage |
|------------------|--------------------|-------------------------------|---------------------------------------|--------------|
| `@Component`     | Any                | Generic Spring bean           | None                                  | Utilities, helpers |
| `@Service`       | Business           | Business logic / use cases    | None                                  | Service classes |
| `@Repository`    | Persistence        | Data access / DAO             | **Exception translation**             | Repositories, DAOs |
| `@Controller`    | Presentation       | Web controller                | Enables MVC request handling          | Traditional MVC |
| `@RestController`| Presentation       | REST controller               | `@Controller` + `@ResponseBody`       | REST APIs |

---

### Best Practices

1. Prefer the most specific stereotype available (`@Service`, `@Repository`, `@RestController`) over plain `@Component`.
2. Always use `@Repository` on classes that talk to the database — even if you use Spring Data JPA (where the interface is already annotated).
3. Keep the classic layered architecture clear:
    - Controller → Service → Repository
4. Exception translation works for JPA, JDBC, Hibernate, and many other technologies supported by Spring.

---

**Summary**

- `@Component`, `@Service`, `@Repository`, and `@Controller` are all component-scanning markers.
- The first three differ mainly in **intent and readability**.
- `@Repository` is the only one that adds real technical behavior: **automatic translation of persistence exceptions into Spring’s DataAccessException hierarchy**.

That exception translation is the reason `@Repository` still matters even in the age of Spring Data JPA.