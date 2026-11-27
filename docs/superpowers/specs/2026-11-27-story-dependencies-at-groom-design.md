# Story dependencies at groom: the analyst writes `blockedBy`

## Intent

Make the board's dependency graph true at the moment a story becomes Ready: the analyst records every story-level
prerequisite as a GitHub `blockedBy` link while grooming, so `ship-sprint` orders the queue from links that reflect
the plan.

## Why

`ship-sprint` builds its queue only from `blockedBy` and deliberately ignores prose. Nothing in the workflow ever
creates that link: the analyst writes a *Dependencies* section in the story body and the plan, then moves the story to
Ready. The graph is therefore empty until someone remembers to set it by hand.

Evidence from the 2026-11-27 run:

- `#300` was dispatched first, then deferred: its body says *“Dependencies — #299 and #392 on `main`”*, but it had no
  `blockedBy` link and `#392` was still In progress.
- `#393` declares *“Prerequisite: #392 on `main`”* and had the same gap.
- All six stories then in Ready had zero `blockedBy` links.

The information existed at groom time; only the link was missing.

## Decision

The analyst sets the links at groom. `ship-sprint` is unchanged in how it reads the graph.

Alternatives considered and rejected:

- **A machine-readable body line** (`Blocked by: #392`) mirrored to the link: a second source of truth that will drift
  from the board.
- **A separate sprint readiness audit**: late (the sprint has already started), needs prose inference, and would run
  every sprint instead of once per story.

## The rule

A **story-level hard prerequisite** is a story whose merged work the plan builds on — the implementation cannot land
before it. For each one:

- **Link it** with `gh issue edit <id> --add-blocked-by <blocker>`, whatever the blocker's status (Backlog, Triaged,
  Ready, In progress). The link states the true order, not the blocker's readiness.
- **Do not link** a parent (`Part of #X` is hierarchy), a shared-code release (`Shared code: ndopify-dto`), a module
  prerequisite, or a closed story. A story that merely *mentions* another (“#360's interceptor is not on `main` yet —
  blocks nothing” in `#377`) is not a blocker.
- **Reconcile on re-groom**: the plan's dependencies are authoritative. Remove a link whose blocker the revised plan no
  longer requires (`--remove-blocked-by`), and report every link added or removed.
- **An open blocker does not prevent Ready.** Ready means “groomed”; the sprint queue orders by `blockedBy`. Gating
  Ready on a closed blocker would empty the column.

The prose *Dependencies* section stays — for humans and for the plan. The board link is the source of truth for tools.

## Where

- `.claude/agents/analyst.md`:
  - §1 *Enrich*: the *Dependencies* section names each story-level prerequisite.
  - §5 *Close the loop*: before the status moves to Ready, set or reconcile the `blockedBy` links.
  - *Done when*: every open story-level prerequisite named by the plan has a `blockedBy` link.
- `.claude/skills/ship-sprint/SKILL.md`: when a child reports `OUTCOME: blocked` because a prerequisite was not
  merged, the stop message names the missing `--add-blocked-by` command and the run file records it. The queue
  algorithm, prose policy and “never edit the board” contract are unchanged.

## Non-goals

- Parsing prose in `ship-sprint`.
- A new body syntax or a second dependency list.
- Changing the queue algorithm, the Ready definition, the statuses or the board schema.

## Verification

- Re-groom a story whose plan has a hard prerequisite: the GraphQL `blockedBy` link appears before Ready.
- Negative probes: `Part of #X`, a closed blocker, a shared-code release and a “blocks nothing” mention create no link.
- `ship-sprint` on `#300` / `#393`: `#392` is ordered before both (or the run stops naming `ship 392` because it is In
  progress), never `#300` first.
