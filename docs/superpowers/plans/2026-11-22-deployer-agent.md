# Deployer Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a project-agnostic `deployer` agent that merges an approved PR, waits for its deployment, then closes the story and moves it off review.

**Architecture:** One new agent file, `.claude/agents/deployer.md`, built like `reviewer.md`/`developer.md`: every repository value comes from `.claude/project-profile.md`. Two profile values are added: `repository.merge_method` and `deployment.workflows`. The `story` skill is untouched. No runtime code or automated tests exist for agent prose; verification is read-through plus `grep`.

**Tech Stack:** Markdown, YAML frontmatter, GitHub CLI (`gh`), git.

**Spec:** `docs/superpowers/specs/2026-11-22-deployer-agent-design.md`

## Global Constraints

- Touch only these files: `.claude/agents/deployer.md` (create), `.claude/project-profile.md` (modify), and the new design/plan docs. Never stage, commit or revert the existing dirty files: `.claude/skills/story/SKILL.md`, `DESIGN.md`, `.AGENTS.md`, `.opencode`, the two `icon-*.png`.
- Stay on the current branch `chore/agent-portability`; do not create or switch branches.
- The agent file contains no repository-specific value: no `wutsi`, `ndopify`, `main` as a branch, `In review`, `Done`, `--merge`, or workflow filename. It reads `repository.default_branch`, `repository.story_ref`, `repository.merge_method`, `tracker.statuses.review`, `tracker.statuses.done` and `deployment.workflows` from the profile.
- Keep the voice of `reviewer.md`/`developer.md`: terse, second person, backticks around commands, statuses and profile keys.
- The shared report shape (`.claude/agent-report.md`) is unchanged; the deployment links go in `NOTES`.
- Commit prefixes: `docs(agents): …` for the spec/plan, `chore(agents): …` for the profile, `feat(agents): …` for the agent.

## Review Focus

- **Stale approval:** a commit lands after the approving review; a reasonable user expects the merge to refuse. Pinned by the gate re-reading the head and latest review immediately before merging (Task 3, Gate/Merge).
- **Late Actions listing:** `gh run list --commit` is empty for a few seconds after a merge; the deployer must retry, not conclude "nothing to deploy". Pinned by the retry rule (Task 3, Deployment).
- **Red deployment:** a run fails, is cancelled or times out after the merge; the story must stay `In review`, the issue open. Pinned by the stop-before-close rule (Task 3, Deployment/Close).
- **Re-run after merge:** the PR is already merged; the agent must resume at the deployment wait and never merge twice. Pinned by the resume rule (Task 3, opening/Merge).
- **Missing configuration:** no `repository.merge_method`, or a board without the review/done status; the agent must stop, never fall back to a hardcoded value. Pinned by the stop rule (Task 3, opening/Gate).

---

### Task 1: Commit the approved design and this plan

**Files:**
- Create: `docs/superpowers/specs/2026-11-22-deployer-agent-design.md` (already written)
- Create: `docs/superpowers/plans/2026-11-22-deployer-agent.md` (this file)

**Interfaces:**
- Consumes: the approved design.
- Produces: the design and plan on the branch, before any agent change.

- [ ] **Step 1: Confirm the branch and the dirty set**

```bash
git branch --show-current
git status --short
```

Expected: `chore/agent-portability`; the pre-existing dirty files listed in Global Constraints plus the untracked design doc and plan doc.

- [ ] **Step 2: Commit only the two docs**

```bash
git add docs/superpowers/specs/2026-11-22-deployer-agent-design.md \
  docs/superpowers/plans/2026-11-22-deployer-agent.md
git diff --cached --name-only
```

Expected: exactly those two paths.

- [ ] **Step 3: Commit**

```bash
git commit -m "docs(agents): add the deployer agent design and plan"
```

---

### Task 2: Add the profile values

**Files:**
- Modify: `.claude/project-profile.md` (the `repository:` block and a new `deployment:` block)

**Interfaces:**
- Consumes: the design's profile additions.
- Produces: `repository.merge_method` (`merge` | `squash` | `rebase`) and `deployment.workflows` (list of workflow paths; empty means every run on the merge commit). Task 3's agent reads both.

- [ ] **Step 1: Add the merge method**

In the `repository:` block, after `story_ref`, add:

```yaml
  merge_method: merge
```

with the inline comment `# merge | squash | rebase`.

- [ ] **Step 2: Add the deployment block**

After the `prototype:` block and before `branch:`, add:

```yaml
deployment:
  # Workflow paths to wait for after a merge. Empty: every run the merge commit starts.
  workflows: []
```

- [ ] **Step 3: Verify the YAML block**

```bash
sed -n '/^```yaml/,/^```/p' .claude/project-profile.md
grep -n 'merge_method\|deployment:' .claude/project-profile.md
```

Expected: one `merge_method` under `repository`, one `deployment` block with `workflows: []`; no duplicate keys.

- [ ] **Step 4: Commit only the profile**

```bash
git add .claude/project-profile.md
git diff --cached --name-only
git commit -m "chore(agents): add the deployer profile values"
```

Expected: only `.claude/project-profile.md` staged.

---

### Task 3: Create the deployer agent

**Files:**
- Create: `.claude/agents/deployer.md`

**Interfaces:**
- Consumes: the profile keys from Task 2; the reviewer's approval body marker (`Round <n> — Approved` / `Final — Approved`); the shared report in `.claude/agent-report.md`.
- Produces: the `deployer` agent, invoked with a PR number.

- [ ] **Step 1: Write the frontmatter**

```yaml
---
description: Use when a PR is approved and ready to ship — merge it, wait for its deployment, then close the story and move it off review.
mode: all
model: deepseek/deepseek-v4-pro#high
steps: 60
permissions:
  - action: edit
    resource: "*"
    effect: deny
  - action: shell
    resource: "*"
    effect: allow
  - action: shell
    resource: "gh pr review*"
    effect: deny
  - action: shell
    resource: "gh pr create*"
    effect: deny
  - action: shell
    resource: "gh pr close*"
    effect: deny
  - action: shell
    resource: "git push*"
    effect: deny
  - action: subagent
    resource: "*"
    effect: deny
---
```

- [ ] **Step 2: Write the opening contract**

Title `# Story deployer`; one paragraph: it merges one approved PR, waits for the deployment, closes the story and leaves it in the done status; it never reviews, edits, pushes or opens a PR.

Then, mirroring `reviewer.md`:

- **Argument:** `<pr-number>` of the current repository; missing → stop and ask, never guess.
- **Precondition:** the PR body carries the profile's `repository.story_ref`, the story is in `tracker.statuses.review`, and the PR is open or merged. A merged PR resumes at the deployment wait; a story already in `tracker.statuses.done` reports done and stops. Work from the branch's worktree when it exists (`git worktree list`).
- **Read first:** `AGENTS.md` (or `CLAUDE.md`), every touched module's instructions, `MEMORY.md` when present, and `.claude/project-profile.md`. Their rules win. If the profile is missing, its `platform` is not `github`, or `repository.merge_method` is absent, stop and ask. A denied tool is a stop-and-report.

- [ ] **Step 3: Write the story tracker and the gate**

Tracker: copy the generic block from `reviewer.md` (`gh repo view`, `gh project list`, `gh project item-list --limit 1000`, `gh project field-list`), plus the `gh project item-edit` command. State that the deployer only needs the review and done statuses, and that a board without an equivalent for either is a stop-and-ask. Never hardcode board or field ids.

Gate, in this order:

1. Resolve the PR, its story and the board (`gh pr view <pr> --json number,title,body,state,headRefName,baseRefName,mergeCommit,files,reviews`; `gh issue view <id> --comments`).
2. Latest review: `gh api repos/<owner>/<repo>/pulls/<pr>/reviews --jq '.[-1] | {state, body, commit_id}'`; head: `gh api repos/<owner>/<repo>/pulls/<pr> --jq .head.sha`. Required: body starts with `Round <n> — Approved` or `Final — Approved`, and `commit_id` equals the head. `state` may be `COMMENTED`. Otherwise stop and ask for `review <pr-number>`.
3. Every check finished and green: `gh pr checks <pr> --watch`; no check reported counts as green; red → report and stop.
4. Immediately before merging, re-read the head and the latest review; if either moved, restart the gate.

- [ ] **Step 4: Write the merge step**

`gh pr merge <pr> --<merge_method>` with `<merge_method>` = the profile's `repository.merge_method` (the flag is that value). Then `gh pr view <pr> --json mergeCommit --jq .mergeCommit.oid`; a null merge commit after a short retry is a stop. A branch-protection refusal is reported and stops. If the PR was already merged, skip to the deployment wait with its merge commit.

- [ ] **Step 5: Write the deployment wait**

Resolve the runs for the merge commit:

```bash
gh run list --commit <sha> --limit 100 \
  --json databaseId,path,workflowName,status,conclusion,url
```

- When `deployment.workflows` is non-empty, keep only runs whose `path` matches an entry; otherwise keep every run.
- Retry the listing right after the merge while Actions catches up (about 10 tries, 15 seconds apart).
- No selected run: record that nothing was deployed (e.g. a docs-only merge) and continue to the close.
- Watch each selected run: `gh run watch <run-id> --exit-status`.
- A `failure`, `cancelled`, `timed_out` or `startup_failure` conclusion: report the run URL and stop, leaving the story in `tracker.statuses.review` and the issue open. Only `success` counts as deployed.

- [ ] **Step 6: Write the close step**

Only once every selected run is green:

1. `gh issue close <id> --comment "<PR link>\n\nDeployed: <run links>"` (say `No deployment run started` when none).
2. Move the card to `tracker.statuses.done` with `gh project item-edit`.
3. From the main worktree, remove the story's worktree (`git worktree remove <dir>`) then its local branch (`git branch -D <branch>`). A dirty worktree is a stop, never `--force`.

- [ ] **Step 7: Write the report, stop rules and done condition**

- **Report:** `.claude/agent-report.md`; success is `OUTCOME: done`, `GATE: -`, `STORY: #<id>`, `BRANCH`, `WORKTREE`, `PR`, `STATUS: <tracker.statuses.done>`, `APPROVAL: yes`, `NEXT: none`, and the deployment run links (or `no deployment run`) in `NOTES`. A red deployment is `OUTCOME: blocked`, `APPROVAL: yes`, `STATUS: <tracker.statuses.review>`, the failing run in `NOTES`.
- **Stop and ask:** no approved head or a head that moved; red checks; a refused merge; a null merge commit; a red deployment run; a dirty worktree; an ambiguous board; a missing `repository.merge_method`. When running as a subagent, end with `OUTCOME: gate`, `GATE: question`.
- **Done when:** the PR is merged, every selected deployment run is green, the issue is closed, the card is in `tracker.statuses.done`, and the worktree and local branch are gone — or the agent stopped and the report says why.

- [ ] **Step 8: Verify the file**

```bash
grep -n '^#\|^##' .claude/agents/deployer.md
sed -n '1,40p' .claude/agents/deployer.md
```

Expected: the frontmatter above, then the sections in the order written; no section left as a placeholder.

---

### Task 4: Verify against the spec and commit

**Files:**
- Verify: `.claude/agents/deployer.md`, `.claude/project-profile.md`
- Modify: `.claude/agents/deployer.md` only if a check fails

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: the committed agent, matching the spec.

- [ ] **Step 1: Grep out repository-specific values**

```bash
grep -nE '\bwutsi\b|ndopify|origin/main|--merge\b|\bIn review\b|STATUS: Done|\.ya?ml\b' .claude/agents/deployer.md
```

Expected: no output.

- [ ] **Step 2: Grep the required profile references**

```bash
grep -n 'repository\.story_ref\|repository\.merge_method\|repository\.default_branch\|deployment\.workflows\|tracker\.statuses\.review\|tracker\.statuses\.done\|agent-report\.md' .claude/agents/deployer.md
```

Expected: every key appears at least once.

- [ ] **Step 3: Read the Review Focus against the file**

For each of the five Review Focus lines, point at the sentence in `.claude/agents/deployer.md` that pins it: stale approval (gate re-read), late listing (retry), red deployment (stop before close), re-run after merge (resume), missing configuration (stop). Fix any missing rule inline.

- [ ] **Step 4: Compare the permission block with `reviewer.md`**

```bash
sed -n '/^permissions:/,/^---$/p' .claude/agents/reviewer.md
sed -n '/^permissions:/,/^---$/p' .claude/agents/deployer.md
```

Expected: the deployer allows shell, denies edit, `gh pr review*`, `gh pr create*`, `gh pr close*`, `git push*` and subagents; it does not deny `gh pr merge*`.

- [ ] **Step 5: Commit the agent**

```bash
git add .claude/agents/deployer.md
git diff --cached --name-only
git commit -m "feat(agents): add the deployer agent"
```

Expected: only `.claude/agents/deployer.md` staged.

- [ ] **Step 6: Confirm the working tree**

```bash
git status --short
```

Expected: only the pre-existing dirty files from Global Constraints remain; the new agent, profile and docs are committed.
