# Ship sprint: the story skill, once per Ready story, in sequence

## Intent

Add `.claude/skills/ship-sprint/SKILL.md`: one run ships every story in `tracker.statuses.ready`, one at a time, each
in its own subagent that runs `story ship <id>`. The queue respects GitHub `blockedBy` links and excludes epics that
still have open sub-issues.

## Why

A sprint today is `ship <id>` typed once per story in the main context, in whatever order the user remembers. On this
board the Ready column sits inside epics (#360 ships under #351), and dependencies are recorded as `blockedBy` links
(#294 has one). Prose such as "Part of #294" or "after #297" is not reliable — "Part of" is hierarchy, not a blocker.
The order and the eligibility have to be computed once, and each story's phase chatter has to stay out of the main
context.

## Goals

- One command ships the Ready queue end to end.
- Dependency-aware order: topological on `blockedBy`; an unmet blocker stops before dispatch.
- Sub-issue-aware eligibility: a story with open sub-issues is an epic and is never dispatched.
- Per-story isolation: one `general` subagent per story runs `ship`; the main thread keeps one summary per story.
- Gates are relayed to the user and answered into the same child session; `blocked` stops the run.
- Resumable: the queue and progress live in a run file.

## Non-goals

- Changing the story skill, the agents, the profile, or the board.
- Grooming Triaged stories, moving statuses, closing epics.
- Shipping several stories in parallel.
- Parsing free text ("Part of #X", "depends on …") as dependencies.

## Anti-goals

- A silent fallback to running the phases in the main thread when the subagent is unavailable.
- Skipping an unmet blocker to "keep going".
- Queuing an epic because it is in Ready.
- Stopping the run on a gate instead of relaying the question to the same child session.

## Constraints

- OpenCode `experimental.subagent_depth` defaults to 1: a subagent cannot dispatch `analyst`, `developer`, `reviewer`
  or `deployer`. The run requires `2` in the project `opencode.json` (or `~/.config/opencode/opencode.json`).
- `gh project item-list` carries no dependency or hierarchy data: use GraphQL (`blockedBy`, `parent`, `subIssues`).
- The story skill's `ship` already resolves a mid-flight start point, so a resumed sprint needs no special casing.
- Profile values only: statuses from `tracker.statuses.*`, repository and default branch from
  `.claude/project-profile.md`.
- Skill prose only: no code, no test harness.

## Approaches

- **A — nested (chosen).** The sprint skill dispatches one `general` subagent per story; that subagent runs
  `story ship <id>` and keeps its own report. Main context holds one summary line per story.
- **B — flat.** The sprint skill dispatches the four phase agents itself. Rejected: it duplicates `ship`'s state
  resolution, and every phase report of every story lands in one context.
- **C — `opencode run` subprocess per story.** Rejected: a gate cannot be relayed into a separate process, so the run
  stalls on the first question.

## Decisions

### Trigger and description

```yaml
name: ship-sprint
description: Use when asked to ship a whole sprint — every story in Ready, one at a time — e.g. "ship the sprint",
  "ship all ready stories", "run the sprint", "ship-sprint". Not for one story (use `story`'s `ship`).
```

The dependency and sub-issue rules live in the body, not the description: the description says only when to use the
skill.

### Queue construction

1. **Snapshot** the project items whose status is `tracker.statuses.ready`, with `number`, `title`, `priority`,
   `parent`, `subIssues` (total and open) and `blockedBy` (number, state).
2. **Epics out.** An item with at least one open sub-issue is an epic: it is not queued, and it is reported as
   `#<id> — <done>/<total> children Done`. Its Ready children, if any, are queued normally.
3. **Blockers.** Every `blockedBy` node must be Done/closed. A blocker in the queue is ordered before its dependent. A
   blocker that is not Done and not in the queue stops the run with the `ship <blocker>` command to run first.
4. **Topological sort** on `blockedBy`; a cycle stops the run.
5. **Tie-break:** Priority (`P0` → `P1` → `P2` → unset), then issue number ascending.
6. **Explicit IDs** (`ship-sprint 360 375`) select a subset and override the tie-break, never an unmet blocker, never a
   story that is not Ready.

### What "proper sequence" means

- **Hierarchy** says "child before parent", never "child 1 before child 2". Parents are excluded, so siblings fall back
  to priority then number.
- **`blockedBy`** says "blocker before dependent".
- **Everything else** is issue number ascending.
- **Prose is ignored.** "Part of #294" is a parent link; "after #297" is only a hint until it is recorded as
  `blockedBy`.

### Run file

`docs/superpowers/sprints/YYYY-MM-DD-<slug>.md`, written before the first dispatch and updated after each story:

- the queue in order, with each story's reason (blocker, priority, number) and its parent;
- per story: status, PR, outcome, notes;
- the resume point.

A re-run reads the file, skips stories now Done, re-checks the blockers, and continues. Without a file, a run starts
from a fresh snapshot.

### Dispatch contract

Prompt, one per story:

```text
Run the `story` skill's `ship <id>` mode in <repository path>. You are its orchestrator: dispatch the analyst,
developer, reviewer and deployer agents as it says, and relay its gates. End your final message with exactly:

SPRINT-STORY: #<id>
OUTCOME: done|gate|blocked
PR: <number>|-
STATUS: <board status>|-
NOTES: <one line>
```

Keep the child session id: a gate is answered into the same session, never a new one.

### Report handling

| Child report | The sprint skill |
|---|---|
| `OUTCOME: done`, `STATUS` = Done | record the PR and status, dispatch the next story |
| `OUTCOME: done`, `STATUS` ≠ Done | stop: the report and the board disagree |
| `OUTCOME: gate` | show the question(s) or prototype paths, wait for the user, send the answer to the same session, wait again |
| `OUTCOME: blocked` | record the reason, stop the whole run |
| no report / unknown shape | stop; never guess the outcome |

### Before each dispatch

- Re-read the story's status; Done → skip it.
- Re-read its `blockedBy`; any blocker not Done → stop with the blocker id, its status and `ship <blocker>`.
- A story that gained open sub-issues since the snapshot → skip and report it as an epic.

### Prerequisite check

Before the first dispatch:

- `experimental.subagent_depth` ≥ 2, read from the project `opencode.json`, then `~/.config/opencode/opencode.json`.
  Missing or lower → stop with the exact line to add.
- The `general` subagent must be able to load the `story` skill. If it reports it cannot, the child reads
  `.claude/skills/story/SKILL.md` and follows it; if that is unreadable too, stop.

### Final report

One table: story, title, PR, status, outcome. Then the epics with their child progress, the stories skipped or
deferred, the leftover work, the run file path and the command to resume.

## Out of scope

- The story skill, the agents, the profile, the board schema, CI and deployment.
- Parallelism, grooming, triage and closing epics.

## Verification

`writing-skills` requires a failing baseline first: ask an agent to "ship all the Ready stories" without this skill and
record what it does — expected: a hand-rolled loop in the main thread that ignores `blockedBy` and epics.

Then:

- **Read-through:** every dependency and sub-issue rule appears once, in *Queue construction*; the report contract is
  exact; the skill contains no phase work (`gh project item-edit`, `gh pr merge`, `git push`, `mvn`, `feat/`).
- **Dispatch probe:** one Ready story end to end; the child session id is kept and a gate is relayed into it.
- **Negative probes:** depth 1 → stop with the config line; a missing blocker and a cycle → stop; an epic in Ready →
  excluded and reported with its progress; a story moved to Done between snapshot and dispatch → skipped.
