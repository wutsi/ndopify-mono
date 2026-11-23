# ndopify-partner-central — v2 prototype: property sales

Partner Central's prototype with property sales next to rentals. A full copy of `../` (the current prototype, left as
is), then changed. It goes with the public site's v2 prototype (`modules/ndopify-web/src/main/html/v2/`), whose
*Acheter* and *Vendre* pages link to the wizards here with `?type=SALE`. Never serve this folder, and never delete or
"clean up" anything in it.

Ndopify matches a customer with a verified agent through a mandate; sales add two mandates, not a catalogue:

| Customer | Mandate | Entry |
|---|---|---|
| Buyer | SEARCH · SALE | `find-property.html?type=SALE` |
| Seller | LISTING · SALE | `offer-property.html?type=SALE` |

The DTOs already carry `listingType` (`SALE` / `RENTAL`) and `PeriodicityType.NONE`. A SALE mandate is offered only to
agents whose specialisation covers it.

## Changes from the current prototype

| Page | Change |
|---|---|
| `find-property.html` (*Trouver un bien*) | Step 1 asks *Vous souhaitez : Louer / Acheter* (`listingType`, required, preselected from `?type=`). For a purchase: no *Chambre*, *Terrain* offered for residential use too (no rooms asked; its surface required), no furniture question, *Budget total* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), *Quand souhaitez-vous acheter ?* (same `MovingPeriod` values), the financing (*Comptant* / *Prêt bancaire* / *Transfert depuis l'étranger* / *Pas encore décidé*, required, review *Financement*; a rental sends none) and, for a *Terrain* purchase only, *Quels documents exigez-vous ?* (three documents, optional: *Titre foncier* / *Plan de bornage* / *Certificat de propriété*, review *Documents exigés*) and, as the budget is typed, the implied price per m² (display only, nothing stored); review *Achat, usage …* / *Achat prévu*. |
| `offer-property.html` (*Proposer un bien*) | Step 1 asks *Mettre en location / Vendre*, preselected from `?type=`. For a sale: *Prix de vente souhaité* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), no advance, deposit or furniture (sent as null), no *Chambre*, *Terrain* residential too; *Disponible à partir du* kept. A sale of a *Terrain* asks its *Document de propriété* (*Titre foncier* / *Plan de bornage* / *Certificat de propriété*, optional and several, review *Document de propriété*; sent only for a SALE · LAND, none is a valid answer). A sale of a *Terrain* also shows, as its surface is typed, the implied price per m² (asking price ÷ surface, rounded to the nearest 100 FCFA, display only, never stored). Rental/sale wordings are `data-for="RENTAL"` / `data-for="SALE"` pairs. *Quitter* and the confirmation go to the public site's `/owner/sell` for a sale, `/owner` for a rental. |
| `rejoindre.html` (sign-up), `profile/edit.html` | The agent's specialisation, in the experience step (still 5 steps) and on the profile: *Quels mandats traitez-vous ?* (Location, Vente) and *Quels biens ?* (Logements, Locaux commerciaux, Terrains), at least one of each; shown on the review. Proposed fields: `listingTypes` (ListingType[]), `propertyCategories` (PropertyCategory[]), `land` (Boolean). |
| `index.html` (Espace agent) | Every offer and *Mes mandats* card has a *Vente* / *Location* tag. Samples: sale offer **104** (a buyer's plot of land, 15M FCFA) and sale mandate **301** (a seller's villa, signed, listing to create). |
| `mandate.html` | For a sale: *Acheteur* / *Vendeur*, *Budget total* / *Prix de vente* with no "/ mois", no rental terms; closing and cancel text for a sale (*Vente conclue*, *acte de vente*). The closed panel of a sale records the deal like the wizard that produced it: *Acheteur : …*, *Annonce n° … vendue*, *Vendu à un autre acheteur* for a deal closed without a counterpart, and the notice *félicitations pour cette vente !*. |
| `mandate.html` | A buyer's land purchase (#253/#256): *Documents exigés* (*Titre foncier · Plan de bornage*, or *Aucun document exigé*) stays among the **visible** criteria, with the note that a land for sale whose seller declared none of them is not proposed (*Un seul de ces documents déclaré suffit*). `?documents=none` shows a buyer who requires none: no note. |
| `mandate/contract.html` | For a sale, a **Commission** field in % of the sale price (default 5 %): a proposal, see open questions. |
| `mandate/close.html` | For a sale: the counterpart step is *Acheteur* (a listing) / *Bien* (a search), *À quel acheteur le bien a-t-il été vendu ?*, *Vendu à un autre acheteur* / *Un bien hors de vos annonces*; the price step *Prix* / *Prix de vente final* in FCFA with no " / mois"; the recap *Acheteur* / *Prix de vente final*, its note naming the seller's / buyer's mandate; the request carries `PeriodicityType.NONE` (MONTHLY for a rental). Closing a buyer's search on another agent's listing leaves that listing on the market with its mandate: the note says so. |
| `mandate/edit.html`, `listing.html`, `listing/conditions.html` | Sale labels; rental-only terms (deposit, advance, lease term, notice, furniture) hidden and saved empty; the listing's checklist row is *Prix et conditions*. |
| `offline.html` | Its links to the production stylesheet and icon go one level up (`../../resources/…`). |

To review the sale states: `index.html` (offer 104, card 301), `mandate.html?id=301` (a seller's villa) and
`mandate.html?id=302` (a seller's plot of land, whose criteria show its *Document de propriété*),
`mandate/close.html?id=301`; accept offer 104 to open `mandate.html?id=104` (a buyer's land search: its visible
*Documents exigés* and the note of #256 — `&documents=none` for *Aucun document exigé*, without the note) and
`mandate/edit.html?id=104`; *Créer l'annonce* on 301, then `listing.html?id=501` and `listing/conditions.html?id=501`.

Closing a sale (#258): `mandate.html?id=301&listing=published` shows the seller's villa published (listing 601) with
the buyers it was sent to, then *Fermer le mandat* opens `mandate/close.html?id=301&listing=published`: the
counterpart step offers Mireille Tchoumi and Patrice Owona, closing with one closes their search too, records
*Acheteur : …* on the seller's page and leaves the listing *Vendu* (`listing.html?id=601`); *Vendu à un autre
acheteur* records the outside deal instead. `mandate.html?id=303` is the buyer's villa search and
`mandate/close.html?id=303&step=listing` its counterpart step: closing on another agent's villa (Clarisse Mbarga's)
only closes the search. `?counterparts=unavailable` and `?counterpart=gone` show the counterpart step's two failures
(the list unreadable, the answer kept no longer available).

## Out of scope, never promised here

As in the MVP, plus, for sales: title-deed checks, buyer vetting or solvency, valuation, the deed of sale and the
notary, and any payment through the platform.

## Open questions

1. **Sale commission**: who pays (seller, buyer, both) and how much. The contract page proposes a % of the price,
   default 5 %.
2. **Specialisation and dispatch**: should a sale mandate fall back to any agent of the area when no specialist
   accepts it? Are `listingTypes` / `propertyCategories` / `land` the right shape?
3. **Timing**: a sale takes months; match expiry, mandate duration (*Durée*, 1–12 months) and the visit fee may need
   their own defaults for a SALE.
4. **Buying horizon**: reuse `MovingPeriod` (as here) or a sale-specific enum?

## Not done

- Some generic wording still says "client" on a sale mandate (*Envoyer au client*, the contract page's resend line).
- No sale match / proposal sample (`agent.html`, the visits) beyond the mandate pages.
- Nothing in `templates/` or the BFF: this is the prototype only.
