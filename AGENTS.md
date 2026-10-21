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
