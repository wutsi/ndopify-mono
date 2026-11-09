# NPC agent profile page — prototype

## Intent

Co-brokerage comes later: an agent will see other agents' listings, and each listing will link to its
agent (`/agent?id=…`). This page is that target — another agent's profile, meant to build enough trust
to contact them and, eventually, co-broke. This iteration is the static prototype only.

## Goals

- A page at `agent.html?id=…` under `src/main/html/`, in Partner Central's existing page language.
- Shows one agent's public profile: identity, verification, coverage, biography, WhatsApp contact.
- Reached from another agent's listing once co-brokerage ships; the back link returns there (Espace agent
  as the no-JS fallback).

## Non-goals

- Co-brokerage itself, the agent's listings, or an agent directory / search.
- BFF controller, Thymeleaf template, tests, or server changes.
- Experience / tenure (explicitly excluded by the product owner).

## Content

- Back link.
- Hero: photo (or initials), name, "Vérifié" badge when the party's KYC is VERIFIED, agent type
  ("Indépendant" / "Rattaché à une agence immobilière") and city.
- No rating yet (and no "not rated" fallback).
- "Membre depuis <mois année>" from `joinedAt`. No experience level.
- Primary action "Contacter sur WhatsApp" (wa.me), only when a number exists. Never shows the e-mail.
- "Ses secteurs": city + up to three neighbourhoods, as chips; hidden when empty.
- "À propos": biography; hidden when empty.
- No listings section yet: co-brokerage will add it below the profile as the page's natural extension point.

## States (prototype fixtures)

- Default / `?id=…`: complete profile — photo, name, verified, independent, Douala, Bonapriso / Akwa /
  Bonanjo, member since, biography, WhatsApp.
- `?id=…` incomplete: initials instead of photo, no biography, sectors only.
- Unknown id: "Agent introuvable" panel with a link back to the Espace agent.
- Own profile is not a state: the link always points at another agent.

## Approaches considered

- **Carte de visite (chosen)** — one narrow column, trust first, contact action, room for listings later.
  Fits the profile / mandate pages and works on a phone.
- **Vitrine** — identity strip then the agent's listings immediately. Rejected: no agent-listings API, and
  the page is opened from a listing the agent is already looking at.
- **Two-column directory profile** — rejected: NPC has no such layout, and it adds CSS for no benefit.

## Prototype implementation

- Create `src/main/html/agent.html`, `assets/css/agent.css` and `assets/js/agent.js`.
- `agent.js` only switches fixtures for the `?id=` states; the default profile renders without JS. It also
  links the back action to the listing's URL when one is passed, otherwise to `index.html`.
- Reuse `partner-central.css` tokens and classes (`.page`, `.column`, `.page-intro`, `.hint`, `.btn`,
  `.status-tag`); page-specific classes live in `agent.css`.
- `data-page="agent"`, header and account menu as on the other signed-in pages.
- No server, controller or Thymeleaf work.

## Open point

- The agency's name is not in the DTO, so the type label is all the page can show.

## What's left to the builder

- Exact responsive spacing and the final visual pass on the prototype.
- Later work: the real `/agent` controller/template and the listing → agent link.
