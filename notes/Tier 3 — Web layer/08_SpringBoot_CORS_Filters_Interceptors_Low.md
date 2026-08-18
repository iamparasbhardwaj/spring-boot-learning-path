# Spring Boot Web Security: CORS & Request Interception

## 1. What is CORS? (Cross-Origin Resource Sharing)
CORS is a security feature built into **web browsers**, not your server. 

By default, a browser will not let a frontend web app running on `http://localhost:3000` (like a React app) make an API call to your Spring Boot server running on `http://localhost:8080`. The browser blocks it because the domains (origins) are different. This prevents malicious websites from silently making requests to your bank API while you are logged in.

### How to Fix it in Spring Boot
You must explicitly tell the browser, "It is okay, I trust this origin."

**The Quick Fix (Per Controller):**
```java
@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class MyController { ... }
```

**The Global Fix (For the whole app):**
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("http://localhost:3000");
    }
}
```

---

## 2. Filters vs. Interceptors: The Mental Model
Both Filters and Interceptors do essentially the same thing: they intercept an incoming HTTP request before it reaches your controller, or intercept the response before it goes back to the client. The difference is **where** they live.

Think of your application as a castle. 

### Filters (The Outer Moat)
*   **Where they live:** Inside the Web Server (Tomcat), completely outside of the Spring MVC framework.
*   **What they see:** Only raw HTTP data (`HttpServletRequest` and `HttpServletResponse`). They know nothing about your Spring Controllers or Java methods.
*   **Best Used For:** Global, low-level tasks.
    *   Handling CORS checks.
    *   Reading raw request payloads for security logging.
    *   Global authentication (like Spring Security's filter chain).

### Interceptors (The Throne Room Guards)
*   **Where they live:** Deep inside Spring MVC, managed by the `DispatcherServlet`.
*   **What they see:** The actual Spring context. They know exactly which Java `@RestController` method is about to be executed (the `HandlerMethod`).
*   **Best Used For:** Application-specific business logic.
    *   Checking if a user has a specific role required for a specific controller method.
    *   Adding specific metadata to a model before returning it to a view.
