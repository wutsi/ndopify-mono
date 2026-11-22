# Story skill: orchestrate the agents instead of re-implementing them

## Intent

Turn `.claude/skills/story/SKILL.md` into a thin router over the five agents: `analyst`, `developer`, `reviewer`,
`deployer`, and — through the analyst — `designer`. Every mode dispatches one agent, relays its gate, and follows its
`NEXT`; the phase procedures live only in `.claude/agents/`.

## Why

The skill is a second implementation of the agents: ~300 lines duplicating the analyst's groom, the developer's
implement/push, the reviewer's loop and the deployer's merge. `ship` even dispatches a general-purpose subagent that
re-invokes the same skill. The copies have already drifted (the skill still hardcodes `wutsi` project #6, option ids
and `main`, while the agents read `.claude/project-profile.md`). One implementation removes the drift and makes the
agents reusable on their own.

## Goals

- Every mode body becomes: argument check → dispatch one agent → relay the report → next.
- `groom` → analyst, `implement` → developer, `review <pr>` → reviewer, `merge <pr>` → deployer,
  `ship` → developer → reviewer → deployer.
- Gates (`OUTCOME: gate`, `GATE: question|prototype`) are relayed to the user and answered into the same agent session.
- `OUTCOME: blocked` stops the chain with the agent's reason.
- `ship` starts from the story's actual state and stops when a phase blocks or a library-only story ends `Done`.
- No repository value in the skill: statuses, story ref, default branch and PR search come from the profile and `gh`.
- A mode never does phase work itself: no code, commit, review, merge, board edit or push.

## Non-goals

- Changing any agent's behaviour, permissions or report format.
- Registering agents in a harness (see *Harness contract*; that is configuration).
- Changing the lifecycle or the GitHub Project itself.

## Anti-goals

- Keeping the inline procedures "as a fallback" and dispatching agents only under `ship`.
- A silent fallback to `general` when a named agent is unknown — that loses the agent's model and permissions.
- The skill moving statuses or closing issues; the agents own the board.
- Re-dispatching a new agent session after a gate: the answer goes to the session that asked.

## Constraints

- Agent protocol: `OUTCOME: done|gate|blocked`, `GATE: question|prototype`, `STATUS`, `APPROVAL`, `NEXT`.
- `designer` is `mode: subagent` and belongs to the analyst; the skill never dispatches it.
- The developer now owns push and PR, so the old `push` mode has no work left.
- OpenCode registration is currently incomplete in this service: only `analyst` and `designer` resolve; `developer`,
  `reviewer` and `deployer` need a service restart before the refactor can be exercised. Claude Code reads
  `.claude/agents/<name>.md` directly.
- The skill is Markdown only; verification is read-through, `grep` and dispatch probes.

## Approaches

- **A — thin router, unconditional delegation (chosen).** The skill keeps its mode names and start arguments, deletes
  every phase procedure, and dispatches the matching agent. Also replaces the hardcoded board/branch values with the
  profile.
- **B — agents only under `ship`.** Keeps the inline modes as a fallback. Rejected: two sources of truth, exactly the
  drift this refactor removes.
- **C — one autonomous `story <id>` pipeline** from Backlog to Done. Rejected: loses per-phase control and gates.

## Decisions

### Mode → agent

| Mode | Agent | Notes |
|---|---|---|
| `groom <story-id>` | `analyst` | gates for questions and the prototype |
| `implement <story-id>` | `developer` | Ready/In progress/In review resume; owns release, push, PR |
| `push <story-id>` | `developer` | deprecated alias of `implement`; kept so old muscle memory works |
| `review <pr-number>` | `reviewer` | approval loop |
| `merge <pr-number>` | `deployer` | merge, deployment wait, close |
| `ship <story-id>` | `developer` → `reviewer` → `deployer` | start point resolved first |
| — | `designer` | only the analyst dispatches it |

### Router contract

- **Argument.** A mode without its id/PR stops and asks; never guess.
- **Dispatch.** Use the harness's subagent tool with the agent ID and the mode's argument, plus one line of context
  (mode, repository). Keep the child session id.
- **Report relay.** Read only the agent's report (`OUTCOME`, `GATE`, `STATUS`, `APPROVAL`, `NEXT`, `NOTES`), not its
  tool transcript. `done` → next; `gate` → show the question or prototype paths, then resume the same session with the
  answer and wait again; `blocked` → report and stop.
- **Resume without a session.** If the harness cannot resume a child, re-dispatch the same agent for the same phase:
  every agent rebuilds its state from git, the PR and the story.
- **No phase work.** The router may read `gh issue view`, `gh pr view/list` and the profile to pick a start point; it
  never edits, commits, reviews, merges, pushes or changes a board status.

### Per-mode shape

- **groom** — dispatch `analyst <story-id>`. Relay its question/prototype gates. Done when the analyst reports
  `STATUS: <tracker.statuses.ready>` and `NEXT: implement <id>`.
- **implement / push** — dispatch `developer <story-id>`. Done when it reports `NEXT: review <pr>` (or `STATUS:
  <tracker.statuses.done>` for a library-only story).
- **review** — dispatch `reviewer <pr-number>`. Done when `APPROVAL: yes` and `NEXT: merge <pr>`; a `Final` review with
  findings stops the chain.
- **merge** — dispatch `deployer <pr-number>`. Done when `STATUS: <tracker.statuses.done>` and `NEXT: none`.
- **ship** — start point from the story's status and its PR (`gh issue view <id> --json projectItems`, `gh pr list
  --state all --search "Refs #<id> in:body" --json number,state`):
  - Backlog → stop and ask for `groom`; Done → report done.
  - Ready, In progress, or In review without a PR → `developer`.
  - In review with an open PR → `reviewer`.
  - In review or Done with a merged PR → `deployer`.
  Then follow each report's `NEXT`; a library-only `developer` (`Done`, no PR) ends the chain; a gate is relayed and
  the same session resumed; `blocked` stops.

### Profile and repository values

The skill reads `.claude/project-profile.md` before acting and stops if it is missing or `platform` is not `github`.
It uses `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch`; it no longer contains
`wutsi`, `ndopify-mono`, project #6, option ids or `main`. The board writes stay in the agents.

### Harness contract

- Claude Code: `.claude/agents/<name>.md`; dispatch by filename.
- OpenCode V2: `.opencode/agents/<name>.md` (the repo's `.opencode` symlink exposes `.claude/agents`); a running
  service must restart to pick up a new or changed agent file.
- Unknown agent → `OUTCOME: blocked` with "agent <name> is not registered; restart/reload the harness" — never fall
  back to `general` or to an inline procedure.

### Docs

- Update the skill's frontmatter description and intro to say it orchestrates the agents.
- Update the `story` row in `README.md`.
- The agents' files, the profile and `agent-report.md` stay unchanged.

## Out of scope

- Fixing the OpenCode service discovery (a restart, done outside the skill).
- Agent behaviour, permissions, models, step caps.
- The review loop, deployment detection, board schema and CI.

## Verification

No automated test covers skill prose. Read-through plus:
- `grep` the skill for phase work that must be gone: `gh project item-edit`, `gh pr merge`, `gh pr create`, `git push`,
  `mvn`, `feat/`, `wutsi`, `ndopify-mono`, `In review`, `Done`.
- Every mode section names exactly one agent (`ship` names three) and has no numbered procedure.
- Dispatch probe: launch each agent with a no-op prompt; `deployer`, `developer` and `reviewer` must resolve after the
  harness reload (today they return Unknown agent).
- Read-through of the three gate paths (question, prototype, blocked) and the five `ship` start points.
