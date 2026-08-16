**@Bean vs @Component**  
**“You own the class vs you don’t”**

This is one of the cleanest ways to decide which annotation to use.

---

### Core Rule of Thumb

| Situation                        | Annotation to use | Reason |
|----------------------------------|-------------------|------|
| **You own the class** (you wrote the source code) | `@Component` (or `@Service`, `@Repository`, `@Controller`) | Spring can scan and instantiate it automatically |
| **You don’t own the class** (third-party library, JDK class, or you want full control) | `@Bean` | You must explicitly tell Spring how to create the instance |

---

### 1. @Component – When You Own the Class

```java
@Service          // or @Component, @Repository, etc.
public class OrderService {
    // your own code
}
```

**How it works**
- You write the class.
- You put a stereotype annotation on it.
- `@ComponentScan` finds it.
- Spring creates the instance using the constructor (preferably) and manages its lifecycle.

**Typical use**
- Virtually all of your application classes:
    - Services
    - Repositories
    - Controllers
    - Utilities, helpers, mappers, etc.

---

### 2. @Bean – When You Don’t Own the Class

```java
@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // any custom setup
        return mapper;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**How it works**
- The class (`ObjectMapper`, `RestTemplate`, `DataSource`, etc.) comes from a third-party library (or the JDK).
- You cannot (or should not) add `@Component` to it.
- You explicitly define a method annotated with `@Bean` inside a `@Configuration` class.
- Spring calls that method and registers the returned object as a bean.

**Common real-world cases**
- Jackson `ObjectMapper`
- `RestTemplate` / `WebClient`
- `DataSource` (when you want full control)
- Security filters, custom `PasswordEncoder`
- Any third-party client (AWS SDK, Stripe client, etc.)
- Classes that need complex or conditional creation logic

---

### Side-by-Side Comparison

| Aspect                      | @Component (and stereotypes)       | @Bean                              |
|-----------------------------|------------------------------------|------------------------------------|
| Who writes the class?       | You                                | Someone else (or you want control) |
| How is the bean discovered? | Component scanning                 | Explicit declaration in `@Configuration` |
| Instantiation control       | Limited (constructor / field / setter) | Full control (you write the creation code) |
| Custom initialization logic | Possible but less clean            | Excellent                          |
| Multiple instances of same type | Harder                           | Easy (different method names)     |
| Typical location            | On the class itself                | Inside a `@Configuration` class    |
| Proxying / AOP              | Works normally                     | Full `@Configuration` class gives CGLIB proxying (method interception) |

---

### Practical Examples

**You own it → @Component**
```java
@Service
public class PricingService {
    public BigDecimal calculatePrice(...) { ... }
}
```

**You don’t own it → @Bean**
```java
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
```

**Hybrid case** (you own a class but still want to use `@Bean`)  
Sometimes people define their own classes with `@Bean` methods when they need very fine-grained control or multiple variants. This is valid but less common.

---

### Important Technical Notes

1. **@Bean methods inside `@Configuration` classes** are special:  
   Spring creates a CGLIB proxy so that calls between `@Bean` methods are intercepted (singleton guarantee).  
   This is called *full* `@Configuration` mode.

2. **@Bean on a plain class** (without `@Configuration`) works in *lite* mode — no inter-bean method interception.

3. You can combine both approaches in the same project freely.  
   Spring just registers all beans into the same ApplicationContext.

---

### Decision Flowchart

```
Do you own the source code of the class?
│
├── Yes → Use @Component / @Service / @Repository / @Controller
│
└── No  → Use @Bean inside a @Configuration class
```

---

**Summary**

- `@Component` family = “Spring, please find my classes and manage them.”
- `@Bean` = “Spring, here’s exactly how I want this object created.”

The simple mental model **“you own the class vs you don’t”** will correctly guide you in the vast majority of real-world situations.