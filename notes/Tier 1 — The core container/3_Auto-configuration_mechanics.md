**Auto-configuration Mechanics**  
**AutoConfiguration.imports + @ConditionalOnClass + @ConditionalOnMissingBean**

This is the real engine behind Spring Boot’s “magic”.  
When you add a starter, Spring Boot automatically configures dozens of beans — but only when it makes sense. Here’s exactly how that decision process works.

---

### 1. How Spring Boot Discovers Auto-Configuration Classes
**`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**

#### The modern discovery mechanism (Spring Boot 2.7+)

In every Spring Boot jar (especially `spring-boot-autoconfigure`), you will find a file:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

This is a simple text file. Each line contains the fully qualified name of an auto-configuration class:

```
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
... (100+ more entries)
```

**Older mechanism (before Spring Boot 2.7)**  
It used `META-INF/spring.factories` with the key  
`org.springframework.boot.autoconfigure.EnableAutoConfiguration`.

#### What happens at startup
1. `@EnableAutoConfiguration` (inside `@SpringBootApplication`) triggers the process.
2. Spring Boot reads **all** `AutoConfiguration.imports` files from every jar on the classpath.
3. It loads the listed classes.
4. It evaluates the conditions on each class (and on individual `@Bean` methods).
5. Only the configurations whose conditions match are applied.

This file is the **entry point** — without it, auto-configuration would not know which classes to even consider.

---

### 2. @ConditionalOnClass

```java
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
```

#### Meaning
“Only apply this configuration **if** the given classes are present on the classpath.”

#### Why it exists
Spring Boot should not try to configure a `DataSource` if the project doesn’t even have the JDBC API on the classpath.  
It should not configure MongoDB if `MongoClient` is missing, etc.

#### Real example from Spring Boot source
```java
@AutoConfiguration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@EnableConfigurationProperties(DataSourceProperties.class)
@Import({ DataSourcePoolMetadataProvidersConfiguration.class, ... })
public class DataSourceAutoConfiguration {
    // ...
}
```

If you don’t have `spring-boot-starter-data-jpa` or any JDBC driver, this entire class is skipped.

You will also see the opposite:
```java
@ConditionalOnClass(name = "com.mongodb.client.MongoClient")  // using string to avoid hard dependency
```

---

### 3. @ConditionalOnMissingBean

```java
@ConditionalOnMissingBean
```

#### Meaning
“Only create this bean **if** the user has not already defined a bean of the same type (or name).”

#### Why it is critical
This is what makes Spring Boot **non-invasive**.  
You can always override any auto-configured bean just by declaring your own.

#### Real example
```java
@Bean
@ConditionalOnMissingBean(DataSource.class)
@ConfigurationProperties(prefix = "spring.datasource")
public DataSource dataSource(DataSourceProperties properties) {
    // create and return a DataSource
}
```

- If you **don’t** declare your own `DataSource` → Spring Boot creates one.
- If you **do** declare your own `@Bean DataSource` → Spring Boot politely steps aside.

You can be more specific:
```java
@ConditionalOnMissingBean(name = "myCustomDataSource")
@ConditionalOnMissingBean(value = DataSource.class, ignoredType = "javax.sql.DataSource")
```

---

### How the Three Work Together (Complete Flow)

Let’s take a concrete example: adding `spring-boot-starter-data-jpa` + H2.

1. **Discovery**  
   Spring Boot reads `AutoConfiguration.imports` and finds  
   `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration`.

2. **Class-level conditions** (`@ConditionalOnClass`)
    - Is `DataSource.class` on the classpath? → Yes
    - Is `EntityManager.class` on the classpath? → Yes  
      → Both auto-configuration classes are eligible.

3. **Bean-level conditions** (`@ConditionalOnMissingBean`)  
   Inside `DataSourceAutoConfiguration`:
   ```java
   @Bean
   @ConditionalOnMissingBean
   public DataSource dataSource(...) { ... }
   ```
    - Have you already defined a `DataSource` bean? → No  
      → Spring Boot creates an embedded H2 DataSource.

4. Further conditions continue evaluating (e.g., `@ConditionalOnProperty`, `@ConditionalOnSingleCandidate`, etc.).

Result: You get a fully working `DataSource`, `EntityManagerFactory`, and transaction manager with zero configuration.

---

### Other Important Conditional Annotations (for context)

| Annotation                        | Meaning |
|-----------------------------------|-------|
| `@ConditionalOnProperty`          | Only if a property is set (or has a specific value) |
| `@ConditionalOnWebApplication`    | Only in a web application |
| `@ConditionalOnNotWebApplication` | Only in a non-web application |
| `@ConditionalOnSingleCandidate`   | Only if there is exactly one bean of that type |
| `@ConditionalOnBean`              | Only if a certain bean already exists |
| `@ConditionalOnExpression`        | SpEL expression evaluates to true |

---

### Key Takeaways

- **`AutoConfiguration.imports`** → “Which configuration classes should I even consider?”
- **`@ConditionalOnClass`** → “Is the required technology on the classpath?”
- **`@ConditionalOnMissingBean`** → “Has the user already provided their own version?”

Together they implement Spring Boot’s core philosophy:

> **Auto-configure as much as possible, but never get in the user’s way.**

You can always override any auto-configured bean, exclude entire auto-configurations, or write your own auto-configuration classes that follow the exact same pattern.