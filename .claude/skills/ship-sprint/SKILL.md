---
name: ship-sprint
description: Use when asked to ship a whole sprint — every story in Ready, one at a time — e.g. "ship the sprint", "ship all ready stories", "run the sprint", "ship-sprint". Not for one story (use `story`'s `ship`).
---

# Ship sprint

You ship the stories in `tracker.statuses.ready`, one at a time, each in its own subagent running the `story` skill's
`ship <id>`. You compute the queue, dispatch, relay gates and record progress. You never ship, review, merge, push or
edit the board yourself.

**Arguments:** `$ARGUMENTS` is an optional list of story ids. Without it, every story in Ready. An id that is not in
Ready is not added.

**Read first:** the repository's agent instructions (`AGENTS.md`, or `CLAUDE.md` when there is no `AGENTS.md`),
`MEMORY.md` when it exists, and the project profile (`.claude/project-profile.md`). Their rules win. The profile
supplies `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch`. If the profile is missing or its
`platform` is not `github`, stop and ask.

## Preconditions

- `experimental.subagent_depth` must be at least 2: read `opencode.json`, then `~/.config/opencode/opencode.json`.
  Missing or lower — stop with the line to add: `"experimental": { "subagent_depth": 2 }`. Never fall back to running
  the stories in the main thread.
- The dispatched subagent must load the `story` skill. If it reports that it cannot, it reads
  `.claude/skills/story/SKILL.md` and follows it; if that is unreadable, stop.
- Never guess a story, a blocker or a pull request. When a value is ambiguous, stop and ask.

## Snapshot

Read the Ready items, then enrich each with one GraphQL call — `gh project item-list` returns neither dependencies nor
hierarchy:

```bash
gh project item-list <project> --owner <owner> --limit 1000 --format json \
  --jq '.items[] | select(.status=="<tracker.statuses.ready>")
        | {number: .content.number, title, priority}'

gh api graphql -f query='
query($owner:String!, $repo:String!, $n:Int!) {
  repository(owner:$owner, name:$repo) {
    issue(number:$n) {
      number title state
      parent { number state }
      subIssues(first:100) { totalCount nodes { number state } }
      blockedBy(first:50) { nodes { number title state } }
    }
  }
}' -f owner=<owner> -f repo=<repo> -F n=<id>
```

The project number and owner come from a Ready story's `projectItems` (`gh issue view <id> --json projectItems`). If
several projects or items match, stop and ask.

## Queue

Build the queue once, in this order:

1. **Drop an epic.** An item with at least one open sub-issue is a parent: never queued. Report it as
   `#<id> — <done>/<total> children Done`. Its own status is irrelevant: a child ships while its parent is Backlog.
2. **Drop a Done item.**
3. **Check the blockers.** Every `blockedBy` node must be `tracker.statuses.done` or closed. A blocker in the queue is
   ordered before its dependent. A blocker that is neither Done nor in the queue stops the run before the first
   dispatch, with the `ship <blocker>` command to run first.
4. **Sort** topologically on `blockedBy`; a cycle stops the run.
5. **Break ties** by Priority (`P0`, `P1`, `P2`, unset), then issue number ascending.
6. **Apply the explicit ids**, when given: they select a subset and reorder ties, never bypass a blocker, never add a
   story that is not Ready.

Hierarchy says "child before parent", never "sibling 1 before sibling 2". Dependencies come only from `blockedBy`;
prose such as "Part of #294" or "after #297" is ignored.

## Run file

Write `docs/superpowers/sprints/YYYY-MM-DD-<slug>.md` before the first dispatch:

```markdown
# Sprint <date>

| # | Title | Parent | Reason in the queue |
|---|---|---|---|
| 360 | … | #351 | P0, no blocker |

| # | Status | PR | Outcome | Notes |
|---|---|---|---|---|
```

Update it after each story. A re-run reads it first: skip the stories now Done, re-check the blockers, continue from
the resume point. Without a file, start from a fresh snapshot.

## Dispatch

One subagent per story, sequentially. Never two in flight. Use the harness's subagent tool with a general-purpose
agent, the story id and the repository path:

```text
Run the `story` skill's `ship <id>` mode in <repository path>. You are its orchestrator: dispatch the analyst,
developer, reviewer and deployer agents as it says, and relay its gates. End your final message with exactly:

SPRINT-STORY: #<id>
OUTCOME: done|gate|blocked
PR: <number>|-
STATUS: <board status>|-
NOTES: <one line>
```

Keep the child session id and read only its final report, never its tool transcript.

## Before each dispatch

- Re-read the story's status; Done → skip it and update the run file.
- Re-read its `blockedBy`; a blocker not Done → stop with the blocker's id, its status and `ship <blocker>`.
- An item that gained open sub-issues since the snapshot → skip it and report it as an epic.

## Reports

| Report | You |
|---|---|
| `OUTCOME: done`, `STATUS` is `tracker.statuses.done` | record the PR and status, dispatch the next story |
| `OUTCOME: done`, any other status | stop: the report and the board disagree |
| `OUTCOME: gate` | show the question(s) or the prototype paths, wait for the user, send the answer to the same session, wait again |
| `OUTCOME: blocked` | record the reason, stop the run |
| missing or unreadable | stop; never guess |

A child that reports the subagent depth limit is a blocked run: report the `experimental.subagent_depth` line and
stop.

## Report

One table — story, title, PR, status, outcome — then the epics with their child progress, the stories skipped or
deferred and why, the leftover work, the run file path and the command to resume. Do not repeat the children's tool
output.

## Stop and ask

- the profile is missing or its `platform` is not GitHub;
- `experimental.subagent_depth` is below 2, or a child hits the limit;
- the project or an item is ambiguous;
- the queue has a cycle, or a blocker that is neither Done nor in the queue;
- a gate needs the user's answer, a child is blocked, or its report and the board disagree.

## Done when

Every queued story is `tracker.statuses.done`, or the run stopped on a gate or a block with the run file naming the
state and the exact resume point.
