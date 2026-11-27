# Ship Sprint Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `ship-sprint` skill that ships every Ready story once, in dependency order, one story per subagent.

**Architecture:** The skill is prose only. It snapshots the project board over GraphQL (status, `parent`, `subIssues`,
`blockedBy`), builds a dependency-ordered queue (epics with open sub-issues excluded), then dispatches one `general`
subagent per story to run `story ship <id>`. It relays gates into the same child session and keeps a run file for
resume.

**Tech Stack:** Markdown skill (`.claude/skills/ship-sprint/SKILL.md`), `gh` CLI + GraphQL, OpenCode subagents.

**Spec:** `docs/superpowers/specs/2026-11-27-ship-sprint-design.md`

## Global Constraints

- `experimental.subagent_depth` must be ≥ 2; OpenCode defaults to 1 and then a subagent cannot dispatch
  `analyst`/`developer`/`reviewer`/`deployer`.
- Repository values come from `.claude/project-profile.md` (`tracker.statuses.*`, `repository.*`); never hardcode a
  project number, status label or branch.
- Dependencies come only from GitHub `blockedBy`; sub-issue eligibility only from `subIssues`. Prose is never parsed.
- The skill never ships work itself: no `gh project item-edit`, `gh pr merge`, `gh pr create`, `git push`, `mvn`.
- Skill prose lives in `.claude/skills/ship-sprint/SKILL.md`; `.opencode` is a symlink to `.claude`, so no second copy.
- Commit messages follow the repo's `type(scope): subject` convention.

## Review Focus

- An epic sitting in Ready (this board: #294, #351) must be excluded and reported, never dispatched.
- A story blocked by a story outside the sprint that is not Done must stop the run with `ship <blocker>`.
- A story moved to Done between snapshot and dispatch must be skipped, not shipped twice.
- A child that reports the subagent depth limit must stop the run with the config fix, never fall back to the main
  thread.
- A gate question must be answered into the same child session, never a freshly dispatched one.

---

### Task 1: Record the RED baseline

**Files:**
- Modify: `docs/superpowers/plans/2026-11-27-ship-sprint.md` (this file, add an evidence section)

**Interfaces:**
- Produces: the verbatim baseline behavior that the skill must fix, and the synthetic board fixture reused in Task 4.

- [x] **Step 1: Fix the synthetic board scenario**

Use this fixture in the prompt (states are board statuses):

```text
#10 Ready, parent epic #1, Priority P1, no blockers
#11 Ready, Priority P2, blockedBy #13
#12 Ready, has sub-issues #20 and #21 both open, no blockers
#13 Ready, Priority P2, no blockers
#14 Ready, Priority P0, no blockers
#15 Ready, blockedBy #99 (open, not in this list)
#16 Ready, Priority P0, blockedBy #11
```

- [x] **Step 2: Dispatch a fresh `general` subagent without the skill**

Prompt: *"Here is a GitHub project board: <fixture>. In what order would you ship these stories for a sprint, which
would you skip, and when would you stop? Analysis only: do not run commands, do not modify files, do not dispatch
agents."*

- [x] **Step 3: Record the answer verbatim under `## Baseline (RED)`**

- [x] **Step 4: Confirm it fails at least one of the Review Focus rules**

Expected failures: #12 (epic) included, #15's out-of-sprint blocker ignored, or #16 ordered before #11. If it already
satisfies every rule, stop and report that the skill has nothing to fix.

---

### Task 2: Enable nested subagents

**Files:**
- Modify: `opencode.jsonc` (the project already had `experimental.subagent_depth: 2`)

**Interfaces:**
- Produces: `experimental.subagent_depth: 3`, so a `general` subagent can dispatch the story skill's phase agents,
  which dispatch their own (`developer`'s implementers, `analyst`'s `designer`).

- [x] **Step 1: Raise the project config**

```jsonc
"experimental": {
  "subagent_depth": 3
}
```

- [x] **Step 2: Verify the effective value**

Run: `opencode debug config | jq -r '[.[] | .info.experimental.subagent_depth // empty] | last'`
Expected: `3`

- [x] **Step 3: Note the restart**

The setting is read at startup; a running OpenCode service must restart before the nested dispatch works.

- [x] **Step 4: Commit**

```bash
git add opencode.jsonc
git commit -m "chore(agents): allow subagents to dispatch the story agents"
```

---

### Task 3: Write the skill

**Files:**
- Create: `.claude/skills/ship-sprint/SKILL.md`

**Interfaces:**
- Consumes: the story skill's `ship <id>` mode and its report.
- Produces: the per-story dispatch prompt and report contract used by Task 4:
  `SPRINT-STORY: #<id>` / `OUTCOME: done|gate|blocked` / `PR: <n>|-` / `STATUS: <board status>|-` / `NOTES: <one line>`.

- [x] **Step 1: Write the frontmatter**

```yaml
---
name: ship-sprint
description: Use when asked to ship a whole sprint — every story in Ready, one at a time — e.g. "ship the sprint",
  "ship all ready stories", "run the sprint", "ship-sprint". Not for one story (use `story`'s `ship`).
---
```

- [x] **Step 2: Write the body, in this order**

1. **Purpose** — orchestrates; never ships, reviews, merges, pushes or edits the board itself.
2. **Arguments** — optional explicit story ids; none means every Ready story.
3. **Read first** — `AGENTS.md`/`CLAUDE.md`, `MEMORY.md` when present, `.claude/project-profile.md`; stop if the
   profile is missing or its `platform` is not `github`.
4. **Preconditions** — `subagent_depth` ≥ 3 read from `opencode debug config` (last document wins); missing or lower →
   stop with the exact JSON line to add. The `general` subagent must be able to load the `story` skill; if it cannot,
   it reads `.claude/skills/story/SKILL.md` instead; if that is unreadable, stop.
5. **Snapshot** — `gh project item-list` for the Ready items, then one GraphQL read per item:

   ```bash
   gh api graphql -f query='
   query($owner:String!, $repo:String!, $n:Int!) {
     repository(owner:$owner, name:$repo) {
       issue(number:$n) {
         number title state
         parent { number state }
         subIssues(first:100) { totalCount nodes { number state } }
         blockedBy(first:50) { nodes { number state } }
       }
     }
   }' -f owner=<owner> -f repo=<repo> -F n=<id>
   ```

6. **Queue construction**, in this order:
   - drop an item with at least one open sub-issue; report it with `<done>/<total>` children Done;
   - drop a Done item;
   - every `blockedBy` must be Done/closed; a blocker in the queue is ordered before; a blocker that is not Done and
     not in the queue stops the run with `ship <blocker>`;
   - topological sort on `blockedBy`; a cycle stops;
   - tie-break Priority (`P0`, `P1`, `P2`, unset) then issue number;
   - explicit ids select a subset and reorder ties only.
7. **Run file** — `docs/superpowers/sprints/YYYY-MM-DD-<slug>.md`, written before the first dispatch and updated after
   each story: the ordered queue with each reason and parent, then `#id | status | PR | outcome | notes`.
8. **Dispatch** — one `general` subagent per story:

   ```text
   Run the `story` skill's `ship <id>` mode in <repository path>. You are its orchestrator: dispatch the analyst,
   developer, reviewer and deployer agents as it says, and relay its gates. End your final message with exactly:
   SPRINT-STORY: #<id>
   OUTCOME: done|gate|blocked
   PR: <number>|-
   STATUS: <board status>|-
   NOTES: <one line>
   ```

   Keep the child session id.
9. **Before each dispatch** — re-read the story's status (Done → skip) and its `blockedBy` (unmet → stop); a story that
   gained open sub-issues → skip and report as an epic.
10. **Report handling** — `done` + Done → record and continue; `done` + not Done → stop; `gate` → show the question or
    prototype paths, wait, send the answer to the same session, wait again; `blocked` → record and stop; no report →
    stop.
11. **Final report** — table (story, title, PR, status, outcome), epics with child progress, skipped/deferred stories,
    leftovers, run file path, resume command.
12. **Done when** — every queued story is Done, or the run stopped on a gate/block with the run file naming the
    resume point.

- [x] **Step 3: Check the skill contains no phase work**

Run: `grep -nE "gh project item-edit|gh pr merge|gh pr create|git push|mvn|feat/" .claude/skills/ship-sprint/SKILL.md`
Expected: no output.

---

### Task 4: GREEN — apply the skill to the fixture

**Files:**
- Modify: `docs/superpowers/plans/2026-11-27-ship-sprint.md` (evidence section)

**Interfaces:**
- Consumes: `.claude/skills/ship-sprint/SKILL.md` from Task 3 and the fixture from Task 1.

- [x] **Step 1: Dispatch a fresh `general` subagent with the skill**

Prompt: *"Read `.claude/skills/ship-sprint/SKILL.md` and apply its queue construction to this board: <fixture>. Do not
execute anything; output the queue, the exclusions and any stop."*

- [x] **Step 2: Check the expected output**

Expected: `#14` (P0), `#10` (P1), `#13` (P2, before its dependent), `#11`, `#16` (P0 but blocked by #11); `#12`
excluded as an epic (`0/2`); the run stops on `#15` blocked by `#99`. (The first draft of this step omitted `#10`,
which is Ready and unblocked; the implementation output was right and the expectation wrong.)

- [x] **Step 3: If the output differs, fix the wording that failed and re-run Step 1**

Close the loophole in the skill, not in the test. Re-run until Step 2 matches.

- [x] **Step 4: Commit**

```bash
git add .claude/skills/ship-sprint/SKILL.md opencode.json docs/superpowers/plans/2026-11-27-ship-sprint.md
git commit -m "feat(agents): ship a sprint one story at a time in its own subagent"
```

---

### Task 5: Negative probes and read-through

**Files:**
- Modify: `.claude/skills/ship-sprint/SKILL.md` only if a probe exposes a gap.

- [x] **Step 1: Epic-only board**

Ask a fresh subagent with the skill: a board with a single Ready epic (3 open children). Expected: excluded, reported
with `0/3`, no dispatch, no error.

- [x] **Step 2: Story moved to Done**

Ask: the fixture plus "#13 moved to Done after the snapshot". Expected: #13 skipped, #11 and #16 still ordered after it
in the queue.

- [x] **Step 3: Depth limit**

Ask: "The first child reports `Subagent depth limit reached (1)`." Expected: the run stops and prints the exact
`experimental.subagent_depth` line; no fallback to the main thread.

- [x] **Step 4: Gate relay**

Ask: "The child reports `OUTCOME: gate` with a prototype path." Expected: the skill shows the path, waits for the user,
and resumes the *same* session id.

- [x] **Step 5: Read-through against the spec**

Walk `docs/superpowers/specs/2026-11-27-ship-sprint-design.md` section by section; every decision must appear in the
skill exactly once.

---

### Task 6: Document and commit

**Files:**
- Modify: `README.md` (the Skills table, after the `story` row)

**Interfaces:**
- Produces: the README row that lets a human find the skill.

- [x] **Step 1: Add the row**

```markdown
| [`ship-sprint`](.claude/skills/ship-sprint/SKILL.md) | Ships every story in Ready, one at a time: dependency order from `blockedBy`, epics with open sub-issues excluded, one subagent per story running `story ship <id>`. |
```

- [x] **Step 2: Verify the badge block is untouched**

Run: `git diff README.md`
Expected: only the new table row.

- [x] **Step 3: Commit**

```bash
git add README.md docs/superpowers/specs/2026-11-27-ship-sprint-design.md
git commit -m "docs(agents): document the ship-sprint skill"
```

---

## Baseline (RED)

Run on 2026-11-27 against a clean tree (the design doc and plan were moved out of the repository first; an earlier run
in the same tree read them and echoed the design, so it was discarded). Prompt: *"Ship all the stories that are in
Ready, as one sprint. Produce the plan you would follow…"*, read-only.

Board at the time: #360 and #375 in Ready, both children of Backlog epics (#351 and #294), no `blockedBy` links.

Observed failures against the spec:

- **Dependencies from prose.** It qualified #375 by reading *"#297 and #299 on main"* from the story/plan text. It
  never queried `blockedBy`. A story whose prose is stale or wrong would be shipped or refused on a guess.
- **Parents inferred, not read.** It identified #351 and #294 from the story text and issue titles; no `subIssues`
  query. An epic in Ready, or a Ready story that gained open children, would not be detected.
- **No story-level isolation.** It delegated phases through the story skill in the main thread; nothing kept each
  story's phase chatter out of the orchestrator, and nothing checked `experimental.subagent_depth`.
- **No run file.** Nothing recorded the queue or the resume point; a stop mid-sprint loses the order.
- **No out-of-sprint blocker rule.** It had no rule for a blocker that is not Done and not in the queue.
- **No report contract.** The child's final message shape was unspecified, so a report cannot be parsed reliably.

The parts it got right — one story at a time, never an epic, sequential PRs, gates relayed — are kept.

---

## Fix pass (final review)

The whole-branch review found two run-blocking defects and several correctness gaps; all Critical and Important
findings were fixed in one pass.

- **Run file dirtying the checkout (Critical).** `developer.md:71` requires `git status --porcelain` to print nothing,
  and `docs/superpowers/sprints/` was not ignored. Added the directory to `.gitignore` (mirroring `docs/reviews/`), and
  the skill now says never commit or leave the run file untracked.
- **Board bootstrap (Critical).** `gh issue view --json projectItems` returns a title only, and a run with no arguments
  has no story to start from. Added `tracker.project` to `.claude/project-profile.md`, resolved with
  `gh project list`, and used the repository owner (not the project owner) for the GraphQL `repository(owner:)`.
- **Priority (Important).** `gh project item-list --format json` omits custom fields. Priority now comes from the
  issue's `projectItems.fieldValues`, filtered on `field.name == "Priority"`.
- **Explicit ids (Important).** They are intersected with Ready before the blocker check, and their given order wins
  among the selected stories except where `blockedBy` constrains them.
- **Gate contract (Important).** Added `GATE: question|prototype|-` and allowed multi-line `NOTES`; an unresumable
  child session is now a stop-and-ask, never a fresh child.
- **Depth (Important).** The chain is `main → sprint child → phase agent → the phase's own subagents`, so the setting
  is 3, not 2. Raised it in the existing `opencode.jsonc` and deleted the duplicate `opencode.json` the first draft
  added.
- **Config reading (Important).** The skill reads the effective value from `opencode debug config`, not one file.
- **Live dispatch probe (Important, deferred).** A nested dispatch needs the OpenCode service restarted after the
  depth change; this session cannot restart it. The precondition check and the depth-limit block are the evidence
  until then; run the probe after the restart.
- **Minors.** Profile status values in prose, a `Resume:` line in the run file, blocker checks on the closed issue
  (the deployer closes at Done), `content.type == "Issue"` in the snapshot, a truncation stop, placeholder ids in the
  template, the `ship-sprint 360 375` argument example, and the `general` agent named explicitly.
