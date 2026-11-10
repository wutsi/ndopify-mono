# NPC agent profile page — prototype

## Intent

Co-brokerage comes later: an agent will see other agents' listings, and each listing will link to its
agent (`/agent?id=…`). This page is that target — another agent's profile, meant to build enough trust
to contact them and, eventually, co-broke. It also shows the agent's current published listings, so a
colleague can see what they have on offer. This iteration is the static prototype only.

## Goals

- A page at `agent.html?id=…` under `src/main/html/`, in Partner Central's existing page language.
- Shows one agent's public profile: identity, verification, coverage, biography, WhatsApp contact.
- Shows that agent's current PUBLISHED listings.
- Reached from another agent's listing once co-brokerage ships; the back link returns there (Espace agent
  as the no-JS fallback).

## Non-goals

- Co-brokerage mechanics (proposing or accepting a listing, commissions), or an agent directory / search.
- BFF controller, Thymeleaf template, tests, or server changes.
- Experience / tenure (explicitly excluded by the product owner).

## Content

- Back link.
- Compact hero: photo (or initials) on the left; the name, "Vérifié" badge when the party's KYC is
  VERIFIED, agent type ("Indépendant" / "Rattaché à une agence immobilière"), city and member-since on
  the right; then the sectors as an inline chip line; then the WhatsApp action.
- No rating yet (and no "not rated" fallback).
- "Membre depuis <mois année>" from `joinedAt`. No experience level.
- Primary action "Contacter sur WhatsApp" (wa.me), only when a number exists. Never shows the e-mail.
- "Ses secteurs": city + up to three neighbourhoods, as chips inline under the identity; hidden when
  empty.
- "Ses annonces": the agent's current PUBLISHED listings, newest first, one compact row card each — a
  large thumbnail (or placeholder), a concise title (property type + rooms), a place line (neighbourhood,
  city), a facts line (area · furniture) and the rent — no repeated place or room count — linking to
  NPC's listing page (`/listings/{id}`). Hidden when the agent has none.
- "À propos": biography; hidden when empty.

## States (prototype fixtures)

- Default / `?id=…`: complete profile — photo, name, verified, independent, Douala, Bonapriso / Akwa /
  Bonanjo inline sectors, member since, biography, WhatsApp, three published listings.
- `?id=…` incomplete: initials instead of photo, no biography, inline sectors only, no listings (the
  section is hidden).
- Unknown id: "Agent introuvable" panel with a link back to the Espace agent.
- Own profile is not a state: the link always points at another agent.

## Approaches considered

- **Carte de visite (chosen)** — one narrow column, a compact horizontal identity card (avatar left,
  name/meta right, sectors inline), trust first, contact action, listings below. Fits the profile /
  mandate pages and works on a phone.
- **Vitrine** — identity strip then the agent's listings immediately. Rejected: it leads with inventory
  before trust, and the page is opened from a listing the agent is already looking at; the listings sit
  below the identity instead.
- **Two-column directory profile** — rejected: NPC has no such layout, and it adds CSS for no benefit.

## Prototype implementation

- Create `src/main/html/agent.html`, `assets/css/agent.css` and `assets/js/agent.js`.
- `agent.js` only switches fixtures for the `?id=` states; the default profile and its listings render
  without JS. It also links the back action to the listing's URL when one is passed, otherwise to
  `index.html`.
- The listing cards are fixtures: no photos, so each shows a placeholder; each card links to
  `listing.html?id={id}`, NPC's listing page.
- Reuse `partner-central.css` tokens and classes (`.page`, `.column`, `.page-intro`, `.hint`, `.btn`,
  `.status-tag`); page-specific classes live in `agent.css`.
- `data-page="agent"`, header and account menu as on the other signed-in pages.
- No server, controller or Thymeleaf work.

## Open points

- The agency's name is not in the DTO, so the type label is all the page can show.
- Listing photos: `ListingSummary` carries `heroImageId`, not a URL. The BFF must resolve the hero image
  (`GET /v1/listings/{id}/images`, or a batch).
- Listing access: NPC's `/listings/{id}` is the listing agent's checklist page and locks any other agent.
  A co-brokerage read-only view (or access for PUBLISHED listings) is needed before these cards open a
  colleague's listing; the prototype links to `listing.html?id={id}`.
- Listing labels: the card uses a concise title (property type + rooms), a place line (neighbourhood,
  city) and a facts line (area · furniture) built from the summary. `ListingSummary.title` is free text
  and can repeat those, so the BFF should use the generated labels for these cards.

## What's left to the builder

- Exact responsive spacing and the final visual pass on the prototype.
- Later work: the real `/agent` controller/template, the listing → agent link, the agent-listings search
  (`GET /v1/listings?agent-id={id}`; the server restricts another agent to PUBLISHED), the image URLs and
  the read-only listing access for co-brokerage.
