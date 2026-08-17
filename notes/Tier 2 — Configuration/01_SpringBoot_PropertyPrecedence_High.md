# Spring Boot Property Source Precedence & Internal Architecture

## The Core Concept: The `Environment` Abstraction
In Spring Boot, properties do not just come from a single `application.yml` file. Spring abstracts all configuration data through the `Environment` interface, which delegates to a `MutablePropertySources` collection.

Internally, this collection is essentially a concurrent, ordered linked list of `PropertySource` objects. When you request a property (e.g., using `@Value("${app.timeout}")` or `@ConfigurationProperties`), Spring iterates through this list from **highest precedence to lowest precedence**. The first `PropertySource` that contains the key "wins", and the search stops immediately.

---

## The Strict Precedence Order
Spring Boot establishes a very specific order for property overriding. Below is the complete hierarchy, ordered from **Highest Precedence (Overrides everything)** to **Lowest Precedence (Ultimate fallback)**:

1. **Devtools Global Settings:** Properties in `~/.spring-boot-devtools.properties` (active only when devtools is running).
2. **`@TestPropertySource`:** Annotations on your test classes.
3. **`properties` attribute on tests:** Using `@SpringBootTest(properties = "...")`.
4. **Command Line Arguments:** Passed directly to the application execution (e.g., `java -jar app.jar --server.port=9090`).
5. **Spring Application JSON:** Inline JSON bound to a system property or environment variable (e.g., `SPRING_APPLICATION_JSON='{"foo":{"bar":"spam"}}'`).
6. **ServletConfig init parameters**
7. **ServletContext init parameters**
8. **JNDI Attributes:** From `java:comp/env`.
9. **Java System Properties:** Set via JVM arguments (e.g., `System.getProperties()` / `java -Dserver.port=8080 -jar app.jar`).
10. **OS Environment Variables:** Standard operating system environment variables (e.g., `SERVER_PORT=8080`).
11. **Random Value Property Source:** Properties that only have `random.*` in them (e.g., `${random.int}`).
12. **Profile-Specific Configuration (Outside JAR):** e.g., `application-{profile}.yml` located in the directory where the JAR is run or in a `/config` subdirectory.
13. **Profile-Specific Configuration (Inside JAR):** e.g., `application-{profile}.yml` packaged on the classpath.
14. **Standard Configuration (Outside JAR):** e.g., `application.yml` located in the directory where the JAR is run.
15. **Standard Configuration (Inside JAR):** e.g., `application.yml` packaged on the classpath.
16. **`@PropertySource` Annotations:** Placed on your `@Configuration` classes (note: these are not loaded early enough to affect logging or application context initialization).
17. **Default Properties:** Specified programmatically via `SpringApplication.setDefaultProperties()`.

---

## Architectural Deep Dive

### 1. `ConfigDataEnvironmentPostProcessor`
How does Spring read your `application.yml` files before the ApplicationContext even exists?

The framework uses the `EnvironmentPostProcessor` interface. Specifically, the `ConfigDataEnvironmentPostProcessor` is triggered by a `SpringApplication` startup event (`ApplicationEnvironmentPreparedEvent`). It reads the file system, parses YAML/Properties files using a `PropertySourceLoader`, and inserts them into the `Environment` *before* the bean factory is instantiated.

### 2. Relaxed Binding via `ConfigurationPropertySources`
If you set an OS environment variable `MY_SUPER_URL=http://localhost`, how does Spring know to inject it into `@Value("${my.super-url}")`?

Spring Boot wraps the raw `PropertySources` in a specialized layer called `ConfigurationPropertySources`. This layer acts as an adapter that implements **Relaxed Binding**. It normalizes all keys (from camelCase, snake_case, kebab-case, or UPPER_SNAKE_CASE) into a uniform, lowercase, hyphen-separated format before querying the underlying `PropertySource`.

### 3. The `spring.config.import` Mechanism
In Spring Boot 2.4+, the config loading architecture was heavily refactored to support the `spring.config.import` property. This allows properties to be resolved dynamically from external systems (like HashiCorp Vault, AWS Parameter Store, or Kubernetes ConfigMaps) *during* the environment preparation phase. These imported documents are inserted into the `PropertySource` chain directly below the document that imported them.

---

## Edge Cases and Potential Traps

### List/Array Overriding in YAML
When dealing with lists (arrays) in YAML, properties do **not** merge across different sources; the entire list is replaced.

```yaml
# application.yml
my:
  servers:
    - serverA
    - serverB

# application-prod.yml
my:
  servers:
    - serverC
```
If the `prod` profile is active, `my.servers` will only contain `serverC`. It will **not** be `[serverA, serverB, serverC]`.

### The `@Value` vs `@ConfigurationProperties` Trap
*   `@Value` is resolved **once** at bean instantiation time (during the BeanPostProcessor phase).
*   `@ConfigurationProperties` objects can be refreshed at runtime (if annotated with `@RefreshScope` and triggered via Spring Cloud Bus or Actuator).
*   **Performance:** Heavily relying on `@Value` for hundreds of properties involves traversing the `MutablePropertySources` list many times via the `PropertySourcesPropertyResolver`. Using `@ConfigurationProperties` parses the tree natively and binds it to a Java POJO, which is significantly faster and type-safe.
