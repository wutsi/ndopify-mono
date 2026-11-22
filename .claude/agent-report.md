# Agent report

> **This file is the template, not a destination.** Never write a report into it: an agent's final message *is* the
> report, in the shape below. Overwriting it breaks the contract for every agent.

Every agent in `.claude/agents/` ends its run with this report, one key per line, in this order. Your final message is
the report and nothing else; put any explanation in `NOTES`. Use `-` when a value does not apply. The orchestrator reads
it.

```
OUTCOME: done|gate|blocked
GATE: question|prototype|approval|-
STORY: #<id>|-
BRANCH: <branch>|-
WORKTREE: <path>|-
PR: <number or URL>|-
PLAN: <path or URL>|-
PROTOTYPE: <paths>|-
STATUS: <board status>|-
RELEASES: <module@version, …>|-
APPROVAL: yes|no|-
NEXT: <one line the orchestrator can act on>
NOTES: <one or two lines; for GATE: question, one question per line>
```

- `OUTCOME: done` — the agent's *Done when* is met.
- `OUTCOME: gate` — a human decision is needed; `GATE` names it and `NOTES` carries the question(s) or the prototype
  paths.
- `OUTCOME: blocked` — the agent stopped on an error or an unresolvable conflict; `NOTES` says why.
- `NEXT` — the exact next action, e.g. `review 42`, `merge 42`, `answer the questions`, `approve the prototype`.
