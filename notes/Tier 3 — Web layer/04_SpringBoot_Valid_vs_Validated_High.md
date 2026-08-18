# Spring Boot Bean Validation: @Valid vs @Validated

## Overview: The JSR-380 Standard
Bean Validation in Java is defined by a specification (JSR-380), recently transitioned to Jakarta EE (`jakarta.validation`). The specification defines the annotations (like `@NotNull`, `@Size`, `@Email`), but requires an implementation to actually run the checks. In Spring Boot, **Hibernate Validator** is the default and standard implementation.

While both `@Valid` and `@Validated` trigger this validation engine, they operate at different layers of the framework and offer different capabilities.

---

## 1. `@Valid` (The Java Standard)
`@Valid` is a standard Jakarta annotation (`jakarta.validation.Valid`). 

### Primary Use Case 1: Spring MVC Controllers
When placed on a controller method parameter (typically alongside `@RequestBody` or `@ModelAttribute`), it tells Spring's `WebDataBinder` to validate the deserialized object *before* invoking your controller method.

*   **Under the Hood:** Handled by the `RequestResponseBodyMethodProcessor`. 
*   **Exception:** If validation fails, it throws a `MethodArgumentNotValidException`. Spring Boot automatically intercepts this and translates it into a 400 Bad Request response.

### Primary Use Case 2: Cascading Validation
If you have a complex POJO with nested objects, validating the top-level object does **not** automatically validate the nested objects. You must use `@Valid` to trigger a cascading check.

```java
public class CreateOrderRequest {
    
    @NotBlank
    private String customerId;

    // Without @Valid here, the Address constraints will be completely ignored!
    @NotNull
    @Valid 
    private Address shippingAddress; 
}

public class Address {
    @NotBlank
    private String street;
}
```

---

## 2. `@Validated` (The Spring Power-Up)
`@Validated` is a Spring-specific annotation (`org.springframework.validation.annotation.Validated`). It was created to overcome two major limitations of the standard `@Valid` annotation: **Validation Groups** and **Method Validation (AOP)**.

### Power-Up 1: Validation Groups
Sometimes you want to apply different validation rules to the exact same class depending on the context. For example, when creating a user, the ID should be null. When updating a user, the ID must be present. 

`@Validated` allows you to pass a "Group" (which is just an empty marker interface).

```java
// 1. Define Marker Interfaces
public interface OnCreate {}
public interface OnUpdate {}

// 2. Assign constraints to groups
public class UserDTO {
    @Null(groups = OnCreate.class)
    @NotNull(groups = OnUpdate.class)
    private Long id;

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    private String name;
}

// 3. Trigger specific groups in the Controller
@RestController
public class UserController {

    @PostMapping("/users")
    public void createUser(@Validated(OnCreate.class) @RequestBody UserDTO user) { ... }

    @PutMapping("/users")
    public void updateUser(@Validated(OnUpdate.class) @RequestBody UserDTO user) { ... }
}
```

### Power-Up 2: AOP Method Validation (Crucial Architecture)
By standard design, validation only works automatically on Spring MVC `@RequestBody` objects. But what if you want to validate a single `@RequestParam`, an `@PathVariable`, or the arguments of a standard `@Service` class method?

Placing `@Valid` on a standard method argument does **nothing** on its own. 

To fix this, you must place `@Validated` at the **Class Level**.
*   **Under the Hood:** When Spring's `MethodValidationPostProcessor` sees a class annotated with `@Validated`, it wraps that bean in a **CGLIB/JDK Proxy**. 
*   **The Interceptor:** Every method call is now intercepted by a `MethodValidationInterceptor`. This interceptor uses Java Reflection to read the method arguments, passes them to the Hibernate Validator, and only proceeds if they pass.
*   **Exception:** If it fails, it throws a `ConstraintViolationException` (unlike MVC's `MethodArgumentNotValidException`).

```java
@Service
@Validated // 1. CLASS LEVEL ANNOTATION CREATES AN AOP PROXY
public class UserService {

    // 2. Now standard method arguments can be validated!
    public void updateEmail(@NotNull Long userId, @Email String newEmail) {
        // ...
    }
}
```

---

## 3. The Controller Trap: `@RequestParam` Validation
A classic pitfall involves validating path variables or query parameters in a controller. 

```java
@RestController
// TRAP: Without @Validated here, the @Email annotation below does nothing!
@Validated 
public class SearchController {

    @GetMapping("/search")
    public List<User> searchByEmail(@RequestParam @Email String email) {
        // ...
    }
}
```
**Why does this happen?** 
Because `@RequestParam` resolves to a primitive/String, not a complex bean mapped by `WebDataBinder`. Therefore, it bypasses standard MVC validation. To validate it, Spring must fall back to the AOP proxying mechanism, which requires the class to be annotated with `@Validated`.
