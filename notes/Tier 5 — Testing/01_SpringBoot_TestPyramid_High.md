# The Test Pyramid in Spring Boot Terms

## Overview
"The test pyramid" is usually taught abstractly (unit / integration / e2e). Spring Boot gives that abstraction concrete, framework-specific shapes, and this project's four test classes are each a deliberate example of one layer:

```
                    ▲
                   / \
                  /   \      SmokeTest            @SpringBootTest — full context, slowest
                 /-----\
                /       \    TaskControllerTest    @WebMvcTest — web slice
               /         \   TaskRepositoryTest    @DataJpaTest — persistence slice
              /-----------\
             /             \ TaskServiceTest       plain unit test — fastest, most numerous
            /_______________\
```

The pyramid shape is a **statement about proportions**, not just a list of test types: many fast plain unit tests at the bottom, fewer slice tests in the middle, a small number of full-context tests at the top. Inverting that shape (few unit tests, many full `@SpringBootTest`s) is the single most common testing anti-pattern in Spring codebases — it looks thorough but makes the build slow and the failures hard to localize.

---

## Layer 1 — Plain Unit Tests (`TaskServiceTest`)
No Spring container involved at all. `@ExtendWith(MockitoExtension.class)`, `@Mock` for collaborators, plain `new TaskService(...)` construction:

```java
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    AppProperties properties = new AppProperties(50, 20, "https://example.com");

    @Test
    void findById_returnsMappedResponse() {
        ...
        TaskService service = new TaskService(taskRepository, projectRepository, properties);
        assertThat(service.findById(1L).title()).isEqualTo("Learn DI");
    }
}
```
This is only possible because `TaskService` uses **constructor injection** — the exact payoff named in `TaskServiceTest`'s own comment: *"This is the payoff of constructor injection — you can build the object with `new`."* No `ApplicationContext` boots, so this runs in single-digit milliseconds. This is where the bulk of your business-logic test coverage should live.

## Layer 2 — Slice Tests (`TaskControllerTest`, `TaskRepositoryTest`)
A **slice test** boots only the part of the Spring context relevant to one architectural layer, using a purpose-built auto-configuration:

- `@WebMvcTest(TaskController.class)` — boots `DispatcherServlet`, `HandlerMapping`, message converters, `@RestControllerAdvice`, and Spring Security's test support, but **no** `DataSource`, no JPA, no repositories. Collaborators like `TaskService` are replaced with `@MockitoBean`.
- `@DataJpaTest` — boots an embedded database, Hibernate, and your `@Repository` beans, but no web layer. Each test runs inside a transaction that's rolled back afterward.

Slices are the middle of the pyramid: slower than a plain unit test (a partial context still has to boot), but far faster than the full application, and they still exercise real framework behavior — real JSON serialization, real SQL, real bean validation — that a mocked unit test can't verify.

## Layer 3 — Full Context (`SmokeTest`)
```java
@SpringBootTest
class SmokeTest {
    @Test
    void contextLoads() { }
}
```
Boots the **entire** `ApplicationContext` — every bean, every auto-configuration, the real (or a test) `DataSource`. It is the slowest test in the suite by a wide margin, and its value is almost entirely about **wiring**, not business logic: a missing bean, an ambiguous `@Autowired` candidate, a bad property binding, a startup-time `ConditionalOnClass` mismatch. `contextLoads()` looking trivial is the point — if wiring is broken, this is the one test that fails and nothing else even gets a chance to run.

The comment on it says exactly why there should be only a handful of these: *"Keep exactly a few of these, not hundreds."* Every additional `@SpringBootTest` multiplies build time linearly (each one may boot a fresh context unless Spring's test context cache can reuse one across test classes with identical configuration) — hundreds of them turn a two-minute test suite into a twenty-minute one.

---

## Choosing a Layer for a New Test
| Testing... | Reach for |
|---|---|
| Business logic, branching, calculations | Plain unit test + Mockito |
| Controller routing, JSON shape, status codes, validation errors | `@WebMvcTest` |
| Query correctness, mapping, cascades, N+1 | `@DataJpaTest` |
| "Does the whole app actually boot with this configuration" | `@SpringBootTest` (sparingly) |

---

## Interview Answer, Compressed
> "In Boot terms the pyramid is: plain unit tests with Mockito at the base — no Spring context, fast, testing business logic, which is only possible because of constructor injection. Then slice tests in the middle — `@WebMvcTest` for the web layer with `@MockitoBean` collaborators, `@DataJpaTest` for the persistence layer with a real embedded DB and automatic rollback. At the top, a small number of `@SpringBootTest`s that boot the whole context, mainly to catch wiring problems — a missing bean, a bad property — not to re-verify business logic already covered lower down. The proportions matter as much as the layers: too many full-context tests and your build time becomes the actual engineering problem."
