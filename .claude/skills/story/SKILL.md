---
name: story
description: Use to take an ndopify story through its lifecycle - `groom <story-id>` (enrich a Backlog story and write its plan), `implement <story-id>` (code a Ready story and open its PRs), `review <pr-number>` (review and fix a story's PR until it is ready to merge). Triggers on "groom story", "implement story", "review PR" for this repo.
---

# Story workflow

You are a senior engineer on `ndopify-mono`. This skill moves a story through
`Backlog → Ready → In progress → In review → Done`.

**Arguments:** `$ARGUMENTS` is `<mode> <id>`. Modes: `groom <story-id>`, `implement <story-id>`, `review <pr-number>`.
If the mode or id is missing, ask for it — never guess the story.

**Merging:** only the library PRs (`ndopify-dto`, `ndopify-web-core`) are merged by this skill, once their checks
are green and nothing outside `modules/ndopify-dto/` / `modules/ndopify-web-core/` (and the root `pom.xml` version
properties) is in the diff: `gh pr merge <pr> --merge --delete-branch`. Every other PR is merged manually: when it's
ready, tell the user and wait for them to merge it.

## Story tracker

A story is an issue of `wutsi/ndopify-mono` (the story id is the issue number) on the GitHub Project `wutsi` #6.

- Read: `gh issue view <id> --comments`
- Update the description / title: `gh issue edit <id> --body-file <file> --title "<title>"`
- Comment: `gh issue comment <id> --body "<text>"`
- Read the status:
  `gh project item-list 6 --owner wutsi --format json --jq '.items[] | select(.content.number==<id>) | .status'`
- Change the status: find the item id, then set the `Status` field:

  ```bash
  ITEM=$(gh project item-list 6 --owner wutsi --format json --jq '.items[] | select(.content.number==<id>) | .id')
  gh project item-edit --project-id PVT_kwHOAlySnc4BrmpM --id "$ITEM" \
    --field-id PVTSSF_lAHOAlySnc4BrmpMzhp4mHE --single-select-option-id <option>
  ```

  Options: `Backlog` f75ad846, `Ready` 61e4505c, `In progress` 47fc9ee4, `In review` df73e18b, `Done` 98236657.
  If an id no longer matches, re-read them with `gh project field-list 6 --owner wutsi --format json`.

**Before any mode:** read the root `CLAUDE.md`, the `CLAUDE.md` of every module the story touches, and `MEMORY.md`.
Their rules win over this skill (ex: UI features are prototyped first and the plan validated before any
DTO/server/BFF edit; schema changes go in a new Flyway script).

**Stop and ask the user** instead of guessing when: a requirement is ambiguous, the story conflicts with a repo rule,
a build fails twice for the same reason, or a step would be irreversible and isn't listed here.

## Mode `groom <story-id>`

Precondition: the story is in `Backlog`. Otherwise stop and report its status.

1. **Enrich** — read the story and use `superpowers:brainstorming` to explore it. Ask the user every open question
   (one at a time); never answer one yourself. Add to the story:
   - context (why, who, which modules/pages/endpoints),
   - acceptance criteria, testable (Given/When/Then),
   - dependencies, risks, assumptions, and what is out of scope,
   - whether it changes `ndopify-dto` / `ndopify-web-core` (drives the PR order below).

   Update the story description, and its title if a shorter, clearer one fits.
2. **Plan** — use `superpowers:writing-plans`, keeping the path and filename it picks. Steps must be small enough for
   a coding agent, each with its tests and the module it touches.
3. **Publish the plan** — on an up-to-date `main`, commit only the plan file
   (`docs(<scope>): add the <topic> plan`) and push. Never include other changes in that commit.
4. Add the plan's GitHub link to the story and move it to `Ready`.

Done when: no open question left, the plan is on `origin/main`, the story is `Ready` and links the plan.

## Mode `implement <story-id>`

Precondition: the story is `Ready` and links a plan. Otherwise stop.

1. Move the story to `In progress`.
2. Read the story and its plan. Create a worktree with `superpowers:using-git-worktrees`, branch
   `feat/<scope>-<topic>`.
3. Execute the plan with `superpowers:subagent-driven-development` (independent steps) or `superpowers:executing-plans`,
   following `superpowers:test-driven-development`.
4. Coding requirements:
   - Follow the module's `CLAUDE.md` (code organization, test patterns, KDoc, French UI text, `kotlin.test`
     assertions, `TenantFormatter` for dates/amounts).
   - Every behavior change has a test; JaCoCo coverage of a touched module must not drop.
   - Before any push, use `superpowers:verification-before-completion`: `mvn verify` in each touched module
     (ktlint + tests + coverage gate; `ndopify-server` needs local MySQL). Don't push a red build.
5. **PR order** when the story changes shared libraries (a web app's CI can only use a released version):
   1. `ndopify-dto` PR → green checks → merge it → wait for `ndopify-dto-main.yml` to release
      (`gh run watch`, tag `ndopify-dto-<version>`).
   2. `ndopify-web-core` PR (if needed), with `<ndopify-dto.version>` in the root `pom.xml` set to that release →
      green → merge it → wait for the `ndopify-web-core-<version>` release.
   3. The PR for the other modules, with `<ndopify-dto.version>` / `<ndopify-web-core.version>` set to the released
      versions — never a `-SNAPSHOT`.
6. Each PR: conventional-commit title (`feat(<scope>): …`), body linking the plan, with `Refs #<id>` (`Closes #<id>` on
   the last PR only). Comment the PR link on the story, then move the story to `In review`.

Done when: every PR is open with green checks, and the story is `In review` with the PR links.

## Mode `review <pr-number>`

1. Wait for the checks: `gh pr checks <pr> --watch`. If red, read the failing log (`gh run view --log-failed`), fix,
   push, wait again.
2. Review with a reader not anchored on the author's reasoning: `/code-review` or a fresh subagent given the diff,
   the story and the plan — not this session's conclusions. Check the acceptance criteria and the repo rules.
3. Post the findings as inline PR comments. For each one, use `superpowers:receiving-code-review`: fix it (push,
   back to step 1) or reply why not.
4. When nothing is left and checks are green: a library PR (see *Merging*) is merged; any other PR, tell the user
   it's ready to merge, and stop. Don't merge it.
5. Once the user says it's merged, watch its `*-main.yml` run. When all the story's PRs are merged and their main
   builds are green, move the story to `Done`.

Done when: the PR is ready to merge with green checks, and the user knows it. The story is `Done` only after the user
has merged all its PRs and the main builds are green.
