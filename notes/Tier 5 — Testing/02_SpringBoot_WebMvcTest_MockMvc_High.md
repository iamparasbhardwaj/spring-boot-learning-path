# `@WebMvcTest` + `MockMvc`: The Web Slice With No Database

## Overview
`@WebMvcTest` is a **slice test** annotation: it boots only the beans relevant to the Spring MVC web layer, instead of the entire application. `TaskControllerTest` is the reference example:

```java
@WebMvcTest(TaskController.class)
class TaskControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean TaskService taskService;
    ...
}
```

---

## 1. What Actually Boots
Passing `TaskController.class` explicitly scopes the context to that one controller (plus shared infrastructure). `@WebMvcTest` auto-configures:
- `DispatcherServlet` and the full `HandlerMapping` → `HandlerAdapter` machinery
- `HttpMessageConverter`s (Jackson) — real JSON serialization/deserialization happens
- `@RestControllerAdvice` classes (`ApiExceptionHandler` in this app) — real global error handling
- Spring Security's web test support, if Security is on the classpath (which is why `TaskControllerTest` needs `.with(user(...))` and `.with(csrf())`)

It explicitly does **not** boot: `@Service`, `@Repository`, `DataSource`, or anything JPA-related. Any collaborator your controller depends on (here, `TaskService`) has to be supplied another way — which is what `@MockitoBean` is for.

## 2. `MockMvc` — Simulating HTTP Without a Real Server
`MockMvc` drives requests through the **real** `DispatcherServlet` dispatch mechanism, in-process, without opening an actual TCP socket or running an embedded servlet container. You get real routing, real argument resolution, real serialization — just without network overhead, which is what makes this slice fast (typically tens of milliseconds per test, not seconds).

```java
mockMvc.perform(get("/api/tasks/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Learn slices"))
        .andExpect(jsonPath("$.status").value("TODO"));
```
- `.perform(...)` builds and dispatches the mock request.
- `.andExpect(status()...)` and `.andExpect(jsonPath(...))` assert on the resulting `MockHttpServletResponse` — status code, headers, and specific fields in the JSON body via a JSONPath-like expression.

## 3. What This Slice Is Actually Good At Verifying
Things that are meaningless to test with a plain unit test, because they only exist at the HTTP/serialization boundary:
- **Status codes and headers** — `TaskControllerTest.post_returns201WithLocationHeader` checks both the `201` status and the `Location` header pointing at the new resource.
- **Bean Validation wiring** — `post_returns400WhenTitleBlank` sends an intentionally invalid `CreateTaskRequest` and asserts the `@RestControllerAdvice` translates `MethodArgumentNotValidException` into a `400` with a `$.errors.title` field.
- **Global exception → HTTP mapping** — `get_returns404ProblemDetailWhenMissing` mocks `TaskService` to throw `TaskNotFoundException`, and verifies `ApiExceptionHandler` turns it into a `404` `ProblemDetail`.
- **Security rules at the request layer** — the POST tests attach `.with(user("user").roles("USER"))` and `.with(csrf())` because `SecurityConfig` requires authentication (and, in a stateful config, CSRF) for that endpoint; a `@WebMvcTest` will actually enforce those rules if Security is on the classpath.

None of this requires a database — the controller's only real collaborator, `TaskService`, is a mock, so the test is entirely about how the **web layer** behaves given a known service response.

## 4. What It Deliberately Does Not Verify
Whether `TaskService.findById` actually queries the right thing, whether an N+1 happens, whether a transaction rolls back correctly — those all belong to `TaskServiceTest` (plain unit, mocked repositories) or `TaskRepositoryTest` (`@DataJpaTest`, real queries). Mixing those concerns into a `@WebMvcTest` (e.g., by not mocking `TaskService`) either doesn't compile — there's no `DataSource` in this slice — or defeats the purpose of an isolated, fast web-layer test.

---

## Interview Answer, Compressed
> "`@WebMvcTest` boots only the Spring MVC web layer — dispatcher, handler mapping, message converters, `@RestControllerAdvice`, Security's filter chain if it's on the classpath — and nothing JPA-related. `MockMvc` drives requests through the real dispatch machinery in-process, so I get real routing, real JSON serialization, and real validation/error-handling behavior, without a running server or a database. It's the right layer for asserting status codes, response shape, and how exceptions map to HTTP — anything below the controller boundary gets mocked out with `@MockitoBean`."
