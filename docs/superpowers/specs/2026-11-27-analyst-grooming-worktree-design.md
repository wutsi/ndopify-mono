# Analyst grooming worktree — design

**Date:** 2026-11-27
**Status:** approved

## Intent

Grooming publishes a plan and, for a UI story, a prototype to GitHub's default branch. Today the analyst does that from
the repository's primary checkout: it checks the branch, refuses a dirty tree and fast-forwards a behind-only `main`. On
a working checkout — dirty with the user's WIP, or behind GitHub — every groom therefore stops and asks the user to
clean it first, and the publish step depends on the state of that checkout.

The analyst must instead always groom in a throwaway worktree cut from `origin/<repository.default_branch>`, and leave
the primary checkout exactly as it found it.

## Goals

- Every groom runs in `.worktrees/<id>-<topic>` on branch `groom/<id>-<topic>`, cut from
  `origin/<repository.default_branch>`.
- The primary checkout's branch, dirtiness and lag are never inspected, corrected or reported — so never asked about.
- The plan (and prototype) is still published by pushing the commit to the default branch.
- The worktree and branch are removed after a successful publish; a gate or a block keeps them for the resumed session.

## Non-goals

- Changing how the developer, reviewer or deployer use worktrees.
- Publishing plans through a pull request.
- Reusing the primary checkout for any grooming file.

## Approaches

1. **A git worktree under `.worktrees/`, cut from `origin/<default>`** — chosen. Cheap, isolated, shares the object
   store, and matches the `groom/<id>` worktrees already used in this repository.
2. **The native worktree tool** — rejected: it branches from the current `HEAD`, which is the stale or dirty primary
   checkout's commit, the very state being escaped.
3. **A fresh clone per groom** — rejected: strongest isolation, but copies the repository on every groom.
4. **A detached-HEAD temporary worktree** — rejected: no readable branch, and an interrupted groom is harder to resume
   and report.

## Design

### Start

Replace the analyst's *Start from a clean default branch* with:

> ## Start in a fresh grooming worktree
>
> Grooming never touches the repository's primary checkout: its branch, dirtiness and lag are the user's, and they are
> neither inspected nor corrected. Do all the work in a throwaway worktree cut from GitHub's default branch, and publish
> from it, so a stale or dirty checkout cannot contaminate the plan.
>
> ```bash
> git fetch origin
> git worktree add .worktrees/<id>-<topic> -b groom/<id>-<topic> origin/<repository.default_branch>
> ```
>
> `<topic>` is a short slug of the story title — lowercase, accents removed, non-alphanumeric runs collapsed to `-`, at
> most 40 characters. Run every command and edit every file inside that worktree; the primary checkout only hosts the
> directory, through `git worktree add` and the removal in §4. When the directory or branch already exists (an
> interrupted groom), reuse it only when `git -C <worktree> status --porcelain` prints nothing and
> `origin/<repository.default_branch>` is an ancestor of its `HEAD`; otherwise stop and report — never force-, reset or
> recreate it.

### Prototype

The designer's brief gains one bullet: the absolute path of the grooming worktree, where the designer creates and edits
the prototype — never in the primary checkout.

### Publish

The publish section works in the same worktree, with only the plan and, for `UI: yes`, the prototype staged, and pushes
the groom branch's `HEAD` to the default branch:

```bash
git -C <worktree> status --porcelain
git fetch origin
git -C <worktree> rebase origin/<repository.default_branch>
git -C <worktree> add <plans.dir>/<id>-<title>.md
git -C <worktree> add <prototype-paths>          # UI: yes only
git -C <worktree> diff --cached --name-only      # only the plan and the prototype files
git -C <worktree> commit -m "docs(<scope>): add the <topic> plan and prototype" -m "Refs #<id>"
git -C <worktree> push origin HEAD:<repository.default_branch>
```

The existing rules stay: no `git add -A`, no `--autostash`, a rejected push is rebased once and retried, a second
failure stops.

After a successful push, remove the worktree and its branch:

```bash
git worktree remove .worktrees/<id>-<topic>
git branch -D groom/<id>-<topic>
```

A worktree that will not remove cleanly is a stop-and-report — never `--force`. A gate or a block leaves the worktree
and branch in place, and the report names the path so the resumed session reuses it.

### Report, stop and done

- Report `WORKTREE: -` after the cleanup; on a gate or block, the worktree path.
- *Stop and ask*: "the default branch is dirty or diverged from GitHub" becomes "the grooming worktree cannot be
  created, or an existing one is dirty or not based on GitHub's default branch".
- *Done when* gains "the grooming worktree and its branch are removed" (a gate keeps them for the resume).

## Repository change

- `.gitignore` gains `.worktrees/`, so every clone leaves the directory out of `git status`.
- The spec and its plan are committed with the instruction change, then pushed directly to `main` as
  `chore(agents): …`, matching the file's history. No pull request, no deployment.

## Decisions

- Worktree location: `.worktrees/` inside the repository (user's choice over a sibling `-worktrees` directory).
- Cleanup after publish (user's choice over keeping the worktree for the developer).
- The location is derived from the repository, so no new project-profile value is introduced.

## What is left to the builder

The exact wording and placement of the edited sections in `.claude/agents/analyst.md`; nothing else is open.
