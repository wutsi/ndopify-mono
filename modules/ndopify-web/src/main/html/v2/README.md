# ndopify-web — v2 prototype: property sales

The public site's prototype with property sales next to rentals. Built from `../mvp/` (copied, then changed); the
MVP prototype stays as is. Never serve this folder, and never delete or "clean up" anything in it.

The site's job is unchanged: **match a customer with a verified agent through a mandate**. Sales add two customers,
not a catalogue:

| Goal | Page | Mandate | Call to action (Partner Central) |
|---|---|---|---|
| Louer | `louer.html` | SEARCH · RENTAL | `/find-property` |
| Acheter | `acheter.html` | SEARCH · SALE | `/find-property?type=SALE` |
| Mettre en location | `bailleur.html` | LISTING · RENTAL | `/offer-property` |
| Vendre | `vendre.html` | LISTING · SALE | `/offer-property?type=SALE` |
| (agent) | `agent.html` | — | `/join` |

The DTOs already carry `listingType` (`SALE` / `RENTAL`) on `Mandate` and `Listing`, and `ListingStatus.SOLD`.

## Changes from mvp

| Page | Change |
|---|---|
| `index.html` | New home (`/`): *Quel est votre projet immobilier ?* — four cards in two groups (*Je cherche un bien*: Louer, Acheter; *J'ai un bien*: Mettre en location, Vendre), each a plain link to its landing page. Then the shared promise (verified agent, agent of your area who handles that kind of project, signed mandate, nothing to pay up front), the 4 steps, and a band for agents. |
| `louer.html` | The MVP's `index.html` (tenant page) moved to `/louer`, with a *Louer / Acheter* switch in the hero. |
| `acheter.html` | New buyer page: what the agent does for a buyer (searches, incl. off-market; proposes; visits, incl. WhatsApp video; puts them in touch with the seller), how the agent is chosen (verified, handles sales and that property type, area, ratings), a callout for buyers abroad, FAQ (land, cost, video visits, title deed, mandate). |
| `bailleur.html` | A *Mettre en location / Vendre* switch in the hero; otherwise unchanged. |
| `vendre.html` | New seller page (`/owner/sell`): what the agent does for a seller (audit, presented to matching buyers, visits, presents interested buyers), how the agent is chosen, a callout on buyers' searches already on Ndopify, FAQ (cost, agent choice, who sets the price, land, deed of sale, letting instead). |
| `agent.html` | Sale mandates next to rental ones; new *Les mandats que vous traitez* card and FAQ *Puis-je recevoir des mandats de vente ?*: the agent declares rental / sale / both and property types; sale mandates go only to agents who handle sales. |
| all pages | Nav *Louer · Acheter · Propriétaires · Agents* (the logo goes to the goal picker). |
| `annonce.html`, `proposition.html`, `visite.html` | Nav only. A sale listing / match uses the same pages (badge *À vendre*, no `/ mois`); their sale wording isn't prototyped yet (see below). |
| `assets/css/styles.css` | `.goal-switch` (segmented pill of two links), `.goal-groups` / `.goal-card` (home picker), `.callout`. No new JS: every page works without it. |

## Out of scope, never promised here

As in the MVP, plus, for sales:

- Title-deed checks (the buyer FAQ says Ndopify verifies the agent, not the property's papers), buyer vetting or
  solvency, valuation.
- The deed of sale, the notary, and any payment through the platform (the price is paid to the seller directly).
- A catalogue of properties for sale to browse: listing pages remain an entry point to a SEARCH mandate only.

## Open questions

1. **Commission for sales.** The pages say "la commission écrite dans votre mandat", with no figure. Who pays (seller,
   buyer, both) and how much (a % of the price?) is to decide, with a sale clause in the mandate contracts.
2. **Agent specialisation.** New: the agent's rental / sale / both and property types, set at `/join` and in the
   profile, and dispatch filtering on it. Should a sale mandate fall back to any agent of the area when no specialist
   accepts it?
3. **Wizards.** `?type=SALE` is a proposal: Partner Central's `/find-property` and `/offer-property` need a sale branch
   (asking price / budget, buying horizon instead of the moving period; no deposit, advance, lease term or furniture).
   Only what dispatch needs: the rest is for the agent.
4. **Timing.** A sale takes months: match expiry (`ndopify.match.expire.days`), mandate duration and the visit fee
   may need their own values for `SALE`.
5. **Order.** Ship sellers or buyers first? Sellers bring the stock that buyers' mandates are matched against.

## Not built yet

- `/`, `/louer`, `/acheter`, `/owner/sell`: only `/`, `/owner` and `/agent` exist in `templates/` (and
  `SecurityConfiguration`); `/` is still the tenant page.
- The sale branches of Partner Central's wizards, and the agent's specialisation (DTO, server, `/join`, dispatch).
- Sale wording on `annonce.html` / `proposition.html` (*Conditions de vente*, *Pourquoi ce bien ?*).
