# Spring Boot Profiles: Architecture & Advanced Configuration

## Overview: The Profile Abstraction
In Spring Boot, a "Profile" is essentially a named logical grouping of configuration properties and bean definitions. At its core, it is a way to conditionally segregate parts of your application context. The active profiles dictate which beans are registered in the `BeanFactory` and which `PropertySource` files are loaded into the `Environment`.

---

## 1. The `@Profile` Annotation: Internal Mechanics

While `@Profile` seems like a standalone feature, it is actually a specialized form of Spring's `@Conditional` architecture.

### How it Works Internally
`@Profile` is meta-annotated with `@Conditional(ProfileCondition.class)`. During the component scanning and auto-configuration phases, the `ConfigurationClassPostProcessor` evaluates these conditions. If the `ProfileCondition` returns `false` (meaning the profile is not active), the bean definition is completely skipped—it never enters the `BeanFactory`.

### Advanced Expression Logic
You are not limited to exact string matches. `@Profile` supports a rich expression language for complex conditional wiring:

```java
// Active if 'dev' OR 'qa' is active
@Profile("dev | qa") 
@Component
public class NonProdCache {}

// Active only if 'prod' is active AND 'fallback' is NOT active
@Profile("prod & !fallback")
@Bean
public MyService myService() {}
```

---

## 2. Profile Activation & Profile Groups

### Standard Activation
Profiles are activated by modifying the `Environment`. Because property resolution follows a strict precedence (args > env vars > YAML), the standard way to activate a profile in production is via OS environment variables or command-line arguments, overriding whatever is in `application.yml`.

*   **Command Line:** `java -jar app.jar --spring.profiles.active=prod`
*   **Environment Variable:** `SPRING_PROFILES_ACTIVE=prod`

### Spring Boot 2.4+ Profile Groups (Advanced Feature)
Before Spring Boot 2.4, developers used `spring.profiles.include` to chain multiple profiles together. This often led to unpredictable loading orders. Spring Boot 2.4 introduced **Profile Groups**, allowing you to define a single logical profile that expands into multiple granular profiles.

```yaml
# application.yml
spring:
  profiles:
    group:
      # Activating 'prod' will actually activate 'prod-db', 'prod-metrics', and 'prod-security'
      prod: prod-db, prod-metrics, prod-security
      local: h2-db, mock-auth
```
*Architecture Note:* The group expansion happens at the `Environment` level *before* any bean definitions are evaluated, ensuring consistent property resolution.

---

## 3. Per-Profile YAML & Multi-Document Files

Spring Boot allows you to externalize configuration per profile. You can do this using separate files (e.g., `application-dev.yml`) or by using a **Multi-Document YAML** file.

### Multi-Document YAML (`---`)
A single YAML file can be split into multiple logical documents using three dashes (`---`). Each document can be bound to a specific profile using `spring.config.activate.on-profile` (Spring Boot 2.4+ syntax).

```yaml
# DOCUMENT 1: Global Defaults (Always loaded first)
server:
  port: 8080
spring:
  profiles:
    active: dev # Default profile if none is specified externally
    
---
# DOCUMENT 2: Dev Profile
spring:
  config:
    activate:
      on-profile: dev
server:
  port: 8081
logging:
  level:
    root: DEBUG

---
# DOCUMENT 3: Prod Profile
spring:
  config:
    activate:
      on-profile: prod
server:
  port: 443
```

### The Processing Order (Why it Matters)
When Spring's `ConfigDataEnvironmentPostProcessor` parses these documents, it loads the profile-specific documents *after* the default document, adding them to the top of the `MutablePropertySources` list.

This means properties in the `prod` document will **override** properties in the global document if the `prod` profile is active.

### The `spring.profiles.default` Fallback
If absolutely no profiles are activated by the user, Spring falls back to a profile named `default`. You can override this fallback name programmaticly or via properties: `spring.profiles.default=local`. If any explicit profile is active (e.g., `dev`), the `default` profile is ignored entirely.
