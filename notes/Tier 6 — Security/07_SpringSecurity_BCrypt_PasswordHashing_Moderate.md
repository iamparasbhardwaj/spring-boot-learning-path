# BCrypt, and Why Never MD5/SHA for Passwords

## The Bean, In Context
```java
@Bean
PasswordEncoder passwordEncoder() {
    // BCrypt: adaptive, salted per hash. Never MD5/SHA for passwords.
    return new BCryptPasswordEncoder();
}
```
Used both to encode passwords when creating users (`encoder.encode("password")` in `SecurityConfig.users(...)`) and, transparently inside `DaoAuthenticationProvider`, to check a submitted password against the stored hash (`PasswordEncoder.matches(raw, stored)`).

---

## 1. Why General-Purpose Hash Functions (MD5, SHA-1, SHA-256) Are Wrong for Passwords
MD5 and the SHA family were designed to be **fast** — that's the entire point of a cryptographic hash used for things like file-integrity checks or digital signatures, where you want to hash gigabytes of data quickly. That exact property is a liability for passwords: fast to compute for you means fast to compute for an attacker running a brute-force or dictionary attack against a stolen hash database. Modern GPUs compute billions of SHA-256 hashes per second — a "computationally infeasible" password space stops being infeasible very quickly against that kind of throughput.

**Salting alone doesn't fix this.** A per-user random salt (`hash(password + salt)`) does defeat precomputed rainbow tables — the attacker can't reuse one big lookup table across every user — but it does nothing to slow down a *targeted* brute-force against one specific hash, because SHA/MD5 are still cheap to compute per-guess.

## 2. What BCrypt Does Differently
BCrypt is a **deliberately slow**, adaptive password-hashing algorithm (based on the Blowfish cipher), purpose-built to resist brute-forcing:

- **Built-in, unique-per-hash salt.** Every call to `encoder.encode(...)` generates a fresh random salt and embeds it directly in the output string — no separate salt column or storage scheme required.
- **A tunable cost/work factor.** BCrypt's "rounds" parameter (default 10 in Spring's `BCryptPasswordEncoder`) controls how many iterations of internal key-setup it performs — each increment roughly **doubles** the computation time. This is deliberately exponential, not linear, specifically so the cost factor stays meaningful as hardware gets faster.
- **Adaptive over time.** As attacker hardware improves, you raise the cost factor for newly-encoded passwords (existing hashes keep working; `matches()` reads the embedded cost factor from the stored hash string itself, no migration needed) — a knob SHA-256 simply doesn't have.

```
$2a$10$N9qo8uLOickgx2ZMRZoMye1J9M...IhL0wR8AQMTVs2N2wZfnr
 │  │  └─ cost factor (10 = 2^10 rounds)
 │  └──── BCrypt algorithm version
 └─────── format identifier
```
The cost factor and salt are both stored, in the clear, right in the hash string itself — this is intentional and not a weakness. Security doesn't come from hiding the salt or the cost factor; it comes purely from the *computational cost* of reversing the hash, which no amount of knowing the salt or cost factor shortcuts.

## 3. `matches()`, Not `equals()`
```java
// what DaoAuthenticationProvider does, conceptually
boolean valid = passwordEncoder.matches(submittedRawPassword, storedHashFromDatabase);
```
You never decode or reverse the stored hash to compare it — BCrypt is one-way by design. `matches()` re-hashes the submitted password using the **same salt and cost factor already embedded in the stored hash**, and compares the two resulting hash strings.

## 4. The Broader Family: BCrypt, Argon2, PBKDF2, scrypt
BCrypt is the long-standing, battle-tested default and is what Spring Security ships as `BCryptPasswordEncoder`. **Argon2** (winner of the 2015 Password Hashing Competition) is a newer alternative that additionally tunes *memory* cost, not just CPU cost — deliberately making GPU/ASIC-based cracking (which parallelizes CPU-bound work easily but is memory-bandwidth-limited) meaningfully harder. Spring Security also ships `Argon2PasswordEncoder` and `Pbkdf2PasswordEncoder` for teams that want that specific trade-off. All of them share the same essential property MD5/SHA lack: deliberate, tunable slowness.

Spring Security's `DelegatingPasswordEncoder` (the default returned by `PasswordEncoderFactories.createDelegatingPasswordEncoder()`) stores the algorithm identifier as a prefix (`{bcrypt}$2a$10$...`), which lets an application support **multiple** encoding schemes simultaneously and migrate old hashes to a stronger scheme over time without breaking existing users' passwords.

---

## Interview Answer, Compressed
> "MD5 and SHA are designed to be fast, which is exactly wrong for password hashing — fast for you means fast for an attacker brute-forcing a stolen hash database, and salting alone only defeats precomputed rainbow tables, not a targeted brute-force. BCrypt is deliberately slow and adaptive: it generates a unique salt per hash automatically, embeds both the salt and a tunable cost factor directly in the stored string, and each increment of that cost factor roughly doubles the compute cost — so I can raise it over time as hardware gets faster without needing a migration, since `matches()` reads the cost factor straight out of the stored hash. Argon2 is the newer alternative that also tunes memory cost specifically to blunt GPU-based cracking, but the shared principle with BCrypt is the same: deliberate, tunable slowness, which general-purpose hash functions were never built to have."
