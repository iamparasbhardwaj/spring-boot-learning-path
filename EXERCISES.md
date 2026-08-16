# Hands-on drills

Do these **in order**. Each one takes 10–30 minutes and converts a topic you can recite
into a story you can tell. Aim to finish 1–9 on day one and 10–18 on day two.

---

### Day 1

**1. Read the auto-config report** (10 min)
Run with `--debug`. Find `DataSourceAutoConfiguration` in the positive matches and read *why* it
matched. Then find one negative match and read why it backed off.
→ *Answers:* "how does auto-configuration work?"

**2. Break auto-configuration on purpose** (10 min)
Define your own `DataSource` `@Bean`. Re-run with `--debug` and watch Boot's version disappear from
the positive matches.
→ *Answers:* "what is `@ConditionalOnMissingBean`?"

**3. Cause an ambiguity failure** (10 min)
Add a second `PasswordEncoder` bean. Read the `NoUniqueBeanDefinitionException`. Fix it with
`@Primary`, then delete that and fix it again with `@Qualifier`.
→ *Answers:* "what happens when two beans match a dependency?"

**4. Prove singleton scope** (10 min)
Add `System.out.println(this)` in a `@PostConstruct` on `TaskService`. Note it prints once.
Change to `@Scope("prototype")`, inject it in two places, observe the difference.

**5. Add a `PATCH` endpoint** (20 min)
Partial update: only non-null fields change. Decide the semantics yourself and be ready to defend
them (`Optional<T>` fields? JSON Merge Patch?).
→ *Answers:* "PUT vs PATCH, and is your API idempotent?"

**6. Add a custom validation annotation** (25 min)
`@ValidDueDate` — rejects dates more than a year out. Write the `ConstraintValidator`.
→ *Answers:* "how does Bean Validation work?"

**7. Break the error handler** (10 min)
Delete `handleNotFound` from `ApiExceptionHandler` and hit `/api/tasks/99`. Compare the default
Boot error body to your `ProblemDetail`. Put it back.
→ *Answers:* "how do you standardise errors?"

**8. Add a `prod` profile behaviour difference** (15 min)
Make the app refuse to start under `prod` if `app.quotes-base-url` is `http://`. Run both profiles.
→ *Answers:* "how do profiles work?" + "how do you validate config?"

**9. Break `@ConfigurationProperties` validation** (10 min)
Set `app.max-tasks-per-project: 0` and start the app. Read the failure analysis Boot prints.

---

### Day 2

**10. Reproduce the N+1** (25 min) ← *the single best story you can walk in with*
Set `logging.level.org.hibernate.SQL=DEBUG`. Write a test that calls `projectRepository.findAll()`
then loops `p.getTasks().size()`. **Count the SELECT statements.** Now switch to
`findAllWithTasksFetchJoin()` and count again.
→ *Answers:* "tell me about a performance problem you fixed."

**11. Trigger `LazyInitializationException`** (15 min)
Return the `Task` entity directly from the controller instead of `TaskResponse`. Watch it blow up
during serialisation. Then fix it three ways: DTO, fetch join, `@Transactional` boundary.

**12. Prove the self-invocation trap** (20 min)
Add a `@Transactional` method to `TaskService` and call it from another method in the *same class*.
Confirm no transaction starts. Then extract it to a separate bean and confirm it does.
→ *Answers:* the single most common `@Transactional` interview question.

**13. Dirty checking without `save()`** (10 min)
Confirm `TaskService.update` issues an `UPDATE` even though it never calls `save()`. Then move the
same code outside a transaction and watch it silently do nothing.

**14. Write one test per tier** (30 min)
Add a new endpoint and cover it with a plain unit test, a `@WebMvcTest`, and a `@DataJpaTest`.
Time each. Explain the tradeoff out loud.

**15. Secure a single endpoint** (20 min)
Make `DELETE /api/tasks/{id}` admin-only. Do it once with a `requestMatchers` rule and once with
`@PreAuthorize("hasRole('ADMIN')")`. Prove both with `MockMvc` + `@WithMockUser`.

**16. Add API versioning** (25 min) — *Boot 4 showcase*
Expose `/api/tasks` at version `1.1` with an extra field, keeping `1.0` intact. Try header-based
first, then path-based. Check the current reference docs for the exact
`ApiVersionConfigurer` setup — this API is new in Framework 7.
→ *Answers:* "how do you evolve a public API without breaking clients?"

**17. Call an external API and make it fail well** (25 min)
Wire up `QuoteClient` and expose `/api/quote`. Then point `app.quotes-base-url` at a black hole
(`https://10.255.255.1`) and confirm your timeouts fire instead of hanging. Add a fallback.
→ *Answers:* "what happens when a downstream dependency is slow?"

**18. Health and readiness** (15 min)
Write a custom `HealthIndicator` that reports DOWN when the quotes API is unreachable. Hit
`/actuator/health` and `/actuator/health/readiness`. Explain why readiness ≠ liveness for k8s.

---

## The 12 questions to rehearse out loud

Talk, don't read. 90 seconds each, no notes. If you can't do these cleanly, go back to the file.

1. What does `@SpringBootApplication` do, and how does auto-configuration actually work?
2. Why constructor injection over field injection?
3. Walk me through what happens between an HTTP request arriving and your controller method running.
4. What does `@Transactional` do under the hood, and when does it silently not work?
5. What is the N+1 problem and how did you fix it?
6. How do you decide between `@SpringBootTest` and a slice test?
7. Explain Spring Security's filter chain and where JWT validation fits.
8. How would you standardise error responses across an API?
9. `@Value` vs `@ConfigurationProperties` — which and why?
10. How would you debug an endpoint that got slow in production?
11. How would you version a public REST API without breaking existing clients?
12. What's new in Spring Boot 4, and would you migrate a 3.5 service today?
