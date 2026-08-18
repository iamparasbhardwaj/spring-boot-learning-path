# CSRF: What It Protects, and Why Disabling It for a Stateless API Is Defensible

## What CSRF Actually Is
Cross-Site Request Forgery exploits one specific fact about browsers: they automatically attach a site's cookies (including session cookies) to **any** request to that site, regardless of which page initiated the request. If a user is logged into `bank.com` (a session cookie is set), and then visits an unrelated, malicious `evil.com` that contains a hidden auto-submitting form posting to `bank.com/transfer`, the browser dutifully attaches the `bank.com` session cookie to that request — the bank's server sees what looks like a legitimate, authenticated request, because from the cookie's perspective, it is one.

**The core exploited assumption:** "if this request carries a valid session cookie, the user must have intended to make it." CSRF breaks that assumption by getting the user's own browser to make the request on the attacker's behalf, using credentials the browser already holds.

## Why It's Specifically a Cookie/Session Problem
CSRF protection exists to protect **cookie-based, browser-managed authentication** — because that's the only mechanism where the browser attaches credentials *automatically*, without the requesting page having to know or supply them. An attacker's page can trigger a request, but it cannot read or forge the victim's session cookie itself — it doesn't need to, because the browser attaches it automatically for any same-site request.

## Why This Project Disables It — and Why That's the Correct Call, Not a Shortcut
```java
// SecurityConfig.java
.csrf(csrf -> csrf.disable())
.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
```java
/**
 * Disabled because this is a stateless API consumed by non-browser clients.
 * INTERVIEW: CSRF protects cookie-authenticated browser sessions. A
 * token-in-header API is not vulnerable the same way. Know WHY, do not
 * just say "we disable it".
 */
```
This API uses **HTTP Basic** with `STATELESS` session policy — credentials are sent as an explicit `Authorization` header on every request, not stored in a cookie the browser attaches automatically. An attacker's page cannot make the victim's browser "automatically" attach an `Authorization: Basic ...` header the way it can with a cookie — there's no ambient credential for the browser to forward on the victim's behalf. Without an ambient, auto-attached credential, there's no forged request to defend against — CSRF simply doesn't apply to this authentication model. (The same reasoning covers a `Bearer` JWT sent as a header, per the JWT flow note — token-in-header auth of any kind sidesteps this specific attack, not because tokens are inherently safer, but because nothing attaches them automatically.)

**The critical caveat this comment is guarding against:** this reasoning holds *only* as long as authentication stays header-based. The moment any part of the same application also uses cookie-based session authentication (even for one endpoint — a browser-facing admin console, say), CSRF protection needs to come back for that surface, because the ambient-cookie assumption is back in play.

## When CSRF Protection Must Stay On
Any application (or any part of one) that authenticates via a session cookie, form login serving a browser, or any mechanism where the browser auto-attaches credentials — CSRF protection should remain enabled. Spring Security's default (CSRF **enabled**) reflects that this is the far more common and riskier case; disabling it is the exception that needs an explicit, stated justification, not the default posture.

## The Mechanism, Briefly, When It Is Enabled
Spring Security's CSRF protection issues a per-session (or per-request) token, delivered to the legitimate page via a cookie or a hidden form field, that must be echoed back in a request header or form parameter on any state-changing request (`POST`/`PUT`/`DELETE`). An attacker's forged cross-site request has no way to read that token value (same-origin policy prevents reading another site's response body/cookie), so it cannot include it — the server rejects the request for a missing/invalid CSRF token, even though the session cookie itself was validly attached.

---

## Interview Answer, Compressed
> "CSRF exploits the fact that browsers auto-attach cookies to any request to a site, regardless of which page triggered it — so a malicious page can make the victim's browser fire an authenticated request the user never intended. It's specifically a cookie/session-authentication problem, because that's the one case where credentials get attached automatically without the requesting page supplying them. This API disables CSRF because it's stateless and uses a token in the `Authorization` header — nothing browser-ambient to forge. That reasoning breaks the moment any part of the app authenticates via a session cookie instead — at that point CSRF protection has to come back for that surface specifically."
