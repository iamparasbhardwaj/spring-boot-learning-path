# `AuthenticationManager` → `AuthenticationProvider` → `UserDetailsService` → `PasswordEncoder`

## Overview
When a request arrives with credentials (an `Authorization: Basic ...` header, a login form submission, a bearer token in a custom filter), something has to answer: "are these credentials valid, and if so, who is this?" That work is delegated through a specific chain of collaborators, each with one narrow responsibility.

---

## 1. The Chain, End to End
```
Authentication filter (e.g. BasicAuthenticationFilter)
  -> AuthenticationManager.authenticate(Authentication)
       -> ProviderManager (the standard AuthenticationManager implementation)
            -> iterates registered AuthenticationProvider(s), picks one that supports() this Authentication type
                 -> DaoAuthenticationProvider (the standard one for username/password)
                      -> UserDetailsService.loadUserByUsername(username)
                      -> PasswordEncoder.matches(rawPassword, storedHash)
```

- **`AuthenticationManager`** — the entry point interface. One method: `authenticate(Authentication)`, returns a fully-populated `Authentication` on success or throws `AuthenticationException` on failure.
- **`ProviderManager`** — the standard implementation of `AuthenticationManager`. It doesn't do the actual credential checking itself; it delegates to a list of `AuthenticationProvider`s, trying each until one both `supports()` the given `Authentication` type and successfully authenticates it (or all of them fail/decline, in which case authentication fails).
- **`AuthenticationProvider`** — the actual strategy for one authentication mechanism. `DaoAuthenticationProvider` is the standard one for username/password: it looks the user up and checks the password.
- **`UserDetailsService`** — one method, `loadUserByUsername(String)`, returning a `UserDetails` (username, encoded password, authorities, account-status flags). This is the pluggable seam for *where user data comes from* — in-memory, a database via Spring Data, LDAP, or a custom lookup.
- **`PasswordEncoder`** — compares a raw, user-submitted password against the stored (hashed) one. Never a plain `.equals()` — see the BCrypt note for why.

## 2. This Project's Wiring
```java
@Bean
UserDetailsService users(PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
            User.withUsername("user").password(encoder.encode("password")).roles("USER").build(),
            User.withUsername("admin").password(encoder.encode("password")).roles("ADMIN").build()
    );
}

@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
`InMemoryUserDetailsManager` is a `UserDetailsService` implementation backed by an in-memory list — appropriate for a demo/test app with two fixed users. Spring Boot auto-configures a `DaoAuthenticationProvider` wired to whatever `UserDetailsService` and `PasswordEncoder` beans it finds, and wraps it in a `ProviderManager` automatically — none of that plumbing needs to be written explicitly here, only its two building-block beans.

**Swapping to a real user store** — the most common next step in a real application — means providing a different `UserDetailsService` bean (e.g., one backed by a `UserRepository extends JpaRepository<AppUser, Long>` that loads from the database), while `AuthenticationManager`, `ProviderManager`, and `DaoAuthenticationProvider` remain unchanged. This is the whole point of the chain being built from small, swappable interfaces: the *where users come from* concern is isolated to exactly one bean.

## 3. What `loadUserByUsername` Failing Looks Like
`UserDetailsService.loadUserByUsername` throws `UsernameNotFoundException` when no such user exists. `DaoAuthenticationProvider` deliberately treats "user not found" and "password mismatch" the same way at the HTTP response level — both surface as a generic authentication failure (401), never a distinguishing message like "no such user" — specifically to avoid leaking which usernames exist in the system to an attacker probing the login endpoint.

## 4. Multiple `AuthenticationProvider`s
A single application can register more than one `AuthenticationProvider` — e.g., one for username/password, another for validating a JWT's claims, another for LDAP — and `ProviderManager` tries each in turn. This is the extension point for supporting more than one authentication mechanism side-by-side, rather than hard-coding a single strategy into the filter chain.

---

## Interview Answer, Compressed
> "The chain is: the authentication filter hands an `Authentication` object to the `AuthenticationManager`, whose standard implementation, `ProviderManager`, delegates to one or more `AuthenticationProvider`s. For username/password, that's `DaoAuthenticationProvider`, which calls `UserDetailsService.loadUserByUsername` to fetch the stored user, then `PasswordEncoder.matches()` to check the submitted password against the stored hash. Each piece is a separate, swappable interface — I can swap `InMemoryUserDetailsManager` for a database-backed `UserDetailsService` without touching anything else in the chain, which is exactly the point of the design."
