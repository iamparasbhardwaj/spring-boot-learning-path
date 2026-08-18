# `SecurityFilterChain` Bean Config: The Lambda DSL

## `WebSecurityConfigurerAdapter` Is Gone — Know Why
Older Spring Security tutorials (and a huge share of Stack Overflow answers) configure security by extending `WebSecurityConfigurerAdapter` and overriding `configure(HttpSecurity http)`. That class was **deprecated in Spring Security 5.7** and **fully removed** in later versions. The replacement is a `@Bean`-returning method that builds and returns a `SecurityFilterChain` directly — this project's `SecurityConfig` uses exactly that shape:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tasks/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
```

**Why the change was made, not just what changed:** inheritance-based configuration (extend a class, override a method) locks you into a single configuration per subclass and makes composing multiple, independent `SecurityFilterChain`s (e.g., one for `/api/**` stateless JWT auth, another for `/admin/**` form login) awkward. The component-based, `@Bean`-returning style lets you define **multiple** `SecurityFilterChain` beans, each scoped to a different request matcher, composed rather than inherited — and it aligns with the rest of the framework's move away from `*Adapter` base classes toward explicit `@Bean` configuration.

## Reading the DSL: Each Line Is a Filter Configuration
`HttpSecurity` is a builder; each lambda configures one aspect of the chain, and `.build()` at the end assembles the actual `SecurityFilterChain`:

| Call | Configures |
|---|---|
| `.csrf(csrf -> csrf.disable())` | Whether the CSRF-protection filter is active |
| `.sessionManagement(s -> s.sessionCreationPolicy(STATELESS))` | Whether Security ever creates an `HttpSession` |
| `.authorizeHttpRequests(auth -> ...)` | The `AuthorizationFilter`'s URL → access-rule mapping |
| `.headers(h -> h.frameOptions(f -> f.sameOrigin()))` | Security response headers (`X-Frame-Options`, etc.) |
| `.httpBasic(Customizer.withDefaults())` | Enables the HTTP Basic authentication filter with default settings |

`Customizer.withDefaults()` is the idiom for "turn this on with Spring's default configuration" without further customization — used here for HTTP Basic since this API's test users (`user`/`password`, `admin`/`password`) don't need anything beyond the default challenge-response behavior.

## Rule Ordering in `authorizeHttpRequests` Is Significant
Rules are evaluated **in the order declared**, and the **first matching rule wins**:
```java
.requestMatchers(HttpMethod.GET, "/api/tasks/**").permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
.anyRequest().authenticated()
```
A `GET /api/tasks/5` matches the first, more specific rule and is allowed through with no authentication required. A `GET /actuator/beans` falls through to the `/actuator/**` rule and requires the `ADMIN` role. Anything not matched by an earlier rule falls all the way to `anyRequest().authenticated()` — the deliberate catch-all default-deny-unless-authenticated posture. Putting `anyRequest()` **before** a more specific rule would silently shadow it — a common source of "why is this endpoint returning 401 when I explicitly permitted it" bugs.

## `STATELESS` Sessions — Why It's Set Here
`SessionCreationPolicy.STATELESS` tells Security to never create or use an `HttpSession` to store the `SecurityContext` — every request must carry its own credentials (here, HTTP Basic's `Authorization` header; in a real JWT setup, a bearer token). This is the correct default for an API with no browser-managed session state, and it's also *why* CSRF is safely disabled in this config — CSRF specifically protects cookie/session-based authentication, which this API deliberately doesn't use.

---

## Interview Answer, Compressed
> "`WebSecurityConfigurerAdapter` is deprecated since 5.7 and removed in current Spring Security — the current approach is a `@Bean` method that takes an `HttpSecurity` builder and returns a `SecurityFilterChain`, configured through a lambda-based DSL: CSRF, session policy, authorization rules, headers, and the auth mechanism, each as one chained call. It's not just a syntax change — bean-based config lets you compose multiple independent filter chains for different URL patterns, which inheritance-based config made awkward. The one sharp edge worth knowing: `authorizeHttpRequests` rules are evaluated in declared order with first-match-wins, so rule ordering is part of the actual behavior, not just style."
