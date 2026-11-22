# Developer agent: own the push, PR and `In review`

## Intent

`developer` takes a story from `Ready` to `In review` on its own: implement, release its library changes, push the
branch, open the PR, link it on the story and move the story to review. The separate `push` hand-off disappears from
this path.

## Why

- One agent owns the whole coding path, so the user no longer has to run `push <story-id>` after `implement`.
- The release ordering already exists in `push` (libraries to `main` first, rebase the branch, then push and PR). Doing
  it in that order avoids a PR that starts with library commits and is force-updated after the release.
- The story skill will be refactored later; today only the agent changes, so the skill's `implement` still hands off.

## Decisions

- **Scope.** Only `.claude/agents/developer.md`. The `story` skill, `ship`, `reviewer` and CI are untouched; the known
  divergence between `developer` and `story implement` is accepted until the skill refactor.
- **Contract.** `developer` never merges and never posts a review; it may push (branch and library `main`) and open the
  PR. The `review`/`merge` steps still own approval and merge.
- **Status gate.** The story moves to `In review` only after the PR exists, is pushed, and its checks are green. Red
  checks keep it `In progress` while the agent fixes them.
- **Step 3** becomes *Commit and verify*: conventional commits, tree clean except the local `-SNAPSHOT` override,
  `verification-before-completion` green in each touched module.
- **Step 4 — Release, push and open the PR**, mirroring `push` steps 1–4:
  - **Push gate.** `git diff origin/main --stat` touches only the story; root `pom.xml` versions are released, never
    `-SNAPSHOT`; commits are conventional; the repo verification command is green on the tree being pushed.
  - **Libraries** (skip if untouched), `ndopify-dto` then `ndopify-web-core`, each in a throwaway detached worktree on
    the latest `origin/main`: cherry-pick the branch's `+` commits touching that module (oldest first), run the library
    checks (diff touches only the module, every commit is a cherry-pick of this branch, verify green), push
    `HEAD:main`, wait for the `-main` release run, tag included. A `fix dependencies` commit first when `web-core`
    needs the just-released `dto`. Comment the pushed commit links and released versions on the story.
  - **Branch.** Revert the local `-SNAPSHOT` edit, rebase on `origin/main` (library commits drop out; one that remains
    is a stop), commit `fix dependencies: ndopify-dto <version>, ndopify-web-core <version>` for versions not yet on
    `main`, `git push -u origin <branch>`.
  - **PR.** `gh pr create` with a conventional title, a body linking the plan and the releases, `Refs #<id>` (never
    `Closes`/`Fixes`); wait for checks (`gh pr checks <pr> --watch`). Red checks → read the failing log, fix,
    verification, push, wait again. **Only once the PR is pushed and its checks are green**: comment the PR link on
    the story, move it to `In review`, then stop.
  - **Library-only story.** Nothing left on the branch after the rebase → push the `fix dependencies` commit to `main`,
    wait for the `-main` run, close the story, move it to `Done`, remove the worktree and branch, stop.
- **Resume.** `Ready` with a plan, or `In progress`/`In review` with the branch: skip what is done by reading the
  branch's commits, `git cherry -v origin/main <branch>`, the release tags and runs, the story comments and any open
  PR. An open PR resumes at the checks.
- **Permissions.** Keep `edit`/`shell` allow. Replace the `git push*` and `gh pr *` denies with targeted denies:
  `gh pr merge*`, `gh pr review*`, `gh pr close*`. Force-push only as `--force-with-lease` after a rebase (instruction,
  not a permission pattern). Subagent denies unchanged.
- **`steps`** rises from 60 to 120 for implementation plus release waits.
- **Story tracker.** Add the `Done` option for the library-only path; keep the existing work-in-progress and review
  statuses.
- **Stop and ask.** Add: a cherry-pick that does not apply or touches outside its module, a red release run, red PR
  checks still red after a fix. Keep: never merge, never force-push beyond `--force-with-lease`, never unrelated
  commits.

## Out of scope

- The `story` skill's `implement`, `push` and `ship` modes, and their preconditions.
- The 3-round review loop, the merge gate and the release workflows themselves.

## Verification

No automated test covers agent prose. Read-through: every push gate, library check and ordering from `story push`
appears in step 4; the story moves to `In review` only after green PR checks; the agent cannot merge, approve or close a
PR; no "don't push" / "hand-off" wording remains; resume covers the open-PR state; the library-only path ends `Done`;
`steps` and permissions match the new contract.
