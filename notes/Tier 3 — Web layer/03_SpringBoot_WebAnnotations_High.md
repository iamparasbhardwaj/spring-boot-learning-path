# Spring Web MVC: Request Binding & Response Control

## Overview
When a request reaches your `@RestController`, Spring's `HandlerAdapter` must translate the raw HTTP request into Java objects, and eventually translate your Java response back into an HTTP response.

To map specific parts of an HTTP request (URL, query string, or body) to your method parameters, Spring provides specialized annotations. For granular control over the outbound HTTP response, it provides `ResponseEntity`.

---

## 1. `@PathVariable`: Extracting from the URI
Used to extract values directly from the URI path. This is foundational for RESTful design, where resources are identified by their URL (e.g., `/users/123`).

*   **How it works:** You define a placeholder in your `@GetMapping` using curly braces `{}`. Spring's `PathVariableMethodArgumentResolver` extracts the string at that exact position in the URI and converts it to your declared Java type.
*   **Type Conversion:** Spring automatically converts the path segment (which is inherently a String) into Integers, Longs, UUIDs, or Enums using registered `Converter` beans.

```java
@GetMapping("/api/users/{userId}/orders/{orderId}")
public Order getOrder(
        @PathVariable("userId") Long uId, // Binds {userId} to uId
        @PathVariable UUID orderId        // Name matches exactly, no string value needed
) {
    return orderService.find(uId, orderId);
}
```
*Note: If the path variable is missing, Spring throws a `MissingPathVariableException` (resulting in a 500 error, though usually, a missing path variable just means a 404 Not Found because the route itself doesn't match).*

---

## 2. `@RequestParam`: Query Parameters & Form Data
Used to extract data from the URL query string (e.g., `?status=ACTIVE&page=2`) or from standard HTML form submissions (`application/x-www-form-urlencoded`).

*   **How it works:** The `RequestParamMethodArgumentResolver` looks at the `HttpServletRequest.getParameter()` map.
*   **Default Behavior:** By default, parameters are **required**. If the client omits them, Spring throws a `MissingServletRequestParameterException` (resulting in a 400 Bad Request).
*   **Advanced Capabilities:** You can map multiple parameters of the same name into a `List`, or map the entire query string into a `Map` without specifying every key.

```java
@GetMapping("/api/products")
public List<Product> searchProducts(
        @RequestParam String category,                          // Required
        @RequestParam(required = false) String brand,           // Optional (will be null if missing)
        @RequestParam(defaultValue = "1") int page,             // Optional with default
        @RequestParam List<String> tags,                        // Handles ?tags=tech&tags=new
        @RequestParam Map<String, String> allParams             // Captures everything!
) {
    return productService.search(category, brand, page, tags);
}
```

---

## 3. `@RequestBody`: The HTTP Payload
Used to read the raw body of the HTTP request (usually JSON or XML) and deserialize it into a complex Java POJO.

*   **Under the Hood:** Handled by the `RequestResponseBodyMethodProcessor`. It reads the HTTP `InputStream` and passes it to the `HttpMessageConverter` chain (usually Jackson for JSON).
*   **Validation Synergy:** `@RequestBody` is almost always paired with `@Valid` or `@Validated` to trigger JSR-303 Bean Validation immediately after deserialization, before the method even executes.
*   **Limitation:** You can only have **one** `@RequestBody` per method because reading the HTTP request body stream consumes it. You cannot read the same stream twice.

```java
@PostMapping("/api/users")
public User createUser(@Valid @RequestBody UserDTO userDto) {
    // If the JSON is malformed, or @Valid fails, 
    // Spring intercepts and throws an exception before this line is reached.
    return userService.save(userDto);
}
```

---

## 4. `ResponseEntity<T>`: Total Response Control
When you return a POJO from a `@RestController`, Spring automatically serializes it and wraps it in an HTTP 200 OK response. But what if you need to return a 201 Created, a 404 Not Found, or inject custom HTTP headers?

`ResponseEntity` is a wrapper object that gives you full programmatic control over the entire HTTP response (Status Code, Headers, and Body).

*   **Under the Hood:** Processed by the `HttpEntityMethodProcessor`. Because you are explicitly defining the status and headers, Spring bypasses its default status resolution and applies your exact configuration to the `HttpServletResponse`.
*   **The Builder Pattern:** `ResponseEntity` exposes a fluent builder API to construct responses cleanly.

```java
@PostMapping("/api/documents")
public ResponseEntity<Document> uploadDoc(@RequestBody Document doc) {
    Document saved = docService.save(doc);
    
    return ResponseEntity
            .status(HttpStatus.CREATED) // 201 Created
            .header("X-Custom-Tracking-Id", UUID.randomUUID().toString()) // Custom header
            .body(saved); // The JSON payload
}

@GetMapping("/api/users/{id}")
public ResponseEntity<User> getUser(@PathVariable Long id) {
    return userService.findById(id)
            .map(user -> ResponseEntity.ok(user)) // 200 OK with body
            .orElse(ResponseEntity.notFound().build()); // 404 Not Found, empty body
}
```

### When to use `ResponseEntity`?
You do not need to use it for every method. If a method always returns 200 OK, just return the POJO directly. Use `ResponseEntity` when:
1.  The HTTP status code changes dynamically based on business logic (e.g., returning 200 vs 404).
2.  You need to return custom headers (like pagination tokens or cache-control directives).
3.  You are writing `@ExceptionHandler` methods to standardize your error responses.