# Spring Boot — 2-Day Interview Prep (Ordered Curriculum + Practice App)

Target: **Spring Boot 4.1.x / Spring Framework 7 / Java 21+**
Working app included. Every file carries an `INTERVIEW:` comment naming the question it answers.

---

## Run it

```bash
cd task-api
./mvnw spring-boot:run          # or: mvn spring-boot:run
# no wrapper? -> https://start.spring.io generates one, or just use your local mvn
```

Do this **once**, on purpose — it is the highest-value 10 minutes of the whole plan:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--debug
```

Read the **Positive matches / Negative matches** auto-configuration report. After that you can
say "auto-config is conditional bean definitions that back off when I define my own" and mean it.

Try it out:

```bash
curl localhost:8080/api/tasks
curl localhost:8080/api/tasks/99                        # 404 as RFC 7807 problem+json
curl -u user:password -X POST localhost:8080/api/tasks \
     -H 'Content-Type: application/json' \
     -d '{"title":"","projectId":1}'                    # 400 with field errors
curl -u admin:password localhost:8080/actuator/beans
curl -u admin:password localhost:8080/actuator/configprops
mvn test
```

H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:taskdb`, user `sa`, no password).

---

## The ordered topic list and details are present in [notes](https://github.com/iamparasbhardwaj/spring-boot-learning-path/tree/main/notes)

Study in this order. Depth column: **must** = you will be asked, **should** = likely,
**nice** = only if time remains. Each row points at the file in this repo that demonstrates it.

### [Tier 0 — Framing (30 min)](https://github.com/iamparasbhardwaj/spring-boot-learning-path/tree/main/notes/Tier%200)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 1 | Spring Framework vs Spring Boot vs Spring Cloud — Boot is opinionated config on top of the Framework, not a replacement | must | — |
| 2 | The problem Boot solves: no XML, no manual servlet container, no dependency version roulette | must | `pom.xml` |
| 3 | Starters and the parent POM's dependency management | must | `pom.xml` |
| 4 | Fat JAR vs WAR, embedded Tomcat, `java -jar` | should | — |

### Tier 1 — The core container (2.5 h) — *this is where interviews are won or lost*

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 5 | IoC and DI: what "inversion" actually inverts | must | `TaskService` |
| 6 | `@SpringBootApplication` = `@SpringBootConfiguration` + `@ComponentScan` + `@EnableAutoConfiguration` | must | `TaskApiApplication` |
| 7 | Auto-configuration mechanics: `AutoConfiguration.imports`, `@ConditionalOnClass`, `@ConditionalOnMissingBean` | must | run with `--debug` |
| 8 | Constructor vs field vs setter injection, and why constructor wins | must | `TaskService` |
| 9 | `@Component` / `@Service` / `@Repository` / `@Controller` — and what `@Repository` uniquely adds (exception translation) | must | across the app |
| 10 | `@Bean` vs `@Component`: you own the class vs you don't | must | `HttpClientConfig` |
| 11 | Bean scopes (singleton default, prototype, request, session) + the singleton-injecting-prototype trap | should | — |
| 12 | Bean lifecycle: instantiate → populate → aware callbacks → `BeanPostProcessor` → `@PostConstruct` → destroy | should | — |
| 13 | `@Qualifier`, `@Primary`, and injecting `List<T>` of all implementations | should | — |
| 14 | Circular dependencies: why they now fail fast, how to fix them properly | nice | — |
| 15 | `ApplicationContext` vs `BeanFactory`; eager vs lazy init | nice | — |

### Tier 2 — Configuration (1 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 16 | Property source precedence order (args > env > profile yml > yml > defaults) | must | `application.yml` |
| 17 | `@Value` vs `@ConfigurationProperties` — type safety, relaxed binding, validation | must | `AppProperties` |
| 18 | Profiles: `@Profile`, `spring.profiles.active`, per-profile YAML documents | must | `application.yml` |
| 19 | Secrets: env vars / vault, never committed YAML | should | — |

### Tier 3 — Web layer (2 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 20 | Request lifecycle: `DispatcherServlet` → `HandlerMapping` → `HandlerAdapter` → converters → response | must | `TaskController` |
| 21 | `@RestController` vs `@Controller`; `HttpMessageConverter` and Jackson | must | `TaskController` |
| 22 | `@PathVariable` / `@RequestParam` / `@RequestBody` / `ResponseEntity` | must | `TaskController` |
| 23 | Bean Validation with `@Valid`, and `@Valid` vs `@Validated` | must | `CreateTaskRequest` |
| 24 | Global error handling: `@RestControllerAdvice` + `@ExceptionHandler` + `ProblemDetail` (RFC 7807) | must | `ApiExceptionHandler` |
| 25 | REST semantics: status codes, `Location` on 201, PUT vs PATCH, idempotency | must | `TaskController` |
| 26 | DTOs vs entities — why you never serialise an entity | must | `web/dto/` |
| 27 | Pagination and sorting with `Pageable` | should | `TaskController` |
| 28 | CORS, filters vs interceptors | nice | — |

### Tier 4 — Data (2.5 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 29 | Spring Data repository proxies — who implements your interface | must | `TaskRepository` |
| 30 | Derived queries, `@Query` JPQL, native queries, projections | must | `TaskRepository` |
| 31 | `@Transactional`: proxy-based AOP, **self-invocation bypasses it**, rollback rules | must | `TaskService` |
| 32 | Propagation (`REQUIRED` vs `REQUIRES_NEW`) and isolation levels | should | `TaskService` |
| 33 | Persistence context, managed entities, dirty checking (why `save()` is often redundant) | must | `TaskService.update` |
| 34 | LAZY vs EAGER, `LazyInitializationException`, `@ManyToOne` defaults to EAGER | must | `Task`, `Project` |
| 35 | **N+1 problem** and its three fixes: fetch join, `@EntityGraph`, batch size | must | `ProjectRepository` |
| 36 | Optimistic locking with `@Version` vs pessimistic locking | should | — |
| 37 | Schema migrations: Flyway/Liquibase, and why `ddl-auto=update` is not a production strategy | should | `application.yml` |
| 38 | Connection pooling with HikariCP; pool size as a failure mode | nice | — |

### Tier 5 — Testing (1.5 h) — *disproportionately high ROI, most candidates are weak here*

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 39 | The test pyramid in Boot terms: plain unit → slice → `@SpringBootTest` | must | all 4 test classes |
| 40 | `@WebMvcTest` + `MockMvc` — the web slice with no DB | must | `TaskControllerTest` |
| 41 | `@MockitoBean` (`@MockBean` is removed in Boot 4) | must | `TaskControllerTest` |
| 42 | `@DataJpaTest`, `TestEntityManager`, automatic rollback | must | `TaskRepositoryTest` |
| 43 | Testcontainers as the grown-up answer to "is H2 enough?" | should | — |
| 44 | Test slices vs full context, and why build time is an engineering concern | should | `SmokeTest` |

### Tier 6 — Security (1.5 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 45 | The filter chain model — Security is one servlet filter delegating to an ordered chain | must | `SecurityConfig` |
| 46 | `SecurityFilterChain` bean config (the lambda DSL; `WebSecurityConfigurerAdapter` is long gone) | must | `SecurityConfig` |
| 47 | `AuthenticationManager` → `AuthenticationProvider` → `UserDetailsService` → `PasswordEncoder` | must | `SecurityConfig` |
| 48 | JWT flow and where a custom `OncePerRequestFilter` slots in | must | `SecurityConfig` javadoc |
| 49 | Authentication vs authorization; `@PreAuthorize` and method security | should | — |
| 50 | CSRF: what it protects and **why** disabling it for a stateless API is defensible | should | `SecurityConfig` |
| 51 | BCrypt and why never MD5/SHA for passwords | should | `SecurityConfig` |

### Tier 7 — Production concerns (1 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 52 | Actuator: health, metrics, info; readiness vs liveness probes | must | `application.yml` |
| 53 | Never expose `/env`, `/beans`, `/heapdump` publicly | must | `SecurityConfig` |
| 54 | Micrometer → Prometheus; the three pillars (metrics, logs, traces) | should | — |
| 55 | Structured logging, correlation IDs, MDC | should | `ApiExceptionHandler` |
| 56 | Graceful shutdown, Docker layered JARs, GraalVM native images | nice | — |

### Tier 8 — Talking to other services (1 h)

| # | Topic | Depth | Where |
|---|-------|-------|-------|
| 57 | `RestTemplate` (legacy) vs `WebClient` vs `RestClient` vs HTTP interface clients | must | `QuoteClient` |
| 58 | Timeouts, retries (idempotent calls only), circuit breakers, bulkheads | must | `HttpClientConfig` |
| 59 | Microservices vocabulary: service discovery, config server, API gateway, distributed tracing | should | — |
| 60 | Sync vs async, idempotency keys, the outbox pattern | nice | — |

### Tier 9 — Spring Boot 4 currency (45 min) — *your unfair advantage*

Most candidates answer as if it is 2023. Boot 4.1.0 shipped June 2026; **every 3.x and 2.x branch
is now past open-source support**, so "we're planning the 4.x migration" is a live, credible topic.

| # | Topic | Depth |
|---|-------|-------|
| 61 | Native **API versioning** — path / header / query / media-type strategies, declared on the mapping (`@GetMapping(url="/x", version="1.1")`), RFC 9745 deprecation handling | must |
| 62 | **HTTP Service Clients** — declarative `@HttpExchange` interfaces as first-class beans, replacing OpenFeign | must |
| 63 | **JSpecify null safety** — non-null by default per package, `@Nullable` where it isn't; caught at compile time | must |
| 64 | **Modularized auto-configuration** + migration to **Jackson 3** | should |
| 65 | Built-in resilience: `@Retryable`, `@ConcurrencyLimit` in core (Resilience4j now optional) | should |
| 66 | **Spring Data AOT** — query generation at build time, materially faster startup | should |
| 67 | Baseline: Java 17 minimum, first-class Java 25, Jakarta EE 11, virtual threads stable | should |

---

## Sources (in the order you should reach for them)

1. **[Spring Boot reference docs](https://docs.spring.io/spring-boot/index.html)** — the actual source of truth. Skim "Core Features" and "Web".
2. **[Marco Behler's guides](https://www.marcobehler.com/guides)** — best "explained like an engineer, not a tutorial" writing on Spring anywhere.
3. **[Baeldung](https://www.baeldung.com/spring-boot)** — for targeted lookups. Do not read linearly.
4. **[Dan Vega — Spring Boot 4 is here](https://www.danvega.dev/blog/spring-boot-4-is-here)** — fast, current, covers 4.0 and 4.1.
5. **[InfoQ — Spring Framework 7 and Spring Boot 4](https://www.infoq.com/news/2025/11/spring-7-spring-boot-4)** — dense summary of exactly what changed.
6. **[Official 4.0 announcement](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)** + the linked migration guide.
7. **[spring.io/guides](https://spring.io/guides)** — short official how-tos when you want to try one thing.

---

## Two failure modes

- **Memorising annotation lists.** They will ask "why" or "what happens if two beans match" and you will stall. Prefer mechanisms over vocabulary.
- **Reading instead of running.** The `--debug` report, the N+1 query log, and one green `@WebMvcTest` are worth more than three more hours of articles.

See `EXERCISES.md` for the hands-on drills.
