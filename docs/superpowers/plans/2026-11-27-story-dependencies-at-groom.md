# Story dependencies at groom — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The analyst records every story-level prerequisite as a GitHub `blockedBy` link before a story reaches Ready, so `ship-sprint` computes a correct queue.

**Architecture:** Prompt-only change to two markdown files: the analyst agent gains the link step in its Close-the-loop section, and the ship-sprint skill names the missing command in a blocked report. No code, no queue-algorithm change.

**Tech Stack:** Markdown, `gh` CLI 2.104+ (`gh issue edit --add-blocked-by`), GitHub GraphQL for verification.

**Spec:** `docs/superpowers/specs/2026-11-27-story-dependencies-at-groom-design.md`

## Global Constraints

- `ship-sprint` still never edits the board and still ignores prose; only its blocked report changes.
- The queue algorithm, the Ready definition, statuses and the board schema are unchanged.
- `.claude/skills/<name>/SKILL.md` and `.opencode/skills/<name>/SKILL.md` are hardlinks (same inode). After editing,
  verify `ls -li` shows one inode and `diff -q` prints nothing; if an editor replaced the file, restore the link with
  `ln -f .claude/skills/ship-sprint/SKILL.md .opencode/skills/ship-sprint/SKILL.md`.
- Commits are conventional (`docs(agents): …`) with `Refs #300, #393`.
- Do not push; leave the commits local.

## Review Focus

- **Over-linking** — a parent (`Part of #X`), a shared-code release, or a “blocks nothing” mention becomes a
  `blockedBy`, and the sprint stalls on a story that blocks nothing. Pinned by the negative probe in Task 3.
- **Under-linking** — the prose *Dependencies* section is still written but no link is created, i.e. the bug this plan
  fixes survives. Pinned by the Done-when criterion in Task 1 and the graph check in Task 3.
- **Stale link** — a revised plan drops a prerequisite but the link stays, so the queue waits forever. Pinned by the
  reconcile sentence and its report requirement in Task 1.
- **Broken hardlink** — the OpenCode copy of the skill drifts from the Claude copy. Pinned by the inode step in Task 2.
- **Ambiguous ownership** — the ship-sprint wording reads as if the sprint skill may add the link. Pinned by the
  read-through in Task 2.

---

### Task 1: The analyst sets `blockedBy` links at groom

**Files:**
- Modify: `.claude/agents/analyst.md` (§1 *Enrich*, §5 *Close the loop*, *Done when*)

**Interfaces:**
- Consumes: nothing.
- Produces: the rule and command that Task 3 verifies.

- [ ] **Step 1: Amend the §1 body bullet**

Replace the bullet `- dependencies, risks, assumptions, out of scope;` with:

```markdown
- dependencies, risks, assumptions, out of scope — *dependencies* names every story-level prerequisite (the stories
  whose merged work the plan builds on), and §5 turns each open one into a `blockedBy` link;
```

- [ ] **Step 2: Insert the link step in §5, before the plan comment**

In `## 5. Close the loop`, before the `gh issue comment` block, insert:

```markdown
Before moving the story to Ready, record its story-level prerequisites as `blockedBy` links:

- Link every story whose merged work the plan builds on, whatever its status:
  `gh issue edit <id> --add-blocked-by <blocker>`.
- Never link a parent (`Part of #X`), a shared-code release (`Shared code: …`), a module prerequisite, a closed story,
  or a story the plan only mentions (“blocks nothing”). An open blocker does not keep the story out of Ready.
- On a revised plan, the plan is authoritative: remove a link it no longer requires (`--remove-blocked-by`), and report
  every link added or removed.
```

- [ ] **Step 3: Add the Done-when criterion**

In `## Done when`, add after the plan-published bullet:

```markdown
- every open story-level prerequisite named by the plan has a `blockedBy` link, and no parent, shared-code or closed
  story is linked;
```

- [ ] **Step 4: Verify the file**

Run: `grep -c "add-blocked-by" .claude/agents/analyst.md`
Expected: `1` (the rule appears once, in §5).

Run: `grep -n "story-level prerequisite" .claude/agents/analyst.md`
Expected: three matches (§1 bullet, §5 rule, *Done when*).

- [ ] **Step 5: Commit**

```bash
git add .claude/agents/analyst.md
git commit -m "docs(agents): record story prerequisites as blockedBy links at groom" -m "Refs #300, #393"
```

---

### Task 2: The ship-sprint blocked report names the missing link

**Files:**
- Modify: `.claude/skills/ship-sprint/SKILL.md` (Reports table, `OUTCOME: blocked` row)
- Same inode: `.opencode/skills/ship-sprint/SKILL.md` (no separate edit)

**Interfaces:**
- Consumes: nothing.
- Produces: the stop message that Task 3 reads through.

- [ ] **Step 1: Replace the blocked row in the Reports table**

Replace:

```markdown
| `OUTCOME: blocked` | record the reason, stop the run |
```

with:

```markdown
| `OUTCOME: blocked` | record the reason, stop the run; when the reason is an unmerged story prerequisite, name the
  exact `gh issue edit <story> --add-blocked-by <blocker>` command and record it in the run file |
```

- [ ] **Step 2: Verify the rule appears once**

Run: `grep -c "add-blocked-by" .claude/skills/ship-sprint/SKILL.md`
Expected: `1`.

- [ ] **Step 3: Verify the hardlink survived the edit**

Run: `ls -li .claude/skills/ship-sprint/SKILL.md .opencode/skills/ship-sprint/SKILL.md`
Expected: the same inode number on both lines.

Run: `diff -q .claude/skills/ship-sprint/SKILL.md .opencode/skills/ship-sprint/SKILL.md`
Expected: no output.

If the inodes differ, run:
`ln -f .claude/skills/ship-sprint/SKILL.md .opencode/skills/ship-sprint/SKILL.md`
then re-run both checks.

- [ ] **Step 4: Read-through for ownership and duplication**

Read the modified row in context. Expected: the sprint skill only *reports* the command; it never adds the link, never
infers a dependency, and the queue rules in *Queue* are untouched.

- [ ] **Step 5: Commit**

```bash
git add .claude/skills/ship-sprint/SKILL.md
git commit -m "docs(skills): name the missing blockedBy command on a blocked sprint report" -m "Refs #300, #393"
```

---

### Task 3: Verify the rule against the real board

**Files:**
- None.

**Interfaces:**
- Consumes: Task 1's rule, Task 2's report wording.
- Produces: evidence for the design's Verification section.

- [ ] **Step 1: Check the existing links**

Run:

```bash
OWNER=$(gh repo view --json owner --jq .owner.login); REPO=$(gh repo view --json name --jq .name)
gh api graphql -f query='query($o:String!,$r:String!){repository(owner:$o,name:$r){
  a:issue(number:300){blockedBy(first:10){nodes{number state}}}
  b:issue(number:393){blockedBy(first:10){nodes{number state}}}}}' -f o="$OWNER" -f r="$REPO" \
  --jq '.data.repository | to_entries[] | "#\(.key) " + ([.value.blockedBy.nodes[] | "#\(.number)(\(.state))"] | join(","))'
```

Expected: `#a #392(OPEN)` and `#b #392(OPEN)`.

- [ ] **Step 2: Negative probes (read-through, not live grooming)**

Against the current Ready stories, confirm the rule links none of these:

- `#360`’s `Part of #351` → parent, not a blocker;
- `#377`’s “#360’s interceptor is not on `main` yet … blocks nothing” → no link;
- `#376` → `#297`/`#298`/`#299` are closed → no link;
- `#300` → `#299` closed → no link; `#392` open → link.

- [ ] **Step 3: Live probe (manual, after the change is in use)**

Re-groom a story whose plan names an open prerequisite and confirm the `blockedBy` link exists before its status
becomes Ready. If no candidate exists, record that the static checks above are the evidence and leave the live probe
for the next groom.

- [ ] **Step 4: Report**

Report the two commits, the inode check result, the graph check, and any probe left for later. Do not push.
