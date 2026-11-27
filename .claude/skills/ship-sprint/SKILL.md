---
name: ship-sprint
description: Use when asked to ship a whole sprint — every story in Ready, one at a time — e.g. "ship the sprint", "ship all ready stories", "run the sprint", "ship-sprint". Not for one story (use `story`'s `ship`).
---

# Ship sprint

You ship the stories in `tracker.statuses.ready`, one at a time, each in its own subagent running the `story` skill's
`ship <id>`. You compute the queue, dispatch, relay gates and record progress. You never ship, review, merge, push or
edit the board yourself.

**Arguments:** `$ARGUMENTS` is an optional list of story ids, e.g. `ship-sprint 360 375`. Without it, every story in
Ready. An id that is not in Ready is not added.

**Read first:** the repository's agent instructions (`AGENTS.md`, or `CLAUDE.md` when there is no `AGENTS.md`),
`MEMORY.md` when it exists, and the project profile (`.claude/project-profile.md`). Their rules win. The profile
supplies `tracker.project`, `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch`. If the profile
is missing or its `platform` is not `github`, stop and ask.

## Preconditions

- `experimental.subagent_depth` must be at least 3: the chain is you → the story subagent → its phase agents → their
  own subagents (the developer's implementation agents, the analyst's designer). Read the effective value:

  ```bash
  opencode debug config | jq -r '[.[] | .info.experimental.subagent_depth // empty] | last // "unset"'
  ```

  `unset` or lower than 3 — stop with the line to add to `opencode.json`/`opencode.jsonc`:
  `"experimental": { "subagent_depth": 3 }`. Never fall back to running the stories in the main thread.
- The dispatched `general` subagent must load the `story` skill. If it reports that it cannot, it reads
  `.claude/skills/story/SKILL.md` and follows it; if that is unreadable, stop.
- Never guess a story, a blocker or a pull request. When a value is ambiguous, stop and ask.

## Snapshot

Resolve the board from the profile, not from a story — a run without arguments has no story to start from:

```bash
OWNER=$(gh repo view --json owner --jq .owner.login)
gh project list --owner "$OWNER" --format json \
  --jq '.projects[] | select(.title=="<tracker.project>") | .number'
```

If nothing matches, try `--owner @me`. If still nothing, or several projects match, stop and ask.

Read the Ready items, then enrich each with one GraphQL call — `gh project item-list` returns neither dependencies,
hierarchy nor the Priority field:

```bash
gh project item-list <project> --owner "$OWNER" --limit 1000 --format json \
  --jq '.items[] | select(.content.type=="Issue" and .status=="<tracker.statuses.ready>")
        | {number: .content.number, title}'

gh api graphql -f query='
query($owner:String!, $repo:String!, $n:Int!) {
  repository(owner:$owner, name:$repo) {
    issue(number:$n) {
      number title state
      parent { number state }
      subIssues(first:100) { totalCount nodes { number state } }
      blockedBy(first:50) { totalCount nodes { number title state } }
      projectItems(first:10) {
        nodes { fieldValues(first:50) { nodes {
          ... on ProjectV2ItemFieldSingleSelectValue {
            name
            field { ... on ProjectV2SingleSelectField { name } }
          }
        } } }
      }
    }
  }
}' -f owner=<repository owner> -f repo=<repository name> -F n=<id>
```

`<repository owner>` and `<repository name>` come from `gh repo view --json nameWithOwner`; they are not the project
owner when the board belongs to a user and the repository to an organization. Priority is the value whose
`field.name` is `Priority`. A page that fills `subIssues(first:100)` or `blockedBy(first:50)` is a stop and ask, never
a silently truncated queue.

## Queue

Build the queue once, in this order:

1. **Keep the explicit ids**, when given: intersect them with the Ready items. A story that is not Ready is not added.
2. **Drop an epic.** An item with at least one open sub-issue is a parent: never queued. Report it as
   `#<id> — <closed>/<total> children Done`. Its own status is irrelevant: a child ships while its parent is in
   `tracker.statuses.backlog`.
3. **Drop a Done item.**
4. **Check the blockers.** Every `blockedBy` node must be closed (the deployer closes a story at Done). A blocker in
   the queue is ordered before its dependent. A blocker that is neither closed nor in the queue stops the run before
   the first dispatch, with the `ship <blocker>` command to run first.
5. **Sort** topologically on `blockedBy`; a cycle stops the run.
6. **Break ties** by Priority (`P0`, `P1`, `P2`, unset), then issue number ascending.
7. **Explicit ids order the result**, when given: the order you were given wins among the selected stories, except
   where `blockedBy` constrains them. It never bypasses a blocker and never adds a story that is not Ready.

Hierarchy says "child before parent", never "sibling 1 before sibling 2". Dependencies come only from `blockedBy`;
prose such as "Part of #294" or "after #297" is ignored.

## Run file

Write `docs/superpowers/sprints/YYYY-MM-DD-<slug>.md` before the first dispatch. The directory is gitignored: the
developer stops on a dirty primary checkout, so never commit the run file and never leave it untracked in the
repository.

```markdown
# Sprint <date>

Resume: <story id, or "complete">

| # | Title | Parent | Reason in the queue |
|---|---|---|---|
| <id> | … | #<parent> | P0, no blocker |

| # | Status | PR | Outcome | Notes |
|---|---|---|---|---|
```

Update it after each story **and before stopping on a gate or a block**, including the `Resume:` line. A re-run reads
it first: skip the stories now Done, re-check the blockers, continue from the resume point. Without a file, start from
a fresh snapshot.

## Dispatch

One subagent per story, sequentially. Never two in flight. Use the harness's subagent tool with the `general` agent,
the story id and the repository path:

```text
Run the `story` skill's `ship <id>` mode in <repository path>. You are its orchestrator: dispatch the analyst,
developer, reviewer and deployer agents as it says, and relay its gates. End your final message with exactly:

SPRINT-STORY: #<id>
OUTCOME: done|gate|blocked
GATE: question|prototype|-
PR: <number>|-
STATUS: <board status>|-
NOTES: <one or more lines; for GATE, one question per line or the prototype paths>
```

Keep the child session id and read only its final report, never its tool transcript. Read the whole report: `NOTES`
carries the questions or the prototype paths, one per line.

## Before each dispatch

- Re-read the story's status; Done → skip it and update the run file.
- Re-read its `blockedBy`; a blocker not closed → stop with the blocker's id, its state and `ship <blocker>`.
- An item that gained open sub-issues since the snapshot → skip it and report it as an epic.

## Reports

| Report | You |
|---|---|
| `OUTCOME: done`, `STATUS` is `tracker.statuses.done` | record the PR and status, dispatch the next story |
| `OUTCOME: done`, any other status | stop: the report and the board disagree |
| `OUTCOME: gate` | show `NOTES` — the question(s) or the prototype paths — wait for the user, send the answer to the same session, wait again |
| `OUTCOME: blocked` | record the reason, stop the run; when the reason is an unmerged story prerequisite, name the exact `gh issue edit <story> --add-blocked-by <blocker>` command and record it in the run file |
| missing, unreadable or no contract block | stop; never guess |

If the harness cannot resume the child session, stop and ask. Never dispatch a new child to answer a gate: the
questions and the answers live only in the session that asked.

A child that reports the subagent depth limit is a blocked run: report the `experimental.subagent_depth` line and
stop.

## Report

One table — story, title, PR, status, outcome — then the epics with their child progress, the stories skipped or
deferred and why, the leftover work, the run file path and the command to resume. Do not repeat the children's tool
output.

## Stop and ask

- the profile is missing or its `platform` is not GitHub;
- `experimental.subagent_depth` is below 3, or a child hits the limit;
- the board cannot be resolved to exactly one project;
- the queue has a cycle, or a blocker that is neither closed nor in the queue;
- a gate needs the user's answer, a child is blocked, or its report and the board disagree;
- the child session cannot be resumed.

## Done when

Every queued story is `tracker.statuses.done`, or the run stopped on a gate or a block with the run file naming the
state and the exact resume point.
