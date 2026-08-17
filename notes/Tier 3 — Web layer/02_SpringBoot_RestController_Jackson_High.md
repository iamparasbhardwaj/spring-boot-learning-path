# @RestController vs @Controller & The Magic of Jackson

## 1. `@Controller` (Traditional Web MVC)
Historically, Spring Web was designed to serve HTML pages (Server-Side Rendering). The `@Controller` annotation was built for this purpose.

*   **Primary Goal:** To return a **View** (an HTML template, JSP, or Thymeleaf file).
*   **How it works:** When a method inside a `@Controller` returns a `String`, Spring assumes this string is the *name of a view template*. It passes the string to a `ViewResolver`, which locates the actual HTML file, injects any model data, and returns the rendered HTML to the browser.

```java
@Controller
public class WebController {

    @GetMapping("/greeting")
    public String greeting(Model model) {
        model.addAttribute("name", "Alice");
        // Spring looks for a template named "greeting.html" in src/main/resources/templates
        return "greeting"; 
    }
}
```

## 2. `@RestController` (Modern REST APIs)
As Single Page Applications (React, Angular) and mobile apps became the standard, servers stopped sending HTML and started sending raw data (usually JSON). Spring introduced `@RestController` to streamline this.

*   **Primary Goal:** To return **Data** directly to the caller, bypassing the view resolution entirely.
*   **Under the Hood:** `@RestController` is simply a meta-annotation that combines `@Controller` and `@ResponseBody`.

```java
// These two are functionally identical:

@Controller
@ResponseBody
public class LegacyApiController { ... }

@RestController
public class ModernApiController {
    
    @GetMapping("/api/user")
    public User getUser() {
        // Returns the actual object, which Spring converts to JSON automatically
        return new User("Alice", "alice@example.com"); 
    }
}
```

---

## 3. `HttpMessageConverter`: The Bridge to the Network
When your `@RestController` method returns a `User` object, how does that Java object turn into a string of JSON over the network? This is the job of the `HttpMessageConverter` interface.

### Content Negotiation
Spring Boot has a list of configured message converters. When your method returns, Spring performs **Content Negotiation** to figure out which converter to use:
1.  It looks at the HTTP request's `Accept` header (e.g., `Accept: application/json` or `Accept: application/xml`).
2.  It looks at the return type of your Java method.
3.  It scans its list of converters to find one that can translate your Java object into the requested format.

If the client asks for JSON, Spring selects the `MappingJackson2HttpMessageConverter`.

---

## 4. Jackson: The Default JSON Engine
Jackson is a highly optimized Java library for processing JSON. Spring Boot includes it by default and automatically configures a central Jackson `ObjectMapper` bean for you.

### How Jackson Works
*   **Serialization (Java ➡️ JSON):** The `ObjectMapper` takes your returned object, uses Java Reflection to read its public getters (or fields, depending on config), and writes them out as a JSON string.
*   **Deserialization (JSON ➡️ Java):** When a request comes in with `@RequestBody`, the `ObjectMapper` parses the incoming JSON string, calls the no-arguments constructor of your target class, and uses setters or reflection to populate the fields.

### Essential Jackson Annotations
You can control exactly how Jackson maps your objects using annotations on your POJO:

*   **`@JsonIgnore`:** Prevents a field from being serialized (crucial for passwords or internal IDs).
*   **`@JsonProperty("custom_name")`:** Maps a Java field to a completely different JSON key.
*   **`@JsonInclude(JsonInclude.Include.NON_NULL)`:** Tells Jackson to omit this field from the JSON output entirely if its value is null, saving bandwidth.
*   **`@JsonFormat(pattern = "yyyy-MM-dd")`:** Formats Dates or DateTimes into specific string patterns rather than timestamps.

```java
public class UserDTO {
    
    private Long id;

    @JsonProperty("first_name") // Will appear as "first_name" in JSON
    private String firstName;

    @JsonIgnore // Will never be sent to the client
    private String passwordHash;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String optionalBiography; // Won't appear in JSON if null
    
    // Getters and Setters...
}
```

### Customizing the `ObjectMapper`
If you need to change Jackson's behavior globally (like enforcing snake_case for all JSON keys, or failing when unknown properties are sent), you don't need to write a custom converter. You can just configure the auto-configured `ObjectMapper` via `application.yml`:

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
    deserialization:
      fail-on-unknown-properties: true
    default-property-inclusion: non_null
```