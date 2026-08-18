# Advanced REST Semantics in Spring Boot

## Overview
REST (Representational State Transfer) is not a protocol; it is an architectural style. True RESTful APIs use HTTP as an **application protocol**, leveraging its native verbs, headers, and status codes to convey intent and state, rather than just treating it as a transport tunnel for JSON payloads.

---

## 1. Safety and Idempotency

Understanding safety and idempotency is the foundation of distributed systems design. These concepts dictate how clients (like browsers or microservices) can safely retry failed network requests.

### Safe Methods
A method is **safe** if it is strictly read-only. It does not alter the state of the server. 
*   **Methods:** `GET`, `HEAD`, `OPTIONS`.
*   **Benefit:** Clients can cache these aggressively and retry them infinitely without fear of causing unintended side effects.

### Idempotent Methods
A method is **idempotent** if the *intended effect on the server* of multiple identical requests is the same as the effect of a single request.
*   **Methods:** `GET`, `HEAD`, `OPTIONS`, `PUT`, `DELETE`.
*   **The `DELETE` Paradox:** If you send `DELETE /users/5`, the first response is usually `200 OK` or `204 No Content`. If you retry it, the response might be `404 Not Found`. Is it still idempotent? **Yes.** Idempotency refers to the *state of the server*, not the status code returned. After both the first and second call, the server state is identical: User 5 does not exist.

### Non-Idempotent Methods
*   **Methods:** `POST`, `PATCH`.
*   **Why POST is not idempotent:** Calling `POST /users` twice with the same payload will create two distinct users (often resulting in a duplicate key constraint or two distinct records).

---

## 2. `PUT` vs `PATCH`: The Update Semantics

The distinction between `PUT` and `PATCH` is one of the most frequently misunderstood concepts in API design.

### `PUT` (Full Replacement)
*   **Semantics:** `PUT` replaces the *entire* target resource with the payload provided. 
*   **Rule:** If a field exists on the server but is omitted from the `PUT` request payload, the server **must** set that field to null or its default value.
*   **Idempotent:** Yes. Replacing a document with the exact same document 100 times leaves the server in the exact same state.

### `PATCH` (Partial Modification)
*   **Semantics:** `PATCH` applies a set of changes to a resource. You only send the fields you want to alter. 
*   **Implementation Standards:**
    *   **JSON Merge Patch (RFC 7396):** The most common approach. You send a partial JSON object `{"email": "new@email.com"}`. The server merges this into the existing record. Setting a value to `null` deletes it.
    *   **JSON Patch (RFC 6902):** A more robust, operation-based approach. You send an array of instructions: `[{"op": "replace", "path": "/email", "value": "new@email.com"}]`.
*   **Idempotent:** No, not inherently. While a simple JSON Merge Patch (e.g., updating an email) *acts* idempotently, a JSON Patch operation like `{"op": "add", "path": "/items/-", "value": "apple"}` (which appends to an array) will add a new item every time it is called.

---

## 3. The `201 Created` and `Location` Header

When a `POST` request successfully creates a new resource, returning `200 OK` is semantically lazy. RFC 7231 dictates that the server should return `201 Created`.

Furthermore, the server should provide the client with the exact URI of the newly created resource using the HTTP `Location` header. 

### Spring Boot Implementation
Spring provides the `ServletUriComponentsBuilder` to dynamically construct this URI based on the current request context, preventing you from hardcoding hostnames or base paths.

```java
@PostMapping("/api/users")
public ResponseEntity<User> createUser(@RequestBody UserDTO userDto) {
    User savedUser = userService.create(userDto);

    // Dynamically build the URI: /api/users/{id}
    URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()          // Gets http://localhost:8080/api/users
            .path("/{id}")                 // Appends /{id}
            .buildAndExpand(savedUser.getId()) // Replaces {id} with the actual ID
            .toUri();

    // Returns 201 Created with the Location header and the saved object in the body
    return ResponseEntity.created(location).body(savedUser);
}
```

---

## 4. Nuanced HTTP Status Codes

Stop relying solely on `200`, `400`, and `500`. High-depth REST APIs use specific codes to give clients actionable intelligence.

### Success (2xx)
*   **`200 OK`:** General success. Used for `GET` or `PUT`.
*   **`201 Created`:** Resource created. Must include `Location` header.
*   **`204 No Content`:** The action succeeded, but there is no payload to return. Highly recommended for `DELETE` operations, or `PUT` operations where returning the modified object is redundant.

### Client Errors (4xx)
*   **`400 Bad Request`:** The request is malformed (e.g., invalid JSON syntax).
*   **`401 Unauthorized`:** The client lacks valid authentication credentials (e.g., missing or expired JWT).
*   **`403 Forbidden`:** The client is authenticated, but does not have *authorization* (permissions/roles) to access this specific resource.
*   **`404 Not Found`:** The URI does not map to a resource.
*   **`409 Conflict`:** The request conflicts with the current state of the server. Commonly used when a `POST` attempts to create a record that violates a unique constraint (e.g., email already exists), or for optimistic locking failures (version mismatch).
*   **`422 Unprocessable Entity`:** (RFC 4918) The JSON is perfectly well-formed, but the *semantic content* is invalid. This is the absolute best status code for **Bean Validation** failures (e.g., an email field contains "not-an-email").

### Server Errors (5xx)
*   **`500 Internal Server Error`:** Unhandled exceptions (database down, NullPointerException).
*   **`502 Bad Gateway` / `504 Gateway Timeout`:** Usually generated by API Gateways or load balancers when your Spring Boot application is unresponsive.
