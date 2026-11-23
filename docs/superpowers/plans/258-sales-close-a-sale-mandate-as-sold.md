# Close a sale mandate as sold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close a SALE mandate as sold in Partner Central: the closing wizard asks for the sale price (no `/ mois`), both mandates of the deal close, the listing goes SOLD, and the mandate page reads *Vente conclue*.

**Architecture:** No server, DTO or migration change — ndopify-server already closes both sides (`MatchService.close` → `MandateService.closeDeal`, `MandateService.close`) and takes a `SALE` listing off the market as `SOLD` (`ListingService.onMandateClosed`), pinned by `CloseMandateEndpointTest` and `CloseMatchEndpointTest`. This plan makes `ndopify-partner-central`'s closing flow sale-aware: it branches on `Mandate.listingType` in `MandateCloseViews`, `MandateCloseRules`, `MandateCloseController`, `MandateViews` and `MandateController`, ports the approved prototype's sale markup into `templates/mandate/close.html` and `templates/mandate.html`, and sends `PeriodicityType.NONE` (not `MONTHLY`) on a sale's close request.

**Tech Stack:** Kotlin, Spring MVC, Thymeleaf, `TenantFormatter`, `kotlin.test` + `mockito-kotlin`, `MockMvc` standalone, real-Thymeleaf template tests. Maven (`mvn verify`: ktlint, tests, JaCoCo gate).

**Spec:** issue [#258](https://github.com/wutsi/ndopify-mono/issues/258) (enriched body, 9 acceptance criteria, grooming decisions) and its epic [`2026-11-22-v2-property-sales.md`](2026-11-22-v2-property-sales.md). The approved prototype is the design baseline: `modules/ndopify-partner-central/src/main/html/v2/mandate/close.html`, `…/v2/mandate.html`, rendered by `…/v2/assets/js/partner-central.js` (approved 2026-11-22).

## Global Constraints

- **Module:** `modules/ndopify-partner-central` only. Never touch `ndopify-server`, `ndopify-dto`, `ndopify-web`, `ndopify-esignature` or `ndopify-web-core`. No new dependency, no `application*.yml` change.
- **The prototype is the design.** Tasks 2 and 3 port the approved prototype's markup and wording; they design nothing again and change no label, order or state it settled.
- **French UI, English code.** Every label, hint and error message is French; classes, KDoc, comments, log and commit messages are English. Amounts and dates go through `TenantFormatter` / `MandateLabels`, never a hard-coded pattern or "FCFA".
- **No `/ mois` on a sale.** A SALE mandate's price and final price show the amount only; `MandateLabels.price` already omits the suffix for `PeriodicityType.NONE`.
- **A rental is unchanged (AC 6).** Every rental screen, label, note and request must stay byte-identical: only `sale ? … : …` branches may differ.
- **Sale = `Mandate.listingType == ListingType.SALE`.** `RENTAL`, `UNKNOWN` and `null` read as a rental (legacy mandates).
- **Sale price rule:** any positive amount; no sale floor is added (`MandateCloseRules.amount` is unchanged).
- **No JavaScript.** The served wizard is server-rendered; `mandate.js` only enhances. Every rule is re-checked on POST.
- **Co-brokerage:** closing a search on another agent's listing closes that search only (the server's rule); no page claims the other agent's listing or mandate was closed, and this plan adds no such action.
- **Assertions:** `kotlin.test`. **Mocks:** `com.nhaarman.mockitokotlin2`.
- **Commits:** `feat(npc): …`, each carrying `Refs #258` in the body.

## Review Focus

- **A rental close is byte-identical.** The sale branch sits in shared code paths: a `RENTAL` mandate must render the same legend (*Avec quel client le bien a-t-il été loué ?*), the same *Loyer* / *Loyer convenu* / `FCFA / mois`, the same notes (*la recherche du client*, *le mandat du bailleur*, *retirée du marché*) and send `PeriodicityType.MONTHLY`. Task 2 and Task 3 each pin a rental rendering test.
- **An `UNKNOWN` listing type reads as a rental.** A legacy mandate must never show sale wording; Task 2 and Task 3 test `ListingType.UNKNOWN`.
- **Co-brokerage adds no action the agent cannot take.** Closing a sale search matched on another agent's listing closes that search only (the server's rule, pinned by `CloseMatchEndpointTest`); the page must not offer or claim a change to the other agent's listing or mandate. Partner Central's `CloseCandidateView` carries no owner, so the note keeps the served page's sentence structure (today's rental note has the same shape) — improving it needs the candidate's agent, which is out of this story. Task 1 pins the sale note and Task 4 runs the server test.
- **A final price is not always known.** A mandate closed before this feature has no `finalPrice`: the page must leave the price line out, not print ` / mois` or `null`; Task 3 tests a null `finalPrice`.
- **The close request's periodicity.** A sale request must carry `NONE`; a rental `MONTHLY`. Task 1 tests both, and Task 2 asserts what the controller passes.

---

### Task 1: The closing labels and the request's periodicity

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRulesTest.kt`

**Interfaces:**
- Consumes: `PendingMandateClose`, `Mandate` (`listingType`), `TenantFormatter`.
- Produces:
  - `MandateCloseViews.steps(counterpartApplicable: Boolean, owner: Boolean, sale: Boolean): List<CloseStepView>` — labels: counterpart `owner ? (sale ? "Acheteur" : "Client") : "Bien"`; price `sale ? "Prix" : "Loyer"`; then `"Confirmation"`.
  - `MandateCloseView` gains `val sale: Boolean`.
  - `MandateCloseViews.of(mandate, candidates, candidatesUnavailable, step, steps, currency, recap)` keeps its signature and sets `sale = mandate.listingType == ListingType.SALE`.
  - `CloseRecapView.rent: String` is renamed `val price: String`.
  - `MandateCloseViews.recap(mandate, matchId, selected, outside, listing, customer, locations, amount, formatter)` keeps its signature; `price` = `formatter.money(amount) + if (mandate.listingType == ListingType.SALE) "" else " / mois"`; the note is sale-aware (below).
  - `MandateCloseRules.closeMatch(draft: PendingMandateClose, currency: String, sale: Boolean): CloseMatchRequest` and `closeMandate(draft: PendingMandateClose, currency: String, sale: Boolean): CloseMandateRequest` — `periodicity = if (sale) PeriodicityType.NONE else PeriodicityType.MONTHLY`.

  The note keeps the served sentence structure (no names or mandate numbers, as today) and swaps only the sale's words:
  | Case | Sale note |
  |---|---|
  | owner, with a counterpart | `À la confirmation : le mandat n° $id est fermé, l'annonce est marquée vendue et le mandat de l'acheteur est fermé. Cette action est définitive.` |
  | owner, outside | `À la confirmation : le mandat n° $id est fermé et l'annonce est marquée vendue. Cette action est définitive.` |
  | search, with a counterpart | `À la confirmation : le mandat n° $id est fermé, l'annonce est marquée vendue et le mandat du vendeur est fermé. Cette action est définitive.` |
  | search, outside | unchanged from today (`Aucune annonce n'est retirée du marché.`) |

  The rental texts stay exactly as they are.

- [ ] **Step 1: Write the failing tests**

In `MandateCloseViewsTest`, add (reusing the file's `mandate`, `formatter` fixtures, and adding `saleMandate = mandate.copy(listingType = ListingType.SALE)`):

```kotlin
@Test
fun `a sale's steps are the buyer, the price and the confirmation`() {
    assertEquals(listOf("Acheteur", "Prix", "Confirmation"),
        MandateCloseViews.steps(counterpartApplicable = true, owner = true, sale = true).map { it.label })
    assertEquals(listOf("Bien", "Prix", "Confirmation"),
        MandateCloseViews.steps(counterpartApplicable = true, owner = false, sale = true).map { it.label })
}

@Test
fun `a rental's steps are unchanged`() {
    assertEquals(listOf("Client", "Loyer", "Confirmation"),
        MandateCloseViews.steps(counterpartApplicable = true, owner = true, sale = false).map { it.label })
}

@Test
fun `a sale's recap shows the final price without a month`() {
    val recap = MandateCloseViews.recap(saleMandate, null, null, true, null, null, emptyMap(), 15_000_000, formatter)
    assertEquals("15 000 000 FCFA", recap.price)
    assertEquals("À la confirmation : le mandat n° 201 est fermé et l'annonce est marquée vendue. Cette action est définitive.", recap.note)
}

@Test
fun `a sale's recap names the buyer's mandate`() {
    val owner = saleMandate.copy(mandateType = MandateType.LISTING)
    val recap = MandateCloseViews.recap(owner, 9001, selectedBuyer, false, null, null, emptyMap(), 15_000_000, formatter)
    assertTrue(recap.note.contains("et le mandat de l'acheteur est fermé"))
}

@Test
fun `a rental's recap is unchanged`() {
    val recap = MandateCloseViews.recap(mandate, null, null, true, null, null, emptyMap(), 240_000, formatter)
    assertEquals("240 000 FCFA / mois", recap.price)
    assertEquals("À la confirmation : le mandat n° 201 est fermé. Aucune annonce n'est retirée du marché. Cette action est définitive.", recap.note)
}

@Test
fun `an unknown listing type reads as a rental`() {
    val unknown = mandate.copy(listingType = ListingType.UNKNOWN)
    assertEquals(listOf("Client", "Loyer", "Confirmation"),
        MandateCloseViews.steps(counterpartApplicable = true, owner = true, sale = unknown.listingType == ListingType.SALE).map { it.label })
    assertEquals("240 000 FCFA / mois",
        MandateCloseViews.recap(unknown, null, null, true, null, null, emptyMap(), 240_000, formatter).price)
}
```

In `MandateCloseRulesTest`, extend the two request tests and add the sale ones:

```kotlin
@Test
fun `a sale's close match carries no periodicity`() {
    val draft = PendingMandateClose(matchId = 100, priceAmount = 15_000_000)
    assertEquals(
        CloseMatchRequest(Money(amount = 15_000_000, currency = "XAF", periodicity = PeriodicityType.NONE)),
        MandateCloseRules.closeMatch(draft, "XAF", sale = true),
    )
}

@Test
fun `a sale's outside close carries no periodicity`() {
    val draft = PendingMandateClose(outside = true, priceAmount = 15_000_000)
    assertEquals(
        CloseMandateRequest(Money(amount = 15_000_000, currency = "XAF", periodicity = PeriodicityType.NONE)),
        MandateCloseRules.closeMandate(draft, "XAF", sale = true),
    )
}

@Test
fun `a rental's requests keep the monthly periodicity`() {
    val draft = PendingMandateClose(matchId = 100, priceAmount = 240_000)
    assertEquals(PeriodicityType.MONTHLY, MandateCloseRules.closeMatch(draft, "XAF", sale = false).finalPrice.periodicity)
    assertEquals(PeriodicityType.MONTHLY, MandateCloseRules.closeMandate(draft, "XAF", sale = false).finalPrice.periodicity)
}
```

Update the two existing `close match` / `close mandate` tests to pass `sale = false` (their expectations do not change).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateCloseViewsTest+MandateCloseRulesTest'`
Expected: compilation error (`steps` takes 3 arguments; `CloseRecapView.price` doesn't exist).

- [ ] **Step 3: Implement `MandateCloseViews` and `MandateCloseRules`**

Add the `sale` parameter and the branches above; rename `CloseRecapView.rent` to `price`; add `sale` to `MandateCloseView` set by `of`; derive `sale` in `recap` from `mandate.listingType`; add the `sale` parameter to both `MandateCloseRules` request builders and pick the periodicity there. Keep every rental string character for character.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateCloseViewsTest+MandateCloseRulesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseRulesTest.kt
git commit -m "feat(npc): word the closing wizard for a sale" -m "Refs #258"
```

---

### Task 2: The closing page (controller message and template)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/close.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseTemplateTest.kt`

**Interfaces:**
- Consumes: Task 1's `MandateCloseViews.steps(…, sale)`, `recap`, `MandateCloseRules.closeMatch(…, sale)` / `closeMandate(…, sale)`, `MandateCloseView.sale`.
- Produces: the controller computes `val sale = mandate.listingType == ListingType.SALE` in each handler and passes it on; the price step's error is `sale ? "Indiquez le prix de vente final, en ${formatter().currency()}." : "Indiquez le loyer convenu, en ${formatter().currency()} par mois."`.

  The template renders, from `close.sale` (`c.sale`):
  - legend: `c.sale ? 'À quel acheteur le bien a-t-il été vendu ?' : 'Avec quel client le bien a-t-il été loué ?'` (owner) — the search's legend, the hint and the *Précédent* / *Retour* buttons do not change;
  - the search's hint for a sale: `'Les biens qui ont intéressé l'acheteur. Il peut aussi avoir acheté ailleurs.'`;
  - the owner's hint for a sale: `'Les acheteurs qui ont répondu intéressés. Il peut aussi avoir été vendu autrement.'`;
  - the *none* label: `c.sale ? 'Vendu à un autre acheteur' : 'Loué à un autre client'` (its meta line is unchanged);
  - the price step: label `c.sale ? 'Prix de vente final' : 'Loyer convenu'`, hint `c.sale ? "L'acte de vente est signé ? Confirmez le prix de vente final." : 'Le bail est signé ? Confirmez le loyer convenu.'`, suffix `th:text="${c.sale} ? ${currency} : ${currency} + ' / mois'"`;
  - the confirmation's term: `c.sale ? 'Prix de vente final' : 'Loyer convenu'`; the *Bien* / *Client* (→ *Acheteur*) terms as the prototype has them;
  - the page's `<meta name="description">` becomes the prototype's generic "Fermer un mandat : la contrepartie, le prix convenu, puis la confirmation."
  - **Not ported:** the prototype's standalone *unavailable* pane and its conflict alert block. The served page already shows those states inline (`candidatesUnavailable` note and the `conflict` flash); porting the prototype's blocks would restyle the rental screen, which acceptance criterion 6 forbids.

- [ ] **Step 1: Write the failing tests**

In `MandateCloseTemplateTest`, following the file's existing sale/rental fixtures and sign-in helper:

```kotlin
@Test
fun `a sale's closing page asks the buyer and the final sale price`() {
    // GET /mandates/{id}/close?step=counterpart for an APPROVED SALE listing mandate with a PUBLISHED listing
    //  and one ACCEPTED buyer match
    assertTrue(html.contains("À quel acheteur le bien a-t-il été vendu ?"))
    assertTrue(html.contains("Vendu à un autre acheteur"))
    // GET …?step=price
    assertTrue(html.contains("Prix de vente final"))
    assertTrue(html.contains("L'acte de vente est signé ? Confirmez le prix de vente final."))
    assertFalse(html.contains("FCFA / mois"))
    // GET …?step=confirm
    assertTrue(html.contains("Prix de vente final"))
    assertTrue(html.contains("marquée vendue"))
}

@Test
fun `a rental's closing page is unchanged`() {
    // Same three steps for an APPROVED RENTAL listing mandate
    assertTrue(html.contains("Avec quel client le bien a-t-il été loué ?"))
    assertTrue(html.contains("Loué à un autre client"))
    assertTrue(html.contains("Loyer convenu"))
    assertTrue(html.contains("FCFA / mois"))
    assertTrue(html.contains("Le bail est signé"))
}
```

In `MandateCloseControllerTest`:

```kotlin
@Test
fun `a sale's price error asks the final sale price`() {
    // POST /mandates/301/close/price with finalPrice = ""
    assertEquals(
        mapOf("finalPrice" to "Indiquez le prix de vente final, en FCFA."),
        result.modelAndView!!.model["errors"],
    )
}

@Test
fun `a sale's confirm sends no periodicity`() {
    // POST …/close/confirm after the session holds a matchId and a price; capture the client call
    assertEquals(PeriodicityType.NONE, requestCaptor.firstValue.finalPrice.periodicity)
}

@Test
fun `a rental's price error is unchanged`() {
    assertEquals(
        mapOf("finalPrice" to "Indiquez le loyer convenu, en FCFA par mois."),
        result.modelAndView!!.model["errors"],
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateCloseControllerTest+MandateCloseTemplateTest'`
Expected: FAIL — the page still says *Loyer convenu* / `FCFA / mois`, and the error still asks for the rent.

- [ ] **Step 3: Update the controller and port the prototype's markup**

In `MandateCloseController`: compute `sale` from the loaded mandate in `show`, `counterpart`, `price` and `confirm`; pass it to `MandateCloseViews.steps(…, sale)` and `MandateCloseRules.closeMatch/closeMandate(…, sale)`; make the price error sale-aware. In `templates/mandate/close.html`: port the labels above from `v2/mandate/close.html`, keeping the file's structure, class names and the prototype's `close.html` header comment style. Keep every rental path identical (the ternaries' `else` branch is today's text).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateCloseControllerTest+MandateCloseTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/mandate/close.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCloseTemplateTest.kt
git commit -m "feat(npc): close a sale mandate on its sale price" -m "Refs #258"
```

---

### Task 3: The mandate page after the closing

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `Mandate` (`listingType`, `finalPrice`, `price.currency`), `MandateLabels.price`, `MandateController.outcome(outcome, view, model)`.
- Produces:
  - `MandateView` gains `val sale: Boolean` (`mandate.listingType == ListingType.SALE`).
  - `MandateViews`: `finalPrice = mandate.finalPrice?.let { MandateLabels.price(Money(it, mandate.price.currency, if (sale) PeriodicityType.NONE else PeriodicityType.MONTHLY), formatter) }` (so a rental keeps `… FCFA / mois`, a sale `… FCFA`).
  - `MandateViews.dealLine`: for a sale, `clientLabel = "Acheteur : $name"` (today `Client : $name`) and `outside = "Vendu à un autre acheteur"` (today `Client hors de vos recherches`); a search's outside stays `Bien hors de vos annonces`.
  - `MandateController.outcome`: `CLOSED -> if (view.sale) "Mandat fermé : félicitations pour cette vente !" else "Mandat fermé : félicitations pour ce bail !"`.
  - `templates/mandate.html`, closed panel: title `m.sale ? 'Vente conclue' : 'Bail signé'`; the sentence `Mandat fermé le …, ` + `m.sale ? 'au prix de' : 'au loyer de'` + the price; the *Et ensuite ?* hint `m.sale ? "Une fois l'acte de vente signé, fermez le mandat (en bas de page) en indiquant le prix de vente final." : today's text`; the *Et ensuite* sentence's first part (presenting the property / proposing properties) is unchanged.

- [ ] **Step 1: Write the failing tests**

In `MandateViewsTest`:

```kotlin
@Test
fun `a closed sale shows its final price without a month and its buyer`() {
    val sale = mandate.copy(
        listingType = ListingType.SALE,
        mandateType = MandateType.LISTING,
        status = MandateStatus.CLOSED,
        finalPrice = 15_000_000,
        dealMatchId = 9001,
    )
    val view = MandateViews.of(sale, deal, contract, accepted = null, customer, locations, formatter, now)
    assertEquals(true, view.sale)
    assertEquals("15 000 000 FCFA", view.finalPrice)
    assertEquals("Acheteur : Sandrine Eboa", view.clientLabel)
}

@Test
fun `a closed sale without a counterpart is sold outside`() {
    val view = MandateViews.of(sale.copy(dealOutside = true, dealMatchId = null), null, contract, null, customer, locations, formatter, now)
    assertEquals("Vendu à un autre acheteur", view.outside)
}

@Test
fun `a closed rental is unchanged`() {
    val view = MandateViews.of(closedRental, deal, contract, null, customer, locations, formatter, now)
    assertFalse(view.sale)
    assertEquals("240 000 FCFA / mois", view.finalPrice)
    assertEquals("Client : Sandrine Eboa", view.clientLabel)
}

@Test
fun `a closed mandate without a final price leaves the line out`() {
    assertEquals(null, MandateViews.of(sale.copy(finalPrice = null), null, contract, null, customer, locations, formatter, now).finalPrice)
}

@Test
fun `an unknown listing type reads as a rental`() {
    assertFalse(MandateViews.of(mandate.copy(listingType = ListingType.UNKNOWN), deal, contract, null, customer, locations, formatter, now).sale)
}
```

(`deal`, `contract`, `customer`, `locations`, `formatter`, `now`, `mandate`, `closedRental` are the file's existing fixtures; add the ones it lacks.)

In `MandateControllerTest`:

```kotlin
@Test
fun `a closed sale congratulates the sale`() {
    // GET /mandates/301 with the `closed` flash for a CLOSED SALE mandate
    assertEquals("Mandat fermé : félicitations pour cette vente !", result.modelAndView!!.model["outcomeText"])
}

@Test
fun `a closed rental congratulates the lease`() {
    assertEquals("Mandat fermé : félicitations pour ce bail !", result.modelAndView!!.model["outcomeText"])
}
```

In `MandateTemplateTest`:

```kotlin
@Test
fun `a closed sale reads Vente conclue with its sale price`() {
    assertTrue(html.contains("Vente conclue"))
    assertTrue(html.contains("au prix de"))
    assertTrue(html.contains("15 000 000 FCFA"))
    assertFalse(html.contains("au loyer de"))
}

@Test
fun `a closed rental reads Bail signé with its rent`() {
    assertTrue(html.contains("Bail signé"))
    assertTrue(html.contains("au loyer de"))
    assertTrue(html.contains("240 000 FCFA / mois"))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateViewsTest+MandateControllerTest+MandateTemplateTest'`
Expected: compilation error (`MandateView.sale` doesn't exist) then FAIL on *Vente conclue* / *au prix de*.

- [ ] **Step 3: Implement the view, the outcome and the template**

Add `sale` to `MandateView` and set it in `MandateViews.of`; format `finalPrice` through `MandateLabels.price` with the periodicity above; make `dealLine` and `outcome` sale-aware; port the closed panel's and the *Et ensuite ?* hint's sale wording from `v2/mandate.html`, keeping the rental branches identical.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateViewsTest+MandateControllerTest+MandateTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show a sale mandate as sold" -m "Refs #258"
```

---

### Task 4: Module verification and the rental sweep

**Files:**
- No production file: verification only.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: a green `ndopify-partner-central` build and the rental regression evidence.

- [ ] **Step 1: Run the whole module**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint clean, every test, the JaCoCo gate). No MySQL needed.

- [ ] **Step 2: Re-run the server's closing evidence (needs local MySQL)**

Run: `cd modules/ndopify-server && mvn test -Dtest='CloseMandateEndpointTest+CloseMatchEndpointTest'`
Expected: PASS — `the published listing of a sale is sold`, `closing the search on its match closes both mandates`, `another agent's listing is only recorded` (this story changes no server code; the step proves the behaviour the page relies on).

- [ ] **Step 3: Re-read the rental diff**

Run: `git diff main -- modules/ndopify-partner-central/src/main/resources/templates/mandate.html modules/ndopify-partner-central/src/main/resources/templates/mandate/close.html`
Expected: every changed line is inside a `th:text="${…sale} ? '…' : '…'"` (or a `th:if`) whose `else` branch is today's text; no rental string moved, no class or id changed. Stop and fix the branch otherwise.

- [ ] **Step 4: Check the prototype side by side**

Run: `cd modules/ndopify-partner-central/src/main/html && python3 -m http.server 8899`, then open
`http://localhost:8899/v2/mandate.html?id=301&listing=published` (Fermer le mandat), `…/v2/mandate/close.html?id=301&listing=published`, `…/v2/mandate/close.html?id=303&step=listing`, and the rental `…/v2/mandate/close.html?id=203&listing=published`.
Expected: the served pages (`mvn spring-boot:run` on 8081, tenant pointing at `localhost:8081`) show the same labels, order and states as the prototype; the rental pages are unchanged.

- [ ] **Step 5: Commit (only if Step 3 or 4 required a fix)**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/resources/templates/mandate/close.html
git commit -m "fix(npc): keep the rental closing screens unchanged" -m "Refs #258"
```
