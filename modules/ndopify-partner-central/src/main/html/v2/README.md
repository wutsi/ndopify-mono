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
| `find-property.html` (*Trouver un bien*) | Step 1 asks *Vous souhaitez : Louer / Acheter* (`listingType`, required, preselected from `?type=`). For a purchase: no *Chambre*, *Terrain* offered under residential use only (no rooms asked; its surface required), no furniture question, *Budget total* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), *Quand souhaitez-vous acheter ?* (same `MovingPeriod` values), the financing (*Comptant* / *Prêt bancaire* / *Transfert depuis l'étranger* / *Pas encore décidé*, required, review *Financement*; a rental sends none) and, for a *Terrain* purchase only, *Quels documents exigez-vous ?* (three documents, optional: *Titre foncier* / *Plan de bornage* / *Certificat de propriété*, review *Documents exigés*) and, as the budget is typed, the implied price per m² (display only, nothing stored); review *Achat, usage …* / *Achat prévu*. The sale reassurance names a verified agent of the area, not a sale specialist. |
| `offer-property.html` (*Proposer un bien*) | Step 1 asks *Mettre en location / Vendre*, preselected from `?type=`. For a sale: *Prix de vente souhaité* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), no advance, deposit or furniture (sent as null), no *Chambre*, *Terrain* under residential use only; *Disponible à partir du* kept. A sale of a *Terrain* asks its *Document de propriété* (*Titre foncier* / *Plan de bornage* / *Certificat de propriété*, optional and several, review *Document de propriété*; sent only for a SALE · LAND, none is a valid answer). A sale of a *Terrain* also shows, as its surface is typed, the implied price per m² (asking price ÷ surface, rounded to the nearest 100 FCFA, display only, never stored). Rental/sale wordings are `data-for="RENTAL"` / `data-for="SALE"` pairs. *Quitter* and the confirmation go to the public site's `/owner/sell` for a sale, `/owner` for a rental. |
| `offer-property.html` (*Proposer un bien*, #273) | The sale's *Caractéristiques* step no longer blocks a *Terrain* nor loses its documents. Before the type is saved, a sale renders *Nombre de chambres*, *Parking ou garage* and *Combien de places ?* but doesn't require them (they carry no `required`), and shows *Document de propriété*; once the type is saved, a *Terrain* hides the three and keeps the documents, and another type hides the documents and asks rooms and parking as today. A sold *Terrain* labels the surface *Surface du terrain estimée* and, once a surface is typed, shows *Soit ≈ 56 300 FCFA/m² pour 800 m²* (asking price ÷ surface, rounded to the nearest 100, display only). Its review's *Conditions* reads the total price in full (*45 000 000 FCFA*, no " par mois"), then — for a land sale only — the same rate line. The rate is formatted like the price — thousands grouped, as every amount of the page, with the tenant's currency in production (FCFA in this prototype), never a raw number. Without its surface, a land sale's step says *Indiquez la surface du terrain.* Land stays sale-only here: the rental offering is unchanged. The project pill next to the step count and under the confirmation's title belongs to #278 (see its row). |
| `find-property.html`, `offer-property.html`, `mandate/edit.html` (#275) | Land is residential and sold or bought, never let: both wizards offer *Terrain* under neither use when *Louer* / *Mettre en location* is chosen (a posted `propertyType=LAND` is refused — *Choisissez le type de bien.* on *Proposer un bien*, and the type step never completes with it on *Trouver un bien*), and offer it under *Résidentiel* only for *Acheter* / *Vendre*. Only the rental's *Commercial* description drops "terrain…" (`data-for` pair, like the *Résidentiel* one); the sale's residential one names "terrain à bâtir…" and its commercial one no longer names it. A rental mandate whose stored type is *Terrain* keeps it readable: `mandate/edit.html` shows it checked and enabled in its saved *Résidentiel* category (208 a renter's search, 209 a landlord's property), saves it unchanged, and stops offering it once another type is picked; a sale mandate offers it under *Résidentiel* (104). Nothing else moves: the listing sections, the sale's documents, surface and rate, and the stored data are untouched. |
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
| `find-property.html` | A building purchased as a whole is captured by two counts (#274): the *bien* step asks *Nombre d'étages* (1–30) and *Nombre d'unités* (1–500), both required, each with an *Indifférent* checkbox (stored as null), and shows no *Places de parking*; the amenities step leaves the wizard, so that branch reads *Étape X sur 6* (progress track, the steps' legends, *Précédent*/Suivant and `?etape=` follow it) and the review lists *Étages* and *Unités* (*Indifférent* when blank) with no amenity line. A rental building keeps the seven steps and its questions. `?sample=` seeds: `building`, `building-any`, `building-review`, `building-review-any`, `rental-building`. |
| `offer-property.html` | A building sale (#274): *Immeuble entier* is offered under *Commercial* only (`data-sale-categories="COMMERCIAL"`; a rental keeps *Résidentiel*, whose sale description no longer names it), the *Caractéristiques* step asks no *Parking ou garage* nor *Combien de places ?* and requires *Étages* and *Unités* (as the buyer's counts, without *Indifférent*); the amenities step leaves the wizard (six steps, same progress and `?etape=` handling) and the review shows the two counts with no *Équipements* line. `?sample=building` opens *Caractéristiques* of that branch, `?sample=building-review` its review. |
| `mandate.html` | A building sale (#274): 305 is a seller's building of 3 étages and 12 units (250M, Bonapriso, *Commercial* · *Immeuble*): its criteria read *Étages* 3 and *Unités* 12 among the visible ones, with no amenity, parking or bedroom row; 306 is a buyer's search for a building of 10 units and 3 étages (250M), whose matches read "12 unités · 3 étages" (and "20 unités au lieu de 10", "Nombre d'unités non précisé") with *Pourquoi ce bien ?* worded in units and étages only. 305 is Assigné, so its edit page is reachable. |
| `mandate/edit.html` | A building sale (#274): the *Le bien* group shows *Étages* (1–30) and *Unités* (1–500), prefilled from the mandate, and hides *Chambres*, *Places de parking* and the whole *Équipements* group; the group's summary counts them ("Immeuble · 3 étages · 12 unités · 850 m²"), and both fields refuse empty, 0, out-of-bound and non-numeric values. Other types are unchanged. |
| `listing.html`, `listing/bien.html` | A building's listing (#274): `?id=701` seeds a DRAFT for mandate 305 (Immeuble, 850 m², 3 étages, 12 units, no bathrooms nor parking); its checklist has six rows (no *Eau, électricité, accès*, not required to send it) and *Le bien* asks *Étages* and *Unités*, both required, with neither *Salles de bains* nor *Places de parking*; `listing/equipements.html?id=701` goes back to the checklist. Other types keep the seven sections. |
| `listing/bien.html`, `assets/js/partner-central.js` (#287) | *Le bien* of a sale listing for a plot of land: no *Places de parking* (an office, a retail or an industrial listing keeps it; a land mandate stores no parking), and a new *Document de propriété (facultatif)* fieldset under the surface — *Titre foncier*, *Plan de bornage*, *Certificat de propriété*, one option per line at every width, none required, several selectable, with the hint that the agent checks them at the audit. Shown for a SALE · LAND only, hidden for another type and for a rental (a hidden answer is saved empty), and pre-filled from the seller's declaration when the listing is created. Layout: every field takes a full row (*Usage*, *Type de bien*, *Ameublement*, each count, *Surface*, *Document de propriété*) and every option of every fieldset sits alone on its row — no two-column count grid and no `choices__grid--2` on this page, at 390 px as at 1024 px. If the agent's answer differs from the seller's, the standard *Indiqué par le propriétaire : Titre foncier · Plan de bornage* line shows under the fieldset (*Aucun document déclaré* when the seller declared none). Once sent, the documents are read-only with the other answers; a section with none still shows the seller's declaration, the fieldset hiding around that line. Samples: `?id=702` (DRAFT from mandate 302, 800 m², its two documents checked), `?id=703` (sent, documents checked), `?id=704` (sent, none recorded by the agent but declared by the seller), `?id=705` (DRAFT, the seller declared none). |
| `assets/js/find-property.js`, `offer-property.js`, `partner-central.js` | #274's logic: the branch's own step list (six for a building sale), the counts' fields and messages, the listing's sections and rules, the building samples (305, 306, 701) and their matches. |

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

Land is residential and sold or bought, never let (#275). `offer-property.html?sample=rental-land` opens the
*Caractéristiques* step of a rental posted with *Terrain*: the type isn't offered under either use, no choice is
selected, and *Suivant* answers *Choisissez le type de bien.* `offer-property.html?type=SALE` walks to the same step
with *Terrain* under *Résidentiel* only, whose description names "terrain à bâtir…" where the rental's names none; the
#273 land samples (`?sample=land`, `land-no-area`, `land-review`, `land-review-none`) are unchanged — documents,
surface and rate. `find-property.html?type=RENTAL` offers no *Terrain* on step 3 under either use, *Indifférent*
included as always, against `?type=SALE`, which offers it under *Résidentiel* only. On a phone the choice grid stays
one column (a plot's answer is one row less on a rental; the sale's cards read the same as before).

A stored rental *Terrain* stays readable: `mandate/edit.html?id=208` (a renter's search) and `?id=209` (a landlord's
property) show *Terrain* checked and enabled under *Résidentiel* (the type list reads one type per row, at every width),
the group's summary reading *Terrain*; *Enregistrer*
unchanged keeps it, and picking another type makes it disappear for good. `?id=201` is the ordinary rental (no
*Terrain* under either use), `?id=104` the sale (*Terrain* under *Résidentiel*). `mandate.html?id=208` and `?id=209` read the
same saved answer (*Type de propriété : Terrain*, loyer, surface), as before the rule. The `?sample=` shortcuts seed
the session once, like every other one: reloading one after changing its answers keeps the change. The empty and
loading states don't apply to this change, and there is no new screen.

A building sold as a whole (#274) is captured by two counts, its étages and its units, instead of its rooms, its
parking and its amenities. The buyer's wizard: `find-property.html?sample=building` opens *Décrivez le bien idéal* on a
sale of *Immeuble* with *Nombre d'étages* and *Nombre d'unités* to fill — *Suivant* is inactive, click it for
*Indiquez le nombre d'étages, ou cochez Indifférent.* (and the same for units; try `0`, `31`, `501` and letters for
*Indiquez un nombre entier, de 1 à 30* / *… à 500*) — six steps read *Étape 3 sur 6* here and *Étape 6 sur 6* on
`?sample=building-review`, which lists *Étages* 3 and *Unités* 12 and no *Indispensable* row; `?sample=building-any`
and `?sample=building-review-any` are the two *Indifférent* states (review rows read *Indifférent*), and
`?sample=rental-building` keeps seven steps, its parking question and its amenities line. The seller's wizard:
`offer-property.html?sample=building` opens *Caractéristiques* with *Immeuble entier* under *Commercial* only, no
*Parking ou garage*, *Étages* and *Unités* empty (*Suivant* answers for them), *Étape 4 sur 6*; `?sample=building-review`
is its review (*Étape 6 sur 6*, *Immeuble entier · 3 étages · 12 unités · 850 m²*, no *Équipements*); `?type=RENTAL`
and the #273 land samples are unchanged. The agent's screens: `mandate.html?id=305` (a seller's building, criteria
*Étages* 3 / *Unités* 12, no amenity row), `?id=306` (a buyer's building search: its three matches read
"Immeuble · 12 unités · 3 étages", "Immeuble · 20 unités au lieu de 10", "Immeuble · Nombre d'unités non précisé" —
never rooms or amenities), `?id=301` and `?id=201` unchanged; `mandate/edit.html?id=305` prefills the two counts and
refuses empty, 0, 31, 501 and letters; `?id=104` and `?id=201` are unchanged. The listing: `listing.html?id=701` (six
rows, no *Eau, électricité, accès*, progress "sur 5 vérifiées"), `listing/bien.html?id=701` (*Étages* 3 and *Unités*
12 prefilled, neither *Salles de bains* nor *Places de parking*, both required), `listing/equipements.html?id=701`
returns to the checklist; `listing.html?id=501` (the rental villa) is unchanged. *Nombre d'étages* and *Nombre
d'unités* never share a row: they stack at every width (390 px and 1024 px alike), and the six-step header keeps its
count and project pill on one line. Assumptions: 305 is *Assigné* so that its edit page opens (a signed mandate is no
longer editable, as 301/302 are); `?id=701` seeds its DRAFT on the first visit and the session then keeps it (its
edits survive a reload); the
buyer's *Indifférent* is a checkbox that disables its count (stored as null); the building's checklist has six rows
because its utilities aren't checked; a building's surface stays optional in the buyer's search and required in the
seller's *Caractéristiques*, as it is today for their types; the seller wizard has no "N réponses" summary line — the
counts are in its review rows and in the mandate edit's group summary. Two points settled with the requester: the count
reads *étages* everywhere (its field stays `levels`), and a building's *Le bien* asks neither *Salles de bains* nor
*Places de parking* — it is counted by its units. Two points settled with the requester: the count
reads *étages* everywhere (its field stays `levels`), and a building's *Le bien* asks neither *Salles de bains* nor
*Places de parking* — it is counted by its units.

A land sale's *Le bien* (#287): `listing/bien.html?id=702` is the DRAFT of seller 302 — *Terrain*, *Résidentiel*, 800 m²,
no *Places de parking* (nor *Salles de bains*, *Chambres*, *Salons*, *Cuisines*, *Ameublement*, *Étages*, *Unités*,
*Étage*), and *Document de propriété (facultatif)* with *Titre foncier* and *Plan de bornage* checked, one option per
line (three rows at 390 px as at 1024 px), no owner line (the agent's answer is the seller's). *Terrain* is
*Résidentiel* and is offered to a sale only, as #275 settled: a rental's *Le bien* never shows it (a legacy rental
*Terrain* is hidden too). Its checklist row is
`listing.html?id=702` (*Terrain · 800 m²*, seven rows, nothing checked). Switching the type to *Maison* (or *Bureau*)
hides the documents and brings *Places de parking* back; switching back to *Terrain* shows them again with the agent's
ticks, and saving another type stores none (a hidden fieldset is saved empty, as for every question of the type).
`listing/bien.html?id=703` is a sent land listing (mandate 307): its two documents are checked and disabled with the
other answers, the hint gone, no action. `?id=704` is a sent listing whose agent recorded none of the seller's two
documents: the fieldset is hidden and only *Indiqué par le propriétaire : Titre foncier · Plan de bornage* remains,
outside anything hidden, under the surface — the seller's declaration survives its own hidden fieldset.
`?id=705` is a DRAFT from seller 309, who declared no document: nothing checked, no owner line; tick one and it reads
*Indiqué par le propriétaire : Aucun document déclaré*. The save error is the page's existing one (the surface's, or a
number's): the documents are optional, so they never raise one, and the save flow is unchanged. The empty and loading
states: 705 is the empty answer, the page has no loading state, and there is no new screen. Assumptions: each sample
listing has its own mandate (a mandate has one listing at most) — 307, 308 and 309 are land sellers with no card on
`index.html`, opened by URL; the sent samples also carry their audited place, GPS and five photos so their checklist
reads as sent; existing listings aren't seeded with their mandate's documents, and a legacy parking count on a land
listing is dropped the next time its section is saved. The narrow check: at 390 px the three options stack one per
line, the hint and the owner line wrap under the fieldset; at 1024 px they still take one line each.
One field per row and one option per row (review feedback): *Usage*, *Type de bien*, *Ameublement*, every count
(*Chambres*, *Salons*, *Salles de bains*, *Cuisines*, *Places de parking*, *Étages*, *Unités*, *Étage*), *Surface* and
*Document de propriété* each take the full width, and *Résidentiel* / *Commercial*, each type, *Non meublé* / *Meublé*
and the three documents each sit alone on their row — the count grid is single-column at every width (`.count-grid`),
and no fieldset of the page uses `choices__grid--2` (other pages keep it, `rejoindre.css` is unchanged).

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
