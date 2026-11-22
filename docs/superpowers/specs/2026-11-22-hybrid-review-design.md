# Review ownership: the reviewer fixes the trivial, the author fixes the substantive

## Intent

Split a review pass's fixes between the reviewer and the developer: the reviewer keeps the verdict, the inline
findings and the trivial corrections; a behavior, design or contract finding goes back to the developer, whose fix
returns to the reviewer. The three review rounds and the `Final` review stay.

## Why

The reviewer currently writes the fix and then approves its own fix on the final head — self-review — and holds write
access to the default branch to release its fixes. The repo already separated `merge` from `review` for the same
reason ("the reviewer never merges what it reviewed", `2026-11-14-story-merge-mode-design.md`). But full separation
(approach A) costs a developer session per typo: most findings are small. The hybrid keeps the cheap case in one
session and sends only the findings that need the author's ownership of the design back to the author.

## Goals

- The reviewer fixes a pass only when every finding in it is trivial; any substantive finding hands the whole pass to
  the developer.
- At most three review passes that end in fixes (rounds 1–3), then one `Final` review that never fixes.
- The author addresses a handoff: fixes or rebuts each finding, runs the full push/release procedure, answers each
  finding where it was posted, pushes, and returns `NEXT: review <pr>`.
- The reviewer keeps posting one review per pass, with inline comments on the lines the PR adds or changes.
- The story skill follows the new transitions in `ship` and refreshes the `ship` start point.

## Non-goals

- The analyst, the designer, the deployer or the merge gate.
- The board, the prototype, the models, the step caps.
- A new skill mode: `implement` already resumes an In-review story.
- The agents' write permissions: the reviewer keeps them for its trivial fixes.

## Anti-goals

- The reviewer changing behavior, a contract, a migration or the plan.
- The author approving, reviewing or merging.
- The reviewer and the author fixing the same pass.
- Counting a handoff as `blocked`, which would stop `ship` before the author's fix.
- A finding without a location the author can act on.

## Constraints

- The report protocol (`agent-report.md`): `OUTCOME`, `GATE`, `STATUS`, `APPROVAL`, `NEXT`; the orchestrator reads only
  the report.
- The merge gate reads the latest review's body: it starts with `<Round n|Final> — Approved` and its `commit_id` is
  the PR head.
- GitHub refuses `APPROVE` and `REQUEST_CHANGES` on the user's own PR; the reviewer already falls back to a `COMMENT`
  review with the same body marker.
- The developer's push gate, shared-module release and verification rules are unchanged and apply to its fixes.

## Approaches

- **A — full separation**: the reviewer never edits; every finding, however small, goes to the developer. The most
  independent, but every typo costs a developer session and a full round trip. Rejected.
- **B — current**: the reviewer reviews and fixes everything, then approves its fix. One session per pass, but
  self-approval on behavior changes and the reviewer writing to the default branch. Rejected.
- **C — hybrid (chosen)**: the reviewer fixes a pass whose findings are all trivial; a pass with any substantive
  finding goes to the author. Keeps the common case cheap and the substantive case owned by the author.

## Decisions

### Routing a pass

After posting the review:

- no finding → approve;
- every finding trivial → the reviewer fixes the pass (*Resolve*) and the next review is the next round;
- at least one substantive finding → the reviewer changes nothing; the developer fixes every finding of the pass; the
  next review is the next round.

One fixer per pass, so two sessions never write the same branch between reviews.

**Resolve, re-classified.** The review is classified and posted before anything is edited. If the reviewer discovers
a substantive finding while resolving:

- before its first push — revert the working-tree edits, edit the review body to `(author)` and add the finding, then
  hand off; nothing was pushed, the author fixes the whole pass;
- after a push — finish the trivial fixes, add the finding as a PR comment on the same round (anchored), then hand
  off; the author fixes it in the next round.

### Trivial or substantive

Trivial — the reviewer may fix:

- a missing or weak test for behavior the story already defines, or a wrong assertion helper;
- a formatting/ktlint fix, a KDoc or comment, developer documentation of what the code does;
- a UI label, error message or French wording, with no contract change;
- an obvious guard (null/empty/blank) at a boundary the plan already covers;
- a typo.

Substantive — the author:

- a behavior the story, the plan or a test defines, an endpoint, a DTO or a published contract;
- a schema change or migration;
- architecture, integration, tenancy, authorization or security;
- concurrency, performance, a new dependency;
- a deviation from the plan or the acceptance criteria, or a plan/story update;
- a fix spanning modules, or one the reviewer is not sure of.

When in doubt, substantive. A trivial fix that changes a shared module's published contract is substantive.

### Rounds

- A **round** is one review pass; rounds 1–3 may end in fixes, by the reviewer or the author.
- The review body starts with `Round <n>` (1–3) or `Final`, then `— Changes requested`, and names the fixer:
  `(author)` or `(reviewer)`.
- After round 3's fixes, one `Final` review; it never fixes, and its findings stop the chain.
- The reviewer counts the rounds from the PR's reviews; the skill counts nothing.
- On a re-run, the reviewer resumes from the last review: its round, whether its findings were addressed, and whether
  it approved the head.
- The prefix and the fixer marker are machine-readable, because `ship` routes on them:
  - `Approved` on the head → merge;
  - `Final — Changes requested` → stop, the cap is reached;
  - `Round n — Changes requested (reviewer)` on the head → the reviewer resumes its own resolve;
  - `Round n — Changes requested (author)` on the head → the developer;
  - any other review whose head has moved since → the reviewer (the fix exists; check it).
- A `Final — Changes requested` is never handed off inside `ship`. An explicit `implement <id>` may address it — the
  same by-hand escape hatch as today — and re-running the reviewer then starts a new count.

### The review

- One review per pass, never a comment per finding.
- Every finding carries an anchor, `F1`, `F2`, …, and the review body opens with a numbered checklist of all of them,
  with the fixer marker. A finding about a line the PR adds or changes goes inline on that line or range (`RIGHT`),
  its detail and anchor in the comment; a missed item or a line outside the diff goes in the body with `file:line`;
  a 422 moves that finding to the body and posts again.
- Every finding is actionable by its fixer: what is wrong, where, why it matters, and what would satisfy it.
- Findings the reviewer sets aside as out of scope stay listed one line each, not fixed.
- A finding that needs a product or plan decision is substantive and says so; the author takes that decision with the
  user.
- The author answers an inline finding in its thread; a body finding is answered by a PR comment quoting its anchor
  (`F3: …`). When re-reviewing, the reviewer reads both: a finding is dropped when the answer satisfies it, or
  repeated with why it stands — never silently re-raised.

### Handoff

- The reviewer ends the pass with `OUTCOME: handoff`, `APPROVAL: no`, `NEXT: implement <id>`, the findings on the PR
  and a one-line summary in `NOTES`.
- `handoff` is a new `OUTCOME` value in `agent-report.md`, next to `done`, `gate`, `blocked`: the phase finished as
  expected and another agent continues. It is not `blocked` (that stops `ship`) and not `done` (nothing was approved).
- The skill's *Dispatch* follows a `handoff`'s `NEXT` in `ship`, and reports it and stops in an explicit mode.

### The author's fix

`developer.md` gains an *Address the review* path, reached by its existing In-review resume:

1. read the last review, its checklist, its inline threads and the PR comments;
2. for each finding: right → fix under the implement rules (test for behavior, conventional commits, one shared module
   per commit); wrong → answer why, no code change; a product/plan decision → gate the user;
3. run the push gate and the shared-module release procedure (§4) as for any push;
4. push, wait for the checks, answer each finding (the thread for an inline one, a PR comment quoting `F<n>` for a
   body one) with the fixing commit or the reason, and report `NEXT: review <pr>`.

The story stays in `tracker.statuses.review` throughout: the path skips §1's assign-and-move, since the PR is still
under review until the merge closes the story. The developer never reviews, approves or merges. It addresses both a
`Round n` and a `Final` review; the cap lives in `ship`, which never dispatches it past a `Final`, and in the
reviewer's counter.

### The story skill

- `review <pr>`: an approval reports `NEXT: merge <pr>`; a handoff reports the findings and the next action
  (`implement <id>`); the `Final` findings stop and report.
- `ship`: reviewer `NEXT: implement <id>` → developer; developer `NEXT: review <pr>` → reviewer; the `Final` findings
  stop.
- `ship` start point, before the open-PR/review row, routes on the latest review's marker (see *Rounds*): `(author)`
  at the head → developer, `(reviewer)` at the head → reviewer, `Final — Changes requested` → stop, an approval at the
  head → deployer.

## Out of scope

- Automation of the round count, tests for the prose, the harness registration.
- Tightening the reviewer's permissions beyond keeping them as they are.

## Verification

No automated test covers agent prose.

- Read-through: the three routing outcomes, the three round markers and their fixer marker, the anchors, the handoff
  and the author's path, the inline rule, the `ship` transitions and start point.
- Greps: `OUTCOME: handoff` in `agent-report.md` and the reviewer; `Round <n>`/`Final` and `(author)`/`(reviewer)` in
  the reviewer; `NEXT: implement` in the reviewer and the skill; `F1`/`F<n>` anchors in the reviewer and the
  developer; no `gh pr review` in `developer.md`.
- Dispatch probe: `reviewer` and `developer` resolve after the harness reload.
- Scenario check: an all-trivial pass (reviewer fixes, round 2 approves); a mixed pass (handoff, author fixes, round 2
  approves); a re-classified pass (reviewer finds a substantive just before pushing); a `ship` resume on each of the
  four markers; a `Final` with findings (stop).

## Docs

- `.claude/agents/reviewer.md`, `.claude/agents/developer.md`, `.claude/agent-report.md`,
  `.claude/skills/story/SKILL.md`, `README.md` (story row).
