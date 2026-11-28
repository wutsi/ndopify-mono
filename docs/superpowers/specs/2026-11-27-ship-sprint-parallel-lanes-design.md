# Ship sprint in two lanes — design

Short design (2026-11-27). Scope: the `ship-sprint` skill, plus a `--to-review` mode on the `story` skill.

## Intent

Ship a sprint faster than one story at a time, without the failures that made the old skill say "never two in flight".

## Goals

- Two stories progress at once, from `ready` to an approved pull request.
- Merges stay strictly serial, so deployment order and Heroku targets are never raced.
- A story that cannot safely overlap with another waits; the run fails safe, never fast.

## Non-goals

- More than two lanes.
- Parallel `mvn verify` in `ndopify-server` (see the enabler).
- Changing what the developer, reviewer or deployer agents do.

## Constraints found in this repository

1. `ndopify-server` integration tests share one MySQL schema (`ndopify`, Flyway `clean-disabled: false`) and one
   GreenMail port (3025, `ServerSetupTest.SMTP`): two suites at once corrupt each other.
2. A shared-module release and its `fix dependencies` commit both edit the root `pom.xml`
   (`ndopify-dto.version`, `ndopify-web-core.version`): two at once collide on the version and the tag.
3. One Heroku app per module: two concurrent `deploy-test` runs to the same app can leave older code running while
   both report green.
4. The harness reports a child only when it ends, so a lane must end at a phase boundary the orchestrator can act on.
   That is why the lane stops at *approved PR* rather than pausing before its deployer.

## Approaches

- **A — two lanes, one merge slot** (chosen): lanes run developer → reviewer; the orchestrator dispatches each
  `merge <pr>` itself, one at a time.
- **B — fully parallel, merges included**: fastest, but races 1-3 are live and a failure reads as the story's fault.
- **C — pairing rule on top of A** (chosen): unit tests (2-4) need no workaround, so A without C would corrupt the
  `ndopify-server` suite today.
- Considered: the sprint driving `implement` → `review` → `merge` explicitly. Same throughput, but it duplicates
  `ship`'s resume routing (rounds, handoffs, Final cap) in the sprint skill. Rejected.

## Decided

- `ship <id> --to-review`: developer → reviewer, then `OUTCOME: staged` with `PR` and `STATUS: review`. One new
  outcome, no new agent.
- The sprint merges with `merge <pr>` in approval order; the merge slot never overlaps a lane gate.
- Pairing inputs: the plan's module list (*Global Constraints*, *File Structure*). Missing plan = stop and ask.
- A staged or finished story frees its lane; a merge child does not occupy a lane.
- Run file gains a lane column, a *Merge queue* table and a two-lane `Resume:` line.
- The harness must run a background subagent; when it cannot, the run is single-lane and says so.

## Left to the builder

- The enabler that later relaxes rule 3: `spring.mail.port=0` (or a per-JVM GreenMail port) and a schema per worktree,
  so two `ndopify-server` suites can run at once. Separate story.
- Whether three lanes are worth it once that lands.
