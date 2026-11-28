---
name: ship-sprint
description: Use when asked to ship a whole sprint — every story in Ready, up to two at a time with merges serialized — e.g. "ship the sprint", "ship all ready stories", "run the sprint", "ship-sprint". Not for one story (use `story`'s `ship`).
---

# Ship sprint

You ship the stories in `tracker.statuses.ready` in at most two lanes. A lane is one subagent running the `story`
skill's `ship <id> --to-review`: it takes the story from `ready` to an approved pull request and stops. You merge the
approved pull requests one at a time. You compute the queue, pair the lanes, dispatch, relay gates, merge and record
progress. You never ship, review, merge, push or edit the board yourself.

**Arguments:** `$ARGUMENTS` is an optional list of story ids, e.g. `ship-sprint 360 375`. Without it, every story in
Ready. An id that is not in Ready is not added.

**Read first:** the repository's agent instructions (`AGENTS.md`, or `CLAUDE.md` when there is no `AGENTS.md`),
`MEMORY.md` when it exists, and the project profile (`.claude/project-profile.md`). Their rules win. The profile
supplies `tracker.project`, `tracker.statuses.*`, `repository.story_ref`, `repository.default_branch`, `plans.dir`,
`version_file` and `shared_modules`. If the profile is missing or its `platform` is not `github`, stop and ask.

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
- The harness must run a subagent in the background. When it cannot, run one lane and say so in the report.
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

### Plan modules

For every queued story, read its plan at `<plans.dir>/<id>-*.md` and collect every `modules/<name>` path it names in
*Global Constraints* and *File Structure*. A story whose plan is missing, or whose modules cannot be read there, is a
stop and ask — never guess them from the title. These modules are the *Modules* column of the run file and the input
of the pairing rule.

## Lanes

- **Two lane children at most**, always in queue order: lane A takes the first story, lane B the first story the
  pairing rule allows beside it. A lane is free again when its child reports `staged`, `done`, `gate` or `blocked`;
  a staged story does not occupy its lane, and neither does one that finished.
- **One merge child at most.** A staged pull request is merged by its own `merge <pr-number>` child, dispatched only
  when no other merge child is running. The lanes keep running while a merge does. Merges go in approval order: a
  story is dispatched only once its blockers are Done, so its branch already carries them and approval order never
  inverts the queue.
- **The pairing rule.** Two stories may run at the same time only when all of these hold; otherwise the second waits
  for a free lane:
  1. neither plan touches a `shared_modules[].path`: a release and a `fix dependencies` commit race in the
     `version_file`;
  2. neither plan touches the profile's `version_file` or `.github/workflows/`;
  3. at most one of the two touches `modules/ndopify-server`: its tests run against one MySQL schema and one GreenMail
     port (3025), so two suites at once corrupt each other. A merge child builds nothing locally — the deployer only
     merges and watches CI — so it counts for nothing here.
- **Two stories may share any other module.** Their merges are serial, so an overlap surfaces as a merge conflict in
  the second one, and the deployer stops on it instead of guessing.
- **A lane's gate does not stop the other lane**, and the merge slot does not gate the lanes.

## Run file

Write `docs/superpowers/sprints/YYYY-MM-DD-<slug>.md` before the first dispatch. The directory is gitignored: the
developer stops on a dirty primary checkout, so never commit the run file and never leave it untracked in the
repository.

```markdown
# Sprint <date>

Resume: lane A #<id> (<phase>); lane B #<id> (<phase>); merges: <pr>, <pr>
Lanes: <1|2> — merges: 1 at a time

| # | Lane | Title | Parent | Modules | Reason in the queue |
|---|---|---|---|---|---|
| <id> | A | … | #<parent> | server, dto | P0, no blocker |

## Merge queue

| PR | Story | Staged at |
|---|---|---|

## Progress

| # | Status | PR | Outcome | Notes |
|---|---|---|---|---|
```

Update it after each report **and before stopping on a gate or a block**, including the `Resume:` line: both lanes'
stories and phases, and the merge queue in order. A re-run reads it first: skip the stories now Done, finish or drop
the merge queue, re-check the blockers and the pairing rule, and continue from the resume point. A queue head left
blocked — a red deployment, a refused merge — is re-dispatched as `merge <pr-number>` once its cause is fixed. Without
a file, start from a fresh snapshot.

## Dispatch

### A lane

Dispatch with the `general` agent, the story id and the repository path:

```text
Run the `story` skill's `ship <id> --to-review` mode in <repository path>: developer then reviewer, stopping before
the deployer. You are its orchestrator: dispatch the developer and reviewer agents as it says, and relay its gates.
A library-only story ends the chain at the developer. End your final message with exactly:

SPRINT-STORY: #<id>
OUTCOME: staged|done|gate|blocked
GATE: question|prototype|-
PR: <number>|-
STATUS: <board status>|-
NOTES: <one or more lines; for GATE, one question per line or the prototype paths>
```

Start a lane in the background and wait for its completion; two lanes may run together, and you never poll for a
report. Keep each lane's session id and read only its final report, never its tool transcript. Read the whole report:
`NOTES` carries the questions or the prototype paths, one per line.

### A merge

Only when no other merge child is running:

```text
Run the `merge <pr-number>` mode of the `story` skill in <repository path>. End your final message with exactly:

SPRINT-MERGE: #<pr>
OUTCOME: done|gate|blocked
STATUS: <board status>|-
NOTES: <one or more lines>
```

## Before each dispatch

- Re-read the story's status; Done → skip it and update the run file.
- Re-read its `blockedBy`; a blocker not closed → stop with the blocker's id, its state and `ship <blocker>`.
- An item that gained open sub-issues since the snapshot → skip it and report it as an epic.
- A lane dispatch: re-read the other lane's story and modules and apply the pairing rule again.

## Reports

| Report | You |
|---|---|
| lane `staged`, `STATUS` is `tracker.statuses.review` | record the PR, add it to the merge queue, free the lane, dispatch the next story |
| lane `done`, `STATUS` is `tracker.statuses.done` (library-only) | record it, free the lane, dispatch the next story |
| lane `done`, any other status | stop: the report and the board disagree |
| lane `gate` | show `NOTES` labelled with its story id — two lanes can gate at once, so label both — wait for the answer, send it to that lane's session, wait again |
| lane `blocked` | record the reason, stop the run; when the reason is an unmerged story prerequisite, name the exact `gh issue edit <story> --add-blocked-by <blocker>` command and record it in the run file |
| merge `done`, `STATUS` is `tracker.statuses.done` | record it, drop it from the merge queue, dispatch the next merge or story |
| merge `blocked` — red deployment, refused merge, surviving worktree or branch | record the reason, stop the run; the story stays in `tracker.statuses.review` |
| missing, unreadable or no contract block | stop; never guess |

If the harness cannot resume the child session, stop and ask. Never dispatch a new child to answer a gate: the
questions and the answers live only in the session that asked.

A stop never kills a lane already running: let it finish, record its report in the run file, and dispatch nothing new
— not even the merge of a pull request it stages.

A child that reports the subagent depth limit is a blocked run: report the `experimental.subagent_depth` line and
stop.

## Report

One table — story, lane, title, PR, status, outcome — then the merge queue left over, the epics with their child
progress, the stories skipped or deferred and why, the leftover work, the run file path and the command to resume. Do
not repeat the children's tool output.

## Stop and ask

- the profile is missing or its `platform` is not GitHub;
- `experimental.subagent_depth` is below 3, or a child hits the limit;
- the board cannot be resolved to exactly one project;
- the queue has a cycle, or a blocker that is neither closed nor in the queue;
- a queued story's plan is missing, or its modules cannot be read from it;
- a gate needs the user's answer, a child is blocked, or its report and the board disagree;
- the child session cannot be resumed.

## Done when

Every queued story is `tracker.statuses.done` and the merge queue is empty, or the run stopped on a gate or a block
with the run file naming the state of both lanes, the merge queue and the exact resume point.
