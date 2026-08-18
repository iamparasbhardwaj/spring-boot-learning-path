# Spring Global Error Handling: @RestControllerAdvice & ProblemDetail

## Overview: The Evolution of Exception Handling
Historically, Spring developers had to create custom, proprietary JSON structures to represent API errors (e.g., `{"errorCode": 404, "message": "Not found"}`). This led to wild inconsistencies across different APIs. 

With Spring Framework 6.0 (Spring Boot 3.0), Spring introduced native support for **RFC 7807 (Problem Details for HTTP APIs)**, standardizing how errors are communicated globally using the `ProblemDetail` object, orchestrated through `@RestControllerAdvice` and `@ExceptionHandler`.

---

## 1. The Interception Layer: `@RestControllerAdvice`

When an exception is thrown in a Controller (or deeper in a Service but propagates up), the `DispatcherServlet` catches it. Instead of crashing or returning a generic white-label error page, it delegates the exception to a `HandlerExceptionResolver`.

### How `@RestControllerAdvice` Works
*   **AOP Under the Hood:** It is a specialization of `@Component` that uses Aspect-Oriented Programming (AOP) to wrap around *all* `@RestController` beans in your application context.
*   **The Meta-Annotation:** Just like `@RestController` is `@Controller` + `@ResponseBody`, `@RestControllerAdvice` is simply `@ControllerAdvice` + `@ResponseBody`. This ensures that any object returned by your error handlers is automatically serialized to JSON via `HttpMessageConverter`s (like Jackson).

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Exception handlers go here
}
```

---

## 2. The Target: `@ExceptionHandler`

Inside your advice class, you define methods annotated with `@ExceptionHandler` to tell Spring *which* exceptions you want to intercept.

### The Routing Mechanism
*   When an exception is thrown, the `ExceptionHandlerExceptionResolver` scans your `@RestControllerAdvice` for the most specific `@ExceptionHandler` match.
*   If a `UserNotFoundException` is thrown, and you have handlers for both `UserNotFoundException` and `RuntimeException`, Spring is smart enough to route it to the exact match.

```java
// Intercepts exactly this exception
@ExceptionHandler(UserNotFoundException.class)
public ResponseEntity<Object> handleUserNotFound(UserNotFoundException ex) { ... }
```

---

## 3. The Modern Standard: `ProblemDetail` (RFC 7807)

RFC 7807 dictates a standard JSON structure for HTTP API errors. Spring Boot 3 provides the `ProblemDetail` class, which implements this specification natively.

### The Standard Fields
A standard RFC 7807 response includes:
*   `type`: A URI reference identifying the problem type (defaults to `about:blank`).
*   `title`: A short, human-readable summary of the problem.
*   `status`: The HTTP status code.
*   `detail`: A human-readable explanation specific to this occurrence.
*   `instance`: A URI reference identifying the specific occurrence of the problem (usually the API path).

### Extending `ProblemDetail`
One of the best features of `ProblemDetail` is that it allows dynamic extensions. If you need to return validation errors, timestamps, or trace IDs, you can inject them as custom properties, and Jackson will flatten them into the root JSON object.

---

## 4. Putting It All Together: Best Practice Architecture

Here is a high-depth implementation of a modern, RFC 7807-compliant global error handler. 

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle a specific custom domain exception.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, WebRequest request) {
        
        // 1. Create the base ProblemDetail object
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, 
                ex.getMessage()
        );
        
        // 2. Populate RFC 7807 standard fields
        problemDetail.setType(URI.create("https://api.mycompany.com/errors/user-not-found"));
        problemDetail.setTitle("User Resource Not Found");
        // Spring automatically sets the 'instance' property to the request URI!

        // 3. Add custom non-standard fields (Extensions)
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("custom_error_code", "ERR_USER_001");

        // Because of @RestControllerAdvice, Spring automatically wraps this 
        // in a ResponseEntity with the status code defined in the ProblemDetail.
        return problemDetail;
    }

    /**
     * Fallback for any unhandled exceptions to prevent leaking stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "An unexpected internal error occurred."
        );
        problemDetail.setTitle("Internal Server Error");
        // Log the actual exception here!
        return problemDetail;
    }
}
```

### The Resulting JSON Output
If a client hits an endpoint that throws `UserNotFoundException`, Spring converts the `ProblemDetail` above into this precise, spec-compliant JSON:

```json
{
  "type": "https://api.mycompany.com/errors/user-not-found",
  "title": "User Resource Not Found",
  "status": 404,
  "detail": "User with ID 12345 could not be found.",
  "instance": "/api/users/12345",
  "timestamp": "2026-08-18T14:30:00Z",
  "custom_error_code": "ERR_USER_001"
}
```
