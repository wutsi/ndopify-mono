# Ndopify Website

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-website-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-website-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-website-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-website-pr.yml)

Static, self-contained marketing + lead-capture website for Ndopify (Cameroon PropTech, Mobile-Money-based rental
platform) — three audiences (locataires, bailleurs, agents), each with a marketing page and a multi-step wizard
form. No build step, no framework: plain HTML/CSS/JS, content in French.

## Directory structure

```
DESIGN.md                      Design-token source of truth (colors, typography, spacing, components)
src/main/html/
  index.html                   Marketing page — locataires (tenants), CTA → rechercher.html
  bailleur.html                Marketing page — bailleurs (landlords), CTA → louer.html
  agent.html                   Marketing page — agents, CTA → joindre.html
  rechercher.html             Wizard — locataire: search criteria → agent matching
  louer.html                  Wizard — bailleur: list a property
  joindre.html                Wizard — agent: join the network
  assets/css/styles.css        Shared stylesheet (tokens from DESIGN.md)
  assets/js/main.js            Shared behavior: wizard navigation/validation, FAQ accordion, nav, field masking
  assets/images/               Logo/icon assets
```

The pages are generated and refreshed by the `init-website-design` and `init-website` Claude Code skills, which live
at the repo root in [`.claude/skills`](../../.claude/skills) — see the
[main README](../../README.md#ai-commands--skills).

## Current behavior

The wizard forms (`rechercher`, `louer`, `joindre`) are front-end only for now: they validate each step and show the
confirmation screen, but do not send data to `ndopify-server`.

## Deployment

On push to `main`, the `ndopify-website-main` workflow syncs `src/main/html/` to the test S3 bucket. There is no
pull-request workflow yet, so the `pull_request` badge above points to a workflow that doesn't exist.

## Running locally

No build step — serve the directory and open it in a browser:

```bash
cd src/main/html
python3 -m http.server 8080
# then open http://localhost:8080/index.html
```

