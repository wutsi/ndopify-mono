# Analyst Grooming Worktree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the analyst always groom in a throwaway `.worktrees/<id>-<topic>` worktree cut from
`origin/<repository.default_branch>`, and clean it up after publishing the plan.

**Architecture:** One instructions change in `.claude/agents/analyst.md` (start, prototype brief, publish, report,
stop-and-ask, done) plus a `.worktrees/` entry in `.gitignore`. The analyst is project-agnostic: the location derives
from the repository, and the default branch comes from `repository.default_branch`.

**Tech Stack:** Markdown agent instructions, git worktrees, GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-11-27-analyst-grooming-worktree-design.md`

## Global Constraints

- Never inspect, correct or stop on the state of the primary checkout: dirty, behind or on another branch is normal.
- Never force, reset, stash, autostash or clean; never `git worktree remove --force`.
- The plan is still published by pushing to `repository.default_branch`; no pull request.
- Keep the instruction project-agnostic: no hardcoded board, field, status, branch or path outside `.worktrees/`.

## Review Focus

- An interrupted groom (gate) leaves a worktree; the resumed run must reuse it, not fail on "already exists".
- A catalogued `origin/<default>` that moved during grooming must be rebased, not force-pushed.
- The designer must write into the worktree, never into the primary checkout.
- Cleanup must happen only after a successful push; a blocked groom must keep its worktree.

---

### Task 1: Groom in a worktree

**Files:**
- Modify: `.gitignore`
- Modify: `.claude/agents/analyst.md` (§ *Start from a clean default branch* → § *Start in a fresh grooming worktree*;
  § 2 brief; § 4 publish and cleanup; *Report*; *Stop and ask*; *Done when*)
- Create: `docs/superpowers/specs/2026-11-27-analyst-grooming-worktree-design.md` (already written)
- Create: `docs/superpowers/plans/2026-11-27-analyst-grooming-worktree.md` (this file)

**Interfaces:**
- Consumes: `repository.default_branch`, `plans.dir`, `plans.naming`, `prototype.dir`, `plans.publish` from the
  project profile.
- Produces: `WORKTREE: <path>` on a gate/blocked report, `WORKTREE: -` after cleanup.

- [ ] **Step 1: Ignore the worktree directory**

  Append to `.gitignore`:

  ```gitignore
  # Agent worktrees
  .worktrees/
  ```

  Run: `git check-ignore -v .worktrees/probe`
  Expected: prints the new `.gitignore` line.

- [ ] **Step 2: Replace the start section**

  In `.claude/agents/analyst.md`, replace § *Start from a clean default branch* (its heading through the paragraph
  ending "pushes only from it.") with the block quoted under **Design → Start** in the spec, verbatim. The section
  must contain `git worktree add .worktrees/<id>-<topic> -b groom/<id>-<topic> origin/<repository.default_branch>` and
  the reuse rule for an interrupted groom.

  Run: `grep -n "Start in a fresh grooming worktree\|git worktree add .worktrees\|Start from a clean default branch" .claude/agents/analyst.md`
  Expected: the new heading and command; no old heading.

- [ ] **Step 3: Give the designer the worktree path**

  In § 2 *Prototype*, add one brief bullet: the absolute path of the grooming worktree, where the designer creates and
  edits the prototype — never in the primary checkout.

- [ ] **Step 4: Publish from the worktree and clean up**

  Replace § 4 *Publish* with the spec's **Design → Publish**: every `git` call prefixed `git -C <worktree>` except
  `git fetch origin`, the push `git push origin HEAD:<repository.default_branch>`, then the cleanup
  (`git worktree remove .worktrees/<id>-<topic>`; `git branch -D groom/<id>-<topic>`) on success only. Keep the
  existing no-`add -A`, no-`--autostash`, rebase-once-then-stop rules.

  Run: `grep -n "git add -A\|--autostash\|git worktree remove\|HEAD:<repository.default_branch>" .claude/agents/analyst.md`
  Expected: the guards and the push/removal present; no plain `git push origin <repository.default_branch>`.

- [ ] **Step 5: Align the report, stop-and-ask and done**

  - *Report*: successful groom reports `WORKTREE: -`; a gate reports the worktree path.
  - *Stop and ask*: replace "the default branch is dirty or diverged from GitHub" with "the grooming worktree cannot
    be created, or an existing one is dirty or not based on GitHub's default branch".
  - *Done when*: add "the grooming worktree and its branch are removed (a gate keeps them for the resume)".

  Run: `grep -n "WORKTREE: -\|grooming worktree cannot be created\|worktree and its branch are removed" .claude/agents/analyst.md`
  Expected: one match for each.

- [ ] **Step 6: Review the whole diff**

  Run: `git diff --stat` and `git diff`
  Expected: only `.gitignore`, `.claude/agents/analyst.md`, the spec and the plan; no production code; the analyst
  instruction reads coherently with no dangling reference to the old section.

- [ ] **Step 7: Commit and publish**

  ```bash
  git add .gitignore .claude/agents/analyst.md docs/superpowers/specs/2026-11-27-analyst-grooming-worktree-design.md docs/superpowers/plans/2026-11-27-analyst-grooming-worktree.md
  git diff --cached --name-only
  git commit -m "chore(agents): groom stories in a worktree cut from origin"
  git fetch origin
  git rebase origin/main
  git push origin HEAD:main
  ```

  Expected: a fast-forward push to `main`; no pull request.

- [ ] **Step 8: Remove the change's own worktree**

  From the primary checkout: `git worktree remove .worktrees/analyst-grooming-worktree`, then
  `git branch -D chore/analyst-grooming-worktree`.

  Expected: `git worktree list` no longer lists it.
