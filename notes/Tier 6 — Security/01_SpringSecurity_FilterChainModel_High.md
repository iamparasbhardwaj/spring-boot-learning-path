# The Filter Chain Model: Spring Security Is One Servlet Filter

## Overview
The single most useful mental model for Spring Security: **it is not a separate parallel system sitting alongside your controllers.** It is one Servlet Filter, registered at the very front of the standard Servlet filter chain, which internally delegates to an ordered list of its own specialized filters before your request ever reaches `DispatcherServlet`.

```java
// SecurityConfig.java
/**
 * INTERVIEW: "Explain Spring Security's architecture."
 * It is ONE servlet filter (DelegatingFilterProxy -> FilterChainProxy) that delegates
 * to an ordered chain of security filters.
 */
```

---

## 1. The Two-Layer Indirection: `DelegatingFilterProxy` → `FilterChainProxy`
- **`DelegatingFilterProxy`** — a standard Servlet filter, registered in the servlet container itself, whose only job is to look up a named Spring bean and delegate every `doFilter()` call to it. This is the bridge between the plain Servlet API (which knows nothing about Spring beans) and the Spring-managed world.
- **`FilterChainProxy`** — the Spring bean `DelegatingFilterProxy` delegates to. It holds the **actual, ordered list** of Spring Security's internal filters and is responsible for running the request through all of them in sequence.

So from the servlet container's point of view, there is exactly **one** filter for all of Security. Internally, that one filter fans out into potentially a dozen or more specialized ones.

## 2. The Ordered Chain, Roughly
```
SecurityContextPersistenceFilter   (load existing auth from session/context, if any)
  -> Authentication filter          (form login / HTTP Basic / your custom JWT filter)
       -> AuthenticationManager -> ProviderManager -> AuthenticationProvider
           -> UserDetailsService.loadUserByUsername() + PasswordEncoder.matches()
  -> ExceptionTranslationFilter     (turns security exceptions into 401 vs 403)
  -> AuthorizationFilter            (does this principal have access to this URL?)
```
Order here is load-bearing, not incidental — authentication has to run and populate the `SecurityContext` **before** authorization can meaningfully ask "is this authenticated principal allowed here?" A custom filter (like a JWT-validating `OncePerRequestFilter`) has to be inserted at a specific point in this chain — before the standard authentication filter — precisely because everything downstream depends on the `SecurityContext` already being populated by the time it runs.

## 3. Filters Run *Before* Your Controller, Every Time
Because this is all standard Servlet filter machinery, it runs for **every** matching request, before `DispatcherServlet` even starts resolving a handler. This is the same "outer moat" position described in the Filters vs. Interceptors note — Security filters see raw `HttpServletRequest`/`HttpServletResponse` and know nothing about which `@RestController` method is about to run; the `AuthorizationFilter` decision is made purely against the configured URL patterns (`SecurityConfig`'s `authorizeHttpRequests` rules), not against Java method metadata.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/tasks/**").permitAll()
        .requestMatchers("/actuator/**").hasRole("ADMIN")
        .anyRequest().authenticated())
```
This means a request that fails authorization never reaches `TaskController` at all — it's rejected at the filter layer, several steps before Spring MVC's own dispatch logic even runs.

## 4. Why This Model Matters in Practice
- **Debugging "why is this endpoint 403ing":** the answer is almost always in the filter chain's rule ordering or matcher specificity, not in the controller — `requestMatchers` rules are evaluated in declaration order, and the first match wins, so an overly broad early rule can shadow a more specific one below it.
- **Extending Security correctly:** a custom filter (JWT validation, request logging, custom header checks) is added via `.addFilterBefore(...)` / `.addFilterAfter(...)` relative to an existing filter in this chain — you're not writing a separate parallel mechanism, you're inserting into this one ordered pipeline at the right point.
- **It's why CSRF, sessions, and CORS are all configured on the same `HttpSecurity` builder** — they're all just different filters in the same chain, configured through one fluent DSL.

---

## Interview Answer, Compressed
> "Spring Security registers as exactly one Servlet filter — `DelegatingFilterProxy` bridging into a Spring-managed `FilterChainProxy` — which internally runs the request through its own ordered chain of specialized filters: load existing auth, authenticate, translate exceptions into 401/403, then authorize against URL patterns. All of this happens before `DispatcherServlet` even resolves a handler, so a request that fails authorization never reaches the controller. Extending Security — say, for JWT — means inserting a custom filter at the correct point in that same ordered chain, not building something separate."
