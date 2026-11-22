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
| `mandate.html` | For a sale: *Acheteur* / *Vendeur*, *Budget total* / *Prix de vente* with no "/ mois", no rental terms; closing and cancel text for a sale (*Vente conclue*, *acte de vente*). |
| `mandate/contract.html` | For a sale, a **Commission** field in % of the sale price (default 5 %): a proposal, see open questions. |
| `mandate/close.html` | For a sale: *Prix de vente final* in FCFA, *Vendu à un autre acheteur*. |
| `mandate/edit.html`, `listing.html`, `listing/conditions.html` | Sale labels; rental-only terms (deposit, advance, lease term, notice, furniture) hidden and saved empty; the listing's checklist row is *Prix et conditions*. |
| `offline.html` | Its links to the production stylesheet and icon go one level up (`../../resources/…`). |

To review the sale states: `index.html` (offer 104, card 301), `mandate.html?id=301` (a seller's villa) and
`mandate.html?id=302` (a seller's plot of land, whose criteria show its *Document de propriété*), `mandate/contract.html?id=104`,
`mandate/close.html?id=301`; accept offer 104 to open `mandate.html?id=104` and `mandate/edit.html?id=104`; *Créer
l'annonce* on 301, then `listing.html?id=501` and `listing/conditions.html?id=501`.

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
