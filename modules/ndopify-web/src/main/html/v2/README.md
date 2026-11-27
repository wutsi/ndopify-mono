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
| `annonce.html` (#274) | A building sold as a whole, opened with `?building=1` (`?sale=1&building=1` works too): badge *À vendre*, 180M FCFA, the same *Prix et conditions* block and interest form as a sale; *En bref* reads **12 unités · 3 étages · 850 m²**, *Caractéristiques* *Type de bien* Immeuble, *Unités* 12, *Étages* 3, *Surface* 850 m² — no bedrooms, no bathrooms, no parking, no furniture; no *Équipements vérifiés* block (no water, electricity, road, security or access line anywhere); the description names the étages and the units. Its photos are a building set (`#listing-photos-building`). `?building=1&state=sold` is the SOLD page (badge *Vendu*, no conditions block, no form). The villa's `?sale=1` and the rental page are unchanged. |
| `proposition.html` (#274) | A buyer's proposition of a building, opened with `?building=1` (or `?sale=1&building=1`): a 12-unit, 3-étages, 850 m² building at 240M FCFA for a search of a 12-unit, 3-étages building up to 250M. *Pourquoi ce logement ?* (sale wording: *Pourquoi ce bien ?*) lists the neighbourhood, the budget, "12 unités, comme demandé", "3 étages, comme demandé" — no bedroom, amenity, furniture or parking line; the search summary reads "immeuble de 12 unités sur 3 étages à Bonapriso, jusqu'à 250 000 000 FCFA" and the searched-amenities list is absent; the facts block and *Caractéristiques* read units, étages and surface. Every state stays reachable (`&state=accepted|accepted-no-visit|visit-requested|rejected|expired|unavailable`, `&type=video`). The villa's `?sale=1` and the rental page are unchanged. |
| `assets/js/annonce.js`, `assets/js/proposition.js` (#274) | The building switch, next to the sale one: `[data-property-only]` (the villa's sale blocks) and `[data-building-only]` (the building's), plus `[data-no-building]` for the blocks a building never has (the *Équipements vérifiés* section, the searched amenities) — the text itself is in the markup, so every variant reads without JavaScript. `?building=1` implies the sale (`isSale`), picks `#listing-photos-building`, words the tab title and the description, and keeps the answer and the visit links on that variant. |

A contact e-mail refused by ndopify-server (#380): the listing interest form's `POST /v1/mandates` answers
`400 EMAIL_ADDRESS_INVALID` when SES rates the address's mailbox undeliverable. The page is rendered again with
`#visit` open (`is-open`), the typed values kept, the e-mail flagged (`aria-invalid`) and the same sentence
« Cette adresse email n'est pas valide. » under it; the popup's global *Votre demande n'a pas pu être envoyée…* is
not shown for that code (it stays for every other refusal). The sentence is static markup; the `?state=` switch is
`annonce.js`'s, like `?sale=1` / `?state=sold`, and it works on the rental page as on the villa and the building.

| URL | State |
|---|---|
| `annonce.html?state=email-invalid` | The rental listing with `#visit` open and every field filled, the e-mail flagged. Also `annonce.html?sale=1&state=email-invalid` (the villa) and `annonce.html?building=1&state=email-invalid`. |
| `annonce.html?state=email-invalid-empty` | The same with only the e-mail typed: the sentence stays above the fold inside the sheet at 320 px and 390 px. |

The loading state doesn't apply (one full-page post, no in-page spinner); a valid submission is unchanged, and the
prototype's submit stays simulated (it never posts — production answers again with the same refusal while the address
is unchanged). At 320 px and 390 px the sentence wraps under the e-mail without pushing the fields; at 1024 px the
popup is centred and the same line takes one row.

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
- A building sold as a whole (#274): `annonce.html?building=1` (badge *À vendre*, 180M FCFA, *12 unités · 3 étages ·
  850 m²* in *En bref*, *Caractéristiques* with *Unités* and *Étages*, no *Équipements vérifiés* block and no
  bedroom, bathroom, parking or utility line, the building's photos) and `annonce.html?building=1&state=sold` (badge
  *Vendu*, the price with its notice only, no conditions block and no form); `annonce.html?sale=1` (the villa) and the
  rental page are unchanged. `proposition.html?building=1` (a 12-unit, 3-étages building at 240M for a 12-unit,
  3-étages search up to 250M: *Pourquoi ce bien ?* with "12 unités, comme demandé" and "3 étages, comme demandé", no
  searched-amenities list, the search summary worded for a building), its states `?building=1&state=accepted`,
  `&state=accepted-no-visit`, `&state=visit-requested` (add `&type=video`), `&state=rejected`, `&state=expired`,
  `&state=unavailable`; `proposition.html?sale=1` (the villa) is unchanged. At 390 px the fact chips wrap on two lines
  and the spec list stacks; at 1024 px they sit on one line. Without JavaScript the page is the rental one until
  `annonce.js`/`proposition.js` picks a variant, as for the sale (the building's text is in the markup).
  Assumptions: `?building=1` implies the sale (`?sale=1&building=1` is equivalent); the building's price (180M on the
  listing, 240M in the proposition) is this prototype's mock data; the building's photos reuse `picsum` seeds like the
  villa's. Two points settled with the requester: the count reads *étages* (its field stays `levels`), and a building
  shows no bathroom — neither in *En bref*, *Caractéristiques* nor the manager's *Le bien* — nor any parking.

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
