# `@MockitoBean` (and Why `@MockBean` Is Gone in Boot 4)

## The Annotation, In Context
```java
@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @MockitoBean TaskService taskService;   // replaces the real bean in this context
    ...
}
```
`@MockitoBean` tells Spring's test framework: "create a Mockito mock of this type, and register it in the `ApplicationContext` **in place of** whatever real bean of this type would otherwise exist." `TaskController` gets the mock injected exactly as if it were the real `TaskService` — it has no idea, and needs no idea, that it's a test double.

---

## 1. What Problem This Solves
`@WebMvcTest(TaskController.class)` boots the web layer only — no `@Service` beans, no `DataSource`. But `TaskController` has a hard constructor dependency on `TaskService`. Without something standing in for it, the `ApplicationContext` fails to start: `NoSuchBeanDefinitionException`. `@MockitoBean` supplies that missing bean **and** lets the test control its behavior per-test-method:

```java
@MockitoBean TaskService taskService;

@Test
void get_returns200AndBody() throws Exception {
    when(taskService.findById(1L)).thenReturn(
            new TaskResponse(1L, "Learn slices", null, TaskStatus.TODO, LocalDate.now(), "Prep"));

    mockMvc.perform(get("/api/tasks/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Learn slices"));
}
```
The web layer is tested in complete isolation from whatever `TaskService` actually does — the test only cares that *given* this service response, the controller produces *this* HTTP response.

## 2. `@MockBean` → `@MockitoBean`: What Changed and Why
`@MockBean` (`org.springframework.boot.test.mock.mockito.MockBean`) was **deprecated in Spring Boot 3.4** and is **fully removed in Spring Boot 4**. The replacement, `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`), isn't just a rename — it's part of a broader, more general **bean override** mechanism (`@TestBean`, `@MockitoBean`, `@MockitoSpyBean`) introduced in Spring Framework 6.2 / Spring Boot 3.4 that isn't Mockito-specific in its underlying design, just in this particular annotation's implementation.

Practically, for day-to-day use the behavior is nearly identical: it still replaces a bean in the test's `ApplicationContext` with a Mockito mock. The things worth knowing that changed:
- The package moved from a Spring **Boot** test package to a Spring **Framework** test package (`org.springframework.test.context.bean.override.mockito`) — the mechanism generalized beyond Boot specifically.
- `@MockitoBean` can also be applied at the **class level** (not just field level) for some use cases, and composes with the newer bean-override SPI.
- There's a companion `@MockitoSpyBean` for wrapping a **real** bean in a Mockito spy (partial mock) rather than fully replacing it — the direct successor to the old `@SpyBean`.

Naming this correctly in an interview is a cheap, high-signal way to show you've actually worked with a current Boot version rather than reciting a 2022 tutorial — `@MockBean` is the single most common outdated-annotation tell.

## 3. Context Caching Interaction
Spring's `TestContext` framework caches `ApplicationContext` instances across test classes when the configuration is identical, to avoid re-booting a context for every test class. A bean override (`@MockitoBean`) is part of that cache key — meaning a context with `@MockitoBean TaskService` is cached separately from one without it. Overusing distinct combinations of mocked beans across many test classes multiplies the number of contexts Spring has to boot and cache, which is one of the underappreciated ways test suites end up slower than expected even with slice tests.

---

## Interview Answer, Compressed
> "`@MockitoBean` replaces a real Spring bean with a Mockito mock inside a test's `ApplicationContext` — I use it in `@WebMvcTest` to stand in for `TaskService` so the web-layer test can control exactly what the service returns without needing a real database behind it. It replaced `@MockBean`, which is deprecated as of Boot 3.4 and fully removed in Boot 4 — the new one lives in `org.springframework.test.context.bean.override.mockito` as part of a more general bean-override mechanism that also includes `@MockitoSpyBean` for partial mocks. Knowing `@MockBean` is gone is a quick tell for whether someone's actually used a current Boot version."
