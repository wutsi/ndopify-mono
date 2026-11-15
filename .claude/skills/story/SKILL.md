---
name: story
description: Use to take an ndopify story through its lifecycle - `groom <story-id>` (enrich a Backlog story and write its plan), `implement <story-id>` (code a Ready story on its branch), `push <story-id>` (release its library changes from `main`, then push the branch and open its PR), `review <pr-number>` (review a story's PR and resolve its findings, up to 3 times, until it approves it), `merge <pr-number>` (merge an approved story PR, then close the story), `ship <story-id>` (run implement, push, review and merge in sequence, each in a fresh subagent). Triggers on "groom story", "implement story", "push story", "review story PR", "merge story PR", "ship story" for this repo - not for a generic PR review.
---

# Story workflow

You are a senior engineer on `ndopify-mono`. This skill moves a story through
`Backlog → Ready → In progress → In review → Done`.

**Arguments:** `$ARGUMENTS` is `<mode> <id>`. Modes: `groom <story-id>`, `implement <story-id>`,
`push <story-id>`, `review <pr-number>`, `merge <pr-number>`, `ship <story-id>`.
If the mode or id is missing, ask for it — never guess the story.

<<<<<<< Updated upstream
**Merging and releases:** a story has at most one PR. `review` approves it: once a review finds nothing, after at most
3 rounds of review → resolve. Only `merge` merges it, and only a head a review approved: a merge deploys to test and
commits coverage badges to `main`. The library changes (`ndopify-dto`, `ndopify-web-core`) get no PR: `push` pushes
them to `main`, which
releases them. A release can't be undone, so each library push to `main` waits for the user's OK on its diff.
=======
**Merging and releases:** a story has one PR, and only `review` merges it: once a review finds nothing, after at most
3 rounds of review → resolve. The library changes (`ndopify-dto`, `ndopify-web-core`) get no PR: `push` pushes them to `main`, which
releases them. A release can't be undone, but the user has pre-authorized it: a library push to `main` that passes
the push gate and the library checks (`push` step 2) goes ahead without asking. Those checks are what make it safe,
so never skip one; a failed check stops the mode instead.
>>>>>>> Stashed changes

## Story tracker

A story is an issue of `wutsi/ndopify-mono` (the story id is the issue number) on the GitHub Project `wutsi` #6.

- Read: `gh issue view <id> --comments`
- Update the description / title: `gh issue edit <id> --body-file <file> --title "<title>"`
- Comment: `gh issue comment <id> --body "<text>"`
- Read the status (`item-list` returns only 30 items without `--limit`):
  `gh project item-list 6 --owner wutsi --limit 1000 --format json --jq '.items[] | select(.content.number==<id>) | .status'`
- Change the status: find the item id, then set the `Status` field:

  ```bash
  ITEM=$(gh project item-list 6 --owner wutsi --limit 1000 --format json --jq '.items[] | select(.content.number==<id>) | .id')
  gh project item-edit --project-id PVT_kwHOAlySnc4BrmpM --id "$ITEM" \
    --field-id PVTSSF_lAHOAlySnc4BrmpMzhp4mHE --single-select-option-id <option>
  ```

  Options: `Backlog` f75ad846, `Ready` 61e4505c, `In progress` 47fc9ee4, `In review` df73e18b, `Done` 98236657.
  If an id no longer matches, re-read them with `gh project field-list 6 --owner wutsi --format json`.

**Before any mode:** read the root `CLAUDE.md`, the `CLAUDE.md` of every module the story touches, and `MEMORY.md`.
Their rules win over this skill (ex: schema changes go in a new Flyway script).

**Stop and ask the user** instead of guessing when: a requirement is ambiguous, the story conflicts with a repo rule,
a build fails twice for the same reason, or a step would be irreversible and isn't listed here.

## Mode `groom <story-id>`

Precondition: the story is in `Backlog`. Otherwise stop and report its status.

1. **Enrich** — read the story and use `superpowers:brainstorming` to explore it. Ask the user every open question
   (one at a time); never answer one yourself. The outcome goes into the story, not a spec file: don't write or
   commit anything under `docs/superpowers/specs/`. Add to the story:
    - context (why, who, which modules/pages/endpoints),
    - acceptance criteria, testable (Given/When/Then),
    - dependencies, risks, assumptions, and what is out of scope,
    - whether it changes the UI (drives the prototype gate below),
    - whether it changes `ndopify-dto` / `ndopify-web-core` (drives the library releases in `push`).

   Update the story description, and its title if a shorter, clearer one fits.
2. **Plan** — use `superpowers:writing-plans`, keeping the path and filename it picks. Steps must be small enough for
   a coding agent, each with its tests and the module it touches. For a UI story, the first step is the
   `src/main/html/` prototype. Stop once the plan is written: don't offer to execute it.
3. **Publish the plan** — on an up-to-date `main` (`git pull --rebase --autostash`), commit only the plan file
   (`docs(<scope>): add the <topic> plan`) and push. Never include other changes in that commit.
4. Add the plan's GitHub link to the story and move it to `Ready`.

Done when: no open question left, the plan is on `origin/main`, the story is `Ready` and links the plan.

## Mode `implement <story-id>`

Precondition: the story is `Ready` and links a plan, or it is `In progress` and its branch exists (resume: skip the
steps already done — read the branch's commits and the story's comments). Otherwise stop.

1. Move the story to `In progress`.
2. Read the story and its plan. Create a worktree with `superpowers:using-git-worktrees` on one branch,
   `feat/<scope>-<topic>`, for the whole story — library changes included.
3. **Prototype gate (UI stories)** — build the prototype in the module's `src/main/html/` first, then stop: show it
   to the user and wait for their OK on it and on the plan before any DTO / server / BFF edit. If their feedback
   changes the design, update the plan, commit and push it to `main` (as in `groom` step 3), and rebase on it.
4. Execute the plan with `superpowers:subagent-driven-development` (independent steps) or `superpowers:executing-plans`,
   following `superpowers:test-driven-development`.
5. Coding requirements:
    - Follow the module's `CLAUDE.md` (code organization, test patterns, KDoc, French UI text, `kotlin.test`
      assertions, `TenantFormatter` for dates/amounts).
    - Every behavior change has a test; JaCoCo coverage of a touched module must not drop.
    - Each commit touches one library module (`modules/ndopify-dto/` or `modules/ndopify-web-core/`) or none:
      `push` moves the library commits to `main` on their own. Library commits are `<type>(dto): …` /
      `<type>(web-core): …` (`feat`, `fix`, …), with `Refs #<id>` in the body.
    - Unreleased library changes: the root `pom.xml` pins released versions, so to build a module against a local
      `ndopify-dto` / `ndopify-web-core` change, `mvn install` the library and point its version property in the
      root `pom.xml` at that `-SNAPSHOT` — a local, uncommitted change. Never commit the `-SNAPSHOT`.
6. **Commit** — conventional commits (`feat(<scope>): …`, `test(…)`, `fix(…)`), nothing unrelated: `git status` is
   clean except the root `pom.xml` version properties pointing at a local `-SNAPSHOT`. Use
   `superpowers:verification-before-completion`: `mvn verify` in each touched module is green. Don't push: tell the
   user the story is ready for `push <story-id>`, then stop.

Done when: every plan step is done and committed on the branch with a green `mvn verify`, nothing is pushed, and the
user knows to run `push <story-id>`.

## Mode `push <story-id>`

Precondition: the story is `In progress` and `implement` is done: `feat/<scope>-<topic>` has the plan's steps
committed. Or it is `In review` and fixes are committed on the branch (`review` step 4 runs steps 1–3 for its fixes): the
same steps release the library fixes, then update the open PR (step 4 is skipped, the story stays `In review`). Find the
branch's worktree with `git worktree list` and work from it. Otherwise stop.

Resume: skip what's done. `git cherry -v origin/main <branch>` marks with `-` the commits already on `main` (a
cherry-picked commit has a new SHA, so `git log origin/main..<branch>` still lists it) and with `+` the ones to
push; also read the release tags, the branch's PR and the story's comments.

To wait for the run a push to `main` started: `gh run list --workflow <workflow>.yml --commit <pushed sha> --json
databaseId --jq '.[0].databaseId'` (retry until it appears), then `gh run watch <run-id> --exit-status`.

1. **Push gate** — before every push, to `main` or to the branch:
    - Nothing unrelated is pushed: `git status` is clean, and `git diff origin/main --stat` touches only what this
      push is for (a library push: only that library's module, plus the root `pom.xml` version properties).
    - The root `pom.xml`'s `<ndopify-dto.version>` / `<ndopify-web-core.version>` are released versions, never a
      `-SNAPSHOT` (`git diff origin/main -- pom.xml`). Revert the local `-SNAPSHOT` edit before the gate.
    - Commits are conventional commits — except `fix dependencies: …`, the repo's message for a version bump.
    - Use `superpowers:verification-before-completion`: `mvn verify` in each touched module, on the tree being pushed
      (ktlint + tests + coverage gate; `ndopify-server` needs local MySQL). Don't push a red build.
2. **Libraries** (skip if the story changes neither). Each goes to `main`, which releases it, in a separate worktree on
   the latest `main` (`git fetch && git worktree add --detach <dir> origin/main`, removed afterwards). The release is
   automatic — no user OK — once the push gate and these **library checks** pass, right before each
   `git push origin HEAD:main`:
    - `git diff --stat origin/main..HEAD` touches only `modules/<library>/` (plus the root `pom.xml` version
      properties for a `fix dependencies` commit),
    - every commit in `git log origin/main..HEAD` is a cherry-pick of this story's branch (or the `fix dependencies`
      commit) — nothing else slipped in,
    - `mvn verify` of that library is green on this worktree.

   Print the pushed commits (`git log --oneline origin/main..HEAD`) in the mode's output, for the record.
    1. `ndopify-dto` — cherry-pick, oldest first, the branch's `+` commits (`git cherry -v origin/main <branch>`)
       that touch `modules/ndopify-dto/` → push gate + library checks → push → wait for its `ndopify-dto-main.yml`
       run to release, tag `ndopify-dto-<version>`.
    2. `ndopify-web-core` (if needed) — on a fresh worktree of the new `origin/main` (it has the release commits).
       If the dto was just released, first commit `fix dependencies: ndopify-dto <version>` (root `pom.xml`) so
       web-core builds against it. Cherry-pick the web-core `+` commits → push gate + library checks → push → wait
       for its `ndopify-web-core-main.yml` run to release, tag `ndopify-web-core-<version>`.
    3. Comment the pushed commits' links and the released versions on the story.

   A cherry-pick that doesn't apply cleanly, or a library commit that touches anything outside its module: stop and
   ask the user. A red release run: report it and stop.
3. **Branch** — revert the local `-SNAPSHOT` edit (`git checkout -- pom.xml`: `git rebase` refuses a dirty tree),
   then rebase on `origin/main`: the library commits drop out, being already upstream (if git keeps one,
   as a conflict or a non-empty commit, stop and ask). Commit `fix dependencies: ndopify-dto <version>,
   ndopify-web-core <version>` with the released versions not yet on `main`. Push gate → `git push -u origin
   <branch>`. Once the PR is open, rebase again only when needed (a conflict, or a new release it needs), then
   `--force-with-lease`.

   Library-only story (nothing left on the branch after the rebase): push the `fix dependencies` commit to `main`
   instead (push gate, no release so no OK needed), wait for the `_main.yml` run it started to be green (red: report
   it and stop), close the story (`gh issue close <id> --comment "<commit links>"`), move it to `Done`, remove the
   worktree and the branch, and stop.
4. **PR** — `gh pr create`: conventional-commit title (`feat(<scope>): …`), body linking the plan and the library
   releases, with `Refs #<id>` — never `Closes`/`Fixes`: the story is closed by `merge`, once the merge's main runs
   are green. Comment the PR link on the story and move it to `In review`. Wait for the checks (`gh pr checks <pr>
   --watch`; if red, read the failing log, fix, push, wait again), then tell the user to run `review <pr-number>`.

Done when: the library changes are released (if any), the PR is open with green checks, and the story is `In review`
with the links — or, for a library-only story, the story is `Done`.

## Mode `review <pr-number>`

Precondition: the PR's body has `Refs #<id>`, and that story is `In review`. Otherwise stop and ask which story it
belongs to. If the PR is already merged, stop and point at `merge <pr-number>` for the cleanup. Work from the branch's
worktree (`git worktree list`).

The mode loops on **review → resolve** while issues are left, at most **3 times**:

- Round 1, 2, 3: review; no finding → approve, done; findings → resolve them, next round.
- After the 3rd resolve, one last review checks the fixes: no finding → approve, done; findings → stop, nothing more
  resolved.

Each review's body starts with `Round <n>` (1 to 3, then `Final`): on a re-run, resume from the PR's reviews
(`gh pr view <pr> --json reviews`) — the last round, whether its findings were resolved (commits after it, replies on
its comments), and whether it approved the current head. An approval of the head, with its checks green, means the mode
is done.

1. **CI** — wait for every check to finish: `gh pr checks <pr> --watch`. The review starts only once they're all
   green; no check reported (a PR whose paths start no workflow) counts as green. If one is red after a resolve, it's
   a finding of that round: fix it (step 4) without counting a new round. Red before round 1: report it to the user
   (`gh run view <run-id> --log-failed`) and stop.
2. **Review** — run `/code-review high <pr>` (without `--comment`: step 3 posts the findings, in one review), and
   check the PR against the story's acceptance criteria, its plan and the repo rules. From round 2, also check that
   the previous round's findings are fixed.
3. **Verdict**:
    - No finding: approve it (`gh pr review <pr> --approve --body "<Round n|Final> — Approved: <what was checked>"`:
      on the last review the body starts with `Final`, not a round number) and stop: the PR is approved on its head,
      ready for `merge <pr-number>`; tell the user.
    - Any finding: post them in one review, with each finding as an inline comment where it makes sense. A finding
      goes inline when it's about a line the PR adds or changes (a line of the diff, on its `RIGHT` side): the comment
      is on that line, or that range (`start_line` + `line`). A finding about something the PR misses (an acceptance
      criterion, a missing test, a file it doesn't touch) or a line outside the diff goes in the review body, with its
      `file:line`. Write the payload to a file and post it:

      ```bash
      # review.json: {"event": "REQUEST_CHANGES", "body": "<Round n|Final> — Changes requested: <summary + findings not inline>",
      #   "comments": [{"path": "<file>", "line": <n>, "side": "RIGHT", "body": "<finding>"}, …]}
      gh api repos/wutsi/ndopify-mono/pulls/<pr>/reviews --method POST --input review.json
      ```

      A 422 means a comment's line isn't in the diff: move that finding to the body and post again. Then go to
      step 4 — or, on the `Final` review, stop: tell the user the findings left and the PR link. The PR stays open,
      the story `In review`; once fixed by hand, re-running `review <pr-number>` starts a new count.

   GitHub refuses `APPROVE` and `REQUEST_CHANGES` on a PR the user authored (every PR `push` opens): then post the
   same verdict with `gh pr review <pr> --comment` (approval) or `"event": "COMMENT"` (findings, inline comments
   kept), keeping the `<Round n|Final> — Approved` / `Changes requested` body.
4. **Fix** — address every finding of the round, following `superpowers:receiving-code-review`: check each one
   against the code before changing it.
    - A finding that's right: fix it on the branch with the `implement` rules (a test for a behavior change, one
      library module per commit, conventional commits, `mvn verify` green in each touched module).
    - A finding that's wrong: don't change the code, reply why (below); the next review must not raise it again.
    - A finding that needs a decision of the user (a product choice, a change to the plan or the acceptance
      criteria): stop and ask; resume the round with their answer.

   Ship the fixes with `push <story-id>` steps 1–3: a fix that touches a library is released from `main` first
   (automatically, after the push gate and library checks), then the branch is rebased if needed and pushed. Reply to each inline
   comment with the commit that fixes it, or why it isn't changed
   (`gh api repos/wutsi/ndopify-mono/pulls/<pr>/comments/<comment-id>/replies --method POST -f body="…"`), then go to
   step 1 for the next round.

Done when: the latest review approves the PR's head and its checks are green — or the mode stopped with findings left
on the `Final` review (or a question), and the user knows them.

## Mode `merge <pr-number>`

Precondition: the PR's body has `Refs #<id>`, and that story is `In review`. Otherwise stop and ask which story it
belongs to. Work from the branch's worktree (`git worktree list`). If the story is already `Done`, report it and stop;
if the PR is already merged, go to step 3 — the gate guards a merge, not the cleanup that follows it.

1. **Gate** — only a head a review approved merges. Read the latest review and the PR's head:

   ```bash
   gh api repos/wutsi/ndopify-mono/pulls/<pr>/reviews --jq '.[-1] | {state, body, commit_id}'
   gh api repos/wutsi/ndopify-mono/pulls/<pr> --jq .head.sha
   ```

   The review's body must start with `Round <n> — Approved` or `Final — Approved`, and its `commit_id` must be the
   head's SHA. The review's `state` may be `COMMENTED`: GitHub refuses `APPROVE` on a PR its author opened, so the
   body is the marker. No approval, or a commit after it → stop and ask the user to run `review <pr-number>`: never
   merge a head no review approved. Then wait for every check (`gh pr checks <pr> --watch`; no check reported counts
   as green); red → report it (`gh run view <run-id> --log-failed`) and stop. Right before merging, re-read the head
   and the latest review: if the head moved, or the latest review changed, during the wait, go back to the start of
   this step — never merge a head the approval doesn't cover.
2. **Merge** — `gh pr merge <pr> --merge` (the repo's merge commits).
3. **After the merge** — watch every run the merge commit started
   (`gh pr view <pr> --json mergeCommit --jq .mergeCommit.oid`, then `gh run list --commit <sha>` and
   `gh run watch <run-id> --exit-status` for each). If one is red, report it and stop. Once all are green, close the
   issue (`gh issue close <id> --comment "<PR link>"`), move the story to `Done`, and remove the story's worktree and
   local branch (`git worktree remove <dir>`, then `git branch -D <branch>`, from the main worktree — not from inside
   the story's).

Done when: the PR is merged, its main runs are green, the story is closed and `Done`, and its worktree and local
branch are removed — or the mode stopped with the reason (no approved head, a red check or run) and the user knows it.

## Mode `ship <story-id>`

Runs `implement`, `push`, `review` and `merge` in sequence, each in its own fresh subagent: the effect of a `/clear`
between phases (which a skill can't run). Each phase rebuilds its state from git, the story and the PR, so nothing is
carried over but the ids; and neither the review nor the merge is anchored on having written the code. `groom` stays a
mode of its own: it's a conversation with the user.

Precondition: the story is `Ready` (links a plan), `In progress` or `In review`. Otherwise stop and report its status.

**The main thread only orchestrates**: it starts the phases, relays the gates and reports. It never does a phase's
work itself (no code, commit, push, review or merge), and keeps only the phases' reports, not their tool output.

1. **Start point** — from the story's status and state: `Ready`, or `In progress` with plan steps not yet committed
   on the branch → `implement`. `In progress` with `implement` done (see its *Done when*) → `push`. `In review` →
   `review`, with the PR whose body has `Refs #<id>`
   (`gh pr list --state all --search "Refs #<id> in:body" --json number,state`); if that PR is already merged →
   `merge`.
2. **Phase** — start a new subagent (Agent tool, `subagent_type: general-purpose` — never `fork`, which inherits this
   context), then wait for its completion notification. Its prompt:

   ```text
   Invoke the `story` skill with `<mode> <id>` (ndopify-mono, <repo path>) and run that mode to its "Done when".
   You run as a subagent and can't talk to the user. Wherever the mode says to wait for the user's OK or to ask
   them, stop instead and end your report with:
     GATE: <prototype | question>
   Library releases need no OK: run them as `push` step 2 says, never stop for one.
     <what's needed: the question, or the worktree dir + the command showing the diff / the prototype files>
   Report, short: the mode's outcome (done, gate, or stopped and why), the branch and worktree, the PR number and
   link, the released library versions, the story status, and anything left for the user.
   ```

   A phase that dispatches its own subagents (`superpowers:subagent-driven-development`) and can't, from a
   subagent, uses `superpowers:executing-plans` instead.
3. **Gate** — the phase stopped on a gate: put it to the user as the mode would. For a `prototype`, point at its
   files. Then send the user's answer to the **same** subagent
   (`SendMessage`, its context kept) and wait again; a new subagent would lose the phase's progress.
4. **Next** — the phase reached its *Done when*: start the next one in a new subagent — `implement` → `push` →
   `review <pr-number>` → `merge <pr-number>` (the PR from `push`'s report). A library-only story ends with `push`
   (it's `Done`, no PR).
   The phase stopped (a red build, a failing release, findings left on the `Final` review, a step its mode doesn't
   cover): report it to the user with what's left, and stop. A re-run of `ship <story-id>` resumes from step 1.

Done when: the story is `Done` (its PR merged and the local branch removed, or a library-only story), or the chain
stopped on a phase and the user knows why and what's left.
