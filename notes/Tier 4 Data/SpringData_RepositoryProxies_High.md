# Spring Data Repository Proxies: Under the Hood

## The Magic Trick
In standard Java, you cannot instantiate an interface. Yet, in Spring Boot, you write an interface extending `JpaRepository`, inject it into your service, and call `.save()` or `.findByEmail()`. 

**Who wrote the implementation?** 
No one. The implementation does not exist as a compiled `.class` file on your disk. Spring creates a **JDK Dynamic Proxy** at runtime to back your interface.

---

## 1. The Startup Phase: Scanning & Registration

When a Spring Boot application starts, the auto-configuration (or `@EnableJpaRepositories`) triggers a scanning process:

1.  **The Registrar:** The `JpaRepositoriesRegistrar` scans your packages for any interfaces extending `Repository` (or `CrudRepository`, `JpaRepository`, etc.).
2.  **The Factory Bean:** For every interface it finds, Spring does *not* register a standard bean. Instead, it registers a `JpaRepositoryFactoryBean`. 
3.  **The Factory:** When the application context actually needs to inject your repository into a service, the `FactoryBean` delegates to a `JpaRepositoryFactory` to generate the proxy object.

---

## 2. The Runtime Phase: Creating the Proxy

Spring uses Java's built-in **JDK Dynamic Proxies** (not CGLIB, because repositories are interfaces, and JDK proxies are designed specifically for interfaces).

When the `JpaRepositoryFactory` creates the proxy, it essentially creates a hollow object that implements your custom interface. However, a proxy is useless without an **Invocation Handler**—a centralized router that intercepts every method call made on the proxy and decides what to do with it.

Spring attaches multiple interceptors (Advice) to this proxy. The two most critical are:
1.  **The Target Object (Standard CRUD)**
2.  **The Query Interceptor (Custom Queries)**

---

## 3. Handling Standard Methods (`save`, `findById`)

If you call a standard method that is defined in `CrudRepository` or `JpaRepository`, the proxy needs a real Java object to delegate the work to.

*   **The Backing Class:** Spring instantiates a single instance of `SimpleJpaRepository`. 
*   **The Delegation:** When you call `userRepository.save(user)`, the proxy intercepts the call, sees that `save` is a standard JPA method, and delegates the execution directly to the underlying `SimpleJpaRepository` instance. 

*If you ever want to see the actual source code executing your standard CRUD operations, look up the `SimpleJpaRepository` class in the Spring Data JPA source code. It relies on the standard JPA `EntityManager`.*

---

## 4. Handling Derived Methods (`findByEmailAndStatus`)

What happens when you call a custom method like `findByEmailAndStatus`? This method does not exist in `SimpleJpaRepository`. 

This is where the magic of the **`QueryExecutorMethodInterceptor`** comes in.

1.  **Interception:** You call `findByEmail(email)`. The proxy intercepts the call.
2.  **Routing:** It sees this is not a standard method, so it routes it to the `QueryExecutorMethodInterceptor`.
3.  **Query Resolution (`QueryLookupStrategy`):**
    *   The interceptor checks if you attached an `@Query` annotation (JPQL or native SQL). If so, it uses that (`USE_DECLARED_QUERY`).
    *   If there is no annotation, it uses the `PartTree` parser (`CREATE`). It physically parses your method name (`findBy` -> `Email` -> `And` -> `Status`), creates an Abstract Syntax Tree (AST), and dynamically generates a JPA Criteria query or JPQL string.
4.  **Execution:** The interceptor takes the generated query, binds the arguments you passed into the method, executes it via the JPA `EntityManager`, and maps the result back to your return type.

*Note: The parsing of method names into queries is computationally expensive. Spring Data parses these names **once** at application startup, caches the resulting generated queries, and reuses them for every subsequent method call.*

---

## Summary Architecture (Mental Model)

When you invoke `userRepository.findByAge(25)`:

1.  **Service** calls method on the injected **JDK Dynamic Proxy**.
2.  The **Proxy** intercepts the call.
3.  The Proxy passes the method metadata to its internal **Interceptors**.
4.  Because it's a derived query, it hits the **`QueryExecutorMethodInterceptor`**.
5.  The Interceptor retrieves the pre-compiled JPA query from the cache (parsed via **`PartTree`**).
6.  The Interceptor executes the query against the database using the internal **`EntityManager`**.
7.  Results are returned to the **Service**.
