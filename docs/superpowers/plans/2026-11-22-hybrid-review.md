# Hybrid Review Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split a review pass's fixes between the reviewer (trivial findings) and the developer (substantive findings), keeping the three rounds and the `Final` review.

**Architecture:** Markdown-only change to four instruction files (`.claude/agents/reviewer.md`, `.claude/agents/developer.md`, `.claude/agent-report.md`, `.claude/skills/story/SKILL.md`), the README story row, and the design record. The reviewer classifies and posts one review per pass, fixes a pass whose findings are all trivial, and otherwise hands the pass to the developer with a new `OUTCOME: handoff`; the developer fixes or rebuts each finding, pushes, answers each thread, and returns the PR to the reviewer.

**Tech Stack:** Markdown, the OpenCode/Claude Code subagent tool, GitHub CLI.

**Spec:** `docs/superpowers/specs/2026-11-22-hybrid-review-design.md`

## Global Constraints

- Branch `chore/hybrid-review` from `main` before editing. Stay on it.
- Touch only `.claude/agent-report.md`, `.claude/agents/reviewer.md`, `.claude/agents/developer.md`,
  `.claude/skills/story/SKILL.md`, `README.md` (the `story` row only) and the two docs. Never stage or revert other dirty
  files.
- The three rounds and the `Final` review stay: rounds 1–3 may end in fixes, by the reviewer or the author; the `Final`
  never fixes, and its findings stop the run.
- The review body's prefix stays exact: `<Round n|Final> — Changes requested` (then the fixer) or
  `<Round n|Final> — Approved` (the merge step reads it).
- Findings are anchored `F1`, `F2`, …; inline on a line the PR adds or changes (`RIGHT`), in the body with `file:line`
  otherwise; a 422 moves that finding to the body.
- The reviewer fixes a pass only when every finding is trivial; any substantive finding hands the whole pass to the
  author. When in doubt, substantive.
- The developer never reviews, approves or merges; the story stays in `tracker.statuses.review` while it addresses a
  review (no board move, no new PR).
- No new skill mode, no permission change, no change to `analyst`, `designer` or `deployer`.
- UI text, e-mails and contracts are French; code, comments, logs and these docs are English.
- Commit prefixes: `docs(agents):` for Task 1, `refactor(agents):` for the reviewer, `feat(agents):` for the developer,
  `refactor(story):` for the skill, `docs(readme):` for the README.

## Review Focus

- **A substantive pass fixed by the reviewer anyway** — self-approval returns. Pinned by the routing rule and the
  mixed-pass scenario check in Task 6.
- **A handoff read as `blocked`** — `ship` stops before the author fixes, or reads it as `done` and merges. Pinned by
  the `OUTCOME: handoff` protocol change and the skill's dispatch bullet.
- **The three-round cap lost across two agents** — the author restarts the count, or the `Final` gets fixed. Pinned by
  the marker table in the reviewer, the reviewer counting from the PR's reviews, and `ship` stopping on a `Final`.
- **`ship` resuming the wrong agent** — a handoff at the head sent back to the reviewer, or a reviewer's own unfinished
  pass sent to the author. Pinned by the machine-readable fixer marker and the four start-point rows.
- **A finding the author cannot act on** — a body finding with no channel for the answer, so the reviewer re-raises it
  forever. Pinned by the anchor checklist and the thread/PR-comment reply rule in both agents.

---

### Task 1: Branch and commit the design and plan

**Files:**
- Create: `docs/superpowers/specs/2026-11-22-hybrid-review-design.md` (written)
- Create: `docs/superpowers/plans/2026-11-22-hybrid-review.md` (this file)

**Interfaces:**
- Consumes: the approved design.
- Produces: the branch and the record every later task follows.

- [ ] **Step 1: Create the branch**

```bash
git switch -c chore/hybrid-review
git status --short
```

Expected: on `chore/hybrid-review`; only the two new docs are untracked.

- [ ] **Step 2: Stage and commit the docs**

```bash
git add docs/superpowers/specs/2026-11-22-hybrid-review-design.md \
  docs/superpowers/plans/2026-11-22-hybrid-review.md
git diff --cached --name-only
git commit -m "docs(agents): add the hybrid review design and plan"
```

Expected: exactly the two paths staged, then committed.

---

### Task 2: The report protocol and the reviewer

**Files:**
- Modify: `.claude/agent-report.md:11-24`
- Modify: `.claude/agents/reviewer.md`

**Interfaces:**
- Consumes: the routing rules and the handoff from the design.
- Produces: `OUTCOME: handoff` for every agent's report; the reviewer emits it with `NEXT: implement <id>`; the review
  body format `Round <n> — Changes requested (author|reviewer)` / `<Round n|Final> — Approved`; findings anchored
  `F1…`, answered by a thread reply or a PR comment quoting the anchor.

- [ ] **Step 1: Add `handoff` to the report template**

In `.claude/agent-report.md`, change the `OUTCOME` line to:

```
OUTCOME: done|handoff|gate|blocked
```

and add this bullet after the `OUTCOME: done` bullet:

```markdown
- `OUTCOME: handoff` — the phase ended as expected and another agent continues; `NEXT` names it (a reviewer handing a
  pass to the author). It is not `blocked`: the chain continues.
```

- [ ] **Step 2: Rewrite the reviewer's frontmatter description and intro**

Frontmatter `description`:

```
description: Use when reviewing a story's PR — review it against the story, plan and repo rules, fix the trivial findings, hand a pass with substantive findings to the author, and loop until it approves the head.
```

Intro:

```markdown
# Story reviewer

You review one story's pull request. You fix the trivial findings yourself and hand a pass with any substantive finding
to its author, whose fix comes back to you. You never merge: the repo's merge step owns that.
```

Keep the argument, precondition and "read before anything else" paragraphs as they are. Add to the precondition, at the
end of the first paragraph: `A pass whose last review is `Round n — Changes requested (reviewer)` on the head resumes
your resolve, not a new review.`

- [ ] **Step 3: Replace the loop section**

Replace the current `## The loop` body with:

```markdown
Loop on **review → route → resolve** while findings are left, at most **3 times** (rounds 1–3):

- Round 1, 2, 3: review; no finding → approve, done. Findings → route the pass (step 3): fix its trivial findings
  yourself when every finding is trivial, or hand the whole pass to the author when any finding is substantive.
- After round 3's fixes, one **Final** review: no finding → approve, done; findings → stop, nothing more resolved.

A **round** is one review pass. The review body starts with `Round <n>` (1–3) or `Final`, then `— Changes requested`
for findings (followed by the fixer, `(author)` or `(reviewer)`) or `— Approved`. Keep that prefix exact: the merge
step reads it.

On a re-run, resume from the PR's reviews (`gh pr view <pr> --json reviews`): the last round, its fixer, whether its
findings were addressed (commits after it, replies in its comment threads, a PR comment quoting their anchors), and
whether it approved the current head. An approval of the head, with its checks green, means the work is done. A
`Round n — Changes requested (reviewer)` on the head with nothing pushed since resumes the resolve, not a new review.
```

- [ ] **Step 4: Extend the review step with anchors and the answer check**

At the end of `## 2. Review`, add:

```markdown
Every finding carries an anchor, `F1`, `F2`, …, and the review body opens with a numbered checklist of all of them. A
finding about a line the PR adds or changes goes inline on that line or range (`RIGHT`), its detail and anchor in the
comment; a missed item, a missing test or a line outside the diff goes in the body with `file:line`.

On a re-review, read the author's answers — the thread replies and the PR comments quoting an anchor. Drop a finding an
answer satisfies; repeat one that stands, with why; never silently re-raise it.
```

- [ ] **Step 5: Replace the verdict section**

Replace `## 3. Verdict` with:

```markdown
## 3. Verdict and route

**No finding** — approve it and stop: the PR is approved on its head, ready for the repo's merge step. End with the
report (`OUTCOME: done`, `APPROVAL: yes`, `NEXT: merge <pr-number>`).

```bash
gh pr review <pr> --approve --body "<Round n|Final> — Approved: <what was checked>"
```

**Any finding** — post them in one review, then route the pass. The body starts with `Round <n> — Changes requested
(<fixer>)`, a numbered checklist of every finding (`F1` `file:line` — one line, …), then the detail of the findings
that are not inline. A finding goes inline when it is about a line the PR adds or changes (a line of the diff, on its
`RIGHT` side): the comment is on that line, or that range (`start_line` + `line`), and starts `F<n> — `. A finding
about something the PR misses (an acceptance criterion, a missing test, a file it doesn't touch) or a line outside the
diff goes in the review body, with its `file:line`. Write the payload to a file and post it:

```bash
# review.json:
#   {"event": "REQUEST_CHANGES", "body": "<Round n — Changes requested (author|reviewer)> <checklist + findings not inline>",
#    "comments": [{"path": "<file>", "line": <n>, "side": "RIGHT", "body": "F<n> — <finding>"}, …]}
gh api repos/<owner>/<repo>/pulls/<pr>/reviews --method POST --input review.json
```

A 422 means a comment's line is not in the diff: move that finding to the body and post again. Then route:

- **Every finding trivial** — go to step 4; you fix them, and the next review is the next round.
- **At least one substantive finding** — edit nothing, commit nothing, push nothing. End with `OUTCOME: handoff`,
  `APPROVAL: no`, `NEXT: implement <id>`, the findings on the PR and a one-line summary in `NOTES`. The author fixes
  every finding of the pass; the next review is the next round.

Trivial — you may fix:

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
- a fix spanning modules, or one you are not sure of.

When in doubt, substantive. A trivial fix that changes a shared module's published contract is substantive. One fixer
per pass, so two sessions never write the same branch between reviews.

On a finding that needs a product or plan decision, say so in the finding: it is substantive, and the author takes the
decision with the user.

On the `Final` review, findings end the run: `OUTCOME: blocked`, `APPROVAL: no`, the findings in `NOTES`, and
`NEXT: fix the findings, then rerun review <pr-number>`. The PR stays open, the story stays in
`tracker.statuses.review`; once fixed by hand, re-running the reviewer starts a new count.

GitHub refuses `APPROVE` and `REQUEST_CHANGES` on a PR the user authored (the developer agent opens every PR): then
post the same verdict with `gh pr review <pr> --comment` (approval) or `"event": "COMMENT"` (findings, inline comments
kept), keeping the `<Round n|Final> — Approved` / `Changes requested` body.
```

- [ ] **Step 6: Rewrite the resolve section for trivial findings**

Replace the `## 4. Resolve` heading and its first paragraph with:

```markdown
## 4. Resolve

Only a pass whose findings are all trivial reaches this step. Address every finding of the round, following
`superpowers:receiving-code-review`: check each one against the code before changing it, one at a time.

If you discover a substantive finding while resolving, stop resolving:

- before your first push — revert the working-tree edits, edit the review body to `(author)` and add the finding, then
  hand off; the author fixes the whole pass;
- after a push — finish the trivial fixes, add the finding as a PR comment on the same round, anchored, then hand off;
  the author fixes it in the next round.
```

Keep the three bullets (right / wrong / needs a decision) but change the decision bullet to point at the rule above:
`A finding that turns out to need a user decision — it is substantive: apply the re-classification rule above.` Keep the
push gate, the shared-module release and the branch text as they are, and keep the inline-reply step.

- [ ] **Step 7: Update the report, stop-and-ask and done-when sections**

In `## Report`, after the approval sentence, add:

```markdown
A handoff is `OUTCOME: handoff`, `PR: <number>`, `STATUS: <tracker.statuses.review>`, `APPROVAL: no`,
`NEXT: implement <id>`.
```

In `## Stop and ask`, remove `a finding needs a product decision or a change to the plan or the acceptance criteria;`
(it is now the author's) and keep every other item.

Replace `## Done when` with:

```markdown
## Done when

The latest review approves the PR's head and its checks are green, and the report says `OUTCOME: done`, `APPROVAL: yes`,
`NEXT: merge <pr-number>` — or you handed a pass to the author (`OUTCOME: handoff`, `NEXT: implement <id>`), or you
stopped with findings on the `Final` review (or a question) and the report says so.
```

- [ ] **Step 8: Verify the reviewer**

```bash
grep -n 'OUTCOME: handoff' .claude/agent-report.md .claude/agents/reviewer.md
grep -n 'Round <n>\|Final' .claude/agents/reviewer.md
grep -n '(author)\|(reviewer)' .claude/agents/reviewer.md
grep -n 'F1\|F<n>' .claude/agents/reviewer.md
grep -n 'gh pr merge' .claude/agents/reviewer.md
```

Expected: two files for `handoff`; the round markers and both fixers present; the anchors present; `gh pr merge` only in
a denied permission, never as an instruction.

Read the file once against *Review Focus* 1, 2, 3 and 5.

- [ ] **Step 9: Commit**

```bash
git add .claude/agent-report.md .claude/agents/reviewer.md
git commit -m "refactor(agents): route review findings between the reviewer and the author"
```

---

### Task 3: The developer's review path

**Files:**
- Modify: `.claude/agents/developer.md`

**Interfaces:**
- Consumes: the reviewer's review body (checklist, anchors, `(author)` marker), the thread replies and the PR comments.
- Produces: fixes or rebuttals on the branch, a thread reply or PR comment per finding, `NEXT: review <pr>`; the
  developer emits no new outcome and never reviews.

- [ ] **Step 1: Update the frontmatter description and intro**

Frontmatter `description`:

```
description: Use when implementing a story in the ready state — code its plan on the story's branch, with tests, release its library changes, push it and open its PR for review, or address that PR's review findings.
```

Intro: after the first sentence, add `You also address a review's findings on that PR, and answer each one where it was
posted.`

- [ ] **Step 2: Extend the precondition**

In the precondition paragraph, after `which resumes at the checks`, add:

```markdown
When the last review starts with `Round n — Changes requested` or `Final — Changes requested`, the resume first
addresses the review (*Address the review*).
```

- [ ] **Step 3: Add the Address the review section**

Insert this section after `### PR` and before `## Story tracker`:

```markdown
## Address the review

Reached on the In-review resume when the last review starts with `Round n — Changes requested` or `Final — Changes
requested`. The story stays in `tracker.statuses.review`: skip §1's assign-and-move, and do not open a new PR.

1. Read the last review, its numbered checklist, its inline threads, the PR comments and the existing replies
   (`gh pr view <pr> --json reviews,comments`; `gh api repos/<owner>/<repo>/pulls/<pr>/comments`).
2. For each finding, by its anchor `F<n>`:
   - right — fix it under the implement rules (§2–3): a test for a behavior change, conventional commits, at most one
     shared module per commit;
   - wrong — do not change the code; answer why (step 4);
   - needs a product or plan decision — stop and ask; `OUTCOME: gate`, `GATE: question`, and resume with the answer.
3. Verify (`verification.command` in each touched module) and run the push gate and the shared-module release procedure
   of §4, exactly as for any push.
4. Push, wait for the checks green (`gh pr checks <pr> --watch`). Answer each finding where it was posted: reply in its
   thread for an inline one
   (`gh api repos/<owner>/<repo>/pulls/<pr>/comments/<comment-id>/replies --method POST --raw-field body="…"`), a PR
   comment quoting its anchor for a body one (`gh pr comment <pr> --body "F3: …"`), with the commit that fixes it or
   why it is not changed.
5. Report `NEXT: review <pr-number>`.

You never review, approve or merge. A `Final — Changes requested` is addressed the same way only when a human runs this
mode explicitly: `ship` never dispatches past a `Final`, and the reviewer starts a new count when it re-reviews.
```

- [ ] **Step 4: Update the report and done-when**

In `## Report`, add after the successful-run sentence:

```markdown
A review addressed is `OUTCOME: done` with `PR`, `STATUS: <tracker.statuses.review>` and `NEXT: review <pr-number>`.
```

In `## Done when`, add before the final clause:

```markdown
— or, resuming a review, every finding is fixed or answered on the branch, the checks are green, and the report says
`NEXT: review <pr-number>` —
```

- [ ] **Step 5: Verify the developer**

```bash
grep -n 'Address the review' .claude/agents/developer.md
grep -n 'NEXT: review <pr-number>' .claude/agents/developer.md
grep -n 'F3\|F<n>' .claude/agents/developer.md
grep -n 'gh pr review' .claude/agents/developer.md
grep -n 'tracker.statuses.review' .claude/agents/developer.md
```

Expected: the section and its report line; the anchor reply; `gh pr review` only as a denied permission; the board
status line present.

Read the section once against *Review Focus* 5: every body finding has a reply channel.

- [ ] **Step 6: Commit**

```bash
git add .claude/agents/developer.md
git commit -m "feat(agents): address a review's findings on the developer agent"
```

---

### Task 4: The story skill

**Files:**
- Modify: `.claude/skills/story/SKILL.md`

**Interfaces:**
- Consumes: `OUTCOME: handoff`, `NEXT: implement <id>`, the four review markers.
- Produces: `ship` dispatch and resume rules; the `review` mode's report; the start-point table rows.

- [ ] **Step 1: Update the agent table**

The reviewer row's *done* column becomes:

```markdown
`APPROVAL: yes`, `NEXT: merge <pr>` — or `OUTCOME: handoff`, `NEXT: implement <id>`
```

- [ ] **Step 2: Add the handoff dispatch bullet**

In `## Dispatch`, after the `OUTCOME: gate` bullet, add:

```markdown
- `OUTCOME: handoff` — the phase ended as expected and another agent continues: in `ship`, follow `NEXT`; in an
  explicit mode, report it and stop.
```

- [ ] **Step 3: Update the review mode**

Replace the `### review <pr-number>` body with:

```markdown
Dispatch `reviewer <pr-number>`. Relay its question gates. On `APPROVAL: yes`, report it; next is `merge <pr>`. A
handoff (`OUTCOME: handoff`, `NEXT: implement <id>`) reports the findings and the developer as the next action. A
`Final` review with findings stops: report the findings in `NOTES`, the pull request link and the reviewer's `NEXT`.
```

- [ ] **Step 4: Update the ship chain and the start point**

In `## Ship`, replace the "Read the story's status and its pull request" block with:

```bash
gh issue view <id> --json projectItems --jq '.projectItems[].status.name'
gh pr list --state all --search "<repository.story_ref with the id> in:body" \
  --json number,state,mergedAt
gh api repos/<owner>/<repo>/pulls/<pr>/reviews --jq '.[-1] | {body, commit_id}'
```

and add after it:

```markdown
Route on the latest review's body and `commit_id`: `<Round n|Final> — Approved` on the head → merge; `Final —
Changes requested` → stop, the cap is reached; `Round n — Changes requested (reviewer)` on the head → reviewer;
`Round n — Changes requested (author)` on the head → developer; any other review whose head has moved since → reviewer.
The head is `gh pr view <pr> --json headRefOid --jq .headRefOid`.
```

In the start-point table, insert these rows before the `an open pull request, with the story in
tracker.statuses.review` row:

```markdown
| an open pull request whose latest review is `Final — Changes requested` | stop; the three rounds are spent, the user fixes and reruns `review <pr>` |
| an open pull request whose latest review is `Round n — Changes requested (author)` on its head | `developer` (address the review) |
| an open pull request whose latest review is `Round n — Changes requested (reviewer)` on its head | `reviewer` (resume its resolve) |
```

In the paragraph after the table, add: `reviewer NEXT: implement <id> → developer;` before
`reviewer NEXT: merge <pr> → deployer`.

- [ ] **Step 5: Update done-when**

In `## Done when`, add `— or the reviewer handed a pass to the developer and the chain is at the developer` before the
existing blocked clause.

- [ ] **Step 6: Verify the skill**

```bash
grep -n 'OUTCOME: handoff' .claude/skills/story/SKILL.md
grep -n 'NEXT: implement' .claude/skills/story/SKILL.md
grep -n 'Changes requested' .claude/skills/story/SKILL.md
grep -n 'gh pr merge\|gh pr create\|git push\|gh project item-edit' .claude/skills/story/SKILL.md
```

Expected: the handoff bullet and the `review` mode; `NEXT: implement` in the table and the chain; the four markers in
the start point; no phase work in the skill.

Read the ship section once against *Review Focus* 2, 3 and 4.

- [ ] **Step 7: Commit**

```bash
git add .claude/skills/story/SKILL.md
git commit -m "refactor(story): route a review handoff through the developer"
```

---

### Task 5: The README story row

**Files:**
- Modify: `README.md:163` (the `story` row)

**Interfaces:**
- Consumes: the final mode behaviour.
- Produces: the documentation entry.

- [ ] **Step 1: Replace the story row**

Change only the `review <pr-number>` phrase of the row to:

```markdown
`review <pr-number>` (the review loop: the reviewer fixes the trivial findings, the developer the substantive ones, up to three rounds and a final review)
```

- [ ] **Step 2: Verify it is one row and the badges are untouched**

```bash
grep -n '\[`story`\]' README.md
git diff --stat README.md
```

Expected: one row; one file, one insertion and one deletion.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs(readme): describe the hybrid review"
```

---

### Task 6: Cross-file verification

**Files:**
- Verify: all five changed files; modify only if a check fails.

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces: a consistent state machine across the three agents and the skill.

- [ ] **Step 1: Check the protocol**

```bash
grep -n 'OUTCOME: done|handoff|gate|blocked' .claude/agent-report.md
grep -n 'OUTCOME: handoff' .claude/agents/reviewer.md .claude/skills/story/SKILL.md
grep -n 'OUTCOME: gate' .claude/agents/developer.md
```

Expected: the template's values, the reviewer's emission, the skill's dispatch rule, the developer's question gate.

- [ ] **Step 2: Walk the three scenarios**

- **All-trivial pass:** review 1 posts `(reviewer)`, fixes, pushes, review 2 approves on the head, `NEXT: merge`. Check
  the reviewer's loop, step 4 and the push gate.
- **Mixed pass:** review 1 posts `(author)` and nothing is edited; the report is `OUTCOME: handoff`,
  `NEXT: implement <id>`; the skill's `ship` goes to the developer; the developer fixes, answers, pushes,
  `NEXT: review <pr>`; `ship` resumes the reviewer; review 2 approves.
- **Final with findings:** `(author)` on round 3 → author fixes → `Final` finds something → `OUTCOME: blocked`, the
  chain stops; `ship` restarted on that PR stops on the `Final` row.

Fix any sentence that contradicts one of them.

- [ ] **Step 3: Check for stale prose**

```bash
grep -n 'Resolve\|resolve' .claude/agents/reviewer.md | head -30
grep -n 'review loop\|approval loop' README.md .claude/skills/story/SKILL.md
```

Expected: the resolve is described as trivial-only; no "approval loop" left that ignores the handoff.

- [ ] **Step 4: Confirm the tree**

```bash
git status --short
git log --oneline -6
```

Expected: clean tree; the five commits from Tasks 1–5.

- [ ] **Step 5: Fix and commit if a check failed**

```bash
git add .claude/agent-report.md .claude/agents/reviewer.md .claude/agents/developer.md \
  .claude/skills/story/SKILL.md README.md
git commit -m "fix(agents): align the review handoff with the skill"
```

Skip when no check failed.
