# Story Agent Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the `story` skill as a project-agnostic router that dispatches the `analyst`, `developer`, `reviewer` and `deployer` agents instead of re-implementing their procedures.

**Architecture:** One Markdown rewrite of `.claude/skills/story/SKILL.md` plus the `README.md` row. The skill keeps its mode names, reads `.claude/project-profile.md` for statuses/story-ref/branch, dispatches one named agent per mode, relays gates into the same agent session, and stops on `blocked`. The designer stays the analyst's child. No phase procedure remains in the skill.

**Tech Stack:** Markdown, YAML frontmatter, the OpenCode/Claude Code subagent tool, GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-11-22-story-agent-orchestration-design.md`

## Global Constraints

- Create the branch `chore/story-agent-orchestration` from `main` before editing. Stay on it.
- Touch only `.claude/skills/story/SKILL.md`, `README.md`, and the design/plan docs. Never stage or revert the other dirty files: `.claude/agents/deployer.md` (local model edit), `DESIGN.md`, `.AGENTS.md`, `.opencode`, the two `icon-*.png`.
- The skill file has pre-existing uncommitted edits. The rewrite replaces the whole file and supersedes them; do not try to keep the old procedure text.
- The skill contains no repository value: no `wutsi`, `ndopify`, `main`, `In review`, `Done`, board/project/field ids, module names, `mvn`, `gh project item-edit`, `gh pr merge`, `gh pr create` or `git push`. It uses `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch` from the profile.
- The skill never does phase work: no code, commit, review, merge, push or board edit. Only `ship` reads the story status and its PR to pick a start point.
- Unknown agent → `OUTCOME: blocked`, never a fallback to `general` or inline prose.
- Commit prefixes: `docs(agents): …` for the design/plan, `refactor(story): …` for the skill, `docs(story): …` for the README.

## Review Focus

- **Gate answered in a new session:** a question or prototype approval resumed in a fresh agent loses the phase's context. Pinned by "resume the same child session; re-dispatch the same agent only when the harness cannot resume".
- **Wrong `ship` start point:** a merged PR sent to `reviewer`, or an In-review story without a PR sent nowhere. Pinned by the explicit start-point table.
- **Router doing phase work:** closing the issue, moving the card, merging, pushing. Pinned by the forbidden-actions paragraph and the verification greps.
- **Library-only story:** developer ends `Done` with no PR; the chain must stop, not dispatch reviewer/deployer. Pinned by the ship chain rule.
- **Hardcoded values returning:** `wutsi` #6, option ids, `main`. Pinned by the verification greps.

---

### Task 1: Branch and commit the design and plan

**Files:**
- Create: `docs/superpowers/specs/2026-11-22-story-agent-orchestration-design.md` (already written)
- Create: `docs/superpowers/plans/2026-11-22-story-agent-orchestration.md` (this file)

**Interfaces:**
- Consumes: the approved design.
- Produces: the branch and the design record the skill rewrite follows.

- [ ] **Step 1: Create the branch**

```bash
git switch -c chore/story-agent-orchestration
git status --short
```

Expected: on the new branch; the pre-existing dirty files still present, untouched.

- [ ] **Step 2: Stage only the two docs**

```bash
git add docs/superpowers/specs/2026-11-22-story-agent-orchestration-design.md \
  docs/superpowers/plans/2026-11-22-story-agent-orchestration.md
git diff --cached --name-only
```

Expected: exactly those two paths.

- [ ] **Step 3: Commit**

```bash
git commit -m "docs(agents): add the story orchestration design and plan"
```

---

### Task 2: Rewrite the story skill as a router

**Files:**
- Modify: `.claude/skills/story/SKILL.md` (whole-file replacement)

**Interfaces:**
- Consumes: the mode→agent table from the design; the agent protocol (`OUTCOME`, `GATE`, `STATUS`, `APPROVAL`, `NEXT`); the profile keys.
- Produces: the skill every mode below follows.

- [ ] **Step 1: Replace the frontmatter and intro**

```yaml
---
name: story
description: Use to take a story through its lifecycle by orchestrating the analyst, developer, reviewer and deployer agents - `groom <story-id>`, `implement <story-id>` (`push` is an alias), `review <pr-number>`, `merge <pr-number>`, `ship <story-id>`. Triggers on "groom story", "implement story", "review story PR", "merge story PR", "ship story" - not for a generic PR review.
---
```

Title `# Story workflow`. Intro: the skill orchestrates; the agents own the work; the skill validates the argument,
dispatches, relays gates and follows the report; it never does phase work.

Then **Arguments:** `$ARGUMENTS` is `<mode> <id>`; modes `groom`, `implement`, `push` (alias), `review`, `merge`,
`ship`; missing mode or id → ask, never guess.

Then **Read first:** `AGENTS.md` (or `CLAUDE.md`), `MEMORY.md` when present, and `.claude/project-profile.md`. Their
rules win; the profile supplies `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch`. If the
profile is missing or `platform` is not `github`, stop and ask. Never hardcode a board, field id, status label or
branch.

- [ ] **Step 2: Write the agent table and dispatch contract**

Table with one row per mode:

| Mode | Agent | Done |
| `groom <story-id>` | `analyst` | `STATUS: <tracker.statuses.ready>`, `NEXT: implement <id>` |
| `implement <story-id>`, `push <story-id>` | `developer` | `NEXT: review <pr>`, or `STATUS: <tracker.statuses.done>` for a library-only story |
| `review <pr-number>` | `reviewer` | `APPROVAL: yes`, `NEXT: merge <pr>` |
| `merge <pr-number>` | `deployer` | `STATUS: <tracker.statuses.done>`, `NEXT: none` |
| `ship <story-id>` | `developer` → `reviewer` → `deployer` | start point resolved first |

Note that `designer` is the analyst's child; the skill never dispatches it.

Dispatch contract, one bullet each:

- Dispatch the named agent with the harness's subagent tool, passing the mode's argument and the repository path; never
  a generic agent, never `fork`.
- Keep the child session id. Relay only the agent's report (`OUTCOME`, `GATE`, `STORY`, `BRANCH`, `WORKTREE`, `PR`,
  `PLAN`, `PROTOTYPE`, `STATUS`, `RELEASES`, `APPROVAL`, `NEXT`, `NOTES`), never its tool transcript.
- `OUTCOME: done` → follow `NEXT` when `ship`, otherwise report it and stop.
- `OUTCOME: gate` → show the question(s) from `NOTES`, or the prototype paths, and ask the user; send the answer back to
  the same child session and wait. If the harness cannot resume, re-dispatch the same agent for the same phase — it
  rebuilds its state from git, the PR and the story.
- `OUTCOME: blocked` → report `NOTES` and stop.
- Unknown agent → `OUTCOME: blocked`, tell the user to register or reload the agents; never fall back to `general` or do
  the phase yourself.

- [ ] **Step 3: Write the explicit modes**

- **groom** — dispatch `analyst <story-id>`; relay its question and prototype gates; report `PLAN`, `PROTOTYPE`,
  `STATUS`; next `implement <id>`.
- **implement / push** — dispatch `developer <story-id>`; report `PR`, `STATUS`; next `review <pr>`, or done for a
  library-only story.
- **review** — dispatch `reviewer <pr-number>`; relay question gates; an approval reports `NEXT: merge <pr>`; a `Final`
  review with findings stops.
- **merge** — dispatch `deployer <pr-number>`; report the deployment outcome and `STATUS`; a red deployment leaves the
  story in review.

- [ ] **Step 4: Write `ship`**

Read the story status (`gh issue view <id> --json projectItems --jq '.projectItems[].status.name'`) and its PR
(`gh pr list --state all --search "<repository.story_ref with the id> in:body" --json number,state,mergedAt`).

Start-point table:

| State | Start |
| `tracker.statuses.backlog` | stop; ask for `groom <id>` |
| `tracker.statuses.done` | report done |
| `ready`, `in_progress`, or `review` without a PR | `developer` |
| `review` with an open PR | `reviewer` |
| a merged PR (any status) | `deployer` |

Chain on reports: developer `NEXT: review <pr>` → reviewer; reviewer `NEXT: merge <pr>` → deployer; deployer
`NEXT: none` → done. Developer `STATUS: <tracker.statuses.done>` (library-only) ends the chain. A gate is relayed and
the same session resumed; `blocked` stops.

- [ ] **Step 5: Write the closing sections**

- **Report:** the mode, the phases run, the outcome, the PR/plan, the story status, and what is left; no tool output.
- **Stop and ask:** missing mode or id; missing profile or non-GitHub platform; unknown agent; a blocked agent; an
  ambiguous `ship` start point (no status, no project item, several PRs); a gate that needs the user's answer. Never
  guess a story or PR.
- **Done when:** the mode's agent reported `done` and the next action is in the report — or the agent stopped `blocked`
  (or on a `Final` review) and the user knows why.

- [ ] **Step 6: Verify the file**

```bash
grep -n '^#\|^##' .claude/skills/story/SKILL.md
```

Expected: `# Story workflow`, `## Agents`, `## Dispatch`, `## Modes`, `## Ship`, `## Report`, `## Stop and ask`,
`## Done when`, and no leftover numbered procedure sections from the old `groom`/`implement`/`push`/`review`/`merge`.

---

### Task 3: Update the README row

**Files:**
- Modify: `README.md:163`

**Interfaces:**
- Consumes: the new mode table.
- Produces: the documentation entry matching the skill.

- [ ] **Step 1: Replace the `story` row**

One row: the skill orchestrates the `analyst`, `developer`, `reviewer` and `deployer` agents; `groom` hands Backlog to
the analyst, `implement`/`push` to the developer, `review` to the reviewer, `merge` to the deployer, and `ship` chains
developer → reviewer → deployer. Keep the link to `.claude/skills/story/SKILL.md`.

- [ ] **Step 2: Verify the row**

```bash
grep -n '\[`story`\]' README.md
```

Expected: one row, no mention of `push` releasing or opening the PR, no per-mode procedure.

---

### Task 4: Verify and commit

**Files:**
- Verify: `.claude/skills/story/SKILL.md`, `README.md`
- Modify: either file only if a check fails

- [ ] **Step 1: Grep out phase work and repository values**

```bash
grep -nE '\bwutsi\b|ndopify|origin/main|\bIn review\b|STATUS: Done|gh project item-edit|gh pr merge|gh pr create|git push|mvn|feat/' .claude/skills/story/SKILL.md
```

Expected: no output.

- [ ] **Step 2: Grep the required references**

```bash
grep -n 'repository\.story_ref\|repository\.default_branch\|tracker\.statuses\.\|project-profile\.md\|agent-report\.md' .claude/skills/story/SKILL.md
```

Expected: the profile keys, the story ref, the default branch and the report reference each appear.

- [ ] **Step 3: Grep the agents and modes**

```bash
grep -nE '^###? .*(groom|implement|push|review|merge|ship)|`analyst`|`developer`|`reviewer`|`deployer`' .claude/skills/story/SKILL.md
```

Expected: every mode and all four dispatched agents; `designer` appears only as the analyst's child, never as a dispatch
target.

- [ ] **Step 4: Read the Review Focus against the file**

Point at the sentence for each of the five: same-session gate resume, correct `ship` start point, forbidden phase work,
library-only chain end, no hardcoded values. Fix inline.

- [ ] **Step 5: Dispatch probes**

```text
Registration check only. Do not read files, run commands or edit anything. Reply exactly: <name> reachable.
```

Expected: `analyst` reachable. `developer`, `reviewer` and `deployer` may return `Unknown agent` until the harness
service reloads (known prerequisite, outside this skill); record the result, do not change the skill for it.

- [ ] **Step 6: Commit**

```bash
git add .claude/skills/story/SKILL.md
git commit -m "refactor(story): orchestrate the agents"
git add README.md
git commit -m "docs(story): describe the agent orchestration"
git status --short
```

Expected: only the pre-existing dirty files remain; the skill, README and docs are committed.
