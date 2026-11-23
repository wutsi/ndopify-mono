# ndopify-web — v2 prototype: property sales

The public site's prototype with property sales next to rentals. Built from `../mvp/` (copied, then changed); the
MVP prototype stays as is. Never serve this folder, and never delete or "clean up" anything in it.

The site's job is unchanged: **match a customer with a verified agent through a mandate**. Sales add two customers,
not a catalogue. Every verified agent handles rentals and sales (since 2026-11-22): no page asks for, or promises,
an agent specialisation.

| Goal | Page (route) | Mandate | Call to action (Partner Central) |
|---|---|---|---|
| Louer | `louer.html` (`/louer`) | SEARCH · RENTAL | `/find-property` |
| Acheter | `acheter.html` (`/acheter`) | SEARCH · SALE | `/find-property?type=SALE` |
| Mettre en location | `bailleur.html` (`/owner`) | LISTING · RENTAL | `/offer-property` |
| Vendre | `vendre.html` (`/owner/sell`) | LISTING · SALE | `/offer-property?type=SALE` |
| (agent) | `agent.html` (`/agent`) | — | `/join` |

The DTOs already carry `listingType` (`SALE` / `RENTAL`) on `Mandate` and `Listing`, and `ListingStatus.SOLD`.

## Changes from mvp

| Page | Change |
|---|---|
| `index.html` (`/`) | New home: *Quel est votre projet immobilier ?* — four cards in two groups (*Je cherche un bien*: Louer, Acheter; *J'ai un bien*: Mettre en location, Vendre), each a plain link to its landing page. Then the shared promise (verified agent, agent of your area, signed mandate, nothing to pay up front), the 4 steps, and a band for agents. Works without JavaScript. |
| `louer.html` (`/louer`) | The MVP's `index.html` (tenant page) moved to `/louer`, with a *Louer / Acheter* switch in the hero. Rental wording unchanged (1 mois de loyer de commission, à la signature du bail). |
| `acheter.html` (`/acheter`) | New buyer page: what the agent does for a buyer (searches and proposes, visits on site or by WhatsApp video, puts them in touch with the seller), how the agent is chosen (verified, of the area, handles rentals and sales, ratings), a callout for buyers abroad, FAQ (land, cost, video visits, title deed, mandate). The commission is **a percentage of the sale price, written in the mandate**, with no figure and no promise about who pays it. |
| `bailleur.html` (`/owner`) | A *Mettre en location / Vendre* switch in the hero; otherwise unchanged (rental owners' page). |
| `vendre.html` (`/owner/sell`) | New seller page: what the agent does for a seller (audit, photos, publication, presented to matching buyers, visits on site or by video, seller sets the price and chooses the buyer), how the agent is chosen, a callout on the buyers' searches already on Ndopify, FAQ (cost, agent choice, who sets the price, land, deed at the notary, letting instead). The sale price is paid to the seller directly; the commission is **a percentage of the sale price, written in the mandate**. |
| `agent.html` (`/agent`) | Sale mandates next to rental ones. No specialisation: every verified agent receives the rental and sale mandates of its neighbourhoods. |
| `annonce.html` | A listing's public page. Its SALE variant is opened with `?sale=1` (a villa in Bonapriso, 250M FCFA): badge *À vendre*, price without *« / mois »*, conditions block *Prix et conditions* with the price and the availability only (never caution, advance, lease term, notice or furniture), the interest form asking *Quand souhaitez-vous acheter ?* (three horizons), and the sale photos. `?sale=1&state=sold` is the SOLD state: badge *Vendu*, the price card keeps the last published price with its notice, and there is no conditions block, no call to action and no interest form (the popup is taken out of the page). The rental page is the default, unchanged. |
| `proposition.html` | The proposition page of a search (the link e-mailed by the agent). Its SALE variant is opened with `?sale=1` (a villa of 4 bedrooms, 240M FCFA, for a buyer searching up to 250 000 000 FCFA): *Serge vous propose ce bien*, *Pourquoi ce bien ?*, the search summary without *« / mois »*, the availability reason *Disponible avant votre achat*, and the answers *Ce bien vous intéresse* / *Pourquoi pas ce bien ?*. Every state is kept: PENDING, ACCEPTED (and ACCEPTED_NO_VISIT), VISIT_REQUESTED, REJECTED, EXPIRED, UNAVAILABLE, with `&state=` (`?sale=1&state=unavailable`, `?sale=1&state=rejected`…). The rental page is the default, unchanged. |
| `visite.html` | The visit page (the link of the visit e-mails). Its SALE variant is opened with `?sale=1` (add `&state=` and `&type=video` as for a rental): the listing block is *Le bien*, never *logement* / *dossier* / *bail*, the price carries no *« / mois »*, and once interested the page says *Serge vous recontacte pour la suite de votre achat*. The fee, the slots, the cancel and the feedback states are unchanged; the rental page is the default. |
| all pages | Nav *Louer · Acheter · Propriétaires · Agents* (the logo goes to the goal picker). |
| `assets/css/styles.css` | `.goal-switch` (segmented pill of two links), `.goal-groups` / `.goal-card` (home picker), `.callout`. No new JS: every page works without it. |
| `assets/js/annonce.js` | Only toggles the SALE variant (`[data-sale-only]` / `[data-rental-only]` / `[data-sold-only]` / `[data-sold-hide]`, `?sale=1`), picks the sale photo list, words the interest form's error for a purchase, and on the SOLD page takes its form out of the document (its popup never opens). The sale text itself is in the HTML. |

## Reviewing the sale states

- Goal picker and landing pages: `index.html`, `louer.html`, `acheter.html`, `bailleur.html`, `vendre.html`,
  `agent.html`.
- Sale listing: `annonce.html?sale=1` (published: badge *À vendre*, no *« / mois »*, *Prix et conditions*, the interest
  form's errors on an empty submit); its SOLD state `annonce.html?sale=1&state=sold` (badge *Vendu*, the price and its
  notice only — no conditions block, no call to action, no form, even from `#visit`).
- Sale proposal: `proposition.html?sale=1` (PENDING), `?sale=1&state=accepted`,
  `?sale=1&state=accepted-no-visit` (the search isn't signed yet: the agent's contact only),
  `?sale=1&state=visit-requested` (add `&type=video` for a video visit), `?sale=1&state=rejected`,
  `?sale=1&state=expired`, `?sale=1&state=unavailable` (the listing is gone: no form, the agent's WhatsApp); a rejected
  answer is the same page's `#reject` popup.
- Sale visit: `visite.html?sale=1` (CONFIRMED), `?sale=1&state=requested`, `?sale=1&state=completed` (the feedback),
  `?sale=1&state=expired`, `?sale=1&type=video` (or `&state=requested&type=video`); a sale's `#cancel` popup names the
  villa.
- Phones (390 px): the goal cards, the price cards and the conditions tables.

## Out of scope, never promised here

As in the MVP, plus, for sales:

- Title-deed checks (the buyer FAQ says Ndopify verifies the agent, not the property's papers), buyer vetting or
  solvency, valuation.
- The deed of sale, the notary as a service, any payment through the platform (the price is paid to the seller
  directly), an offer between the buyer and the seller.
- A catalogue of properties for sale to browse, and off-market inventory: listing pages remain an entry point to a
  SEARCH mandate only.
- An agent specialisation: no page asks for one, and no mandate is filtered on it.

The commission of a sale is a percentage of the sale price, written in the mandate: the pages give no figure and never
say who pays it (#257).

## Open questions

1. **Sale commission figure and payer.** The pages say "un pourcentage du prix de vente, écrit dans le mandat", with
   no figure and no promise about who pays (seller, buyer, both). #257 owns the commission field; the public copy
   follows it.
2. **Timing.** A sale takes months: match expiry (`ndopify.match.expire.days`), mandate duration and the visit fee
   may need their own values for `SALE`.
3. **Order.** Ship sellers or buyers first? Sellers bring the stock that buyers' mandates are matched against.

## Not built yet

- `/`, `/louer`, `/acheter`, `/owner/sell`: only `/`, `/owner` and `/agent` exist in `templates/` (and
  `SecurityConfiguration`); the served `/` is still the tenant page.
- The emitted pages of the sale flows: the sale wording of the listing, proposition and visit pages is prototyped
  here, not yet in the served templates; e-mails and Telegram messages are approved copy, not pages.
- The sale branches of Partner Central's wizards exist in its own `v2/` prototype, not in its served templates.
