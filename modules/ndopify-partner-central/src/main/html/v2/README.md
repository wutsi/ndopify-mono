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

The DTOs already carry `listingType` (`SALE` / `RENTAL`) and `PeriodicityType.NONE`. Every verified agent handles
rentals and sales (decided 2026-11-22): no mandate is filtered on a specialisation, and no page asks for one.

## Changes from the current prototype

| Page | Change |
|---|---|
| `find-property.html` (*Trouver un bien*) | Step 1 asks *Vous souhaitez : Louer / Acheter* (`listingType`, required, preselected from `?type=`). For a purchase: no *Chambre*, *Terrain* offered for residential use too (no rooms asked; its surface required), no furniture question, *Budget total* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), *Quand souhaitez-vous acheter ?* (same `MovingPeriod` values), the financing (*Comptant* / *Prêt bancaire* / *Transfert depuis l'étranger* / *Pas encore décidé*, required, review *Financement*; a rental sends none) and, for a *Terrain* purchase only, *Quels documents exigez-vous ?* (three documents, optional: *Titre foncier* / *Plan de bornage* / *Certificat de propriété*, review *Documents exigés*) and, as the budget is typed, the implied price per m² (display only, nothing stored); review *Achat, usage …* / *Achat prévu*. The sale reassurance names a verified agent of the area, not a sale specialist. |
| `offer-property.html` (*Proposer un bien*) | Step 1 asks *Mettre en location / Vendre*, preselected from `?type=`. For a sale: *Prix de vente souhaité* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), no advance, deposit or furniture (sent as null), no *Chambre*, *Terrain* residential too; *Disponible à partir du* kept. A sale of a *Terrain* asks its *Document de propriété* (*Titre foncier* / *Plan de bornage* / *Certificat de propriété*, optional and several, review *Document de propriété*; sent only for a SALE · LAND, none is a valid answer). A sale of a *Terrain* also shows, as its surface is typed, the implied price per m² (asking price ÷ surface, rounded to the nearest 100 FCFA, display only, never stored). Rental/sale wordings are `data-for="RENTAL"` / `data-for="SALE"` pairs. *Quitter* and the confirmation go to the public site's `/owner/sell` for a sale, `/owner` for a rental. |
| `offer-property.html` (*Proposer un bien*, #273) | The sale's *Caractéristiques* step no longer blocks a *Terrain* nor loses its documents. Before the type is saved, a sale renders *Nombre de chambres*, *Parking ou garage* and *Combien de places ?* but doesn't require them (they carry no `required`), and shows *Document de propriété*; once the type is saved, a *Terrain* hides the three and keeps the documents, and another type hides the documents and asks rooms and parking as today. A sold *Terrain* labels the surface *Surface du terrain estimée* and, once a surface is typed, shows *Soit ≈ 56 300 FCFA/m² pour 800 m²* (asking price ÷ surface, rounded to the nearest 100, display only). Its review's *Conditions* reads the total price in full (*45 000 000 FCFA*, no " par mois"), then — for a land sale only — the same rate line. The rate is formatted like the price — thousands grouped, as every amount of the page, with the tenant's currency in production (FCFA in this prototype), never a raw number. Without its surface, a land sale's step says *Indiquez la surface du terrain.* Land stays sale-only here: the rental offering is unchanged. The project pill next to the step count and under the confirmation's title belongs to #278 (see its row). |
| `find-property.html`, `offer-property.html`, `mandate/edit.html` (#275) | Land is sold or bought, never let: both wizards offer *Terrain* under neither use when *Louer* / *Mettre en location* is chosen (a posted `propertyType=LAND` is refused — *Choisissez le type de bien.* on *Proposer un bien*, and the type step never completes with it on *Trouver un bien*), and keep it under both *Résidentiel* and *Commercial* for *Acheter* / *Vendre*. Only the rental's *Commercial* description drops "terrain…" (`data-for` pair, like the *Résidentiel* one); the sale's keeps it. A rental mandate whose stored type is *Terrain* keeps it readable: `mandate/edit.html` shows it checked and enabled in its saved *Commercial* category, saves it unchanged, and stops offering it once another type is picked (208 a renter's search, 209 a landlord's property); a sale mandate keeps offering it (104). Nothing else moves: the listing sections, the sale's documents, surface and rate, and the stored data are untouched. |
| `find-property.html`, `offer-property.html` (#278) | The project is visible on every step after the first. From step 2 on, a read-only pill sits at the right end of the step-count line — *Vente* / *Location* on *Proposer un bien*, *Achat* / *Location* on *Trouver un bien* — and the confirmation repeats the word under its title (*Vente* under *Bien enregistré*, *Achat* under *Demande envoyée*, never inside the heading). Step 1 (*Quel est votre projet ?*) shows its own choice only, with no pill. The pill is text, never a control: no button, link nor ARIA role, not focusable, its word preceded by a visually hidden *Projet :*; it stays on the step count's line and the header row wraps under it only when both can't fit on a phone. It is the page's `data-for="RENTAL"` / `data-for="SALE"` pair, so the confirmation follows the submitted `listingType`; in production the server renders it from the saved `listingType`, so it shows without JavaScript. One definition serves both wizards: `.progress__head`, `.project-tag` and `.confirmation__head` live in `partner-central.css`. |
| `rejoindre.html` (sign-up), `profile/edit.html` | No agent specialisation (every verified agent handles rentals and sales): the experience step keeps its *Depuis combien de temps exercez-vous ?* and *Comment exercez-vous ?* answers, the profile keeps its experience, city, neighbourhoods and biography. |
| `index.html` (Espace agent) | Every offer and *Mes mandats* card has a *Vente* / *Location* type badge, a status pill of the ink-keyline variant: on a *Mes mandats* card it hugs its own word under the party and type, never the client's name width (#276). A sale's title reads *Acheteur* (a buyer's search) / *Vendeur* (a seller's listing), its facts *Budget* / *Prix* with no "/ mois" and *Achat prévu* / *Disponibilité*. Samples: sale offer **104** (a buyer's plot of land, 15M FCFA) and sale mandate **301** (a seller's villa, 250M FCFA, signed, listing to create). |
| `mandate.html` | For a sale: *Acheteur* / *Vendeur* and a *Vente* type badge, contact role *Acheteur* / *Vendeur*, criteria *Budget* / *Prix* with no "/ mois" and *Achat prévu* / *Disponibilité*, no furniture or other rental term; the next step addresses the buyer (*Proposez des biens à l'acheteur…*) or the seller (*… présentez le bien aux acheteurs*). Closing and cancel text for a sale (*Vente conclue*, *acte de vente*). #276: the type and the status are two pills of the same class, siblings of the title and outside the heading, one after the other on its line; below 640 px the pair takes its own line under the title (sale and rental alike). |
| `mandate.html` | The closed panel of a sale records the deal like the wizard that produced it: *Acheteur : …*, *Annonce n° … vendue*, *Vendu à un autre acheteur* for a deal closed without a counterpart, and the notice *félicitations pour cette vente !* (#258). |
| `mandate.html` | A buyer's land purchase (#253/#256): *Documents exigés* (*Titre foncier · Plan de bornage*, or *Aucun document exigé*) stays among the **visible** criteria, with the note that a land for sale whose seller declared none of them is not proposed (*Un seul de ces documents déclaré suffit*). `?documents=none` shows a buyer who requires none: no note. |
| `mandate.html` | *Termes du contrat* (#257): a section after the progress and before *Sa recherche*, once the mandate has a contract — *Durée*, then for a sale *Commission* (*5 % du prix de vente* for 301, 7 % for 302), *Frais de visite* (*Aucun frais de visite* for 0), then *Signé le* / *Valable jusqu'au* once signed. A rental keeps today's rows, with no commission; a mandate not sent yet (104) has no section at all. |
| `mandate/contract.html` | For a sale, a real **Commission** field in % of the sale price (#257): per mandate, whole number from 1 to 20, default 5 %, pre-filled from the contract (302 re-opens on 7 %, 301 on 5 %, 104 on the 5 % default as it has none); required, and posted without JavaScript. Hidden and disabled on a rental (201), whose success fee stays the contract's 1 month of rent. Its field error: *Indiquez un pourcentage entier, de 1 à 20 (par exemple 5).* |
| `mandate/close.html` | For a sale: the counterpart step is *Acheteur* (a listing) / *Bien* (a search), *À quel acheteur le bien a-t-il été vendu ?*, *Vendu à un autre acheteur* / *Un bien hors de vos annonces*; the price step *Prix* / *Prix de vente final* in FCFA with no " / mois"; the recap *Acheteur* / *Prix de vente final*, its note naming the seller's / buyer's mandate; the request carries `PeriodicityType.NONE` (MONTHLY for a rental). Closing a buyer's search on another agent's listing leaves that listing on the market with its mandate: the note says so. |
| `mandate/edit.html` | For a sale: the group *Budget et achat* (a buyer) / *Prix et disponibilité* (a seller), the *Budget* / *Prix* field in FCFA with no " / mois", no caution, advance, lease term, notice or furniture; its missing-price error names the *budget* / the *prix*. Rental unchanged. |
| `listing.html`, `listing/conditions.html` | A sale listing's checklist row is *Prix et conditions*, its summary the sale price only. The conditions section is *Prix et conditions*: *Prix* and the date the property is free, the rental-only terms (caution, advance, lease term, notice, furniture) hidden and saved empty; the location and GPS hints say *acheteurs*, not *locataires*. |
| Match cards (*Biens correspondants*, *Propositions*) | A sale's card reads *Prix* (no "/ mois") and *Disponibilité*, and its fit lines say *Disponible avant votre achat*. |
| `offline.html` | Its links to the production stylesheet and icon go one level up (`../../resources/…`). |

To review the type and status badges (#276): `index.html` (offer 104 and card 301 read *Vente*; the rental cards read
*Location*, their badge hugging its own word) and `mandate.html?id=301` (*Vente* + *Actif*) against `?id=201`
(*Location* + *Assigné*), both at 390 px to see the pair on its own line under the title.

To review the sale states: `index.html` (offer 104, card 301), `mandate.html?id=301` (a seller's villa) and
`mandate.html?id=302` (a seller's plot of land, whose criteria show its *Document de propriété*), `mandate/close.html?id=301`;
accept offer 104 to open `mandate.html?id=104` (a buyer's land search: its visible *Documents exigés* and the note of
#256 — `&documents=none` for *Aucun document exigé*, without the note) and `mandate/edit.html?id=104`; *Créer
l'annonce* on 301, then `listing.html?id=501` and `listing/conditions.html?id=501`.

The *Proposer un bien* states of #273 (a sale's *Caractéristiques* step, then its review) are one click away — each
`?sample=` seeds the session once and opens that step; walk the wizard from there, or open
`offer-property.html?type=SALE` to go through every step by hand:

| URL | State |
|---|---|
| `offer-property.html?sample=sale` | *Caractéristiques*, a sale whose type isn't saved yet (the step's first render): *Nombre de chambres*, *Parking ou garage* and *Combien de places ?* are visible but not required (no `required` on their radios), *Document de propriété* is visible. Pick a type: *Terrain* hides the three, *Villa, maison* requires them. |
| `offer-property.html?sample=land` | The same with the type *Terrain*: no rooms nor parking, the documents, *Surface du terrain estimée* (800 m²) and, under the surface, *Soit ≈ 56 300 FCFA/m² pour 800 m²* (45 000 000 FCFA ÷ 800, rounded to the nearest 100). |
| `offer-property.html?sample=land-no-area` | The same without its surface: *Suivant* is inactive; click it for *Indiquez la surface du terrain.* |
| `offer-property.html?sample=house` | The same with the type *Villa, maison* (3 chambres, 2 places de parking): the documents are hidden, rooms and parking are asked, no rate line. |
| `offer-property.html?sample=land-review` | The review of the land sale: *Conditions* with *45 000 000 FCFA*, then *≈ 56 300 FCFA/m² pour 800 m²*, then the availability; *Caractéristiques* *Terrain · 800 m²*; *Document de propriété* *Titre foncier · Plan de bornage*. |
| `offer-property.html?sample=land-review-none` | The same sale with no document declared: *Aucun document déclaré*. |
| `offer-property.html?sample=rental` | The review of a rental (appartement non meublé, 3 chambres, 1 place, 250 000 FCFA / mois, 2 mois d'avance et de caution): *Étape 7 sur 7* with the *Location* pill; submit (or reload) for the confirmation's *Location* under *Bien enregistré*; *Précédent* walks every earlier step with the same pill. |

Every one of these sale samples reads *Vente* next to the step count, and so does their confirmation; `?sample=rental`
reads *Location*. Step 1 never shows the pill: its *Mettre en location / Vendre* choice is the only place the project
is chosen.

The project pill (#278) on the buyer's side: `find-property.html` opens on step 1 with no pill; answer *Acheter* (or
open `find-property.html?type=SALE`) and pass it — *Le secteur* is *Étape 2 sur 7* with the *Achat* pill at the right
end, then every step up to the review (*Étape 7 sur 7*) keeps it, and *Envoyer ma demande* shows *Achat* under
*Demande envoyée*. With *Louer* (or `?type=RENTAL`) the same steps read *Location*. On the seller's side the sale
samples above already open later steps with *Vente*, and `?sample=rental` with *Location*. At 380 px the pill stays on
the step count's line for both words, the header row wrapping under it only when count and pill can't fit; at 1024 px
it sits at the right end of that line. In production the pill is rendered by the server from the saved `listingType`,
so it needs no script; the prototype applies the project's `data-for` pair (`applyProjectPill` on the step count,
`applyConfirmation` on the confirmation). No new empty, loading or error state: the pill always has one of its two
words.

The rental states keep their questions: `offer-property.html` starts on a rental, whose *Caractéristiques* step shows
the follow-up questions only once a type is chosen, never the documents nor a rate; from step 2 on it reads
*Location* next to the step count, as every sale reads *Vente*. On a phone, the rate line wraps under the surface, each
review line keeps its own line, and the project pill stays on the step count's line (the header row wraps below it only
when both can't fit).

Land is sold or bought, never let (#275). `offer-property.html?sample=rental-land` opens the *Caractéristiques* step
of a rental posted with *Terrain*: the type isn't offered under either use, no choice is selected, and *Suivant*
answers *Choisissez le type de bien.* `offer-property.html?type=SALE` walks to the same step with *Terrain* under both
*Résidentiel* and *Commercial*, whose description keeps "terrain…" where the rental's names none; the #273 land
samples (`?sample=land`, `land-no-area`, `land-review`, `land-review-none`) are unchanged — documents, surface and
rate. `find-property.html?type=RENTAL` offers no *Terrain* on step 3 under either use, *Indifférent* included as
always, against `?type=SALE`, which keeps it under both. On a phone the choice grid stays one column (a plot's answer
is one row less on a rental; the sale's cards read the same as before).

A stored rental *Terrain* stays readable: `mandate/edit.html?id=208` (a renter's search) and `?id=209` (a landlord's
property) show *Terrain* checked and enabled under *Commercial* (the type list reads one type per row, at every width),
the group's summary reading *Terrain*; *Enregistrer*
unchanged keeps it, and picking another type makes it disappear for good. `?id=201` is the ordinary rental (no
*Terrain* under either use), `?id=104` the sale (*Terrain* under both). `mandate.html?id=208` and `?id=209` read the
same saved answer (*Type de propriété : Terrain*, loyer, surface), as before the rule. The `?sample=` shortcuts seed
the session once, like every other one: reloading one after changing its answers keeps the change. The empty and
loading states don't apply to this change, and there is no new screen.

The empty and error states: `index.html?assignments=empty` (an agent without offers: no *Mandats proposés* section) and
`index.html?mandates=empty` (no mandate in progress: the section's empty line). On `mandate/edit.html?id=104`, submit
without a price: the error names the sale's *budget* (*Indiquez le budget, en FCFA.*). On
`listing/conditions.html?id=501`, the section is *Prix et conditions*: submit without a price for its error, and note
that the caution, advance, lease term and notice fields are absent (a sale saves them empty) and that
`listing.html?id=501` summarises it as the sale price only.

A sold sale listing: after `mandate.html?id=301&listing=published` and the closing of the #258 paragraph below,
`listing.html?id=601` is the villa's listing, tagged *Vendu*, with no section to fill.

The sale match cards are `mandate.html?id=303` and `?id=304`: the buyers of the seller's villa (301), with matches
9301–9303 — *Prix*, *Disponibilité*, *Disponible avant votre achat*; 303's second match is another agent's villa
(co-brokerage).

Closing a sale (#258): `mandate.html?id=301&listing=published` shows the seller's villa published (listing 601) with
the buyers it was sent to, then *Fermer le mandat* opens `mandate/close.html?id=301&listing=published`: the
counterpart step offers Mireille Tchoumi and Patrice Owona, closing with one closes their search too, records
*Acheteur : …* on the seller's page and leaves the listing *Vendu* (`listing.html?id=601`); *Vendu à un autre
acheteur* records the outside deal instead. `mandate.html?id=303` is the buyer's villa search and
`mandate/close.html?id=303&step=listing` its counterpart step: closing on another agent's villa (Clarisse Mbarga's)
only closes the search. `?counterparts=unavailable` and `?counterpart=gone` show the counterpart step's two failures
(the list unreadable, the answer kept no longer available).

The contract (story #257): `mandate/contract.html?id=302` (a sale's *Commission* pre-filled with the contract's
7 %), `?id=301` (5 %), `?id=104` (a sale with no contract yet: the 5 % default) and `?id=201` (a rental: no
*Commission* field); submit the commission blank, 0, 21 or "5,5" for its error, and note the three terms stack on a
phone. `mandate.html?id=301` and `?id=302` show *Termes du contrat* with the commission row (5 % and 7 % du prix de
vente); `mandate.html?id=203` and `?id=204` a signed rental's terms, with no commission row, `?id=202` a rental's
terms not signed yet (no *Signé le*), and `?id=104` no terms section at all (no contract).

## Out of scope, never promised here

As in the MVP, plus, for sales: title-deed checks, buyer vetting or solvency, valuation, the deed of sale and the
notary, any payment through the platform, and any agent specialisation.

The commission of a sale is a percentage of the sale price, written in the mandate; the prototype gives no figure and
never says who pays it (#257 owns the field).

## Open questions

1. **Sale commission figure and payer**: the pages say "un pourcentage du prix de vente, écrit dans le mandat", with no
   figure and no promise about who pays.
2. **Timing**: a sale takes months; match expiry and the visit fee may need their own defaults for a SALE.
3. **Buying horizon**: reuse `MovingPeriod` (as here) or a sale-specific enum?

## Not done

- Some generic wording still says "client" on a sale mandate (*Envoyer au client*, the contract page's resend line).
- E-mails and Telegram messages are approved copy in the story, not prototyped here.
- Nothing in `templates/` or the BFF: this is the prototype only.
