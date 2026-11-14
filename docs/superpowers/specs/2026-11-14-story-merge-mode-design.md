# Story skill: split `merge` out of `review`

## Intent

`review` becomes the review loop only. A new `merge <pr-number>` mode merges the PR and does the
post-merge work. `ship` chains `implement → push → review → merge`.

## Why

- A merge is not a bookkeeping step: the `-main` workflows deploy `ndopify-server`, `ndopify-esignature` and
  `ndopify-partner-central` to test (Heroku) and commit coverage badges back to `main`. It is the same class of
  irreversible action as a library release, so it gets its own step.
- `review` gets a clean terminal state: an approval on the current head. Today it both approves and merges, then
  closes the story, spanning three states in one mode.
- Under `ship`, a fresh subagent performs the merge, so the reviewer never merges what it reviewed.

## Decisions

- **No user OK inside `merge`.** Running the mode — or `ship` reaching it — is the approval. The gate is the
  review's approval on the current head plus green checks.
- **Merge gate.**
  - The PR body has `Refs #<id>`, the story is `In review`, and the PR is open.
  - The latest review's body starts with `<Round n|Final> — Approved` and its `commit_id` is the PR head
    (`gh api repos/wutsi/ndopify-mono/pulls/<pr>/reviews` — the last review — and
    `gh api repos/wutsi/ndopify-mono/pulls/<pr> --jq .head.sha`). The review's `state` may be `COMMENTED`:
    GitHub refuses `APPROVE` on a PR its author opened, so the body marker matters, not the state.
  - Any commit after the approval, no approval, or a review still in progress → stop and ask the user to run
    `review <pr-number>`. Never merge a head no review approved.
  - All checks finished and green (`gh pr checks <pr> --watch`; no check reported counts as green). Red → report
    it and stop.
- **After the merge.** Watch every run the merge commit started; if one is red, report it and stop. Once all are
  green: close the issue with the PR link, move the story to `Done`, and remove the worktree and local branch from
  the main worktree.
- **Resume.** PR already merged → run only the after-merge steps. Story already `Done`, branch gone → report done.
- **`review`.** An approval ends the mode: report it and point at `merge <pr-number>`; step 5 (after the merge)
  moves to `merge`. A merged PR stops the mode and points at `merge` instead of running the cleanup itself. The
  `Round n` / `Final` loop and its 3-round limit are unchanged.
- **`ship`.** Phases become `implement → push → review → merge`, each in a fresh subagent. A library-only story
  still ends at `push`. A review that stops with findings on the `Final` review (or on a question) stops the chain
  before `merge`.
- **Docs to update.** The frontmatter description, the modes list, the "Merging and releases" paragraph, `push`'s
  "closed by" line, the whole `review` mode, the new `merge` mode, `ship`, and the `story` row in `README.md`.

## Out of scope

- The 3-round review → resolve loop, the library release gates, and `groom` / `implement` / `push` behavior.

## Verification

No automated test covers skill prose. Read-through: every responsibility of the old `review` step 5 now appears in
`merge` and nowhere else; every reference to `review` merging is updated (frontmatter, header, `push`, `ship`,
`README.md`); the new mode's commands match the current tracker and `gh` usage in the skill.
