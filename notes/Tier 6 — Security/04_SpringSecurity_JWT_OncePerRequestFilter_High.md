# JWT Flow, and Where a Custom `OncePerRequestFilter` Slots In

## Why This App Uses HTTP Basic, and What Would Change for JWT
`SecurityConfig` uses HTTP Basic (`.httpBasic(Customizer.withDefaults())`) — appropriate for a small demo API, but not what a real stateless production API typically uses. The javadoc on `SecurityConfig` names the shape of the alternative explicitly: *"For JWT you insert your own `OncePerRequestFilter` BEFORE the username/password filter, validate the token, and set the `Authentication` on the `SecurityContextHolder`."*

---

## 1. The JWT Flow, End to End
1. **Login:** client `POST`s credentials to a `/auth/login` endpoint (not itself protected by the main filter chain, or protected by a separate, permissive `SecurityFilterChain`). The server authenticates via the normal `AuthenticationManager` chain, then — instead of establishing a session — **issues a signed JWT** containing claims (subject/username, roles, expiration) and returns it to the client.
2. **Subsequent requests:** the client attaches the token on every request: `Authorization: Bearer <token>`.
3. **Validation:** a custom filter intercepts the request, extracts the token, verifies its signature against the server's secret/public key, checks expiration, and — if valid — builds an `Authentication` object from the token's claims and places it on the `SecurityContextHolder` for the duration of the request.
4. **No session, ever:** because the token itself carries everything needed to establish identity on each request, the server holds no session state — this is why JWT-based auth is paired with `SessionCreationPolicy.STATELESS`, exactly as this project's config already sets (in anticipation of exactly this swap).

## 2. Why a Custom Filter, and Why `OncePerRequestFilter` Specifically
Spring Security's own built-in authentication filters (`BasicAuthenticationFilter`, `UsernamePasswordAuthenticationFilter`) don't know how to parse a JWT — that logic doesn't exist anywhere in the framework by default, because token *format* and *signing scheme* are application-specific decisions. So a custom filter is written and inserted into the chain at the right position.

`OncePerRequestFilter` is the standard base class for this, over implementing the raw Servlet `Filter` interface directly, because it guarantees its `doFilterInternal(...)` method executes **exactly once per request**, even in environments where a request gets forwarded/included internally (e.g., a servlet forward to an error page) and would otherwise re-enter the filter chain and risk validating the same token twice or duplicating side effects.

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                Authentication auth = jwtService.buildAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);   // always continue the chain
    }
}
```

## 3. Where It Goes in the Chain — This Is the Part People Get Wrong
The filter must run **before** the point where authorization decisions get made, and specifically before (or in place of) the standard username/password authentication filter — because everything downstream (`ExceptionTranslationFilter`, `AuthorizationFilter`) reads the `Authentication` off the `SecurityContextHolder`, and that has to already be populated by the time they run.

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
    return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/login").permitAll()
                    .anyRequest().authenticated())
            .build();
}
```
`.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` is the explicit instruction: run this filter earlier in the chain than the standard form-login filter. Getting this ordering wrong — inserting the JWT filter *after* the point where authorization is checked — means the request would be evaluated for authorization before the token was ever validated, and every authenticated request would incorrectly be treated as anonymous.

## 4. What Happens on an Invalid or Missing Token
Note the filter above **never throws** on a missing/invalid token — it simply doesn't set an `Authentication`, and calls `chain.doFilter(...)` regardless. The request continues down the chain as anonymous, and it's the `AuthorizationFilter` further downstream — evaluating `.anyRequest().authenticated()` — that ultimately rejects it with a 401. Throwing directly from inside the JWT filter is also a valid design, but conflates two separate concerns (parsing a token vs. deciding whether a URL requires authentication); letting authorization remain the single source of truth for access decisions keeps that separation clean.

---

## Interview Answer, Compressed
> "JWT auth means the login endpoint issues a signed token instead of establishing a session, and every subsequent request carries it in the `Authorization: Bearer` header. Validating that token needs a custom filter — I'd extend `OncePerRequestFilter` specifically because it guarantees single execution per request even across internal forwards — that parses the token, verifies its signature and expiration, and sets an `Authentication` on the `SecurityContextHolder`. The critical detail is filter *ordering*: it has to run via `addFilterBefore(...)`, positioned before the standard authentication filter, because everything downstream — exception translation, authorization — reads off the `SecurityContext` and needs it already populated by the time they run."
