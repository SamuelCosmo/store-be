# Project Rules

## Git operations are forbidden

NEVER run commit, push, rebase, merge, pull, pull request creation, branch
creation/switching, worktrees, cherry-pick, stash, tag, or any other git
operation that modifies history, refs, remotes, or working-tree state beyond
reading it.

Read-only git commands (status, diff, log, show, blame) are allowed.

If a task seems to require a git write operation, stop and ask the user to
run it themselves.

## Tests are part of every change

When changing DTO/record signatures, constructors, or service behavior,
update `src/test/` in the same change — the Railway build runs
`mvnw install` and fails on stale tests even when main code compiles.
Verify with `./mvnw -q -DskipTests compile` + `./mvnw -q test`.

## User-facing errors use codes

Throw `BusinessException(code, message, params)` (or its 409 subclasses)
for anything a user can hit. `code` is stable UPPER_SNAKE; `message` is
English for logs only — the frontend never shows it. Every new code needs
`err.<CODE>` in `store-fe/lib/i18n/es.json` and `en.json`.

# SAC+ — Master Agent Configuration

SAC+ is a multi-tenant SaaS ERP platform (Spring Boot backend of
`../store-fe`). Current vertical: Japanese food restaurant. This repo:
`store-be` (Spring Boot 3.5, Java 21, PostgreSQL 16). Frontend:
`../store-fe` (Next.js 16, React, TypeScript, Tailwind v4). Future
vertical: hearing-aid clinic (NOT confirmed — foundation only).

## Skills — activate by relevance, not all at once

Project skills live in `.devin/skills/<name>/SKILL.md` (mirrored in
`../store-fe/.devin/skills/`). Invoke the ones matching the task:

| Task touches… | Activate |
|---|---|
| Requirements, scope, MVP, priorities | product-management |
| Design decisions, modules, refactors, ADRs | software-architecture |
| Java/services/controllers/DTOs/API | backend-engineering |
| `store-fe` React/components/UI | frontend-engineering (+ store-api for API calls) |
| Entities, schema, queries, migrations | database-engineering |
| Auth, permissions, secrets, tenant isolation | security-engineering |
| Tests, coverage, "is it done" | testing-qa |
| Deploy, Railway, Docker, env vars, backups | devops-sre |
| Personal data, contracts, privacy, invoicing | legal-compliance-mx |
| ERP modules, plans, entitlements | business-domain-erp |
| Payments, card terminals, refunds | payment-integration |
| Stock, recipes, food cost | inventory-management |
| New/changed UI components | accessibility-wcag |
| Slow pages/queries, caching, scale | performance-engineering |
| Logs, metrics, errors in prod | observability-monitoring |
| Docs, happy path, runbooks | documentation-engineering |
| Releases, versioning, rollback | release-management |
| LLM/AI features | ai-integration |
| Restaurant features (POS/KDS/menu) | restaurant-domain |
| Clinic/health features (future) | hearing-clinic-domain |

Cross-functional example — a payment integration activates:
product-management + backend-engineering + payment-integration +
security-engineering + testing-qa + documentation-engineering.

## Working rules

- **Inspect before implementing** — read the real code and docs first;
  never assume architecture or behavior.
- **Security review** for anything touching auth, permissions, personal
  data, payments, uploads, or secrets.
- **Tests before "done"** — `./mvnw -q -DskipTests compile` +
  `./mvnw -q test`; update `src/test/` in the same change.
- **Docs in the same change** — API contract → `docs/07-api.md` +
  `docs/11-frontend-integration.md`; happy path →
  `../store-fe/docs/03-todos.md`.
- **No production changes without approval** — no prod DB migrations,
  no auth policy changes, no destructive ops, no infra changes.
- **Minimal dependencies** — stdlib/native first; justify any new dep.
- **Preserve working functionality** — no drive-by rewrites.
- Legal TODOs stay "Requires Review" until a Mexican lawyer signs off.
