# Spring Data JPA: Advanced Querying & Projections

## Overview
While standard CRUD operations are handled automatically by `SimpleJpaRepository`, real-world applications require complex data retrieval. Spring Data JPA provides three primary ways to query the database, each with distinct architectural implications, plus a mechanism (Projections) to optimize data transfer.

---

## 1. Derived Queries (Method Name Parsing)

The most "Spring-like" way to query data. You define a method signature, and Spring builds the SQL.

### Internal Mechanism
At application startup, Spring's `PartTree` algorithm parses the method name (e.g., `findByEmailAndStatus`). It splits the string by keywords (`find`, `By`, `And`, `Or`, `OrderBy`), validates the properties against your Entity metadata, and compiles an Abstract Syntax Tree (AST). This is then translated into a JPA Criteria Query and cached. 

### Advanced Usage & Edge Cases
*   **Property Traversal:** If `User` has an `Address` which has a `ZipCode`, how do you query it? 
    *   *Bad:* `findByAddressZipCode(String zip)` - Spring might try to find a property named `addressZipCode` on `User`.
    *   *Good:* `findByAddress_ZipCode(String zip)` - The underscore explicitly denotes traversal, resolving ambiguity.
*   **Performance Trap:** Derived queries execute `SELECT *`. You cannot select specific columns with them. If your entity has heavy `@Lob` fields or dozens of columns, a derived query will fetch them all.
*   **Startup Cost:** Having hundreds of complex derived queries will noticeably slow down your application startup time because `PartTree` parsing and validation are CPU-intensive.

---

## 2. `@Query` with JPQL (Java Persistence Query Language)

When derived query names become unreadable (e.g., `findByStatusAndAgeGreaterThanAndRegistrationDateBefore`), or you need complex joins, you use `@Query`.

### Internal Mechanism
JPQL does not query tables; it queries **Entities**. Hibernate parses your JPQL string, validates it against your mapped Entity classes, and translates it into the dialect of your underlying SQL database. This keeps your application database-agnostic.

### Crucial Concepts
*   **Named Parameters:** Always use `@Param` with named parameters instead of positional parameters (`?1`) for maintainability.
*   **Bulk Operations (`@Modifying`):** JPQL can execute `UPDATE` and `DELETE`. However, these bypass the standard Hibernate lifecycle.
    *   **The Trap:** If you execute a bulk update, the entities currently sitting in Hibernate's First-Level Cache (L1) are *not* updated. If you try to read them later in the same transaction, you will get stale data.
    *   **The Fix:** Use `@Modifying(clearAutomatically = true)`. This forces Hibernate to clear the L1 cache after the query, ensuring subsequent reads hit the database and get fresh data.

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE User u SET u.status = :status WHERE u.lastLogin < :date")
int deactivateInactiveUsers(@Param("status") String status, @Param("date") LocalDate date);
```

---

## 3. Native Queries

Sometimes JPQL is too limiting. If you need to use database-specific features (e.g., PostgreSQL JSONB operators, Window Functions, or recursive CTEs), you must write raw SQL.

### Internal Mechanism
By setting `nativeQuery = true`, you bypass Hibernate's AST parser entirely. Hibernate simply takes your string and hands it directly to the JDBC driver.

### Advanced Considerations
*   **Portability:** You are now locked into your specific database dialect.
*   **Pagination Complexity:** While Spring Data can automatically paginate JPQL, it struggles to count records for complex native SQL (especially with `GROUP BY` or `DISTINCT`). You often must provide a custom `countQuery` explicitly.

```java
@Query(
    value = "SELECT * FROM users u WHERE u.metadata ->> 'tier' = :tier", 
    countQuery = "SELECT count(*) FROM users u WHERE u.metadata ->> 'tier' = :tier", 
    nativeQuery = true
)
Page<User> findUsersByTierNative(@Param("tier") String tier, Pageable pageable);
```

---

## 4. Projections (Performance Optimization)

**The Problem:** Your `User` table has 30 columns. You only need to populate a dropdown menu with `id` and `username`. Fetching the entire Entity uses excessive RAM, saturates network bandwidth, and adds overhead to the Hibernate persistence context.

**The Solution:** Projections allow you to fetch exactly what you need.

### Approach 1: Interface-Based Projections (Closed vs. Open)
You define an interface containing getter methods for the fields you want. Spring generates a proxy at runtime to back this interface.

*   **Closed Projections (Optimized SQL):** The interface methods exactly match the Entity properties. Spring analyzes this and dynamically modifies the SQL query to `SELECT id, username FROM...`. This is highly performant.
    ```java
    public interface UserDropdownView {
        Long getId();
        String getUsername();
    }
    ```
*   **Open Projections (Unoptimized SQL):** You use the `@Value` annotation with SpEL to compute a value. 
    ```java
    public interface UserFullNameView {
        @Value("#{target.firstName + ' ' + target.lastName}")
        String getFullName();
    }
    ```
    *Trap:* Because Spring doesn't know which specific columns are required to evaluate the SpEL expression at the database level, **it falls back to `SELECT *`**, fetching the entire entity into memory before evaluating the expression. 

### Approach 2: Class-Based (DTO) Projections
Instead of proxies, you use standard POJOs. This is often preferred in modern architectures because they are standard objects, not framework-bound proxies.

*   **JPQL Constructor Expression:**
    ```java
    @Query("SELECT new com.example.dto.UserDTO(u.id, u.username) FROM User u WHERE u.status = :status")
    List<UserDTO> findDtoByStatus(@Param("status") String status);
    ```

### Approach 3: Dynamic Projections
If you want to use the same query logic but return different views depending on the use case, you can use generics.

```java
// The repository method
<T> List<T> findByStatus(String status, Class<T> type);

// Usage in Service:
List<UserDropdownView> dropdowns = repo.findByStatus("ACTIVE", UserDropdownView.class);
List<UserDTO> dtos = repo.findByStatus("ACTIVE", UserDTO.class);
