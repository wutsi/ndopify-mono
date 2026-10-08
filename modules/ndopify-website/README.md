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
.claude/
  commands/                    Project slash commands (below)
  skills/                      Vendored skills used by those commands (below)
```

## Running locally

No build step — serve the directory and open it in a browser:

```bash
cd src/main/html
python3 -m http.server 8080
# then open http://localhost:8080/index.html
```

