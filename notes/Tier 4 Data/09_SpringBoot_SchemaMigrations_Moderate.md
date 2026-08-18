# Schema Migrations: Flyway/Liquibase, and Why `ddl-auto=update` Isn't a Production Strategy

## What `ddl-auto` Does
`spring.jpa.hibernate.ddl-auto` tells Hibernate to derive `CREATE`/`ALTER`/`DROP` DDL directly from your `@Entity` classes at startup. This project uses it deliberately for local development:

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # NEVER in production. Say "Flyway/Liquibase" if asked.
```

- `create-drop` — build the schema from entities on startup, drop it on shutdown. Perfect for an in-memory H2 database in a demo/test app: zero setup, always matches the current entity model.
- `update` — try to *diff* the current schema against the entity model and apply incremental changes. This is the one people are tempted to leave on in production, and it's the dangerous one.
- `validate` — don't touch the schema at all, just fail startup if entities don't match it. This project's `prod` profile uses exactly this:
  ```yaml
  spring:
    config:
      activate:
        on-profile: prod
    jpa:
      hibernate:
        ddl-auto: validate
  ```
- `none` — do nothing.

## Why `update` Fails in Production
Hibernate's schema diffing is a best-effort heuristic, not a migration tool, and it shows in specific ways:
- It can add columns and tables, but it will generally **not** drop or rename a column safely — rename a field and Hibernate adds a new column instead of renaming the old one, silently leaving orphaned data behind.
- It has no concept of a **data migration** — backfilling a new `NOT NULL` column with a computed default for existing rows is entirely outside its scope.
- It runs automatically, unreviewed, the moment the application boots. Nobody approves the exact DDL before it executes against a real database — there's no diff to review in a PR, no dry run, no rollback plan.
- Different team members' local entity states can silently diverge from what's actually running in production, since there's no ordered, versioned record of *which* schema changes have actually been applied where.

## The Actual Production Answer: Versioned Migrations
Flyway and Liquibase both solve this by making schema change an explicit, ordered, version-controlled artifact — checked into the repo, reviewed in a PR, and run by an explicit tool step rather than inferred by the ORM.

**Flyway** (plain SQL, versioned filenames):
```
src/main/resources/db/migration/
  V1__create_task_table.sql
  V2__add_task_priority_column.sql
  V3__backfill_priority_default.sql
```
Flyway tracks which migrations have already run in a `flyway_schema_history` table, and applies only the new ones, in strict numeric order, at application startup (or via CLI, independent of the app).

**Liquibase** is the same idea with changesets defined in XML/YAML/JSON (or SQL) instead of raw filenames, giving you database-agnostic changeset definitions and rollback tags at the cost of more verbosity.

With either tool, `ddl-auto` gets set to `validate` (as this project's `prod` profile already does) or `none` in every real environment — the migration tool owns the schema, Hibernate just needs to confirm its entity mappings still agree with what's there.

## The Right Split
| Environment | `ddl-auto` | Schema owned by |
|---|---|---|
| Local dev / tests (H2, in-memory) | `create-drop` | Hibernate, disposable, no data to protect |
| Any real/shared environment | `validate` or `none` | Flyway/Liquibase migrations, versioned in the repo |

---

## Interview Answer, Compressed
> "`ddl-auto=update` looks convenient but it's schema inference, not migration — it can't safely rename or drop columns, has no concept of data backfills, and applies unreviewed DDL the instant the app boots, with no record of what ran where. I use Flyway or Liquibase for anything real: versioned, ordered SQL migrations checked into the repo and reviewed like code, with `ddl-auto` set to `validate` so Hibernate only confirms the entities still match the schema the migrations already built."
