# Spring Boot Pagination and Sorting with `Pageable`

## What is `Pageable`?
When dealing with large datasets, fetching thousands of records at once will crash your database or run your server out of memory. Spring Data provides the `Pageable` interface to handle pagination (fetching a specific "chunk" of data) and sorting in a clean, standardized way across both the Web layer and the Data layer.

---

## 1. The Web Layer: Accepting Pagination Requests
Spring Web automatically resolves pagination parameters from the HTTP request URL and converts them into a `Pageable` object.

### The Controller Code
You simply inject `Pageable` into your controller method. Spring's `PageableHandlerMethodArgumentResolver` does all the heavy lifting.

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/users")
    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
}
```

### How the Client Calls It
The client controls the pagination and sorting using query parameters:

| Parameter | Example | Description |
| :--- | :--- | :--- |
| `page` | `?page=0` | The page number to fetch (Zero-indexed by default). |
| `size` | `?size=20` | The number of records per page (Defaults to 20). |
| `sort` | `?sort=lastName,asc` | The field to sort by, and the direction (`asc` or `desc`). |

**Complex Example:** `GET /api/users?page=1&size=50&sort=lastName,asc&sort=email,desc`
*(Fetches the second page of 50 users, sorted by last name ascending, then by email descending).*

---

## 2. The Data Layer: Querying the Database
Passing the `Pageable` object to a Spring Data JPA Repository automatically translates it into the correct SQL `LIMIT`, `OFFSET`, and `ORDER BY` clauses for your specific database (e.g., PostgreSQL, MySQL).

### Standard Repository Support
If your repository extends `JpaRepository` or `PagingAndSortingRepository`, the `findAll(Pageable pageable)` method is already built-in.

### Custom Query Support
You can add `Pageable` to your own custom derived queries or `@Query` methods. Spring Data will automatically append the pagination/sorting SQL.

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Derived Query with pagination
    Page<User> findByStatus(String status, Pageable pageable);

    // Custom JPQL with pagination
    @Query("SELECT u FROM User u WHERE u.age > :minAge")
    Page<User> findAdults(int minAge, Pageable pageable);
}
```

---

## 3. The Return Types: `Page<T>` vs `Slice<T>`

When you pass a `Pageable` to the database, you have a choice of what type of object you want returned. This choice has massive performance implications.

### `Page<T>` (The Expensive Standard)
*   **What it returns:** The data chunk, plus exact metadata: `totalElements` and `totalPages`.
*   **How it works:** Spring Data fires **two** SQL queries. One query to fetch the limited data, and a second `SELECT COUNT(*)` query to calculate how many total records exist in the entire table.
*   **Best for:** Standard web data tables where you need to show exact page numbers (e.g., "Page 1 of 50").

### `Slice<T>` (The High-Performance Alternative)
*   **What it returns:** The data chunk, plus a simple `hasNext()` boolean. It does not know the total number of pages.
*   **How it works:** Spring Data fires **one** SQL query. It asks the database for `size + 1` records. If it gets that extra record, it knows there is a next page.
*   **Best for:** Infinite scrolling (like social media feeds) or massive tables where running a `COUNT(*)` query is too slow.

```java
// Example of using Slice instead of Page for better performance
Slice<User> findByStatus(String status, Pageable pageable);
```

---

## 4. Default Pagination Settings
You can override Spring Boot's default pagination behavior globally in your `application.yml` file:

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 10      # Change default from 20 to 10
        max-page-size: 100         # Prevent clients from requesting 1,000,000 records
        one-indexed-parameters: true # Make page=1 the first page instead of page=0
```
