# Authentication vs. Authorization, and `@PreAuthorize` Method Security

## The Distinction
- **Authentication** — *who are you?* Verifying an identity claim (a password, a token) actually belongs to the party presenting it. Failure here is a **401 Unauthorized**.
- **Authorization** — *what are you allowed to do?* Given a already-established identity, deciding whether this specific action is permitted. Failure here is a **403 Forbidden**.

These are answered by different parts of the filter chain in sequence, not interchangeably: authentication runs first (populating the `SecurityContext`), then authorization decisions are made against that already-established identity. `SecurityConfig`'s `ExceptionTranslationFilter` is precisely the component that decides which of the two failure responses to send, based on whether a `SecurityContext` was ever established in the first place.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/**").hasRole("ADMIN")
        .anyRequest().authenticated())
```
`.anyRequest().authenticated()` is purely about identity (401 if missing). `.hasRole("ADMIN")` is authorization layered on top (403 if authenticated but the wrong role) — the `admin`/`password` user in `SecurityConfig.users(...)` can reach `/actuator/**`, the `user`/`password` one cannot, even though both are equally "authenticated."

## URL-Based vs. Method-Level Authorization
`authorizeHttpRequests` makes authorization decisions based purely on the **URL pattern** — it has no idea which Java method is about to run, only which route matched. This is coarse-grained and works well for entire endpoint families (`/actuator/**` requires `ADMIN`), but breaks down when the authorization rule depends on something more specific than the URL — e.g., "a user can update *their own* task, but not someone else's," where the URL (`PUT /api/tasks/{id}`) looks identical regardless of ownership.

**`@PreAuthorize`** solves this by attaching an authorization check directly to a method, evaluated via a Spring Expression Language (SpEL) expression **before** the method body runs:

```java
@PreAuthorize("hasRole('ADMIN') or #task.ownerId == authentication.name")
public TaskResponse update(Long id, UpdateTaskRequest request) { ... }
```

Enabling this requires `@EnableMethodSecurity` (the current annotation; `@EnableGlobalMethodSecurity` is the older, deprecated name) on a `@Configuration` class. Under the hood it works the same way `@Transactional` does — an AOP proxy wraps the bean, and the interceptor evaluates the SpEL expression before delegating to the real method — which means it inherits the exact same self-invocation trap: calling `this.update(...)` from another method on the same bean bypasses the proxy and silently skips the check.

## Common SpEL Building Blocks
| Expression | Meaning |
|---|---|
| `hasRole('ADMIN')` | Principal has `ROLE_ADMIN` authority |
| `hasAuthority('tasks:write')` | Fine-grained permission string, not tied to the `ROLE_` convention |
| `authentication.name` | The current principal's username |
| `#paramName` | References a method parameter by name (needs parameter names retained at compile time, or explicit `@P`) |
| `@postAuthorize` | The sibling annotation — evaluated *after* the method runs, useful when the decision depends on the return value |

## Choosing Between URL-Based and Method-Level
Not either/or — layered defense is the normal pattern: URL-based rules in `SecurityFilterChain` give a coarse, easy-to-audit-at-a-glance perimeter (which endpoint families need which role, in one place), while `@PreAuthorize` handles per-resource, data-dependent decisions that a URL pattern structurally cannot express. Relying on method security alone, with no URL-level baseline, means every single service method has to remember its own check — a much easier thing to forget on one new method than a centralized URL rule.

---

## Interview Answer, Compressed
> "Authentication answers who you are and fails with 401; authorization answers what you're allowed to do and fails with 403 — authentication always has to succeed first, since authorization decisions are made against an already-established identity. `authorizeHttpRequests` handles authorization at the URL level, which is coarse but centralized and easy to audit. `@PreAuthorize` handles it at the method level via SpEL, which is necessary once the rule depends on something the URL can't express — like resource ownership. It uses the same AOP-proxy mechanism as `@Transactional`, so it has the identical self-invocation blind spot."
