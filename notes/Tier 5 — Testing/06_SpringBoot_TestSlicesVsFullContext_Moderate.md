# Test Slices vs. Full Context, and Why Build Time Is an Engineering Concern

## The Core Trade-off
Every test that boots any Spring `ApplicationContext` — slice or full — pays a real, measurable startup cost. The whole point of a **slice** (`@WebMvcTest`, `@DataJpaTest`) is to boot the *smallest* context that can still meaningfully exercise the layer under test, instead of defaulting to `@SpringBootTest` for everything out of convenience.

| | `@WebMvcTest` / `@DataJpaTest` | `@SpringBootTest` |
|---|---|---|
| What boots | One architectural layer + its direct infra | The entire application |
| Typical cost | Tens of milliseconds per class | Hundreds of ms to seconds per class |
| What it catches | Layer-specific correctness (routing, JSON, SQL, mapping) | End-to-end wiring: missing beans, bad property binding, ambiguous `@Autowired` |
| Failure signal quality | Precise — you know which layer broke | Broad — a failure could be almost anything |

This project's own test suite is deliberately shaped this way: 2 plain unit tests + 2 slice tests for the bulk of coverage, and exactly **one** `@SpringBootTest` (`SmokeTest`), whose only assertion is that the context loads at all.

## Why "Just Use `@SpringBootTest` Everywhere" Feels Fine at First and Isn't
Early in a project, with a handful of tests, the cost difference between a slice and a full context is invisible — a few hundred milliseconds either way. The problem compounds with scale:
- **Linear cost, non-linear pain.** 5 full-context tests costing 500ms each is a rounding error. 500 of them, each still costing roughly the same, turns a test suite that should run in seconds into one that takes minutes — and that tax is paid on every CI run, every local `mvn test`, every pre-commit hook.
- **Context caching mitigates, but doesn't eliminate this.** Spring's `TestContext` framework caches an `ApplicationContext` and reuses it across test classes with *identical* configuration (same profiles, same `@MockitoBean`s, same properties). But any variation — a different mocked bean, a different active profile — busts the cache and forces a fresh boot. A codebase where every `@SpringBootTest` class configures itself slightly differently effectively gets none of the caching benefit.
- **Weaker failure localization.** A failing `@SpringBootTest` could mean a business logic bug, a broken query, a misconfigured bean, or a serialization mismatch — you have to read the stack trace and guess which layer actually broke. A failing `@WebMvcTest` narrows the search space to "something in the web layer or its immediate contract" before you've read a single line of the failure.
- **Slower feedback loop for the person actually writing code.** The value of a test suite is partly a function of how fast it gives you an answer. A developer who has to wait two minutes for feedback on a one-line change runs tests less often, not more.

## The Practical Rule
Default to the narrowest slice that can meaningfully test the thing you're changing. Reach for `@SpringBootTest` deliberately, for a small, curated set of true end-to-end paths and wiring smoke tests — not as the default choice because it's the annotation that "definitely works." If you find yourself writing a `@SpringBootTest` to test something a plain unit test or a slice could already cover, that's usually a sign the test is testing the wrong thing (or the production code has a testability problem — likely a missing seam for injecting a fake, which usually traces back to skipping constructor injection).

---

## Interview Answer, Compressed
> "I keep the test suite pyramid-shaped on purpose: mostly plain unit tests, a healthy layer of slice tests (`@WebMvcTest`, `@DataJpaTest`) for layer-specific correctness, and only a handful of full `@SpringBootTest`s — mainly as wiring smoke tests. It's tempting to reach for `@SpringBootTest` everywhere because it 'just works,' but the cost isn't linear-and-ignorable at scale: it's the slowest test class by far, context caching only helps when configuration is identical across test classes, and a failure gives you a much wider search space than a slice test would. Build time is a real engineering cost — a slow suite gets run less often, which defeats the point of having it."
