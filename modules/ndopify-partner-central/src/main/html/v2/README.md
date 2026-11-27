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
| `find-property.html` (*Trouver un bien*) | Step 1 asks *Vous souhaitez : Louer / Acheter* (`listingType`, required, preselected from `?type=`). For a purchase: no *Chambre*, *Terrain* offered under both *Résidentiel* and *Commercial* (no rooms asked; its surface required), no furniture question, *Budget total* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), *Quand souhaitez-vous acheter ?* (same `MovingPeriod` values), the financing (*Comptant* / *Prêt bancaire* / *Transfert depuis l'étranger* / *Pas encore décidé*, required, review *Financement*; a rental sends none) and, for a *Terrain* purchase only, *Quels documents exigez-vous ?* (three documents, optional: *Titre foncier* / *Plan de bornage* / *Certificat de propriété*, review *Documents exigés*) and, as the budget is typed, the implied price per m² (display only, nothing stored); review *Achat, usage …* / *Achat prévu*. The sale reassurance names a verified agent of the area, not a sale specialist. |
| `offer-property.html` (*Proposer un bien*) | Step 1 asks *Mettre en location / Vendre*, preselected from `?type=`. For a sale: *Prix de vente souhaité* in FCFA (periodicity `NONE`, at least 1 000 000 FCFA), no advance, deposit or furniture (sent as null), no *Chambre*, *Terrain* under both *Résidentiel* and *Commercial*; *Disponible à partir du* kept. A sale of a *Terrain* asks its *Document de propriété* (*Titre foncier* / *Plan de bornage* / *Certificat de propriété*, optional and several, review *Document de propriété*; sent only for a SALE · LAND, none is a valid answer). A sale of a *Terrain* also shows, as its surface is typed, the implied price per m² (asking price ÷ surface, rounded to the nearest 100 FCFA, display only, never stored). Rental/sale wordings are `data-for="RENTAL"` / `data-for="SALE"` pairs. *Quitter* and the confirmation go to the public site's `/owner/sell` for a sale, `/owner` for a rental. |
| `offer-property.html` (*Proposer un bien*, #273) | The sale's *Caractéristiques* step no longer blocks a *Terrain* nor loses its documents. #304 removed the old preview: no follow-up question — rooms, furniture, parking/places, surface, a building's counts — shows until a type is chosen, and *Document de propriété* is shown for every sale from the step's first render; a chosen *Terrain* asks its surface and keeps the documents, another type asks its own questions and the documents stay. A sold *Terrain* labels the surface *Surface du terrain estimée* and, once a surface is typed, shows *Soit ≈ 56,3K FCFA/m² pour 800 m²* (asking price ÷ surface, rounded to the nearest 100, display only). Its review's *Conditions* reads the total price short (*45M FCFA*, no " par mois"), then — for a land sale only — the same rate line. The rate reads short like every displayed amount (#292), with the tenant's currency in production (FCFA in this prototype); the surface stays a grouped count. Without its surface, a land sale's step says *Indiquez la surface du terrain.* Land stays sale-only here: the rental offering is unchanged. The project pill next to the step count and under the confirmation's title belongs to #278 (see its row). |
| `find-property.html`, `offer-property.html`, `mandate/edit.html` (#275) | Land is sold or bought, never let: both wizards offer *Terrain* under neither use when *Louer* / *Mettre en location* is chosen (a posted `propertyType=LAND` is refused — *Choisissez le type de bien.* on *Proposer un bien*, and the type step never completes with it on *Trouver un bien*), and offer it under both *Résidentiel* and *Commercial* for *Acheter* / *Vendre* (#293). Only the rental's *Commercial* description drops "terrain…" (`data-for` pair, like the *Résidentiel* one); the sale's residential one names "terrain à bâtir…" and its commercial one no longer names it. A rental mandate whose stored type is *Terrain* keeps it readable: `mandate/edit.html` shows it checked and enabled in its saved *Résidentiel* category (208 a renter's search, 209 a landlord's property), saves it unchanged, and stops offering it once another type is picked; a sale mandate offers it under both usages (104), under whichever one was picked (#293). The sale's documents, surface and rate are untouched. #290 takes the same rule to a listing's *Le bien*: a rental never shows *Terrain* (a legacy rental *Terrain* included), a sale shows it under *Résidentiel* and *Commercial* (#293) with no rooms, living rooms, bathrooms, kitchens, parkings, levels nor floor level; the stored usage stays the one the user picked (no row is realigned any more: a stored `COMMERCIAL` land, from a new answer or an import, stays commercial). |
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
| `find-property.html` | A building purchased as a whole is captured by two counts (#274): the *bien* step asks *Nombre d'étages* (1–30) and *Nombre d'unités* (1–500), both required, each with an *Indifférent* checkbox (stored as null), and shows no *Places de parking*; the amenities step leaves the wizard, so that branch reads *Étape X sur 6* (progress track, the steps' legends, *Précédent*/Suivant and `?etape=` follow it) and the review reads the counts in the *Bien recherché* line (*Immeuble · 3 étages · 12 unités · 850 m² minimum*; *étages indifférents · unités indifférentes* when blank), with no amenity line. A rental building keeps the seven steps and its questions. `?sample=` seeds: `building`, `building-any`, `building-review`, `building-review-any`, `rental-building`. |
| `offer-property.html` | A building sale (#274): *Immeuble entier* is offered under *Commercial* only for a sale — and for no rental at all since #304, which made the type sale-only in the shared rule table (`util.js`); the *Caractéristiques* step asks no *Parking ou garage* nor *Combien de places ?* and requires *Étages* and *Unités* (as the buyer's counts, without *Indifférent*); the amenities step leaves the wizard (six steps, same progress and `?etape=` handling) and the review reads both counts in its *Caractéristiques* line, with no *Équipements* line. `?sample=building` opens *Caractéristiques* of that branch, `?sample=building-review` its review. |
| `mandate.html` | A building sale (#274): 305 is a seller's building of 3 étages and 12 units (250M, Bonapriso, *Commercial* · *Immeuble*): its criteria read *Étages* 3 and *Unités* 12 among the visible ones, with no amenity, parking or bedroom row; 306 is a buyer's search for a building of 10 units and 3 étages (250M), whose matches read "12 unités · 3 étages" (and "20 unités au lieu de 10", "Nombre d'unités non précisé") with *Pourquoi ce bien ?* worded in units and étages only. 305 is Assigné, so its edit page is reachable. |
| `mandate/edit.html` | A building sale (#274): the *Le bien* group shows *Étages* (1–30) and *Unités* (1–500), prefilled from the mandate, and hides *Chambres*, *Places de parking* and the whole *Équipements* group; the group's summary counts them ("Immeuble · 3 étages · 12 unités · 850 m²"), and both fields refuse empty, 0, out-of-bound and non-numeric values. Other types are unchanged. |
| `listing.html`, `listing/bien.html` | A building's listing (#274): `?id=701` seeds a DRAFT for mandate 305 (Immeuble, 850 m², 3 étages, 12 units, no bathrooms nor parking); its checklist has six rows (no *Eau, électricité, accès*, not required to send it) and *Le bien* keeps the listing's usage: *Immeuble entier* is offered under *Commercial* for a sale — the category a sale stores — and under *Résidentiel* for a building stored there (a legacy row), while a rental building offers it under *Résidentiel* only; *Étages* and *Unités*, both required, with neither *Salles de bains* nor *Places de parking*; `listing/equipements.html?id=701` goes back to the checklist. Other types keep the seven sections. |
| `listing/bien.html`, `assets/js/partner-central.js` (#287) | *Le bien* of a sale listing for a plot of land: no *Places de parking* (an office, a retail or an industrial listing keeps it; a land mandate stores no parking), and a new *Document de propriété (facultatif)* fieldset under the surface — *Titre foncier*, *Plan de bornage*, *Certificat de propriété*, one option per line at every width, none required, several selectable, with the hint that the agent checks them at the audit. Shown for a SALE · LAND only, hidden for another type and for a rental (a hidden answer is saved empty), and pre-filled from the seller's declaration when the listing is created. Layout: every field takes a full row (*Usage*, *Type de bien*, *Ameublement*, each count, *Surface*, *Document de propriété*) and every option of every fieldset sits alone on its row — no two-column count grid and no `choices__grid--2` on this page, at 390 px as at 1024 px. If the agent's answer differs from the seller's, the standard *Indiqué par le propriétaire : Titre foncier · Plan de bornage* line shows under the fieldset (*Aucun document déclaré* when the seller declared none). Once sent, the documents are read-only with the other answers; a section with none still shows the seller's declaration, the fieldset hiding around that line. Samples: `?id=702` (DRAFT from mandate 302, 800 m², its two documents checked), `?id=703` (sent, documents checked), `?id=704` (sent, none recorded by the agent but declared by the seller), `?id=705` (DRAFT, the seller declared none). |
| `assets/js/find-property.js`, `offer-property.js`, `partner-central.js` | #274's logic: the branch's own step list (six for a building sale), the counts' fields and messages, the listing's sections and rules, the building samples (305, 306, 701) and their matches. |
| `assets/js/util.js`, `offer-property.js`, `find-property.js`, `partner-central.js` (#304) | One type-rule table, `window.NdUtil.propertyRules` in `util.js`, now read by the three page scripts instead of the four copies that disagreed (`WITHOUT_ROOMS`, `WITHOUT_FURNITURE`, `WITHOUT_PARKING(S)`, the category maps): per type, its categories for a rental and for a sale, and the questions it asks. *Immeuble* and *Terrain* are sold only; a residential type asks its rooms (not a *Studio*, not a *Chambre*), its parkings (a *Chambre* included), its furniture on both projects, its documents on a sale, and no surface; a commercial type asks its surface and, on a sale, its documents; a sold building asks its *Étages* and *Unités*. The documents are the same three (`DOCUMENTS`); the rate maths (price ÷ surface, price ÷ units, rounded to the nearest 100) stays in the page scripts, mirrored from the same rules. |
| `offer-property.html` (#304) | The sale's *Document de propriété* is asked whatever the type (optional, several or none; *Aucun document déclaré* on the review), and a rental never shows nor sends it. The *Caractéristiques* step follows the one rule table: no follow-up question shows until a type is chosen (the *Document de propriété* excepted, for a sale); a residential sale asks its *Ameublement* (new on a sale) and no *Surface estimée* any more; a *Chambre* asks *Parking ou garage* and *Combien de places ?*; *Immeuble entier* is sold only; a sold *Terrain* keeps its *Surface du terrain estimée* and its rate per m², a sold *Immeuble* shows its price per unit under *Unités* (*Soit ≈ 20,83M FCFA/unité pour 12 unités*), and the review repeats the rate after the price. *Prix de vente souhaité* and *Surface estimée* are grouped as typed. |
| `find-property.html` (#304) | Every purchase asks *Quels documents exigez-vous ?* whatever the type (optional, several or none; *Aucun document exigé* on the review), never a rental; *Ameublement* is asked on a residential purchase (the buyer keeps *Indifférent*); *Places de parking* for every residential type, *Chambre* included, and none for a commercial type, a building or a plot; a residential non-land property asks no *Surface minimale*; *Immeuble* and *Terrain* are bought only. The *budget* step shows the price per m² of a *Terrain* and, new, the price per unit of an *Immeuble* (*Soit ≈ 20,83M FCFA/unité pour 12 unités*, hidden while *Indifférent*), and the review repeats the rate under the *Budget total*. The *Budget total* and the surface are grouped as typed. |
| `mandate/edit.html` (#304) | The rules of the two wizards: a residential non-land mandate asks no *Surface* (sale included), furniture on a sale (*Indifférent* only for a client), *Places de parking* for a residential type (*Chambre* included) and none for a commercial type, a building or a plot, a sold building its *Étages* / *Unités* and no rooms, parkings nor amenities, *Immeuble* sold only. A sale gets a *Document de propriété* (LISTING) / *Documents exigés* (SEARCH) fieldset, pre-filled, optional and saved on the mandate's own list; it also shows its price per m² (*Terrain*) or per unit (*Immeuble*) under the price field (*Prix* / *Budget*). *Prix* and *Surface* are grouped as typed and re-shown. A stored rental *Immeuble* keeps its type and its rental questions (*Surface* and *Ameublement*); every question the page doesn't ask keeps the mandate's stored answer (201 keeps its 150 m²). |
| `mandate.html`, `mandate/document-new.html`, `mandate/document.html`, `assets/js/mandate-documents.js`, `assets/css/documents.css`, `assets/images/doc-sample-*.svg`, `assets/js/partner-central.js` (#329) | A mandate's **documents**, on LISTING and SEARCH mandates alike. The mandate page gets a *Documents* section before *Sa recherche*: one row per document, newest first, the whole row a link to its page — the type, the agent's title under it only when it differs from the type's name, the status pill (*Reçu* `UPLOADED`, *En cours d'analyse* `EXTRACTING`, *Analysé* `EXTRACTED`, *À corriger* `FAILED` — no *Expiré* status: an expired piece keeps its status and takes #331's *Expirée* marker) and *Ajouté le 25/11/2026 · 2 pages* (tenant date format with the year); *Ajouter un document* beside the title, in the contact block's WhatsApp button style (`btn--secondary btn--compact`), the same in the empty and the list states. States: empty (with a one-line hint of what to add for that mandate: identity and ownership for a seller / landlord, identity and financing for a buyer / client), unavailable (the rest of the page unchanged), a flash after an add or a delete, read-only on a closed or cancelled mandate (no add). *Ajouter un document* is its own page: the type (a native select, the mandate's suggested types first, then *Autres types*), an optional *Titre* (placeholder the type's name, 100 at most) and the pages (one native multiple input without JS; with JS *Prendre une photo* / *Choisir des fichiers*, then the chosen files listed in order with their name and size read in the browser — *Page 1 · IMG_2041.jpg · 1,8 Mo*, each removable, no thumbnail —, shrink to 2000 px and *Envoi de la page 2 sur 3…*). The document page shows **no image** (no preview, thumbnail nor size): its type, title, status and one upload date (*Ajouté le 25/11/2026*), then its pages in order as plain rows (*Page 1*, *Page 2*…), each with *Voir* (a new tab; production's href is the page's presigned URL, here a sample asset) and its own folded *Remplacer* form, then *Renommer* (field errors, the version-conflict message) and *Supprimer le document* (`?delete=1` confirmation without JS, in place with it). New sample mandate **311**: a buyer's search cancelled after its approval, for the read-only states. |
| `mandate/document.html`, `mandate.html`, `assets/js/mandate-documents.js`, `assets/css/documents.css` (#331) | What was read on a document, **on its page and before its pages**. `EXTRACTED` with `fields`: one row per value, in the server's order, its French label by key (`FIELD_LABELS`, production's message bundle; an unknown key reads *Autre donnée*) and its value by kind — a date in the tenant's dateFormat with the year (`14/06/2019`), a pay month as *Octobre 2026*, the short amount of #292 with the document's currency (*350K FCFA*), *•••• 0123*, a grouped surface (*500 m²*), *Solde* / *Attesté* for `amountKind`; `currency` is never a row, no confidence, no JSON and nothing of the pages. A value the page can't read shows as sent, a null or blank one has no row. `EXTRACTED` without fields (an older payload) reads *Aucune donnée n'a été lue sur ce document.* with its *Analysé* status; `UPLOADED` / `EXTRACTING` show a hint that the reading happens on its own. `FAILED` shows one instruction per problem code, in the server's order (wrong type, unreadable document, missing page, doubtful required field, unreadable expiry date, failed extraction; an unknown code reads a generic instruction) and no field row — `DOCUMENT_EXPIRED` is a warning, never an instruction. A piece whose `expiresAt` is before today (tenant clock and zone) gets the notice *Pièce expirée le …*, saying whether it was already expired when it was read (the server's `DOCUMENT_EXPIRED` warning) or has expired since, and the way out (*remplacez ses pages par celles de la nouvelle pièce, ou supprimez-la et ajoutez la nouvelle*); today or later, or no `expiresAt`, no notice. The mandate page's *Documents* section adds the *Expirée* pill beside an expired document's status, and nothing else of the reading. All of it is server-rendered (no JavaScript needed); on a `CLOSED` / `CANCELLED` mandate the reading stays readable with no action of its own (*Voir* only), and its expiry notice keeps the piece consultable instead of naming the way out. The stale *Expiré* status of the dropped #332 is gone (an expired piece is `EXTRACTED` + a warning, or expired since). | A mandate's **documents**, on LISTING and SEARCH mandates alike. The mandate page gets a *Documents* section before *Sa recherche*: one row per document, newest first, the whole row a link to its page — the type, the agent's title under it only when it differs from the type's name, the status pill (*Reçu* `UPLOADED`, *En cours d'analyse* `EXTRACTING`, *Analysé* `EXTRACTED`, *À corriger* `FAILED` — no *Expiré* status: an expired piece keeps its status and takes #331's *Expirée* marker) and *Ajouté le 25/11/2026 · 2 pages* (tenant date format with the year); *Ajouter un document* beside the title, in the contact block's WhatsApp button style (`btn--secondary btn--compact`), the same in the empty and the list states. States: empty (with a one-line hint of what to add for that mandate: identity and ownership for a seller / landlord, identity and financing for a buyer / client), unavailable (the rest of the page unchanged), a flash after an add or a delete, read-only on a closed or cancelled mandate (no add). *Ajouter un document* is its own page: the type (a native select, the mandate's suggested types first, then *Autres types*), an optional *Titre* (placeholder the type's name, 100 at most) and the pages (one native multiple input without JS; with JS *Prendre une photo* / *Choisir des fichiers*, then the chosen files listed in order with their name and size read in the browser — *Page 1 · IMG_2041.jpg · 1,8 Mo*, each removable, no thumbnail —, shrink to 2000 px and *Envoi de la page 2 sur 3…*). The document page shows **no image** (no preview, thumbnail nor size): its type, title, status and one upload date (*Ajouté le 25/11/2026*), then its pages in order as plain rows (*Page 1*, *Page 2*…), each with *Voir* (a new tab; production's href is the page's presigned URL, here a sample asset) and its own folded *Remplacer* form, then *Renommer* (field errors, the version-conflict message) and *Supprimer le document* (`?delete=1` confirmation without JS, in place with it). New sample mandate **311**: a buyer's search cancelled after its approval, for the read-only states. |
| `mandate/document-new.html`, `mandate/document.html`, `assets/js/mandate-documents.js` (#366) | PDFs next to photos. The add form's hint names them (*Une photo par page, dans l'ordre (recto, puis verso). JPEG, PNG, WEBP ou PDF, 5 Mo par fichier, 10 pages au plus. Les pages d'un PDF sont ajoutées à sa place, dans son ordre.*), the native multiple input and the JS picker accept `application/pdf` (*Choisir des fichiers* — *Prendre une photo* keeps images only), and the chosen list shows a PDF by its name and size under *PDF* (no thumbnail) while images keep *Page N*; with a PDF the count line counts files and says its pages count at sending, since the browser can't know them. Submitting with a PDF posts the whole multipart form after the browser shrinks each image (*Envoi du document…*, the bar indeterminate), never presign → PUT → create; images alone keep their direct upload (*Envoi de la page 2 sur 3…*). A PDF over 5 Mo is refused in the browser before any call; ndopify-server's refusals (over 5 Mo, password-protected, empty or damaged, more than 10 pages) come back as the pages field's error, the type and title kept and the pages to choose again: `?errors=pdf-size`, `pdf-protected`, `pdf-unreadable`, `pdf-pages` (`?errors=file` now shows a Word file). The per-page *Remplacer* takes a photo or a one-page PDF (`?replaceError=pdf-size`, `pdf-protected`, `pdf-unreadable`, `pdf-pages&page=2`: the row's box open with the French message, the page unchanged); a PDF posts its form, an image keeps shrinking and attaching. Without JavaScript every input posts the form. A page rendered from a PDF is an ordinary *Page N* of the document page (*Voir* only). |
| `mandate.html`, `mandate/document-new.html`, `mandate/document.html`, `assets/js/mandate-documents.js`, `assets/css/documents.css` (#369) | The mandate's document limits. The Documents section's head counts the stored documents against the cap as a compact *5/5* right after the title — its visually hidden label reads *5 documents sur 5* — all types together, on a LISTING and a SEARCH alike; the compact count keeps *Ajouter un document* on the heading's line at 390 px (if space runs out, the count wraps under the title, never the action). At 5 it hides *Ajouter un document* and shows *Un mandat peut avoir 5 documents au plus.* in its place (the count stays). A read-only mandate keeps its hint and no action; an unavailable list keeps the action and shows no count. The add page at 5 shows the cap notice and a way back instead of the form — the same page, nothing posts — and its hint names the caps (*5 documents par mandat, 10 pages au plus, soit des photos soit un PDF*), with #366's photo order, file types, *5 Mo par fichier* and PDF-pages sentence. With JS a list mixing photos with a PDF (or two PDFs) is refused before sending; without JS the server's 400 is the pages field's error (`?errors=mixed`), and `?errors=pages` (over 10 pages once the PDF is expanded) and `?errors=limit` (the 6th document, the form kept with its type and title) are the other refusals. A document's page names its kind — *PDF* / *Photos*, *Photos ou PDF* for a grandfathered document — and its *Remplacer* takes that kind only (a photo for an image document, a one-page PDF for a PDF document), the hint saying so and `?replaceError=mixed&page=2` for the wrong kind. Samples: 301 and 303 are at 5, 302 and 201 below; 3103 is a PDF document, 3102 an image one. |
| `mandate/document-preview.html`, `mandate/document.html`, `assets/js/mandate-documents.js`, `assets/css/documents.css`, `assets/css/partner-central.css` (#372) | A page of a document opens as a **page of the app**, in the same tab: *Voir* no longer has `target="_blank"` nor `rel`, its hidden hint reads only *la page N*, and its href becomes the new page route `/mandates/{id}/documents/{documentId}/pages/{pageId}` (production's *Voir* href was the app's JPEG route `/pages/{pageId}/preview`, in a new tab; that route stays the preview page's image source). The viewer is one compact bar on the overlay: its whole left side is a single link to the document page at `#pages-title` — the decorative arrow and the document's title, its visually hidden suffix reading *— retour au document* — with *Page N sur M* at its right, on ONE line at every width (the title truncates with an ellipsis, the rank never does); the page's image sits below, centred and never larger than the screen. No site header, account menu, footer nor nav, and no JavaScript is needed for the page, the link or the image (the viewport keeps its default scaling, so pinch-zoom still works). The image is the app's authenticated `…/pages/{pageId}/preview` route, the only source in the HTML: no storage URL, page path nor presigned URL. An image that fails (a 502, a signed-out session) leaves the bar and its way back in place; a 404 is production's error page. The viewer follows the layout language of the production listing's full-screen `viewer__*` (dark overlay, image centred) without a `<dialog>`, and `partner-central.css` gains production's `--color-overlay` / `--color-on-overlay` tokens. Samples: the land title (2 pages), a single-page plan, a read-only cancelled mandate. |
| `mandate.html`, `assets/js/mandate-ownership.js`, `assets/css/ownership.css` (#298) | A seller's / landlord's **owner verification**, on a LISTING mandate only, in a *Vérification du propriétaire* section before the Documents section (same block, after the day's work and before *Sa recherche*; the prototype has no *Termes du contrat* section to follow). The shipped verdict (#297, `ndopify-dto` 0.0.112) is a status and its reasons only — a reason is a code plus, for `*_UNUSABLE`, the document's `problemCodes`; it carries **no evidence date and no document type nor title**, so the section shows neither. Its status pill and one block per state, server-rendered: `PENDING` — *Vérification du propriétaire en cours*, info styling, one line per missing piece with what counts (*une carte nationale d'identité (recto et verso) ou un passeport* / *un titre foncier ou un certificat de propriété*), and *Ajouter ces pièces dans Documents* (`#documents-title`); with no reason (a piece `UPLOADED` / `EXTRACTING`) the reading is in progress. `VERIFIED` — *Propriétaire vérifié*, the hint that a verified owner is what allows publication (#299) and *Voir l'annonce* only when the mandate has a listing (no dead link, no date). `FAILED` — *Propriétaire non vérifié*, warning styling, one block per reason code: the check's kind (*La pièce d'identité* for `OWNERSHIP_ID_*`, *Le document de propriété* for `OWNERSHIP_DOCUMENT_*`, never a type nor an agent title), its French wording, its action (*reprenez la photo* / *changez son type* / *ajoutez un titre*), and for `*_UNUSABLE` the failed document's problem codes worded as the document page words them (#331); no manual review, and no document id, page, path, URL, extracted value, type nor agent title anywhere in the section (the Documents rows below name each piece's type, title and status). `UNAVAILABLE` — a failed read or `UNKNOWN` only makes this section say *Vérification indisponible*. Absent on a SEARCH mandate, on a CLOSED / CANCELLED one, and (production) no ownership request is made for them. |

| `listing.html`, `assets/js/partner-central.js`, `assets/js/ownership-copy.js`, `assets/js/mandate-ownership.js`, `assets/css/listing.css` (#375) | A `VERIFIED` listing whose mandate's owner isn't verified explains the wait and links to the mandate. The tag reads *Vérifiée — publication en attente* (warning; the pill wraps inside the page on a phone) and the *En vérification* panel gives way to the owner verification: `PENDING` — *Publication en attente de la vérification du propriétaire*, one line per missing piece (#298's words and what counts), or with no reason the reading in progress, no error styling; `FAILED` — *Propriétaire non vérifié*, one block per reason (the check's kind, its wording, its action, and for `*_UNUSABLE` the document's problem instructions as the document page words them), no manual review; the verdict read failing (5xx, network, 403/404) or `UNKNOWN` — *Vérification du propriétaire indisponible*, every other section unchanged. One action, *Vérifier le propriétaire dans le mandat*, opens `/mandates/{id}#ownership-title`, immediately before the mandate's Documents; nothing to publish or retry here, and the public page is untouched. Every other status, and the read-only lens of another agent, render as today and make no ownership call. The wording is #298's, shared through `assets/js/ownership-copy.js` (production: `MandateOwnershipViews`, read by `ListingViews`), so the two pages can't drift; `LISTING_TAGS` gains `VERIFIED` (*Vérifiée*) and the hold label, while the mandate's line keeps the plain *Vérifiée* (decision 6). |
| `mandate.html`, `assets/js/mandate-ownership.js` (#377) | A `VERIFIED` owner verdict **overridden by Ndopify** is a state of its own, never worded as a document check. The status pill reads *Vérifiée par Ndopify* (success) and the intro loses its *à partir des pièces de la section Documents* clause; the success block reads *Propriétaire vérifié*, then *Ndopify a vérifié le propriétaire après examen de sa situation.* — never that the documents passed — and keeps #298's publication hint (*Un propriétaire vérifié est ce qui permet de publier l'annonce.*) and *Voir l'annonce* when the mandate has a listing (same read, same no-dead-link rule). It shows no date, no reason, no admin identity and no admin reason (the verdict carries none of them). The non-overridden `VERIFIED` state reads exactly as today, every other state is untouched, and a SEARCH, `CLOSED` or `CANCELLED` mandate still has no section; every state stays server-rendered. |
| `mandate.html`, `assets/js/mandate-ownership.js`, `assets/js/ownership-copy.js`, `assets/css/ownership.css` (#376) | The owner verdict's **city comparison**. The verdict gains `location` — the mandate's city, the title's locality, and `UNKNOWN` / `MATCH` / `MISMATCH` / `UNRESOLVED`. `UNKNOWN` and `MATCH` show nothing. `UNRESOLVED` is a **neutral hint** under the state's block, whatever the status (`PENDING`, `VERIFIED`, or `FAILED` for another check): the two places side by side (*Ville du mandat* / *Localité du titre*), *La localité du titre n'a pas pu être rattachée à une ville connue : vérifiez que le titre et le mandat désignent le même bien.* and *Vérifier le titre dans Documents* (`#documents-title`) — info styling, no status change, no blocking; a blank place reads *non précisée* and both blank hides the hint. `MISMATCH` is a `FAILED` `OWNERSHIP_PROPERTY_MISMATCH` reason: *Le document de propriété*, the same two places, *Le document désigne un bien dans une autre ville que celle du mandat : il ne justifie pas la propriété de ce bien.* and *Vérifiez le document dans Documents : il doit désigner un bien situé dans la ville du mandat.* — no document id, type, title, page, path, storage URL nor other extracted value, and the failed block's *Corriger dans les documents* stays. |

To review #329 (a mandate's documents), with the session fresh (or `&docs=reset` once): `mandate.html?id=301` (a
seller: *Titre foncier n° 4521 — Bonapriso* *Analysé*, *Facture d'eau — octobre 2026* *En cours d'analyse*, *Carte
nationale d'identité* *Analysé* + *Expirée* — no second line, its title being the type's name —, *Procuration — vente
du lot 12* *Reçu*, *Plan de bornage* *À corriger*) and `mandate.html?id=303` (a buyer: *Attestation de solde — Afriland
First Bank*, *Procuration — achat de l'appartement*, *Bulletin de paie — octobre 2026*, *Carte nationale d'identité*
*Analysé*, *Accord de principe — prêt immobilier* *Analysé* + *Expirée* — both lists are at #369's cap of 5, so their
*Ajouter un document* is replaced by the cap notice); the empty state on `mandate.html?id=302`
(a seller: identity and ownership hint) and `?id=201` (a renter: identity and income hint), or any of them with
`&docs=none`; `&docs=error` for *Documents indisponibles*; `&documentOutcome=added&documentTitle=Titre%20foncier`,
`deleted` and `changed` for the flashes; `mandate.html?id=311` the read-only list of a cancelled search (no *Ajouter*).
The add form — 301 and 303 reached their cap in #369, so it is reviewed below the cap:
`mandate/document-new.html?id=302` (seller's suggestions first) and `?id=201` (a client's), then
`&errors=missing` (no type, no page, the title kept), `&errors=file` (a Word file), `&errors=size` (over 5 Mo) and
`&errors=server`, each keeping the type and title; `?id=311` is the locked view. Add a document for real (pick two or
three images, remove one, submit): each chosen file is a row *Page N · name · size* (no thumbnail), then the progress,
then the mandate page with the flash and the new row first. The document page — no image anywhere, one *Ajouté le …*
date, no size, a plain row per page with *Voir* (the preview page, in the same tab since #372) and *Remplacer*: `mandate/document.html?id=301&document=3103`
(two pages), `&document=3102` (an identity card, recto and verso), `&document=3101` (one page); `&outcome=renamed`,
`&outcome=replaced&page=2`, `&errors=blank`, `&errors=long`, `&conflict=1`, `&document=3102&replaceError=type&page=2`
(also `size`, `empty`), `&delete=1` (the confirmation as without JS); `mandate/document.html?id=311&document=3113` read-only (*Voir*
only: no *Remplacer*, *Renommer* nor *Supprimer*). Check every page at 390 px: no horizontal scroll, a long type
(*Facture d'eau ou d'électricité*, *Accord de principe bancaire*) wraps inside its row with the pill kept on the first
line, a long file name wraps beside its remove button, and the dates keep their year. Assumptions: the section sits
after *Biens correspondants* and before *Sa recherche* (the prototype has no *Termes du contrat* section to follow);
dates read `25/11/2026`, the tenant format the story names, where the rest of this prototype still writes *25 novembre
2026*; a 404 / 403 on a document is shown here as a "not available" view, production redirects to the mandate page with
the *changed* flash; the add CTA stays when the list is unavailable (adding doesn't need the list); a page can't be
reordered on the add form (remove it and add it again).

To review #331 (what was read on a document), start on `mandate.html?id=301` and `?id=303`: the identity card of the
seller and the accord de principe of the buyer each read *Analysé* + *Expirée*, beside the other documents' single
pill. Then one URL per state of the document page:

| URL | State |
|---|---|
| `mandate/document.html?id=301&document=3103` | `EXTRACTED` with its fields — the static markup, without JavaScript too: the land title's seven rows in the server's order (*Numéro du titre* 4521, *Propriétaires*, *Département*, *Arrondissement*, *Localité*, *Surface* 500 m², *Date de délivrance* 14/06/2019), under *Lues automatiquement sur la pièce : vérifiez-les sur l'original.* |
| `mandate/document.html?id=301&document=3102` | `EXTRACTED` + expired when it was read: the notice *Pièce expirée le 12/01/2025*, *Elle était déjà expirée lors de sa lecture.*, the way out, then the seven identity rows (*Date d'expiration* 12/01/2025, *Pays* CM). |
| `mandate/document.html?id=301&document=3105` | `EXTRACTING`: *La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire.* — no field, no problem. |
| `mandate/document.html?id=301&document=3104` | `UPLOADED`: *Le document est reçu : sa lecture va commencer…* — on `mandate.html?id=301` its row has no *Expirée* marker (no `expiresAt`). |
| `mandate/document.html?id=301&document=3101` | `FAILED`: *Ce document n'a pas pu être lu* and two instructions, in the server's order (missing page, then the doubtful required field: *Un champ obligatoire n'a pas pu être lu avec certitude…*), no field row. |
| `mandate/document.html?id=301&document=3101&codes=DOCUMENT_UNREADABLE` | `FAILED` with the unreadable code, which no sample carries: `?codes=` replaces the sample's codes (prototype switch, comma-separated), so the instruction reads *Le document n'a pas pu être lu. Remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.* |
| `mandate/document.html?id=303&document=3305` | `EXTRACTED` without a field (an older payload): *Aucune donnée n'a été lue sur ce document…*, its status still *Analysé*. |
| `mandate/document.html?id=303&document=3304` | A proof of funds: *Montant* 12,5M FCFA, *Nature du montant* Solde, *Date du relevé* 20/11/2026, *Compte* •••• 0123 — no `currency` row, no confidence. |
| `mandate/document.html?id=303&document=3302` | A pay slip: *Mois de paie* Octobre 2026, *Salaire brut* 412K FCFA, *Salaire net* 350K FCFA, and the unknown key `position` as *Autre donnée* *Chargée de clientèle*. |
| `mandate/document.html?id=303&document=3301` | A valid identity card (*Date d'expiration* 20/05/2032): no notice. |
| `mandate/document.html?id=303&document=3303` | `EXTRACTED` and expired since: *Pièce expirée le 20/11/2026*, *Elle a expiré depuis sa lecture.*, with *Valable jusqu'au* the same date among the fields. |
| `mandate/document.html?id=311&document=3113` | Read-only fields on the cancelled mandate: the reading, no notice, *Voir* only under the pages. |
| `mandate/document.html?id=311&document=3112` | Read-only and expired: the notice, whose way out reads *Mandat annulé : cette pièce reste consultable telle quelle.* |
| `mandate/document.html?id=311&document=3111` | Read-only `FAILED` with an unknown code (`DOCUMENT_BACK_MISSING`): the generic instruction. |

Replace a page of `3102` (*Remplacer*, a photo — an image document in #369; `3103` is a PDF document and takes a
one-page PDF): the document comes back *Reçu* and its reading is gone (the server
resets its analysis), then read again on its own — the section follows. Deleting and re-adding a document does the
same. At 390 px the field rows keep two columns (the label wraps on two lines, never the value outside the screen), the
notice, the instructions and the waiting hint sit full width above them, and a row's two pills keep the type wrapping
inside its own column; below 360 px the field rows stack (label above value). Assumptions: the reading sits between
the header and *Pages* (the data is what the document is worth, the pages are its evidence); the expiry notice comes
first, then the reading; an expired piece is *EXTRACTED* + `DOCUMENT_EXPIRED` (already expired when read) or
*EXTRACTED* with no code (expired since), which is what the notice's second line tells apart — production compares
`expiresAt` to the tenant's clock, the prototype to the browser's today; the fields keep the server's order and
`currency` never gets a row; a `CLOSED` / `CANCELLED` mandate shows the whole reading but names no action of its own
(none of this story's, and the replace/delete of #329 are absent).

To review #366 (a PDF next to photos in a mandate's documents): on `mandate/document-new.html?id=302` (a seller below
#369's cap; 301 and 303 are at it) choose *Choisir des fichiers*, an image and a PDF — *Page 1 · IMG_2041.jpg · 1,8 Mo*
then *PDF · titre-foncier.pdf · 2,4 Mo*, the count line *2 fichiers choisis · 10 pages au plus. Les pages d'un PDF
comptent à l'envoi.*, each removable; submit for *Préparation des pages…*, then *Envoi du document…* (no page count) and
the mandate page, whose new document's pages are ordinary *Page 1*, *Page 2* rows with *Voir*. Images alone keep
*Envoi de la page 2 sur 3…*. #369 refuses that mixed list before it is sent: remove the PDF (or keep only it) to see
#366's flow. The add form's server refusals:
`&errors=pdf-size` (*titre-foncier.pdf* over 5 Mo), `&errors=pdf-protected` (a password),
`&errors=pdf-unreadable` (empty or damaged), `&errors=pdf-pages` (over 10 pages), each keeping the type and title and
asking for the pages again; `&errors=file` is now a Word file. The replace form: `mandate/document.html?id=301&document=3103`
is a PDF document in #369, so it accepts a one-page PDF (and refuses a photo); `&replaceError=pdf-pages&page=2` (also
`pdf-size`, `pdf-protected`, `pdf-unreadable`) opens the row's box with the French message, the page unchanged. Turn JavaScript off: every input still posts the form and the
page comes back with its field error. At 390 px: a long PDF name wraps in its row, the new error text wraps under the
field, the count line and the replace box stay on screen. Assumptions: a PDF is shown as *PDF*, never *Page N* (its page
count is the server's), and the count line counts files then; the prototype simulates a PDF as two rendered pages;
the 5 Mo limit is per file (the story's copy said per page) and no copy promises a PDF over 5 Mo.

To review the type and status badges (#276): `index.html` (offer 104 and card 301 read *Vente*; the rental cards read
*Location*, their badge hugging its own word) and `mandate.html?id=301` (*Vente* + *Actif*) against `?id=201`
(*Location* + *Assigné*), both at 390 px to see the pair on its own line under the title.

To review #369 (the mandate's document limits), one URL per state:

| URL | State |
|---|---|
| `mandate.html?id=301` | A seller at the cap: *5/5* (its accessible label *5 documents sur 5*), the five rows, no *Ajouter un document*, *Un mandat peut avoir 5 documents au plus.* in its place (the static render). |
| `mandate.html?id=303` | A buyer at the cap: the same, with the buyer's five documents. |
| `mandate.html?id=302` | A seller below the cap: *0/5*, the empty hint and *Ajouter un document* on the heading's line. |
| `mandate.html?id=201` | A renter below the cap: the same. |
| `mandate.html?id=311` | A cancelled search: *3/5* (its accessible label *3 documents sur 5*), the read-only hint, no action (not the cap notice). |
| `mandate.html?id=301&docs=error` | *Documents indisponibles*: no count, the add action stays. |
| `mandate.html?id=301&docs=none` | Empty at *0/5*, the seller's hint, the add action. |
| `mandate/document-new.html?id=301` | At the cap: the notice *Un mandat peut avoir 5 documents au plus.*, how to free a place and *Voir les documents du mandat* — no form (the same for `?id=303`). |
| `mandate/document-new.html?id=302` | The add form below the cap: the hint names the three caps, then the type, the title and the pages. `?id=201` is a client's (identity and financing first). |
| `mandate/document-new.html?id=302&errors=mixed` | The server's 400 for photos and a PDF (or two PDFs): the pages field's error, the type and title kept. |
| `mandate/document-new.html?id=302&errors=pages` | Over 10 pages once the PDF is expanded: the pages field's error, the type and title kept. |
| `mandate/document-new.html?id=301&errors=limit` | The 6th document refused: the form again with the cap's error above it, type and title kept, the way back (the agent's form was open at 4 while another tab added the 5th). |
| `mandate/document-new.html?id=311` | The locked view (closed or cancelled mandate). |
| `mandate/document.html?id=301&document=3103` | A PDF document: the *PDF* tag, two pages, *Remplacer* expecting a one-page PDF (hint and file picker). |
| `mandate/document.html?id=301&document=3102` | An image document: the *Photos* tag, two pages, *Remplacer* expecting a photo. |
| `mandate/document.html?id=301&document=3103&replaceError=mixed&page=2` | The wrong kind for a PDF document: the row open with *Ce document a été créé à partir d'un PDF : … pas par une photo.* |
| `mandate/document.html?id=301&document=3102&replaceError=mixed&page=2` | The wrong kind for an image document: *… à partir de photos : … pas par un PDF.* |
| `mandate/document.html?id=303&document=3302` | Another image document (a pay slip), on the buyer's side; `3303` is a PDF one. |

With JS on `mandate/document-new.html?id=302`, choose a photo and a PDF (or two PDFs): the pages field's error shows as
the list becomes mixed and *Ajouter le document* sends nothing; remove the PDF (or one of the two) and it clears. The
6th document is caught before sending only from a tab left open below the cap (the page at 5 shows no form). Delete two
of 301's documents from their pages and come back to `mandate.html?id=301`: *3/5*, *Ajouter un document* back, the cap
notice gone. The static markup is the server's render, JavaScript or not — the section at 5, the add page's notice and
the form's hint — and every form still posts; the `?errors=` / `?replaceError=` states are this prototype's script
switches (production renders them server-side). At 390 px the compact count keeps *Ajouter un document* on the
heading's line; if space runs out it wraps under the title, never the action, and at 5 the notice takes its own line
under the title and its count. The long hint wraps inside its field, the kind pill follows the status on its line, and
no page scrolls sideways. Assumptions: 301 and 303 being at 5, the add form moved to 302 (a seller) and 201 (a client),
whose suggested types are the same; a document's kind is a neutral pill in its header, not a status; `?errors=limit` is
a form-level error (the cap is not a field's mistake); a document with no recorded kind (grandfathered) accepts either
kind, as #366, and has no sample; data over a cap stays readable (the section counts it, *6/5*, and hides the add; an
11-page document still lists its pages and replaces one), and no sample seeds one; the count comes from the session's
stored list, so adding and deleting on the add and document pages moves it.

To review #372 (a document's page preview, and its way back), start on `mandate/document.html?id=301&document=3103`
and follow *Voir*: the preview opens **in this tab**, with a single bar whose whole left side is one link — *←* and
the document's title, its hidden suffix reading *— retour au document* — and *Page 2 sur 2* at its right, the page's
image centred on the dark overlay; that link lands on the same document page at its *Pages* heading. Every *Voir*
does the same, in the same tab: `mandate/document.html?id=301&document=3101` (one page: *Page 1 sur 1*),
`mandate/document.html?id=301&document=3102` (recto and verso: *Page 1 sur 2*, *Page 2 sur 2*) and
`mandate/document.html?id=311&document=3113` (a cancelled mandate: the same viewer, read-only). The viewer also opens
by URL: `mandate/document-preview.html?id=301&document=3103&page=2` (the default static render; without JavaScript
too), `…&page=1`, `mandate/document-preview.html?id=301&document=3101&page=1` and
`mandate/document-preview.html?id=311&document=3113&page=1`. At 320 px and 390 px the bar stays on one line: the link
shrinks and its title truncates with an ellipsis, *Page 2 sur 2* stays whole at the right, and nothing scrolls
sideways; at 1024 px the same line shows the full title and the image stays centred. Assumptions: production's
acceptance example is page 2 of a 3-page document, but the v2 sample `3103` has two pages, so the viewer reads *Page
2 sur 2* — the rank and count are the document's own, and no sample was changed (301 and 303 are at #369's cap, 302
and 201 keep their empty state); no empty state (a document always has a page) and no loading nor image-error state
of its own — while the JPEG loads, or if it fails, the bar and the way back stay, and a 404 is production's error
page; `mandate-documents.js`'s `initPreview` only picks the sample the URL names, so every *Voir* shows its own
document and rank, and the static markup is production's no-JavaScript render.

To review #298 (the owner verification of a LISTING mandate), one URL per state. The section sits before the Documents
section, its status pill beside its title:

| URL | State |
|---|---|
| `mandate.html?id=301` | The default render: `FAILED`, *Propriétaire non vérifié*, the expired identity check (*La pièce d'identité* — the reason carries no type nor title), its wording and its action, then *Corriger dans les documents* — the static markup, as the server renders it. |
| `mandate.html?id=302` | `PENDING` and empty: a seller with no document, *Vérification du propriétaire en cours*, both missing pieces and what counts, *Ajouter ces pièces dans Documents*. |
| `mandate.html?id=302&ownership=pending&reason=id-missing` | `PENDING` with the identity piece missing only (`document-missing` for the ownership one, `both` for both). |
| `mandate.html?id=301&ownership=pending&reason=in-flight` | `PENDING` with no reason: a piece is being read (301's facture d'eau), *La lecture des pièces est en cours…*, *Voir les documents* — no error styling. |
| `mandate.html?id=301&ownership=verified` | `VERIFIED`: *Propriétaire vérifié*, the publication hint, no date (the verdict carries none), and **no** listing link (the mandate has no listing yet). |
| `mandate.html?id=301&listing=published&ownership=verified` | The same, plus *Voir l'annonce* — the mandate has a listing (601). |
| `mandate.html?id=302&ownership=verified` | The no-listing variant on the plot of land: the hint, no dead link, no date. |
| `mandate.html?id=301&ownership=failed&reason=id-expired` | `OWNERSHIP_ID_EXPIRED` (301's own): *La pièce d'identité*, *La pièce est expirée…*, *Reprenez la photo d'une pièce en cours de validité…* |
| `mandate.html?id=301&ownership=failed&reason=id-unusable` | `OWNERSHIP_ID_UNUSABLE`: *La pièce d'identité*, its `DOCUMENT_UNREADABLE` detail under the wording, *Corrigez la pièce dans Documents : reprenez sa photo, ou changez son type.* — no title. |
| `mandate.html?id=301&ownership=failed&reason=id-name` | `OWNERSHIP_ID_NAME_MISMATCH`: *La pièce d'identité*, *La pièce ne porte pas le nom du propriétaire…*, *Ajoutez une pièce d'identité au nom du propriétaire.* |
| `mandate.html?id=301&ownership=failed&reason=document-unusable` | `OWNERSHIP_DOCUMENT_UNUSABLE`: *Le document de propriété*, two details in the server's order (wrong type, then the missing page). |
| `mandate.html?id=301&ownership=failed&reason=document-name` | `OWNERSHIP_DOCUMENT_NAME_MISMATCH`: *Le document de propriété*, *Le document ne désigne pas le propriétaire du bien.* |
| `mandate.html?id=301&ownership=unavailable` | *Vérification indisponible*, warning styling; every other section renders as before. |
| `mandate.html?id=301&ownership=none` | No section at all, as a SEARCH mandate or a closed / cancelled page renders it. |
| `mandate.html?id=303&ownership=failed` | A buyer's SEARCH mandate: the switch is ignored, no section (no ownership request in production). |
| `mandate.html?id=203&ownership=pending` | A landlord's rental LISTING: the same states with *le bailleur*; `&listing=published` gives the verified state its 501 link. |
| `mandate.html?id=305&ownership=pending` | An ASSIGNED seller's LISTING mandate (a building): the section shows before its approval too, with no listing to link. |

To see a CLOSED / CANCELLED LISTING mandate (the section absent), cancel `mandate.html?id=301` at the bottom of the page
(or close it through `mandate/close.html?id=301`): the section leaves with the status, while the Documents section stays
readable. Check every state at 390 px: the title's pill wraps under it when it must, the check's kind and a long problem
instruction (*Le document n'a pas pu être lu…*) wrap inside the reason without scrolling sideways, and the action sits on
its own line under the text; tab through the section — the action links of the pending, failed and verified states take
the global focus ring, and the section's heading keeps the page's h2 order. Assumptions: the shipped verdict (#297,
`ndopify-dto` 0.0.112) is a status and its reasons only — each reason a code and, for `*_UNUSABLE`, the document's
`problemCodes` as details, with **no evidence date and no document type nor title** — so the section shows no date and
names the check's kind (*La pièce d'identité* / *Le document de propriété*), while the Documents rows below carry each
piece's type, title and status (*À corriger*, *Expirée*); the section opens the documents block (before Documents, after
the day's work, before *Sa recherche*); a `*_MISSING` reason is `PENDING`, a `*_UNUSABLE` one `FAILED` with the
document's problem codes; no reason ever names an id, a page, a path, a URL, an extracted value, a type nor an agent
title, and its action points at `#documents-title`; every state is server-rendered in production — `?ownership=` and
`&reason=` are this prototype's script switches, and `mandate-ownership.js` only renders the samples.

To review #375 (a verified listing waiting on the owner's verification), the sample is **listing 706** — seller 312's
villa, verified, its owner's verdict pending — opened at `listing.html?id=706` (it is seeded on its first visit; 312
has no card on `index.html`). The tag and the block sit above the checklist; one URL per state:

| URL | State |
|---|---|
| `listing.html?id=706` | The default, and the static render: `PENDING`, *Vérifiée — publication en attente*, both pieces missing and what counts, *Vérifier le propriétaire dans le mandat*. |
| `listing.html?id=706&ownership=pending&reason=id-missing` | The identity piece missing only (`document-missing` the ownership one; `both` is the default). |
| `listing.html?id=706&ownership=pending&reason=in-flight` | `PENDING` with no reason (a piece is being read): *La lecture des pièces est en cours…*, no error styling, the same link. |
| `listing.html?id=706&ownership=failed` | `FAILED` with three reasons: the expired identity card, an ownership document that can't be checked (two problem instructions) and one in someone else's name — no manual review. |
| `listing.html?id=706&ownership=failed&reason=id-unusable` | One reason with its details and action (`id-expired`, `id-name`, `document-unusable`, `document-name` are the others). |
| `listing.html?id=706&ownership=verified` | Not blocked: *Vérifiée* (success), the *En vérification* panel as today, no block. |
| `listing.html?id=706&ownership=unavailable` | The verdict can't be read: *Vérification du propriétaire indisponible*, the same link, every other section unchanged. |
| `listing.html?id=701` / `?id=703` / `?id=704` | A DRAFT (a building) and two sent listings (land): as today, no block. |
| `listing.html?id=5001` | Another agent's published listing (the read-only lens): as today, no block and no ownership call. |
| `mandate.html?id=312` | The other half: the *Vérification du propriétaire* section words the same verdict, and once 706 has been opened its *Actif* line reads *Annonce n° 706* *Vérifiée*, *Annonce vérifiée.* — the wait is explained on the listing page only (decision 6). |

Turn JavaScript off on `listing.html?id=706`: the tag, the block, both pieces, the link and the checklist are 706's
server render, not drawn by the script (the other states are this prototype's `?ownership=` switch; production
renders them server-side from `GET /v1/mandates/{id}/ownership`, read only on the listing's own agent's `VERIFIED`
page). At 390 px the hold pill takes its own line under the title and wraps inside the page, the pieces and the
reasons wrap inside the block, the action sits on its own line and nothing scrolls sideways; the link keeps the
global focus ring, and the block's title is an `h2` like the page's sections. Assumptions: the tag's label and the
block's title split AC 1's sentence (*Vérifiée — publication en attente* + *Publication en attente de la vérification
du propriétaire*); the blocked tag is warning, the `PENDING` block info (as #298's pending) and `FAILED` /
unavailable warning; a verdict that can't be read still reads the hold tag, since the listing is verified and not
published; the in-flight verdict keeps the same title (the wait is the same) and only swaps the pieces for the
reading sentence; the default sample is `PENDING` with both pieces missing, and the FAILED switch is the story's
multi-reason example; the prototype's action href carries `?ownership=` / `&reason=` so the mandate page shows the
verdict this block words (the FAILED default carries `reason=mixed`, its three reasons; production links
`/mandates/{id}#ownership-title`); 706 has its own mandate so it never
competes with the published 501 (203) or 601 (301); no loading state (the verdict is read with the page) and no empty
state (the block always carries its verdict, and `VERIFIED` has none); `PUBLISHING` keeps today's *En vérification*
tag and panel and makes no ownership call (the prototype has no separate sample); and the block names no customer,
document id, type, title, page, path, storage URL, extracted value nor verdict JSON — only the status and the
reasons' French labels.

To review #377 (an owner verification overridden by Ndopify), the section is #298's *Vérification du propriétaire*,
with one state more. One URL per state:

| URL | State |
|---|---|
| `mandate.html?id=301` | The default render, unchanged: `FAILED`, *Propriétaire non vérifié*, the expired identity check and its action (the static markup). |
| `mandate.html?id=301&ownership=overridden` | `VERIFIED` by override: the pill *Vérifiée par Ndopify*, *Propriétaire vérifié*, *Ndopify a vérifié le propriétaire après examen de sa situation.*, then the publication hint; the intro has no *à partir des pièces* clause; no date, no reason, no admin name nor reason, and **no** listing link (301 has no listing yet). |
| `mandate.html?id=301&listing=published&ownership=overridden` | The same, plus *Voir l'annonce* — the mandate has a listing (601). |
| `mandate.html?id=302&ownership=overridden` | The no-listing variant on the plot of land: the same block and intro, no dead link. |
| `mandate.html?id=203&listing=published&ownership=overridden` | A landlord's rental LISTING: *le bailleur* and the 501 listing link. |
| `mandate.html?id=305&ownership=overridden` | An ASSIGNED seller's building: the overridden state before approval too, with no listing to link. |
| `mandate.html?id=301&ownership=verified` | The non-overridden `VERIFIED` state, as today: *Vérifié*, *Propriétaire vérifié*, the intro keeps its documents clause, no date. |
| `mandate.html?id=301&ownership=pending&reason=in-flight` | Unchanged loading state: `PENDING`, *Vérification du propriétaire en cours*, the reading sentence, *Voir les documents*. |
| `mandate.html?id=301&ownership=unavailable` | Unchanged error state: *Vérification indisponible*, every other section as before. |
| `mandate.html?id=301&ownership=none` | Unchanged empty state: no section at all, as a SEARCH or a closed / cancelled mandate renders it. |
| `mandate.html?id=303&ownership=overridden` | A buyer's SEARCH mandate: the switch is ignored, no section (no ownership request in production). |

The default static render is still 301's `FAILED`; `?ownership=overridden` is this prototype's script switch, and
production renders the overridden state server-side from the same `GET /v1/mandates/{id}/ownership` verdict
(`status: VERIFIED`, `overridden: true`). Turn JavaScript off on `mandate.html?id=301`: the `FAILED` state is 301's
server render, not drawn by the script. At 390 px the longer pill takes its own line under the title and stays whole,
the after-review sentence and the publication hint wrap inside the block, the action sits on its own line and takes
the global focus ring; the section's title stays an `h2`, and nothing scrolls sideways. Cancel `mandate.html?id=301`
at the bottom of the page to see the section leave, the overridden state included. Assumptions: an overridden verdict
is still `VERIFIED`, so the state is success-styled and carries no error styling; the copy says *après examen de sa
situation* and never names the documents, the admin, a date nor the reason; the intro drops only its *à partir des
pièces de la section Documents* clause, since the documents didn't decide this verdict; the publication hint and the
listing link are #298's, unchanged; the public badge (#300) is out of this story.

To review #376 (the owner verification's city comparison), the section is still #298's *Vérification du propriétaire*:
301 is a seller in **Douala**, so its samples compare Douala with the title's locality. One URL per state:

| URL | State |
|---|---|
| `mandate.html?id=301&ownership=hint` | `VERIFIED` + `UNRESOLVED`: the success block as today, then the **neutral hint** under it — *Ville du mandat* Douala, *Localité du titre* Bonabéri (a quarter, not a known city), the sentence and *Vérifier le titre dans Documents*. The pill stays *Vérifié*, the hint keeps the info hue: no error styling and no status change. |
| `mandate.html?id=301&ownership=hint&locality=none` | The title states no locality: *Localité du titre* *non précisée*, with the mandate's city and the same sentence and link. |
| `mandate.html?id=301&ownership=hint&city=none` | The mandate has no city: *Ville du mandat* *non précisée*, with the title's locality. |
| `mandate.html?id=301&ownership=hint&locality=none&city=none` | Both places blank: no hint at all — the verdict alone, as #298 renders it. |
| `mandate.html?id=301&ownership=hint&state=pending` | The hint under `PENDING` (the identity piece missing): the pending block and its action are unchanged, the hint sits below them. |
| `mandate.html?id=301&ownership=hint&state=failed` | The hint under `FAILED` for another check (301's expired identity card): the failed block, its wording and its action are unchanged, the hint sits below them. |
| `mandate.html?id=301&ownership=failed&reason=property-mismatch` | `FAILED` `OWNERSHIP_PROPERTY_MISMATCH`: *Propriétaire non vérifié*, *Le document de propriété*, the two places (Douala / Yaoundé), *Le document désigne un bien dans une autre ville que celle du mandat…*, its action, then *Corriger dans les documents*; no hint. |
| `mandate.html?id=301&ownership=verified` | `MATCH`: the title resolves to the mandate's city, so *Propriétaire vérifié* as today and **no** hint. |
| `mandate.html?id=301` / `mandate.html?id=301&ownership=failed&reason=id-expired` | `UNKNOWN`, and a `FAILED` for another reason: unchanged, no hint. |
| `mandate.html?id=301&ownership=unavailable` | Unchanged: *Vérification indisponible*, no hint. |
| `mandate.html?id=301&ownership=none` | Unchanged: no section at all. |

The static markup is still 301's `FAILED` render and carries no hint (301's comparison is `UNKNOWN`); the hint and
the mismatch are this prototype's `?ownership=` switches, while production renders them server-side from the same
verdict (`location.status` `UNRESOLVED` or `MISMATCH`). At 390 px the two places stack one under the other inside the
hint as in the failed reason, long city names wrap (`overflow-wrap: anywhere`) and the action link keeps its own line;
from 480 px up they sit side by side, two equal columns, and the failed reason's action stays above the fold. Tab
through the hint: *Vérifier le titre dans Documents* takes the global focus ring, after the state's own action.
Assumptions: the hint shows under the state's block, so the status and its wording always read first and the neutral
comparison second; it is the app's existing `.hint` (info tokens), never `.alert--warning`; the two places are the
verdict's only property data — no document type, title, id, page, path, URL nor other extracted value — and the
labels (*Ville du mandat* / *Localité du titre*) are static page wording, not server data; a sample's mandate city
is 301's own (Douala) and the title's locality is this prototype's (Bonabéri for the hint, Yaoundé for the
mismatch); `?ownership=overridden` and every #298 / #375 state are untouched, and the listing page's owner block
words the new reason with the shared sentence alone (no hint, no places).

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
| `offer-property.html?sample=sale` | *Caractéristiques*, a sale whose type isn't saved yet (the step's first render): only the type choices and *Document de propriété* are visible — no follow-up question before a type is chosen (#304). Pick a type: *Terrain* asks its required surface, *Villa, maison* its furniture, rooms and parking; the documents stay. |
| `offer-property.html?sample=land` | The same with the type *Terrain*: no rooms nor parking, the documents, *Surface du terrain estimée* (800 m²) and, under the surface, *Soit ≈ 56,3K FCFA/m² pour 800 m²* (45 000 000 FCFA ÷ 800, rounded to the nearest 100). |
| `offer-property.html?sample=land-no-area` | The same without its surface: *Suivant* is inactive; click it for *Indiquez la surface du terrain.* |
| `offer-property.html?sample=house` | The same with the type *Villa, maison* (3 chambres, 2 places de parking): the documents are visible (optional), rooms and parking are asked, no rate line. |
| `offer-property.html?sample=land-review` | The review of the land sale: *Conditions* with *45M FCFA*, then *≈ 56,3K FCFA/m² pour 800 m²*, then the availability; *Caractéristiques* *Terrain · 800 m²*; *Document de propriété* *Titre foncier · Plan de bornage*. |
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

Land is sold or bought, never let (#275); a sale offers it under either use (#293). `offer-property.html?sample=rental-land`
opens the *Caractéristiques* step of a rental posted with *Terrain*: the type isn't offered under either use, no choice
is selected, and *Suivant* answers *Choisissez le type de bien.* `offer-property.html?type=SALE` walks to the same step
with *Terrain* under *Résidentiel* and *Commercial* alike — one choice, kept when *Usage* changes — whose *Résidentiel*
description names "terrain à bâtir…" where the rental's names none (the sale's *Commercial* one deliberately names no
land); the #273 land samples (`?sample=land`, `land-no-area`, `land-review`, `land-review-none`) are unchanged —
documents, surface and rate, and `?sample=land` still seeds the residential use: pick *Commercial* at step 1 to see the
same plot under the other one. `find-property.html?type=RENTAL` offers no *Terrain* on step 3 under either use,
*Indifférent* included as always, against `?type=SALE`, which offers it under both usages (answer *Acheter* and
*Commercial* at step 1, then *Terrain* at step 3). At 390 px the choice grid stays one column and *Terrain* closes the
*Commercial* list, after *Immeuble* / *Immeuble entier*, on both wizards (a plot's answer is one row less on a rental;
the sale's cards read the same as before). A sale with no usage answered offers no type at all (*Terrain* included):
step 1 requires that answer, so the type step is only opened with one. These steps load nothing asynchronously, so
there is no loading state.

A stored rental *Terrain* stays readable: `mandate/edit.html?id=208` (a renter's search) and `?id=209` (a landlord's
property) show *Terrain* checked and enabled under *Résidentiel* (the type list reads one type per row, at every width),
the group's summary reading *Terrain*; *Enregistrer*
unchanged keeps it, and picking another type makes it disappear for good. `?id=201` is the ordinary rental (no
*Terrain* under either use), `?id=104` the sale (*Terrain* under both *Résidentiel* and *Commercial*, one choice,
saved under the usage that was picked). `mandate.html?id=208` and `?id=209` read the
same saved answer (*Type de propriété : Terrain*, loyer, surface), as before the rule. The `?sample=` shortcuts seed
the session once, like every other one: reloading one after changing its answers keeps the change. The empty and
loading states don't apply to this change, and there is no new screen.

A building sold as a whole (#274) is captured by two counts, its étages and its units, instead of its rooms, its
parking and its amenities. The buyer's wizard: `find-property.html?sample=building` opens *Décrivez le bien idéal* on a
sale of *Immeuble* with *Nombre d'étages* and *Nombre d'unités* to fill — *Suivant* is inactive, click it for
*Indiquez le nombre d'étages, ou cochez Indifférent.* (and the same for units; try `0`, `31`, `501` and letters for
*Indiquez un nombre entier, de 1 à 30* / *… à 500*) — six steps read *Étape 3 sur 6* here and *Étape 6 sur 6* on
`?sample=building-review`, whose *Bien recherché* line reads *Immeuble · 3 étages · 12 unités · 850 m² minimum*, with
no *Indispensable* row; `?sample=building-any` and `?sample=building-review-any` are the two *Indifférent* states (the
line reads *étages indifférents · unités indifférentes*). `?sample=rental-building` is now a rental *posted with*
*Immeuble* (the type is no longer offered; see #304 below). The seller's wizard:
`offer-property.html?sample=building` opens *Caractéristiques* with *Immeuble entier* under *Commercial* only, no
*Parking ou garage*, *Étages* and *Unités* empty (*Suivant* answers for them), *Étape 4 sur 6*; `?sample=building-review`
is its review (*Étape 6 sur 6*, *Immeuble entier · 3 étages · 12 unités · 850 m²*, no *Équipements*); `?type=RENTAL`
and the #273 land samples are unchanged. The agent's screens: `mandate.html?id=305` (a seller's building, criteria
*Étages* 3 / *Unités* 12, no amenity row), `?id=306` (a buyer's building search: its three matches read
"Immeuble · 12 unités · 3 étages", "Immeuble · 20 unités au lieu de 10", "Immeuble · Nombre d'unités non précisé" —
never rooms or amenities), `?id=301` and `?id=201` unchanged; `mandate/edit.html?id=305` prefills the two counts and
refuses empty, 0, 31, 501 and letters; `?id=104` and `?id=201` are unchanged. The listing: `listing.html?id=701` (six
rows, no *Eau, électricité, accès*, progress "sur 5 vérifiées"), `listing/bien.html?id=701` (*Commercial* selected,
*Immeuble entier* checked, *Étages* 3 and *Unités* 12 prefilled, neither *Salles de bains* nor *Places de parking*,
both required; switch *Usage* to *Résidentiel* and back — *Immeuble entier* stays visible, checked and counted, since
a building keeps the listing's usage), `listing/equipements.html?id=701` returns to the checklist;
`listing.html?id=501` (the rental villa) is unchanged. No rental-building sample exists: the rule keeps *Immeuble
entier* under *Résidentiel* only for a rental, where *Commercial* never offers it. *Nombre d'étages* and *Nombre
d'unités* never share a row: they stack at every width (390 px and 1024 px alike), and the six-step header keeps its
count and project pill on one line. Assumptions: 305 is *Assigné* so that its edit page opens (a signed mandate is no
longer editable, as 301/302 are); `?id=701` seeds its DRAFT on the first visit and the session then keeps it (its
edits survive a reload); the
buyer's *Indifférent* is a checkbox that disables its count (stored as null); the building's checklist has six rows
because its utilities aren't checked; a building's surface stays optional in the buyer's search and required in the
seller's *Caractéristiques*, as it is today for their types; the seller wizard has no "N réponses" summary line — the
counts are in its review's *Caractéristiques* line and in the mandate edit's group summary. Two points settled with the requester: the count
reads *étages* everywhere (its field stays `levels`), and a building's *Le bien* asks neither *Salles de bains* nor
*Places de parking* — it is counted by its units. Two points settled with the requester: the count
reads *étages* everywhere (its field stays `levels`), and a building's *Le bien* asks neither *Salles de bains* nor
*Places de parking* — it is counted by its units.

A land sale's *Le bien* (#287): `listing/bien.html?id=702` is the DRAFT of seller 302 — *Terrain*, *Résidentiel*, 800 m²,
no *Places de parking* (nor *Salles de bains*, *Chambres*, *Salons*, *Cuisines*, *Ameublement*, *Étages*, *Unités*,
*Étage*), and *Document de propriété (facultatif)* with *Titre foncier* and *Plan de bornage* checked, one option per
line (three rows at 390 px as at 1024 px), no owner line (the agent's answer is the seller's). *Terrain* is offered to a
sale under both *Résidentiel* and *Commercial* (#293), one row for both: `?id=702` opens it checked under *Résidentiel*,
and switching *Usage* to *Commercial* keeps it checked and selectable; #275 keeps it out of a rental's *Le bien* (a
legacy rental *Terrain* is hidden too). Its checklist row is
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

The one rule set (#304): *Proposer un bien*, *Trouver un bien* and the mandate edit page now ask the same
questions for a type, from `NdUtil.propertyRules` (`assets/js/util.js`). *Immeuble* and *Terrain* are sold only; a
residential type asks no surface, its rooms unless it is a *Studio* or a *Chambre*, its parkings (a *Chambre*
included), its furniture on a sale as on a rental, and its documents on a sale; a commercial type asks its surface
and, on a sale, its documents; a sold *Immeuble* asks its *Étages* and *Unités*. The *Document de propriété* (seller)
and *Documents exigés* (buyer) are offered for every sale, optional, several or none; a rental never asks them. On
*Proposer un bien*, no follow-up question shows until a type is chosen — sale and rental alike — the *Document de
propriété* excepted, shown by every sale. The
price and the surface are grouped by thousands as typed and when re-shown, with the caret kept after the same digit.
The rate reads short like every displayed amount (#292) — *20,83M FCFA/unité*, *50K FCFA/m²* — while the price
input keeps its grouped digits.
A sale shows a read-only rate — price per m² for a *Terrain*, per unit for an *Immeuble* — rounded to the nearest
100 FCFA, never posted nor stored: under the surface / units at the seller's *Caractéristiques* step and at the
buyer's *budget* step, under the price field on the edit page. It is hidden without a usable amount and surface / unit
count (and while the buyer's units are *Indifférent*). This rule set supersedes the #273/#274 sample descriptions that
it changes: `?sample=sale` now opens on the type choices and the documents alone (the #273 preview is gone),
`?sample=house` on both wizards now shows the furniture and the documents, and `?sample=rental-building`
is now a rental *posted with* *Immeuble* (the type is no longer offered), not a rental-building review.

The *Proposer un bien* states (#304), one click away; each `?sample=` seeds the session once and opens the step
named, and the wizard can be walked from there:

| URL | State |
|---|---|
| `offer-property.html?sample=sale` | *Caractéristiques*, a sale whose type isn't chosen: the type choices and *Document de propriété* only — no follow-up question before a type is chosen; choose *Villa, maison* for furniture, rooms and parking (the surface stays out), choose *Terrain* for its required surface (the documents stay in both cases). |
| `offer-property.html?sample=rental-pending` | The same step for a rental whose type isn't chosen: the type choices only — no follow-up question, no documents, no rate. |
| `offer-property.html?sample=house` | A sold villa: *Ameublement*, *Nombre de chambres* and *Parking ou garage* asked, no surface, *Document de propriété* visible and empty (none ticked is valid). |
| `offer-property.html?sample=house-review-docs` | Its review: *Villa, maison · Meublé · 4 chambres · 2 places de parking*, then the three documents (*Titre foncier · Plan de bornage · Certificat de propriété*). |
| `offer-property.html?sample=house-review-none` | The same sale with no document: *Aucun document déclaré*. |
| `offer-property.html?sample=land` | A sold plot (45M, 800 m²): no rooms nor parking, its surface required and, under it, *Soit ≈ 56,3K FCFA/m² pour 800 m²* (rounded to 100). |
| `offer-property.html?sample=land-no-area` | The same without its surface: *Suivant* inactive; click it for *Indiquez la surface du terrain.* |
| `offer-property.html?sample=land-review` | The review of that sale: *45M FCFA*, then *≈ 56,3K FCFA/m² pour 800 m²*, then the availability; *Document de propriété* *Titre foncier · Plan de bornage*. |
| `offer-property.html?sample=land-review-none` | The same with no document: *Aucun document déclaré*. |
| `offer-property.html?sample=rental-room` | A *Chambre* let (60 000 FCFA): no *Nombre de chambres*, but *Ameublement* and *Parking ou garage* asked (parkings are new for a room), *Combien de places ?* once *Oui*; no surface, no documents, no rate. |
| `offer-property.html?sample=rental-studio` | A *Studio* let (90 000 FCFA): no rooms, *Meublé* checked, *Parking ou garage* *Non* (so no place count), no surface, no documents. |
| `offer-property.html?sample=rental` | A rental's review: no *Document de propriété* row, no rate line, *Location* under *Bien enregistré* once submitted. |
| `offer-property.html?sample=rental-land` | A rental posted with *Terrain*: *Terrain* is not offered under either usage, the answer is refused (*Choisissez le type de bien.* on *Suivant*). |
| `offer-property.html?sample=rental-building` | A rental posted with *Immeuble*, now sold only: the type isn't offered (no *Immeuble entier* in the *Commercial* list), the answer dropped, no count. |
| `offer-property.html?sample=building` | A sold *Immeuble entier* (Commercial, 850 m²): *Étages* and *Unités* to fill, six steps, *Document de propriété* visible. |
| `offer-property.html?sample=building-filled` | The same with 3 étages and 12 unités: *Soit ≈ 20,83M FCFA/unité pour 12 unités* under *Unités*. |
| `offer-property.html?sample=building-review` | Its review: the *Caractéristiques* line *Immeuble entier · 3 étages · 12 unités · 850 m²* (the counts aren't repeated in their own rows), *250M FCFA* then *≈ 20,83M FCFA/unité pour 12 unités*, *Aucun document déclaré*, no *Équipements* line, six steps. |

The *Trouver un bien* states (#304), same mechanics:

| URL | State |
|---|---|
| `find-property.html?sample=sale-pending` | *Décrivez le bien idéal*, a purchase whose type isn't chosen: *Nombre de chambres* and *Meublé ?* (required for a residential search), *Places de parking*, *Surface minimale* and *Quels documents exigez-vous ?* visible and optional. |
| `find-property.html?sample=rental-pending` | The same for a rental: rooms, furniture and parking, no surface, no documents; its type list offers no *Immeuble*. |
| `find-property.html?sample=house` | A house bought: furniture, rooms and parking asked, no surface, the documents visible (none ticked is valid). |
| `find-property.html?sample=purchase-review-docs` | Its review: *Maison · 4 chambres · Non meublé · 2 places de parking*, *Documents exigés* *Titre foncier · Plan de bornage · Certificat de propriété*. |
| `find-property.html?sample=purchase-review-none` | The same without a document: *Aucun document exigé*. |
| `find-property.html?sample=rental-room` | A *Chambre* let: no rooms, *Meublé ?* and *Places de parking* asked, no surface, no documents. |
| `find-property.html?sample=rental` | A rental's review: house, 3 chambres, *Non meublé*, 1 place, no *Documents exigés* row, no rate line. |
| `find-property.html?sample=land` | A plot bought (250M, 800 m²): its required surface is seeded on the *bien* step, and the *budget* step shows *Soit ≈ 312,5K FCFA/m² pour 800 m²*. |
| `find-property.html?sample=land-review` | Its review: *Budget total* *250M FCFA*, then *≈ 312,5K FCFA/m² pour 800 m²*, then the horizon; *Documents exigés* *Titre foncier · Plan de bornage*. |
| `find-property.html?sample=building` | An *Immeuble* bought (850 m²): *Nombre d'étages* and *Nombre d'unités* to fill, each with *Indifférent*, six steps. |
| `find-property.html?sample=building-any` | The same with both *Indifférent*: *Suivant* active, the counts sent as null. |
| `find-property.html?sample=building-budget` | Its *budget* step, 12 unités and 250M: *Soit ≈ 20,83M FCFA/unité pour 12 unités*. |
| `find-property.html?sample=building-budget-any` | The same with the units *Indifférent*: no price-per-unit line. |
| `find-property.html?sample=building-review` | Its review: the *Bien recherché* line *Immeuble · 3 étages · 12 unités · 850 m² minimum* (the counts aren't repeated in their own rows), the budget then the price per unit, no *Indispensable* row. |
| `find-property.html?sample=building-review-any` | The same with both counts *Indifférent*: the line reads *Immeuble · étages indifférents · unités indifférentes · 850 m² minimum*, with no rate. |
| `find-property.html?sample=rental-building` | A rental posted with *Immeuble*: the type isn't offered and the answer is dropped; the *Commercial* list offers *Boutique*, *Bureau*, *Entrepôt* and *Indifférent* only. |
| `find-property.html?type=RENTAL` / `?type=SALE` | Start with the project preselected, answer the usage, then open the type step: a rental offers no *Immeuble* (nor *Terrain*), a purchase offers *Terrain* under both *Résidentiel* and *Commercial*. |

The mandate edit page (#304): `mandate/edit.html?id=310` (URL only, no card) is a land seller's *LISTING · SALE* —
*Surface* re-shown grouped (*1 200*), *Prix* *60 000 000*, *Soit ≈ 50K FCFA/m² pour 1 200 m²* under it,
*Document de propriété* with the three documents checked; the group's summary reads *Terrain · 1 200 m²*.
`?id=305` (URL only) is the sold *Immeuble*: *Étages* 3, *Unités* 12, *Soit ≈ 20,83M FCFA/unité pour 12 unités*,
no rooms, parkings nor amenities, the documents visible and empty. `?id=104` (accept offer 104 first) is the buyer's
*SEARCH · SALE* for a plot: *Documents exigés* with *Titre foncier* and *Plan de bornage* checked, *Surface* 500,
*Soit ≈ 30K FCFA/m² pour 500 m²*; no furniture. `?id=201` is the ordinary rental: no documents, no rate, its
surface not asked — the 150 m² it stores is kept on save. `?id=208` and `?id=209` are the stored rental *Terrain*:
the type stays checked and enabled, its surface shown, no documents nor rate. `?id=210` (URL only) is the stored
rental *Immeuble*: checked and enabled under *Commercial*, its *Surface* (300) and *Ameublement* (*Non meublé*) asked
as the rental questions it had, no counts, documents nor rate, and *Enregistrer* keeps its answers. 301 and 302 are
signed, so their edit page is the locked view, as before.

Assumptions: 104 needs its offer accepted on `index.html` before its edit page opens; 208, 209, 210, 302, 305, 306
and 307–310 have no card on `index.html`, so they open by URL; the edit page's surface stays optional, a land sale
included, unlike the wizards' required plot surface; a question the page no longer asks keeps the mandate's stored
answer (201's 150 m², any count or documents list it doesn't show), and a field it shows saves the agent's answer
(the rental building's furniture is shown and pre-filled); the rate line is display only, never sent nor stored, and
the review repeats it without *Soit*; a rental *Immeuble* is no longer offered but a stored one keeps its type and
its rental questions (surface and furniture); commercial types ask no parkings (the story's *Target rules* table, not
today's buyer rule); furniture is asked for a residential sale and a *Chambre* asks its parkings (both settled
2026-11-24).

Narrow and empty/error checks: at 390 px the grouped *Prix* / *Surface* inputs keep one field per row, the rate line
wraps under the surface / unités and the documents options sit one per line; at 1024 px each rate line takes one line,
the documents options still take one per line, and the edit page keeps its groups stacked, its type list one per row
and its documents one per line.

No surface typed means no rate line (`?sample=land` on either wizard, clearing the surface on its own step; `?id=104`
clearing *Surface*); the buyer's units *Indifférent* hide the price per unit (`?sample=building-budget-any`); no
document declared reads *Aucun document déclaré* (`?sample=house-review-none`, `?sample=land-review-none`) and none
required *Aucun document exigé* (`?sample=purchase-review-none`); a rental has no documents at all. The errors are the
pages' own and grouping doesn't break them: *Indiquez la surface du terrain.* (`?sample=land-no-area`), the seller's
price floor (*Indiquez un prix d'au moins 1M FCFA.* below 1 000 000), the counts' bounds (*Indiquez un nombre entier,
de 1 à 30* / *à 500*), the buyer's budget floor (*Indiquez un budget d'au moins 1M FCFA.*), the surface's minimum (*au
moins 5 m²*), the edit page's price (*Indiquez le budget, en FCFA.* for 104, *Indiquez le prix, en FCFA.* for 305 and
310) and its surface (*0* is refused). The states above load nothing asynchronously — the type, documents and rate
questions are answered from the session — so there is no loading state.

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

A contact e-mail refused by ndopify-server (#380): `POST /v1/mandates` answers `400 EMAIL_ADDRESS_INVALID` when SES
rates the address's mailbox undeliverable. The two public wizards come back to their contact step, every answer kept,
the e-mail flagged (`aria-invalid`, « Cette adresse email n'est pas valide. ») and *Suivant* reading *Revenir à la
revue* — *Précédent* opens the review too, and the contact step never posts by itself. Every other refusal keeps the
review's *Envoi impossible pour le moment.* line, which this prototype doesn't simulate (the valid submission is
unchanged). Each `?sample=` seeds the session once, like every other one; the loading state doesn't apply (the form
is one full-page post), and the submit stays the prototype's simulation (it never posts — production answers again
with the same refusal while the address is unchanged):

| URL | State |
|---|---|
| `find-property.html?sample=email-invalid` | The buyer's contact step after the refusal: all answers kept, the e-mail flagged, *Revenir à la revue*. |
| `find-property.html?sample=email-invalid-empty` | The same with the name, WhatsApp and contact period blank (only the e-mail typed): the sentence wraps under the field at 320 px, nothing scrolls sideways. |
| `offer-property.html?sample=email-invalid` | The seller's contact step after the refusal, the same state. |
| `offer-property.html?sample=email-invalid-empty` | The same with only the e-mail typed. |

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
