# Project profile

The agent instructions in `.claude/agents/` are project-agnostic. This file supplies the repository values they read
before acting. Keep project values here, not in the agent instructions.

**Precedence:** the repository's agent instructions (`AGENTS.md`, or `CLAUDE.md` when there is no `AGENTS.md`) and the
touched module's instructions win over this profile. The profile wins over the agents' generic defaults. An agent stops
and asks when a value it needs is missing. Only `platform: github` (with `tracker.provider: github-projects`) is
supported.

```yaml
platform: github

repository:
  default_branch: main
  story_ref: "Refs #<id>"

tracker:
  provider: github-projects
  statuses:
    backlog: Backlog
    ready: Ready
    in_progress: In progress
    review: In review
    done: Done

plans:
  dir: docs/superpowers/plans
  naming:
    pattern: "<id>-<title>.md"
    title: "lowercase; accents removed; non-alphanumeric runs collapsed to '-'"
    max_title_length: 60
  publish: push

prototype:
  dir: "<module>/src/main/html"

branch:
  pattern: "<type>/<story-id>-<scope>-<topic>"
  example: "feat/123-add-csv-export"

verification:
  command: "mvn verify"
  notes: "ktlint + tests + coverage gate; ndopify-server needs local MySQL"

version_file: pom.xml

shared_modules:
  # Release order. A later module may depend on an earlier one.
  - name: ndopify-dto
    path: modules/ndopify-dto
    release_workflow: ndopify-dto-main.yml
    release_tag: "ndopify-dto-<version>"
    version_property: ndopify-dto.version
  - name: ndopify-web-core
    path: modules/ndopify-web-core
    release_workflow: ndopify-web-core-main.yml
    release_tag: "ndopify-web-core-<version>"
    version_property: ndopify-web-core.version
```

**Static permissions:** the analyst's `edit` permission is bound to `plans.dir`, and the designer's is bound to
`prototype.dir`; the analyst's `git push` allow is `origin *`. Update the agent file if a repository moves either
directory.
