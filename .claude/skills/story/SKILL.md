---
name: story
description: Use to take a story through its lifecycle by orchestrating the analyst, developer, reviewer and deployer agents - `groom <story-id>`, `implement <story-id>` (`push` is an alias), `review <pr-number>`, `merge <pr-number>`, `ship <story-id>`. Triggers on "groom story", "implement story", "push story", "review story PR", "merge story PR", "ship story" - not for a generic PR review.
---

# Story workflow

You orchestrate one story through its lifecycle. The agents own the work; you validate the argument, dispatch the
right agent, relay its gates and follow its report. You never implement, review, merge, push, edit the board or touch
git yourself.

**Arguments:** `$ARGUMENTS` is `<mode> <id>`. Modes: `groom <story-id>`, `implement <story-id>`, `push <story-id>`
(an alias of `implement`), `review <pr-number>`, `merge <pr-number>`, `ship <story-id>`. If the mode or the id is
missing, ask for it — never guess the story or the pull request.

**Read first:** the repository's agent instructions (`AGENTS.md`, or `CLAUDE.md` when there is no `AGENTS.md`),
`MEMORY.md` when it exists, and the project profile (`.claude/project-profile.md`). Their rules win. The profile
supplies `tracker.statuses.*`, `repository.story_ref` and `repository.default_branch`. If the profile is missing or its
`platform` is not `github`, stop and ask. Never hardcode a board, a field id, a status label or a branch.

## Agents

| Mode | Agent | The agent's done |
|---|---|---|
| `groom <story-id>` | `analyst` | `STATUS: <tracker.statuses.ready>`, `NEXT: implement <id>` |
| `implement <story-id>`, `push <story-id>` | `developer` | `NEXT: review <pr>`, or `STATUS: <tracker.statuses.done>` for a library-only story |
| `review <pr-number>` | `reviewer` | `APPROVAL: yes`, `NEXT: merge <pr>` |
| `merge <pr-number>` | `deployer` | `STATUS: <tracker.statuses.done>`, `NEXT: none` |
| `ship <story-id>` | `developer` → `reviewer` → `deployer` | the chain in *Ship* |

`designer` is the analyst's child: the analyst dispatches it for a UI story. Never dispatch it from this skill.

## Dispatch

- Use the harness's subagent tool with the agent ID above (`analyst`, `developer`, `reviewer`, `deployer`), passing the
  mode's id and the repository path. Never a generic agent, never `fork`. A prompt is enough:

  ```text
  Run the <agent> agent for <mode> <id> in <repository path>. Follow your instructions to their "Done when".
  ```

- Keep the child session id. The agent's final message is its report, in the format of `.claude/agent-report.md` —
  `OUTCOME`, `GATE`, `STORY`, `BRANCH`, `WORKTREE`, `PR`, `PLAN`, `PROTOTYPE`, `STATUS`, `RELEASES`, `APPROVAL`,
  `NEXT`, `NOTES`. Read that, never its tool transcript.
- `OUTCOME: done` — in `ship`, follow `NEXT`; in an explicit mode, report the result and stop.
- `OUTCOME: gate` — show the user the questions in `NOTES`, or the prototype paths (`GATE: prototype`), then send their
  answer to the same child session and wait. If the harness cannot resume a child, re-dispatch the same agent for the
  same phase with the questions already asked and the answers so far: the later phases rebuild their state from git,
  the pull request and the story, but the analyst's answers exist only in that session.
- `OUTCOME: blocked` — report `NOTES` and stop.
- Unknown agent — report the block (`<agent>` is not registered) and tell the user to register or reload the agents.
  Never fall back to a generic agent or do the phase yourself.

## Modes

### `groom <story-id>`

Dispatch `analyst <story-id>`. Relay its question and prototype gates. On done, report `PLAN`, `PROTOTYPE` and
`STATUS`; the next action is `implement <id>`.

### `implement <story-id>` and `push <story-id>`

Dispatch `developer <story-id>`. `push` is the same mode, kept as an alias. On done, report `PR` and `STATUS`; next is
`review <pr>`. A library-only story reports `STATUS: <tracker.statuses.done>` and `NEXT: none`: report it and stop.

### `review <pr-number>`

Dispatch `reviewer <pr-number>`. Relay its question gates. On `APPROVAL: yes`, report it; next is `merge <pr>`. A
`Final` review with findings stops: report the findings in `NOTES`, the pull request link and the reviewer's `NEXT`.

### `merge <pr-number>`

Dispatch `deployer <pr-number>`. Report the deployment outcome and `STATUS`. A red deployment leaves the story in
`tracker.statuses.review` and the issue open; report the failing run.

## Ship

`ship <story-id>` runs the phases in sequence, each in a fresh agent session. Resolve the start point first.

Read the story's status and its pull request:

```bash
gh issue view <id> --json projectItems --jq '.projectItems[].status.name'
gh pr list --state all --search "<repository.story_ref with the id> in:body" \
  --json number,state,mergedAt
```

Replace the id in `repository.story_ref`. If the issue has no project item or more than one, or the search returns
several pull requests, stop and ask.

Resolve the start point in this order; the first row that matches wins:

| State | Start with |
|---|---|
| `tracker.statuses.done` | report done and stop |
| a merged pull request, with the story in `tracker.statuses.review` | `deployer` |
| a merged pull request in any other status | stop; the status and the merge disagree |
| `tracker.statuses.backlog` | stop; ask the user to run `groom <id>` |
| a closed pull request that is not merged | stop and ask |
| an open pull request, with the story in `tracker.statuses.review` | `reviewer` |
| an open pull request in any other status | stop; the status and the pull request disagree |
| no pull request, with the story in `tracker.statuses.ready` or `tracker.statuses.in_progress` | `developer` |
| no pull request, with the story in `tracker.statuses.review` | `developer` (resume the push) |
| anything else | stop and ask |

Then follow each report's `NEXT`: developer `NEXT: review <pr>` → reviewer; reviewer `NEXT: merge <pr>` → deployer;
deployer `NEXT: none` → done. A developer `STATUS: <tracker.statuses.done>` with `NEXT: none` is a library-only story:
the chain ends there. A gate is relayed and the same session resumed; `blocked` stops the chain.

The main thread only orchestrates: no code, commit, push, review, merge or board edit.

## Report

Report the mode, the phases run, the outcome, the pull request and plan links, the story status, and what is left for
the user. Do not repeat the agents' tool output.

## Stop and ask

Stop instead of guessing when: the mode or id is missing; the profile is missing or its platform is not GitHub; an
agent is not registered; an agent is blocked; `ship` cannot resolve the status or finds several pull requests; a gate
needs the user's answer. Never guess a story or a pull request.

## Done when

The mode's agent reported `done` and the report names the next action — or the chain stopped on `blocked`, on a `Final`
review, or on a gate, and the user knows why.
