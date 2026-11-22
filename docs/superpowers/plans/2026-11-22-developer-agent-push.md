# Developer Agent Push/PR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `developer` agent carry a story from `Ready` to `In review` by releasing its library changes, pushing the branch and opening the PR itself.

**Architecture:** Edit only `.claude/agents/developer.md`. Step 4 ports the `story` skill's `push` procedure inline (as approved — the skill is refactored later), with one deliberate difference: the story moves to `In review` only after the PR checks are green. No runtime code or automated tests exist for agent prose; verification is read-through plus `grep`.

**Tech Stack:** Markdown, YAML frontmatter, GitHub CLI (`gh`), git.

**Spec:** `docs/superpowers/specs/2026-11-22-developer-agent-push-design.md`

## Global Constraints

- Edit only `.claude/agents/developer.md`; apart from committing the spec and this plan, touch no other file.
- The worktree already has unrelated changes (`DESIGN.md`, `.claude/skills/story/SKILL.md`, `.AGENTS.md`, `.opencode`, icons). Never stage, commit or revert them.
- Keep the agent's existing voice: terse, second person, backticks around commands and statuses.
- The agent may push and open a PR, but must never merge, review or close a PR.
- `In review` only after the PR exists, its branch is pushed and its checks are green.
- Library order is always `ndopify-dto` then `ndopify-web-core`, each in its own throwaway detached worktree on the latest `origin/main`.
- Commit messages are conventional; the agent file commit is `chore(agents): …`, docs are `docs(agents): …`.
- No `-SNAPSHOT` may be committed; it is a local override reverted before the branch push.

## Review Focus

- Leftover hand-off wording ("hand it back", "Don't push", "ready for the push step") — grep for each in Task 3.
- Story moved to `In review` before the PR checks are green — read Task 2's PR paragraph, status must follow the checks.
- Permission block still allows merge/review/close, or still denies all pushes/PRs — grep the YAML in Task 1.
- A PR opened before the library release, or `web-core` released before `dto` — read Task 2's ordering.
- Unrelated dirty files swept into a commit — `git status` check in Task 4.

---

### Task 1: Frontmatter and contract

**Files:**
- Modify: `.claude/agents/developer.md:1-45`

**Interfaces:**
- Consumes: the approved spec.
- Produces: `steps: 120`, the new description, permission block, opening paragraph and resume rule that Task 2 and Task 3 rely on.

- [ ] **Step 1: Start a branch**

```bash
git switch -c chore/developer-agent-push
```

The unrelated dirty files remain untracked/unstaged; leave them alone.

- [ ] **Step 2: Replace the `description`**

Use exactly:

```yaml
description: Use when implementing a Ready story — code its plan on the story's branch, with tests, release its library changes, push it and open its PR for review.
```

- [ ] **Step 3: Raise the step cap**

Change `steps: 60` to `steps: 120`.

- [ ] **Step 4: Replace the permission block**

Remove the `git push*` and `gh pr *` denies; add the three PR denies. The block must read exactly:

```yaml
permissions:
  - action: edit
    resource: "*"
    effect: allow
  - action: shell
    resource: "*"
    effect: allow
  - action: shell
    resource: "gh pr merge*"
    effect: deny
  - action: shell
    resource: "gh pr review*"
    effect: deny
  - action: shell
    resource: "gh pr close*"
    effect: deny
  - action: subagent
    resource: "*"
    effect: deny
  - action: subagent
    resource: general
    effect: allow
  - action: subagent
    resource: explore
    effect: allow
```

- [ ] **Step 5: Rewrite the opening paragraph**

The contract becomes: the agent implements one `Ready` story on its branch, releases its library changes, pushes the branch and opens the PR; it never reviews, merges or closes a PR — the review and merge steps own that. Replace "You never push, open a PR, or merge: the repo's push/release step owns that."

- [ ] **Step 6: Update the precondition and resume rule**

Keep `Ready` with a linked plan, or `In progress`/`In review` with the branch. State that a resume skips finished steps by reading the branch's commits, `git cherry -v origin/main <branch>`, the release tags and runs, the story's comments and any open PR.

- [ ] **Step 7: Verify the frontmatter**

Run: `sed -n '1,30p' .claude/agents/developer.md`
Expected: `steps: 120`; the three `gh pr` denies; no `git push*` or blanket `gh pr *` deny; description matches Step 2.

Run: `git status --short .claude/agents/developer.md`
Expected: `?? .claude/agents/developer.md` — the agent directory is untracked in this worktree today; that is not an error.

No commit yet — the agent is configured but its body still contradicts it.

---

### Task 2: Step 3 rename and the new step 4

**Files:**
- Modify: `.claude/agents/developer.md:56-85`
- Reference: `.claude/skills/story/SKILL.md:104-166` (`push` mode)

**Interfaces:**
- Consumes: Task 1's step cap and permissions.
- Produces: step 4 whose procedure Task 3's *Done when* and stop rules describe.

- [ ] **Step 1: Rename step 3 to "Commit and verify"**

Keep the conventional-commit rules, the clean-tree-except-local-override rule and `superpowers:verification-before-completion`. Delete "Don't push", the review-status move and the "tell the user it is ready" sentence.

- [ ] **Step 2: Add "## 4. Release, push and open the PR"**

Port `push` steps 1–4 from the story skill, adapted to these deltas:

- **Push gate:** `git diff origin/main --stat` touches only the story; root `pom.xml` versions are released, never `-SNAPSHOT`; commits are conventional; verification is green on the tree being pushed.
- **Libraries:** `ndopify-dto` first, then `ndopify-web-core`. Each in a detached worktree on `origin/main` (`git fetch && git worktree add --detach <dir> origin/main`, removed after). Cherry-pick the branch's `+` commits touching that module, oldest first; run the library checks; `git push origin HEAD:main`; wait for the module's `-main` run. Commit `fix dependencies: ndopify-dto <version>` first when `web-core` needs the new `dto`. Comment the pushed commits' links and released versions on the story.
- **Branch:** revert the local `-SNAPSHOT` (`git checkout -- pom.xml`), rebase on `origin/main` (library commits drop out; one that remains is a stop), commit `fix dependencies: ndopify-dto <version>, ndopify-web-core <version>` for versions not yet on `main`, then `git push -u origin <branch>`. Force only as `--force-with-lease` after a rebase.
- **PR:** `gh pr create` with a conventional title, a body linking the plan and releases, `Refs #<id>` (never `Closes`/`Fixes`), then `gh pr checks <pr> --watch`. Red checks → read the failing log, fix, verify, push, wait again. **Only once the checks are green**: comment the PR link on the story, move it to `In review`, stop.
- **Library-only story:** nothing left on the branch after the rebase → push the `fix dependencies` commit to `main`, wait for the `_main` run, close the issue, move it to `Done`, remove the worktree and branch, stop.

- [ ] **Step 3: Verify the ordering by read-through**

Read section 4 alone and confirm, in order: gate → libraries (`dto`, then `web-core`) → branch rebase/push → PR create → checks → status move. The PR must never appear before the library release, and `In review` never before the green checks.

Run: `grep -n 'In review\|gh pr checks\|gh pr create\|HEAD:main' .claude/agents/developer.md`
Expected: `HEAD:main` and `gh pr create` precede the `In review` line; `gh pr checks` sits between them.

No commit yet.

---

### Task 3: Tracker, stop rules, done criteria

**Files:**
- Modify: `.claude/agents/developer.md:87-125`

**Interfaces:**
- Consumes: Task 2's step 4.
- Produces: the final agent text; no later task depends on it.

- [ ] **Step 1: Add the `Done` status to the Story tracker**

The agent now needs three statuses: work in progress, review, and `Done` (library-only stories). Keep the dynamic board/field/option discovery — never hardcode ids.

- [ ] **Step 2: Extend "Stop and ask"**

Add: a cherry-pick that does not apply or touches outside its module, a red release run, and PR checks still red after a fix. Keep "never merge" and "never put unrelated changes in a commit"; replace the blanket "never push" with "force-push only as `--force-with-lease` after a rebase".

- [ ] **Step 3: Rewrite "Done when"**

Done when: every plan step is done and committed on the branch; library changes are released (if any); the PR is open with green checks and linked on the story; the story is `In review` — or `Done` for a library-only story; `git status` is clean apart from nothing (the `-SNAPSHOT` is reverted); and the user knows to run `review <pr-number>`.

- [ ] **Step 4: Final read-through sweep**

Run: `grep -n -i "hand it back\|don't push\|do not push\|ready for the push\|never push" .claude/agents/developer.md`
Expected: no output.

Run: `grep -n "steps:\|gh pr merge\|gh pr review\|gh pr close" .claude/agents/developer.md`
Expected: `steps: 120`, the three denies, no allow entry for them.

Read the whole file once, front to back: the opening contract, step 4 and *Done when* must tell the same story.

- [ ] **Step 5: Commit the docs, then the agent**

```bash
git add docs/superpowers/specs/2026-11-22-developer-agent-push-design.md docs/superpowers/plans/2026-11-22-developer-agent-push.md
git commit -m "docs(agents): add the developer push design and plan"
git add .claude/agents/developer.md
git commit -m "chore(agents): let the developer agent release, push and open the PR"
```

- [ ] **Step 6: Confirm nothing unrelated was committed**

Run: `git show --stat --oneline HEAD~1..HEAD`
Expected: only the spec, the plan and `developer.md`; no `DESIGN.md`, no `SKILL.md`, no `.AGENTS.md`, no icons.

Run: `git status --short`
Expected: the unrelated pre-existing changes remain, untouched. Do not push.
