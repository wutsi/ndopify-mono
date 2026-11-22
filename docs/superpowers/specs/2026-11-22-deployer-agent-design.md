# Deployer agent: merge, wait for the deployment, close the story

## Intent

Add `.claude/agents/deployer.md`, invoked with one pull request number. It merges the PR a reviewer approved on its
head, waits for the deployment the merge commit starts, then closes the story and moves it from `In review` to done.
Like the other agents, it is project-agnostic: repository names, branch, statuses, merge method and deployment
workflows come from `.claude/project-profile.md`, never from the agent.

## Why

The agent set stops at `reviewer`: shipping a story is still prose in the `story` skill's `merge` mode. A deployer
agent gives the orchestrated path a shipping step that cannot review or change code, can only merge a head a review
approved, and keeps the story open when the deployment fails.

## Goals

- Merge only a PR whose head the latest review approved and whose checks are green.
- Wait for every deployment run started by the merge commit.
- Close the issue and move the card to the done status only once the deployment is green.
- Remove the story's worktree and local branch.
- Carry no repository-specific value (owner, repo, branch, status, workflow, merge flag).

## Non-goals

- Refactoring the `story` skill's `merge` / `ship` modes to dispatch this agent (deferred; the divergence is accepted,
  as it was for `developer`).
- HTTP health checks, smoke tests or rollbacks.
- Library releases (they happen before the PR, in `developer` / `push`).
- Reviewing, fixing or pushing code.

## Anti-goals

- Merging a head no review approved, or a head that moved after the approval.
- Closing the story before every deployment run is green.
- Guessing a missing profile value, or hardcoding this repository's names, statuses or workflows.
- Approving, reviewing, opening or closing a PR.

## Constraints

- `platform: github` only, as the profile states.
- A deployment is what the GitHub Actions runs started by the merge commit report. The repo has no separate deploy
  workflow: each module's `-main` workflow builds, deploys its app to test, and commits the coverage badge.
- The board's terminal status is the profile's `tracker.statuses.done` (`Done` here). There is no `Close` status:
  "close" means close the issue and move the card to done.
- The worktree has unrelated changes in progress; this work touches only the new agent file, the profile and its own
  docs.

## Approaches

- **A — agent only (chosen).** Add `.claude/agents/deployer.md` and the two values a project must supply
  (`repository.merge_method`, `deployment.workflows`). The `story` skill stays as it is. Same scope and precedent as
  the `developer` agent change: one agent file, the profile, a design and a plan.
- **B — agent plus skill wiring.** Also make `story merge` and `ship` dispatch the deployer, so one implementation
  serves both. Rejected for now: it touches the skill the user is editing and grows the change beyond the agent set.

## Decisions

- **Argument and precondition.** `<pr-number>` of the current repository; never guess it. The PR body carries the
  profile's `repository.story_ref`, that story is in `tracker.statuses.review`, and the PR is open or merged. A
  merged PR resumes at the deployment wait; a story already `tracker.statuses.done` reports done and stops.
- **Read first.** The repository instructions (`AGENTS.md` or `CLAUDE.md`), the touched modules' instructions,
  `MEMORY.md` when present, and the profile. A missing `repository.merge_method`, or a board with no status matching
  `tracker.statuses.review` / `done`, is a stop-and-ask. Deployment config is optional: `deployment.workflows: []`
  means every run the merge commit starts.
- **Gate.**
  - Resolve the PR, its story and the board with `gh`, never with hardcoded ids.
  - The latest review's body starts with `Round <n> — Approved` or `Final — Approved`, and its `commit_id` is the
    current head. Its `state` may be `COMMENTED`: GitHub refuses `APPROVE` on a PR its author opened, so the body is
    the marker.
  - Every check is finished and green (`gh pr checks <pr> --watch`; no check reported counts as green). Red → report
    and stop.
  - Re-read the head and the latest review right before merging; if either moved, restart the gate.
- **Merge.** `gh pr merge <pr> --<repository.merge_method>` (`merge`, `squash` or `rebase`). Then read the merge
  commit from `gh pr view <pr> --json mergeCommit`; a null merge commit after a short retry is a stop. A merge the
  branch protection refuses is reported and stops.
- **Deployment.** Select the runs for the merge commit: all of them, or only those whose workflow path matches
  `deployment.workflows`. Retry the listing briefly after the merge while Actions catches up. Watch every selected
  run (`gh run watch <id> --exit-status`). A red, cancelled or timed-out run → report its URL and stop with the story
  still `In review`. No selected run means nothing to deploy (a docs-only merge): record it and continue.
- **Close.** Only after the deployment is green: comment the PR link and the deployment run links on the story,
  close the issue, move the card to `tracker.statuses.done`, then remove the worktree and local branch from the main
  worktree (a dirty worktree is a stop, never a forced removal).
- **Permissions.** `edit` denied; `shell` allowed with targeted denies for `gh pr review*`, `gh pr create*`,
  `gh pr close*` and `git push*`; subagents denied. `steps: 60`, model `deepseek/deepseek-v4-pro#high` (a merge plus
  a deployment wait is high-stakes and mostly waiting).
- **Report.** The shared `.claude/agent-report.md` shape, no new key: `OUTCOME: done`, `APPROVAL: yes`,
  `STATUS: <done>`, `NEXT: none`; the deployment run links or the failing run go in `NOTES`.
- **Profile additions.**

  ```yaml
  repository:
    default_branch: main
    story_ref: "Refs #<id>"
    merge_method: merge          # merge | squash | rebase

  deployment:
    # Workflow paths to wait for. Empty: every run the merge commit starts.
    workflows: []
  ```

## Out of scope

- The `story` skill (`merge`, `ship`), the reviewer loop and the workflows themselves.
- Post-deployment verification beyond the workflow runs.

## Verification

No automated test covers agent prose. Read-through: the approval gate matches the reviewer's body marker and the
`story merge` gate; the deployment wait covers every merge-commit run and stops on red before the story moves; the
issue is closed and the card moved only after; no owner, repo, branch, status label, workflow name or merge flag is
literal in the agent (all come from the profile or `gh`); the denied actions are exactly review, PR creation/closure
and push.
