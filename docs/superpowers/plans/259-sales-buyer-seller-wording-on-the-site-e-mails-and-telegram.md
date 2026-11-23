# Buyer and Seller Wording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Word the M1 sale flows — the public site, the agent's pages and the e-mails/Telegram messages — for buyers and sellers, without changing how any of them works nor a single rental page.

**Architecture:** Copy-only where the flow exists, one new public route per sale landing page, and type-aware labels in the two web apps and in `ndopify-server`'s mail/bot layers. Every change is gated on the mandate's or listing's `listingType`: a `SALE` mandate/listing gets the sale words, a `RENTAL` one keeps today's bytes.

**Tech Stack:** Kotlin, Spring Boot + Thymeleaf, MJML + Mustache, Telegram; JUnit/`kotlin.test`, `mockito-kotlin`, `MockMvc` standalone + real Thymeleaf template tests.

**Spec:** issue #259 (the story body is the design artifact: decisions, ACs, assumptions) and the approved prototype of 2026-11-23 in `modules/ndopify-web/src/main/html/v2` and `modules/ndopify-partner-central/src/main/html/v2`. The epic roadmap is `docs/superpowers/plans/2026-11-22-v2-property-sales.md`. The prototype is the copy source: port it, do not redesign it.

## Global Constraints

- The approved prototype is the baseline. Every page's French copy comes verbatim from it (the route map is in Task 1).
- Everything shown to a user goes through `TenantFormatter` / `MandateLabels`: no hard-coded date pattern, amount or "FCFA".
- A RENTAL flow keeps today's bytes: same text, same order, same links. The only shared change a rental page gains is the site shell (nav and footer tagline) and the hero's project switch.
- The story's assumptions are binding: *Acheteur* / *Vendeur* replace *Locataire* / *Bailleur* on a sale mandate; *client* stays where it is neutral (*Envoyer au client*); no figure and no payer for the sale commission.
- Never promise, on any page or message: a payment through Ndopify, a buyer's offer, a notary service, a title-deed check, an agent specialisation, off-market inventory, or a catalogue of properties to browse.
- All copy is French; code, comments, logs and docs are English.
- No `ndopify-dto`, Flyway migration, `ndopify-web-core` or `ndopify-esignature` change. No new ndopify-server endpoint, no new tenant setting.
- No new JavaScript dependency; every changed page works without JavaScript (a form posts, a link navigates).
- Tests: `kotlin.test` assertions, `mockito-kotlin` mocks, tests mirror the main package. The web apps never need MySQL; `ndopify-server`'s endpoint tests do (its mail/bot tests do not).
- Run `mvn validate` (ktlint) before each commit. Final per-module gate: `mvn verify`.

## Review Focus

- **A new public route missing from `SecurityConfiguration`** turns `/louer`, `/acheter` or `/owner/sell` into a 403/redirect. Each landing-page task adds its matcher and a check of it.
- **A sale listing that stored a rental term before this story** must never render a deposit, an advance, a lease term, a notice period or furniture: the terms block, the checklist summary and the published sheet gate on the listing type, not on the stored value.
- **A shared label leaking into a rental**: `MandateLabels`, `ListingRules`, `MatchRules`, `PublishedListingViews` and the bot/mail labels are used by both types; each task's rental case pins today's output.
- **A sale match/summary still saying "loyer", "/ mois" or "emménagement"**: the match page, the match cards and `MandateLabels.criteria` derive their terms from `listingType`.
- **Copy drifting from behaviour** (a specialist agent, off-market stock, a title check, a notary service, a payment): the landing-page and listing-page tests assert the disclaimer lines, not only the sale claims.

---

### Task 1: Site shell, goal-picker home and the renter page at `/louer`

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/home/HomeController.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/site-header.html`
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/site-footer.html`
- Modify: `modules/ndopify-web/src/main/resources/templates/index.html`, `owner.html`, `agent.html`, `listing.html`, `match.html`, `visit.html` (the `header(...)` call and the footer tagline)
- Create: `modules/ndopify-web/src/main/resources/templates/louer.html`
- Modify: `modules/ndopify-web/src/main/assets/css/styles.css` (the goal picker, the switch, the callout)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/home/HomeControllerTest.kt`, `IndexTemplateTest.kt`, new `LouerTemplateTest.kt`

**Interfaces:**
- Produces: `HomeController.VIEW = "index"` (goal picker, no model attribute), `HomeController.RENTER_VIEW = "louer"` (model `findPropertyUrl`), the header fragment's nav rendered whenever `menuLabel != null` and its `active` values `louer`, `acheter`, `owner`, `agent` (or null).
- Consumes: `PartnerCentralUrls.of(tenant, "/find-property")`.

- [ ] **Step 1: Write the failing controller tests**

  In `HomeControllerTest`, replace the `/` cases with:

  ```kotlin
  @Test fun `the root is the goal picker`() {
      val result = index(context(Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm")))
      assertEquals("index", result.modelAndView?.viewName)
      assertNull(result.modelAndView?.model?.get("findPropertyUrl"))
  }

  @Test fun `louer carries the tenant's find-property URL`() {
      val result = index(context(Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm/")), "/louer")
      assertEquals("louer", result.modelAndView?.viewName)
      assertEquals("https://partner.ndopify.cm/find-property", result.modelAndView?.model?.get("findPropertyUrl"))
  }

  @Test fun `louer without a partner central URL`() {
      assertNull(index(context(Tenant(id = 1, partnerCentralUrl = null)), "/louer").modelAndView?.model?.get("findPropertyUrl"))
  }
  ```

- [ ] **Step 2: Run the tests to verify they fail**

  Run: `cd modules/ndopify-web && mvn test -Dtest=HomeControllerTest`
  Expected: FAIL — `louer` view unknown, `/louer` 404.

- [ ] **Step 3: Implement the controller and the security matcher**

  `HomeController`:

  ```kotlin
  companion object {
      const val VIEW = "index"          // the goal picker
      const val RENTER_VIEW = "louer"   // today's renter page
  }

  @GetMapping("/")
  fun index(): String = VIEW

  @GetMapping("/louer")
  fun rent(model: Model): String {
      model.addAttribute("findPropertyUrl", PartnerCentralUrls.of(model.getAttribute("tenant") as Tenant, "/find-property"))
      return RENTER_VIEW
  }
  ```

  `SecurityConfiguration`: add `"/louer"` to the first `requestMatchers(...)` permit list (keep `"/", "/agent", "/owner"`).

- [ ] **Step 4: Port the shell and the two pages**

  - `site-header.html`: render the nav whenever `menuLabel != null` (`th:if="${menuLabel != null}"`); four links — `@{/louer}` *Louer* (active `louer`), `@{/acheter}` *Acheter* (active `acheter`), `@{/owner}` *Propriétaires* (active `owner`), `@{/agent}` *Agents* (active `agent`).
  - `site-footer.html`: same four links; every template's footer tagline becomes `© 2026 Ndopify. Des agents vérifiés, à votre service.`
  - `index.html`: port `modules/ndopify-web/src/main/html/v2/index.html` — *Quel est votre projet immobilier ?*, the two groups *Je cherche un bien* / *J'ai un bien*, four `.goal-card` links to `/louer`, `/acheter`, `/owner`, `/owner/sell`, the promise, the four steps, the agent band. Head from the prototype's `<title>` / meta; canonical `${#strings.isEmpty(tenant.publicPortalUrl) ? null : tenant.publicPortalUrl}`; `header(null, 'Menu', true)`.
  - `louer.html`: today's `index.html` body (hero, guarantees, steps, FAQ) plus the prototype's `goal-switch` (*Louer* current → `/louer`, *Acheter* → `/acheter`); the call to action keeps `findPropertyUrl`; head from `v2/louer.html`; canonical `publicPortalUrl + '/louer'`; `header('louer', 'Menu', true)`.
  - `owner.html`, `agent.html`: `header('owner'|'agent', …)`; Task 3 adds the owner page's `goal-switch`.
  - `listing.html`, `match.html`, `visit.html`: `header(null, 'Menu', true)`.
  - `styles.css`: copy the prototype's `.goal-switch`, `.goal-groups`, `.goal-card`, `.goal-card__title`, `.goal-card__text`, `.goal-card__arrow` and `.callout` rules from `modules/ndopify-web/src/main/html/v2/assets/css/styles.css`.

- [ ] **Step 5: Write the failing template tests**

  Rewrite `IndexTemplateTest` (attributes: `tenant` only) to assert:

  ```kotlin
  assertEquals("Quel est votre projet immobilier ?", doc.selectFirst("h1")!!.text())
  assertEquals(listOf("/louer", "/acheter", "/owner", "/owner/sell"), doc.select("a.goal-card").map { it.attr("href") })
  assertEquals(listOf("Louer", "Acheter", "Propriétaires", "Agents"), doc.select(".site-nav__list a").map { it.text() })
  assertTrue(doc.select("a.goal-card").isEmpty == false)
  assertTrue(doc.select("article, [data-listing]").isEmpty()) // nothing to browse
  ```

  New `LouerTemplateTest` (real Thymeleaf engine, as `IndexTemplateTest` did): with `findPropertyUrl`,
  assert the title *Confiez votre recherche de logement à un agent vérifié*, `a.btn-primary` *Lancer ma recherche*
  with `href = findPropertyUrl` at least three times, the switch links `["/louer", "/acheter"]`, and the rental line
  *1 mois* / *à la signature du bail*; without the attribute, no *Lancer ma recherche*.

- [ ] **Step 6: Run the tests to verify they pass**

  Run: `cd modules/ndopify-web && mvn test -Dtest='HomeControllerTest,IndexTemplateTest,LouerTemplateTest'`
  Expected: PASS. Then `grep -n '"/louer"' src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt` → a match.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-web/src/main/kotlin modules/ndopify-web/src/main/resources/templates modules/ndopify-web/src/main/assets/css/styles.css modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/home
  git commit -m "feat(web): add the goal-picker home and the renter page at /louer" -m "Refs #259"
  ```

---

### Task 2: The buyer landing page at `/acheter`

**Files:**
- Create: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/buyer/BuyerController.kt`
- Create: `modules/ndopify-web/src/main/resources/templates/acheter.html`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: new `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/buyer/BuyerControllerTest.kt`, `BuyerTemplateTest.kt`

**Interfaces:**
- Produces: `BuyerController.VIEW = "acheter"`; model attribute `findPropertyUrl` = `{partnerCentralUrl}/find-property?type=SALE` or null.

- [ ] **Step 1: Write the failing controller test**

  ```kotlin
  @Test fun `buyer page carries the sale find-property URL`() {
      val result = page(Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm/"))
      assertEquals("acheter", result.modelAndView?.viewName)
      assertEquals("https://partner.ndopify.cm/find-property?type=SALE", result.modelAndView?.model?.get("findPropertyUrl"))
  }

  @Test fun `no URL without a partner central URL`() {
      assertNull(page(Tenant(id = 1, partnerCentralUrl = " ")).modelAndView?.model?.get("findPropertyUrl"))
  }
  ```

  (Copy the `MockMvc` standalone + `TenantAdvice` setup from `HomeControllerTest`.)

- [ ] **Step 2: Run it to verify it fails**

  Run: `cd modules/ndopify-web && mvn test -Dtest=BuyerControllerTest`
  Expected: FAIL — no mapping for `/acheter`.

- [ ] **Step 3: Implement the controller and permit the route**

  `BuyerController` mirrors `HomeController`: `@GetMapping("/acheter")`, model `findPropertyUrl` from
  `PartnerCentralUrls.of(tenant, "/find-property?type=SALE")`, view `acheter`. Add `"/acheter"` to the permit list.

- [ ] **Step 4: Port the page**

  Port `modules/ndopify-web/src/main/html/v2/acheter.html` into `templates/acheter.html`: head title
  *Ndopify — Confiez votre projet d'achat à un agent vérifié* and its description, canonical `publicPortalUrl + '/acheter'`,
  `header('acheter', 'Menu', true)`, the `goal-switch` (*Louer* → `/louer`, *Acheter* current), hero, proof stats,
  the four agent cards, the *pour les acheteurs à l'étranger* callout, the four steps, the FAQ, and every
  *Lancer ma recherche* as `th:if="${findPropertyUrl}"` / `th:href="${findPropertyUrl}"`. Replace every relative
  prototype link by its route (`louer.html` → `@{/louer}`, `index.html` → `@{/}`, `bailleur.html` → `@{/owner}`,
  `vendre.html` → `@{/owner/sell}`).

- [ ] **Step 5: Write the failing template test**

  `BuyerTemplateTest` asserts: the title and description; *Lancer ma recherche* appears on every `a.btn-primary`
  with `href = "/find-property?type=SALE"` (at least three); the page says *un même agent traite les locations et les
  ventes*, *un pourcentage du prix de vente*, *Ndopify vérifie l'agent, pas les documents du bien*, *Ndopify ne perçoit
  aucun paiement*; it does **not** contain *affichés nulle part*, *spécialiste*, *off-market*, *catalogue*; without the
  attribute, no *Lancer ma recherche*.

- [ ] **Step 6: Run the tests**

  Run: `cd modules/ndopify-web && mvn test -Dtest='BuyerControllerTest,BuyerTemplateTest'` → PASS.
  Check `SecurityConfiguration` contains `"/acheter"`.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-web
  git commit -m "feat(web): add the buyer landing page" -m "Refs #259"
  ```

---

### Task 3: The seller landing page at `/owner/sell`

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/owner/OwnerController.kt`
- Create: `modules/ndopify-web/src/main/resources/templates/owner/sell.html`
- Modify: `modules/ndopify-web/src/main/resources/templates/owner.html`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/config/SecurityConfiguration.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/owner/OwnerControllerTest.kt`, `OwnerTemplateTest.kt`, new `SellTemplateTest.kt`

**Interfaces:**
- Produces: `OwnerController.SELL_VIEW = "owner/sell"`; on `/owner/sell`, `offerPropertyUrl` = `{partnerCentralUrl}/offer-property?type=SALE` or null. `/owner` keeps `offerPropertyUrl` = `/offer-property`.

- [ ] **Step 1: Write the failing controller tests**

  ```kotlin
  @Test fun `sell page carries the sale offer-property URL`() {
      val result = index(context(Tenant(id = 1, partnerCentralUrl = "https://partner.ndopify.cm")), "/owner/sell")
      assertEquals("owner/sell", result.modelAndView?.viewName)
      assertEquals("https://partner.ndopify.cm/offer-property?type=SALE", result.modelAndView?.model?.get("offerPropertyUrl"))
  }

  @Test fun `owner page keeps the rental URL`() { /* "/owner" → "…/offer-property" */ }

  @Test fun `sell page without a partner central URL`() { /* null */ }
  ```

- [ ] **Step 2: Run them to verify they fail** — `mvn test -Dtest=OwnerControllerTest` → FAIL for `/owner/sell`.

- [ ] **Step 3: Implement the mapping and permit `/owner/sell`**

  Add `@GetMapping("/owner/sell")` to `OwnerController` returning `SELL_VIEW` (template `owner/sell.html`) with
  `PartnerCentralUrls.of(tenant, "/offer-property?type=SALE")`. Add `"/owner/sell"` to the permit list.

- [ ] **Step 4: Port the seller page and adapt the rental owner page**

  - `owner/sell.html` from `v2/vendre.html`: title *Ndopify — Confiez la vente de votre bien à un agent vérifié*,
    canonical `publicPortalUrl + '/owner/sell'`, `header('owner', 'Menu', true)`, the switch (*Mettre en location* →
    `/owner`, *Vendre* current), hero, proof stats (*100 % du prix de vente versé directement à vous*, *un pourcentage
    du prix de vente, écrit dans le mandat*), the four agent cards, the buyers' callout, the steps, the FAQ; every
    *Proposer mon bien* gated on `offerPropertyUrl`.
  - `owner.html`: insert the prototype's `goal-switch` before the hero eyebrow; nothing else changes.
  - The FAQ must include the deed at the notary and no title check (*Ndopify ne les contrôle pas*), no specialisation.

- [ ] **Step 5: Write the failing template tests**

  `SellTemplateTest`: title/meta; *Proposer mon bien* on every `a.btn-primary` with the sale URL; the direct-payment,
  commission, notary-deed and no-title-check lines; no *spécialiste*, *titre foncier vérifié*, *paiement* through
  Ndopify; no CTA without the attribute. `OwnerTemplateTest`: the switch links `["/owner", "/owner/sell"]` and the
  rental wording (*1 mois* / *à la signature du bail*) still there.

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='OwnerControllerTest,OwnerTemplateTest,SellTemplateTest'` → PASS;
  `SecurityConfiguration` contains `"/owner/sell"`.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-web
  git commit -m "feat(web): add the seller landing page at /owner/sell" -m "Refs #259"
  ```

---

### Task 4: The public listing page of a sale

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingInterestRules.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html`, `templates/listing.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`, `ListingTemplateTest.kt`, `ListingInterestRulesTest.kt`

**Interfaces:**
- Produces: `ListingPage.sale: Boolean`, `ListingPage.conditionsTitle: String` ("Prix et conditions" / "Conditions de location"); `ListingPages.of` fills the sale terms; `ListingInterestRules.validate(form, sale: Boolean = false)`.
- Consumes: `Listing.listingType`.

- [ ] **Step 1: Write the failing page tests**

  In `ListingPagesTest`, a sale listing (`listingType = SALE`, `pricePeriodicityType = NONE`, `availableFrom` in the
  future, a deposit/advance/lease/notice/furniture set — the stale-value case):

  ```kotlin
  assertTrue(page.sale)
  assertEquals("Prix et conditions", page.conditionsTitle)
  assertEquals(listOf("Prix", "Disponible"), page.terms.map { it.first })
  assertEquals(listOf("Disponible le 1 déc. 2026"), page.asideTerms)
  assertNull(page.pricePeriod)
  ```
  and the existing rental assertions still pass (title, terms with *Loyer*, *Caution*, *Avance*, *Durée du bail*,
  *Préavis*, `asideTerms` with caution/advance).

- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=ListingPagesTest` → FAIL (no `sale`/`conditionsTitle`, rental terms).

- [ ] **Step 3: Implement the sale terms**

  - `ListingPage`: add `sale: Boolean` and `conditionsTitle: String` (after `typeBadge`).
  - `ListingPages.of`: `val sale = listing.listingType == ListingType.SALE`; `conditionsTitle = if (sale) "Prix et conditions" else "Conditions de location"`; when `sale`, `terms` = `listOfNotNull("Prix" to price, "Disponible" to (available?.let { "À partir du $it" } ?: "Immédiatement"))` and `asideTerms` = `listOfNotNull(available?.let { "Disponible le $it" } ?: "Disponible immédiatement")`; the closed listing keeps empty lists. The rental branches are untouched.
  - `ListingInterestRules.validate(form, sale = false)`: the `movingPeriod` message is *Choisissez quand vous souhaitez acheter.* when `sale`, today's *Choisissez votre date d'emménagement.* otherwise.
  - `ListingController.interest`: when `errors["movingPeriod"] != null`, read the listing once (`listingClient.get(id).listingType == ListingType.SALE`, a failure read as rental) and, for a sale, replace that one message with the sale wording before rendering. No extra call on a valid or otherwise-invalid submission.

- [ ] **Step 4: Port the sale page markup**

  `fragments/listing-body.html`, gated on `listing.sale`: *Bien audité* badge and *Bien audité par Ndopify le …* note
  (*Ndopify vérifie l'agent, pas les documents du bien*), *À propos de ce bien*, gallery label *Photos du bien*;
  the conditions `<h2>` prints `${listing.conditionsTitle}`, its caption *Prix et conditions du bien* for a sale.
  `listing.html`: the `#visit` popup's moving-period legend is *Quand souhaitez-vous acheter ?* for a sale, the lightbox
  label *Photos du bien*, and the price card/bar render (already) `priceAmount` without a period. A sale's
  `asideTerms` already carries no rental term; do not add any.

- [ ] **Step 5: Write the failing template tests**

  `ListingTemplateTest.sale listing page`: *À vendre*, *Prix et conditions*, *Prix*, *Disponible*, *Quand souhaitez-vous
  acheter ?*, *Bien audité*; **no** *Loyer*, *Caution*, *Avance*, *Durée du bail*, *Préavis* in the sale body, the price
  card or the `#visit` popup, and no *logement* in the listing body outside the `#ask` drawer (the pre-existing,
  trigger-less assistant placeholder, out of this story's scope).
  `ListingTemplateTest.sale listing with a stored rental term` (a fixture with deposit/lease): still none of them.
  `ListingInterestRulesTest.a sale words the missing purchase horizon`.

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='ListingPagesTest,ListingTemplateTest,ListingInterestRulesTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-web
  git commit -m "feat(web): word a sale listing's public page" -m "Refs #259"
  ```

---

### Task 5: The proposal page of a sale search

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/match.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt`, `MatchTemplateTest.kt`

**Interfaces:**
- Produces: `MatchPage.sale: Boolean`; `MatchPages.of` words the title, the flash, the summary fallback and the availability reason from `mandate.listingType`.

- [ ] **Step 1: Write the failing page tests**

  ```kotlin
  @Test fun `a sale search's proposal is worded for a buyer`() {
      val page = MatchPages.of(/* a SALE SEARCH mandate, a villa at 240M, available in a month */)
      assertTrue(page.sale)
      assertEquals("Serge vous propose ce bien", page.title)
      assertEquals("Pour votre recherche : villa 4 chambres à Bonapriso, jusqu'à 250 000 000 FCFA.", page.searchSummary)
      assertEquals("Disponible le 1 déc. 2026, avant votre achat", page.reasons.last().text)
      assertEquals("C'est noté : ce bien vous intéresse, votre agent est prévenu.", MatchPages.of(/* answer = "accepted" */).flash)
  }
  ```
  and the existing rental tests still pass (`ce logement`, `/ mois`, *avant votre emménagement*).

- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=MatchPagesTest` → FAIL.

- [ ] **Step 3: Implement the wording**

  - `MatchPage`: add `sale: Boolean` after `state`.
  - `MatchPages.of`: `val sale = mandate.listingType == ListingType.SALE`; title `"$firstName vous propose " + (if (sale) "ce bien" else "ce logement")`; the two flash messages use *ce bien* when `sale`; `searchSummary`'s type fallback is *bien* when `sale`; `reasons`' availability line is *Disponible le X, avant votre achat* / *Disponible avant votre achat* when `sale`, today's *avant votre emménagement* / *Disponible immédiatement* otherwise. The search budget already drops *« / mois »* for a `NONE` periodicity: keep using `ListingPages.PERIODS`.
  - `match.html`, gated on `page.sale`: *Pourquoi ce bien ?*, *Ce bien vous intéresse ?* (answer block, popup, status), *Ce bien n'est plus disponible.* / *Il a été vendu ou retiré.*, *Vous avez écarté ce bien.* / *d'autres biens*, *Bien écarté*, *Serge vous fait visiter le bien…* (two hints), the lightbox label *Photos du bien*. The rental strings stay in the other branch, byte for byte.

- [ ] **Step 4: Write the failing template test**

  `MatchTemplateTest.sale proposal`: the sale title, *Pourquoi ce bien ?*, *Ce bien vous intéresse*, *Pourquoi pas ce bien ?*, *Disponible avant votre achat*, no *logement* in the page body (the footer is now sale-neutral), and the same for the `?state=` fixtures (accepted, rejected, expired, unavailable). The rental template test is unchanged and must still pass.

- [ ] **Step 5: Run the tests** — `mvn test -Dtest='MatchPagesTest,MatchTemplateTest'` → PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add modules/ndopify-web
  git commit -m "feat(web): word the sale search's proposal page" -m "Refs #259"
  ```

---

### Task 6: The visit page of a sale match

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPage.kt`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPages.kt`
- Modify: `modules/ndopify-web/src/main/resources/templates/visit.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitPagesTest.kt`, `VisitTemplateTest.kt`

**Interfaces:**
- Produces: `VisitPage.sale: Boolean` (from the listing's type; false when the listing is unreadable).

- [ ] **Step 1: Write the failing page test**

  ```kotlin
  @Test fun `a sale match's visit is worded for a buyer`() {
      val page = VisitPages.of(/* visit of a SALE listing */)
      assertTrue(page.sale)
  }
  ```

- [ ] **Step 2: Run to verify it fails** — `mvn test -Dtest=VisitPagesTest` → FAIL.

- [ ] **Step 3: Implement and port**

  `VisitPage`: add `sale: Boolean`; `VisitPages.of`: `sale = listing?.listingType == ListingType.SALE`.
  `visit.html`, gated on `page.sale`: *Le bien* (both visit types), *vous recevez un e-mail pour nous dire si le bien
  vous intéresse*, *Le bien vous intéresse : X vous recontacte pour la suite de votre achat.*, *d'autres biens*, and the
  three *Le bien vous intéresse toujours ?* lines. Rental strings unchanged. (The fee, slots, cancel and feedback
  blocks are type-agnostic: leave them.)

- [ ] **Step 4: Write the failing template test**

  `VisitTemplateTest.sale visit`: the page body contains *Le bien*, *la suite de votre achat*, and none of *logement*,
  *dossier* or *bail*; the rental template test still passes and still says *Le logement*.

- [ ] **Step 5: Run the tests** — `mvn test -Dtest='VisitPagesTest,VisitTemplateTest'` → PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add modules/ndopify-web
  git commit -m "feat(web): word the sale match's visit page" -m "Refs #259"
  ```

---

### Task 7: The agent's cards and mandate page for a sale

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/AssignmentView.kt`, `AssignmentViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt`, `MandateCards.kt`, `MandateView.kt`, `MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`, `templates/mandate.html`, `templates/fragments/mandate-card.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`, `MandateRulesTest.kt`, `MandateCardsTest.kt`, `MandateViewsTest.kt`, `MandateTemplateTest.kt`, `ui/home/AssignmentViewsTest.kt`, `HomeTemplateTest.kt`

**Interfaces:**
- Produces:
  - `MandateLabels.party(type: MandateType, listingType: ListingType? = null): String` — SALE: LISTING → *Vendeur*, else *Acheteur*; today's *Propriétaire* / *Locataire* otherwise.
  - `MandateLabels.amountTerm(owner: Boolean, listingType: ListingType?): String` — *Prix* for a sale listing, *Budget* for a sale search, today's *Loyer* / *Budget*.
  - `MandateLabels.listingPriceTerm(listingType: ListingType?): String` — *Prix* for SALE, *Loyer* otherwise.
  - `MandateLabels.dateTerm(owner: Boolean, listingType: ListingType?): String` — *Achat prévu* for a sale search, *Disponibilité* for a listing, *Emménagement* for a rental search.
  - `MandateRules.words(type, listingType)` — the sale `role` is *Vendeur* / *Acheteur*; the other fields keep today's neutral *client* words.
  - `AssignmentView.sale: Boolean`, `MandateCardView.sale: Boolean`, `MandateView.nextStepHint: String`.
- Consumes: `Mandate.listingType`, `Mandate.mandateType`.

- [ ] **Step 1: Write the failing label tests**

  ```kotlin
  assertEquals("Vendeur · Terrain", MandateLabels.title(saleListing()))
  assertEquals("Acheteur · Maison", MandateLabels.title(saleSearch()))
  assertEquals("Prix", MandateLabels.criteria(saleListing(), locations, formatter).first { it.first == "Prix" }.first)
  assertEquals("Achat prévu", MandateLabels.criteria(saleSearch(), locations, formatter).first { it.first == "Achat prévu" }.first)
  assertEquals("Vendeur", MandateRules.words(MandateType.LISTING, ListingType.SALE).role)
  assertEquals("Bailleur", MandateRules.words(MandateType.LISTING, ListingType.RENTAL).role)
  ```

- [ ] **Step 2: Run to verify they fail** — `cd modules/ndopify-partner-central && mvn test -Dtest=MandateLabelsTest` → FAIL.

- [ ] **Step 3: Implement the labels**

  Add the functions above; `MandateLabels.title` uses `party(mandate.mandateType, mandate.listingType)`; `criteria`
  replaces its hard-coded *Loyer* / *Budget* and *Disponibilité* / *Emménagement* with `amountTerm` / `dateTerm`.
  `MandateRules.words(type, listingType)` returns the existing `OWNER` / `CLIENT` for a rental and the same words with
  `role = "Vendeur"` / `role = "Acheteur"` for a sale.

- [ ] **Step 4: Use them in the cards and the mandate page**

  - `AssignmentViews.of`: `sale = mandate.listingType == ListingType.SALE`; `title = MandateLabels.party(...)`;
    `priceTerm = amountTerm(owner, listingType)`; `movingTerm = dateTerm(owner, listingType)`.
  - `MandateCards.of`: `sale` set; the facts come from `MandateLabels.criteria` (already fixed); the card's compact
    price already drops *« / mois »* for a `NONE` periodicity.
  - `MandateViews.of`: `words = MandateRules.words(mandate.mandateType, mandate.listingType)`; add
    `nextStepHint = when { sale && owner -> "Présentez le bien aux acheteurs et organisez les visites."; sale -> "Proposez des biens à l'acheteur et organisez les visites."; owner -> "Présentez le bien aux locataires et organisez les visites."; else -> "Proposez des biens au client et organisez les visites." }`.
  - Templates: `index.html` offer card adds `<span class="status-tag status-tag--info" th:if="${a.sale}" data-deal-tag>Vente</span>` after the title; `fragments/mandate-card.html` the same with `${m.sale}`; `mandate.html` prints `${m.nextStepHint}` instead of the hard-coded conditional sentence (the second sentence already uses `m.sale`).

- [ ] **Step 5: Write the failing view/template tests**

  `AssignmentViewsTest.a sale search's offer`: title *Acheteur*, price term *Budget*, date term *Achat prévu*, sale flag.
  `MandateCardsTest.a sale listing's card`: title *Vendeur · Terrain*, facts *Budget*/*Prix* (first fact is the sector),
  *Prix* with no *« / mois »*; rental card unchanged. `MandateViewsTest.a sale mandate's page`: title, role, criteria
  terms, next-step hint; rental unchanged. `MandateTemplateTest.sale page`: *Acheteur* /*Vendeur* role, *Vente* tag,
  *Proposez des biens à l'acheteur* / *Présentez le bien aux acheteurs*; `HomeTemplateTest.sale cards`: the *Vente* tag
  on 104 and 301. Rental templates unchanged.

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='MandateLabelsTest,MandateRulesTest,AssignmentViewsTest,MandateCardsTest,MandateViewsTest,MandateTemplateTest,HomeTemplateTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-partner-central
  git commit -m "feat(npc): word the agent's sale cards and mandate page" -m "Refs #259"
  ```

---

### Task 8: The agent's match cards of a sale search

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt`, `MatchViewsTest.kt`, `ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Produces: `MatchRules.fit` / `availability` derive the sale wording from `mandate.listingType`; `MatchViews`' fact term is *Prix* for a sale search.

- [ ] **Step 1: Write the failing tests**

  ```kotlin
  @Test fun `a sale match's availability is worded for a purchase`() {
      assertEquals("Disponible avant votre achat", MatchRules.fit(saleMatch(available = true), saleSearch(), listing).first { it.text.contains("votre achat") }.text)
      assertEquals("Pas disponible avant votre achat", MatchRules.fit(saleMatch(available = false), saleSearch(), listing).first { it.text.contains("votre achat") }.text)
  }
  ```
  and in `MatchViewsTest`, a SALE search's card facts: the amount's term is *Prix*, the value *240M FCFA* (no
  *« / mois »*), the date's term *Disponibilité*; a rental card still *Loyer* / *245K FCFA / mois*.

- [ ] **Step 2: Run to verify they fail** — `mvn test -Dtest='MatchRulesTest,MatchViewsTest'` → FAIL.

- [ ] **Step 3: Implement**

  `MatchRules.availability(match, sale)` (pass `mandate.listingType == ListingType.SALE` from `fit`): full →
  *Disponible avant votre achat*; partial → *Disponible moins d'un mois après votre achat*; else *Disponible plus d'un
  mois après votre achat*; rental unchanged. `MatchViews`: `"Prix"` instead of `"Loyer"` when the mandate is a sale
  (use `MandateLabels.listingPriceTerm(mandate.listingType)`); the value keeps `MandateLabels.price(..., compact = true)`
  (already period-less for `NONE`).

- [ ] **Step 4: Write the failing template test**

  `MandateTemplateTest.sale match cards`: *Prix*, *Disponibilité*, *Disponible avant votre achat*, no *Loyer* or
  *« / mois »* in the matches section; the rental fixture unchanged.

- [ ] **Step 5: Run the tests** — `mvn test -Dtest='MatchRulesTest,MatchViewsTest,MandateTemplateTest'` → PASS.

- [ ] **Step 6: Commit**

  ```bash
  git add modules/ndopify-partner-central
  git commit -m "feat(npc): word a sale search's match cards" -m "Refs #259"
  ```

---

### Task 9: The listing checklist, the terms section and the published sheet of a sale

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionForm.kt`, `ListingSectionController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`, `templates/listing/section.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`, `ListingViewsTest.kt`, `PublishedListingViewsTest.kt`, `ListingSectionTemplateTest.kt`, `ListingTemplateTest.kt`

**Interfaces:**
- Produces:
  - `ListingRules.label(listing: Listing, section: ListingSection): String`, `ListingRules.saved(listing, section): String` (the sale TERMS label is *Prix et conditions*, its saved message *Prix et conditions enregistrés.*).
  - `ListingRules.errors(section, form, listing, today, formatter): Map<String, String>` and `ListingRules.request(section, form, listing): UpdateListingRequest` (a sale never checks nor sends deposit/advance/lease/notice/furniture).
  - `ListingView.sale: Boolean`.
- Consumes: `Listing.listingType`, `Listing.pricePeriodicityType`.

- [ ] **Step 1: Write the failing rules tests**

  ```kotlin
  @Test fun `a sale's terms ask for the price only`() {
      val errors = ListingRules.errors(ListingSection.TERMS, form(price = "", securityDeposit = "2", leaseTerm = "12"), saleListing(), today, formatter)
      assertEquals(setOf("price"), errors.keys)
      assertEquals("Indiquez le prix, en XAF.", errors["price"])
  }

  @Test fun `a sale's terms request clears the rental-only terms`() {
      val request = ListingRules.request(ListingSection.TERMS, form(price = "250 000 000", securityDeposit = "2", advanceRent = "3", leaseTerm = "12", noticePeriod = "1"), saleListing())
      assertNull(request.securityDeposit); assertNull(request.advanceRent); assertNull(request.leaseTerm); assertNull(request.noticePeriod)
  }

  @Test fun `a sale listing's furniture is never asked`() { /* PROPERTY: no furnitureType error, request.furnitureType == null */ }

  @Test fun `a rental's terms are unchanged`() { /* today's errors and request */ }
  ```

- [ ] **Step 2: Run to verify they fail** — `mvn test -Dtest=ListingRulesTest` → FAIL (signatures).

- [ ] **Step 3: Implement the rules**

  - `label` / `saved`: map `TERMS` to the sale strings when `listing.listingType == SALE`, else today's `LABELS` / `SAVED`.
  - `errors`: take `listing`; in `TERMS`, for a sale, check the price only (message *Indiquez le prix, en <currency>.*
    / *Indiquez le prix en chiffres, en <currency>.*) and `availableFrom`; skip the four integer choices. In `PROPERTY`,
    skip the furniture requirement for a sale.
  - `request`: for a sale, `furnitureType = null` and the four rental terms `null`, the price and `availableFrom` sent.
  - `ListingSectionForm.of`: for a sale, `furnitureType = null` and the four rental terms `null` (a stale stored value
    is never shown nor re-sent).

- [ ] **Step 4: Port the templates and views**

  - `ListingSectionController`: add `sale = listing.listingType == ListingType.SALE` to the model; call the new
    `errors` / `request` signatures.
  - `listing/section.html`: for a sale, the TERMS heading *Prix et conditions*, the intro *Indiquez le prix et les
    conditions convenus avec le vendeur…*, the label *Prix*, the suffix `{currency}` without *« / mois »*; the caution,
    advance, lease-term and notice fields are not rendered; the furniture field of *Le bien* is not rendered; the
    location and GPS hints say *acheteurs* instead of *locataires*; the property section's wording follows the prototype.
  - `ListingViews.of`: each checklist row's `label` is `ListingRules.label(listing, section)` and the section page's
    heading too (only TERMS differs); the TERMS summary is the sale price only (already the case when the rental terms
    are null but make it explicit). Move `ListingController.show`'s `outcome(...)` call after the listing is loaded and
    use `ListingRules.saved(listing, section)` so a sale's flash reads *Prix et conditions enregistrés.* (a listing that
    can't be loaded keeps today's label; its page is locked anyway).
  - `ListingView`: add `sale`; `templates/listing.html` prints *Prix et conditions* as the published sheet's terms
    heading for a sale.
  - `PublishedListingViews.terms`: for a sale, *Prix* + the price, *Disponible* + the date, nothing else; the rental
    branch is unchanged.

- [ ] **Step 5: Write the failing template tests**

  `ListingSectionTemplateTest.sale terms`: *Prix et conditions*, *Prix*, no *Loyer*, *Caution*, *Avance*, *Durée du
  bail*, *Préavis*; `sale property`: no furniture question; `sale hints`: *acheteurs*, no *locataires*; `sale checklist`:
  the TERMS row is *Prix et conditions*, its summary the price only; `sale published sheet`: *Prix et conditions*, no
  *Loyer* / *Caution*. The rental fixtures remain as they are.

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='ListingRulesTest,ListingViewsTest,PublishedListingViewsTest,ListingSectionTemplateTest,ListingTemplateTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-partner-central
  git commit -m "feat(npc): word a sale listing's checklist and conditions" -m "Refs #259"
  ```

---

### Task 10: The mandate edit page of a sale

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt`, `MandateEditTemplateTest.kt`

**Interfaces:**
- Produces: `MandateEditRules.errors(form, mandate: Mandate, formatter)`, `MandateEditRules.request(form, mandate)` (already), `MandateEditRules.summaries(form, mandate, neighborhoods, formatter)` — the `owner: Boolean` parameter is replaced by the mandate so the sale branches can read `listingType`; the controller's model gains `sale: Boolean`.

- [ ] **Step 1: Write the failing rules tests**

  ```kotlin
  @Test fun `a sale search's budget has no monthly period`() {
      assertEquals("Indiquez le budget, en XAF.", MandateEditRules.errors(form(priceAmount = ""), saleSearch(), formatter)["priceAmount"])
  }

  @Test fun `a sale listing's price is asked, not its rent`() {
      assertEquals("Indiquez le prix, en XAF.", MandateEditRules.errors(form(priceAmount = ""), saleListing(), formatter)["priceAmount"])
  }

  @Test fun `a sale asks no furniture and no rental term`() { /* no furnitureType error, summaries' budget has no " / mois" */ }
  ```

- [ ] **Step 2: Run to verify they fail** — `mvn test -Dtest=MandateEditRulesTest` → FAIL.

- [ ] **Step 3: Implement the rules and the controller**

  - `errors`: replace `owner` by `mandate`; a sale's price error is *Indiquez le prix, en <currency>.* (listing) /
    *Indiquez le budget, en <currency>.* (search), no *par mois*; the moving-period error is *Indiquez quand vous
    souhaitez acheter.* for a sale search and *Indiquez quand le bien est disponible.* for a sale listing; the furniture
    question is not required for a sale.
  - `request`: the furniture is null for a sale; the mandate's rental terms are carried as today (null for a sale).
  - `summaries`: the budget summary uses `formatter.money(amount)` without *« / mois »* for a sale.
  - `MandateController`'s edit GET/POST: `val sale = mandate.listingType == ListingType.SALE`; add `sale` to the model
    and pass the mandate to the rules.

- [ ] **Step 4: Port the template**

  `mandate/edit.html`: the budget group's title is *Budget et achat* (search) / *Prix et disponibilité* (listing) for a
  sale; the label *Budget* / *Prix*; the suffix `{currency}` without *« / mois »*; the moving-period legend *Quand
  souhaitez-vous acheter ?* for a sale search (listing: *Disponibilité du bien*); the furniture question is not rendered
  for a sale. The rental branches keep today's strings.

- [ ] **Step 5: Write the failing template test**

  `MandateEditTemplateTest.sale edit page`: *Budget et achat*, *Budget*, no *Budget mensuel*, no *FCFA / mois*, no
  furniture question, the price error naming the *budget*; the sale listing variant *Prix et disponibilité* / *Prix*.
  The rental fixture still shows *Budget et emménagement* / *FCFA / mois*.

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='MandateEditRulesTest,MandateEditTemplateTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-partner-central
  git commit -m "feat(npc): word the sale mandate's edit page" -m "Refs #259"
  ```

---

### Task 11: The mandate e-mails and the contract type label

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailet.kt`, `MandateApprovedMailet.kt`, `MandateAssignedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mail/ContractSignatureMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/mandate/offer.mjml`, `offer_listing.mjml`, `approved.mjml`, `approved_listing.mjml`, `assigned_listing.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferTemplateTest.kt`, `MandateListingTemplateTest.kt`, `MandateOfferMailetTest.kt`, `MandateApprovedMailetTest.kt`, `MandateAssignedMailetTest.kt`, `com/wutsi/ndopify/contract/server/mail/ContractSignatureMailetTest.kt`

**Interfaces:**
- Produces: `MandateMailLabels.sale(mandate: MandateEntity): Boolean` (a convenience for the mailets) and the new context key `sale` on every mandate mail; `ContractSignatureMailet.TYPES` gains `ContractType.SALE_SEARCH to "Mandat de recherche"` and `ContractType.SALE_LISTING to "Mandat de vente"`.

- [ ] **Step 1: Write the failing tests**

  Extend `MandateOfferTemplateTest` with, for the SEARCH template:

  ```kotlin
  @Test fun `a sale search asks the agent to help a buyer`() {
      val html = resolver.resolve(MandateOfferMailet.TEMPLATE, context + mapOf("sale" to true))
      assertTrue(html.contains("Un client cherche à acheter un bien"))
      assertFalse(html.contains("Un client recherche un bien à"))
  }
  ```
  and for the LISTING template: `"Un propriétaire souhaite vendre son bien"`; `approved_listing`: *le mandat de vente*
  and *acheteurs*; `assigned_listing`: *la vente de votre bien*; `approved` (search): *cherche à acheter un bien*;
  each `assertFalse` the rental sentence. `ContractSignatureMailetTest`: a `SALE_LISTING` contract's body carries
  *Mandat de vente*, a `SALE_SEARCH` one *Mandat de recherche*; the rental cases unchanged.

- [ ] **Step 2: Run to verify they fail** — `cd modules/ndopify-server && mvn test -Dtest='MandateOfferTemplateTest,MandateListingTemplateTest,ContractSignatureMailetTest'` → FAIL.

- [ ] **Step 3: Add the sale flag and the contract types**

  - `MandateOfferMailet` / `MandateApprovedMailet` / `MandateAssignedMailet`: add `"sale" to (mandate.listingType == ListingType.SALE)` to the context map.
  - `ContractSignatureMailet.TYPES`: the two new entries (the enum already has them).

- [ ] **Step 4: Word the templates**

  Each change is a Mustache section so the rental output stays byte-identical:

  - `offer.mjml`: `{{#sale}}Un client cherche à acheter un bien à <strong>{{city}}</strong>…{{/sale}}{{^sale}}Un client recherche un bien à…{{/sale}}`.
  - `approved.mjml`: a sale-only line *Votre client <strong>{{customer_name}}</strong> cherche à acheter un bien à <strong>{{city}}</strong>.* (the greeting and the rest unchanged).
  - `offer_listing.mjml`: *Un propriétaire souhaite vendre son bien* for a sale.
  - `approved_listing.mjml`: *le mandat de vente* and *organiser les visites avec les acheteurs* for a sale.
  - `assigned_listing.mjml`: *pour la vente de votre bien* for a sale.
  - The search templates' existing rows already read *Recherche : Achat - …* and *Budget* (`listing_type` and the context), so no other copy changes.

- [ ] **Step 5: Write the mailet context tests**

  `MandateOfferMailetTest` / `MandateApprovedMailetTest` / `MandateAssignedMailetTest`: with a SALE mandate, the
  captured context has `sale = true`; with a RENTAL one, `sale = false` and every other key/value as today (the rental
  maps in those tests gain exactly that one entry; no other value changes).

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='MandateOfferTemplateTest,MandateListingTemplateTest,MandateOfferMailetTest,MandateApprovedMailetTest,MandateAssignedMailetTest,ContractSignatureMailetTest,ContractSignatureTemplateTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-server
  git commit -m "feat(server): word the sale mandate e-mails and contract type" -m "Refs #259"
  ```

---

### Task 12: The interest, published and welcome e-mails, and the Telegram messages

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingPublishedMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/match/interest.mjml`, `mail/listing/published_agent.mjml`, `mail/listing/published_customer.mjml`, `mail/agent/welcome.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt` (the amount label)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`, `MandateApprovedBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailetTest.kt`, `MatchInterestTemplateTest.kt`, `listing/server/mail/ListingPublishedMailetTest.kt`, `ListingPublishedTemplateTest.kt`, new `agent/server/mail/WelcomeTemplateTest.kt`, `mandate/server/bot/MandateOfferBotletTest.kt`, `MandateApprovedBotletTest.kt`, `listing/server/bot/ListingTelegramLabelsTest.kt`, `match/server/bot/MatchInterestBotletTest.kt`, `MatchAnsweredBotletTest.kt`

**Interfaces:**
- Produces: `MandateMailLabels.amountLabel(mandate: MandateEntity): String` — SALE: *Prix* (listing) / *Budget* (search); the botlets use it in sale messages only. `ListingTelegramLabels.titleAndPrice` inserts *Prix : * before the amount for a `SALE` listing, unchanged otherwise.
- Consumes: `ListingType`, `MandateType`.

- [ ] **Step 1: Write the failing tests**

  ```kotlin
  @Test fun `a sale listing's interest mail speaks of a bien and its price`() {
      // MatchInterestMailetTest: a SALE listing
      assertEquals("Ndopify: Ray Sponsible s'intéresse à un bien", email.subject)
      // MatchInterestTemplateTest: the rendered body
      assertTrue(html.contains("s'intéresse à un bien"))
      assertTrue(html.contains("<b>Prix</b>"))
      assertFalse(html.contains("<b>Loyer</b>"))
  }

  @Test fun `a published sale listing speaks of buyers`() { /* published_agent + published_customer: "acheteurs", no "candidats" */ }

  @Test fun `the welcome mail names buyers and sellers`() { /* WelcomeTemplateTest: "locataires, acheteurs, bailleurs et vendeurs" */ }

  @Test fun `a sale mandate's bot message names its amount label`() {
      // MandateOfferBotletTest: a SALE SEARCH → "Achat - …", "Budget : 250 000 000 FCFA"
      // a SALE LISTING → "Vente - …", "Prix : …"; and assertFalse on "location", "loyer", "logement" (lowercase)
  }
  ```

- [ ] **Step 2: Run to verify they fail** — `mvn test -Dtest='MatchInterestMailetTest,MatchInterestTemplateTest,ListingPublishedTemplateTest,WelcomeTemplateTest,MandateOfferBotletTest,MandateApprovedBotletTest,ListingTelegramLabelsTest'` → FAIL.

- [ ] **Step 3: Word the e-mails**

  - `MatchInterestMailet`: `SUBJECT = "{{tenant_name}}: {{customer_name}} s'intéresse à {{asset}}"` with
    `asset = if (sale) "un bien" else "un logement"`; add `"asset"` and `"price_label" to (if (sale) "Prix" else "Loyer")`
    to the context. `interest.mjml`: *s'intéresse à {{asset}}* and `<b>{{price_label}}</b>`.
  - `ListingPublishedMailet`: add `"sale" to (listing.listingType == SALE)` to the context; `published_agent.mjml` and
    `published_customer.mjml`: `{{#sale}}…acheteurs…{{/sale}}{{^sale}}…candidats…{{/sale}}` on the next-step sentence.
  - `welcome.mjml`: *Renforcer la confiance des locataires, acheteurs, bailleurs et vendeurs envers vos offres.*
    (no condition: the agent handles both).

- [ ] **Step 4: Word the Telegram messages**

  - `MandateMailLabels.amountLabel`: as above.
  - `MandateOfferBotlet.text`: for a SALE, the criteria line's amount becomes `" — " + amountLabel(mandate) + " : " + budget`; a rental keeps `" — " + budget`. Same in `MandateApprovedBotlet`.
  - `ListingTelegramLabels.titleAndPrice`: after `" — "`, insert `"Prix : "` when `listing.listingType == ListingType.SALE`, then the price. A rental's line is unchanged.

- [ ] **Step 5: Write the rental regression tests**

  The exact-context maps gain the new keys and nothing else: `MatchInterestMailetTest.send` adds
  `"asset" to "un logement"` and `"price_label" to "Loyer"` (its subject assertion stays
  `Ndopify: Ray Sponsible s'intéresse à un logement`); `ListingPublishedMailetTest` adds `"sale" to false`;
  `MatchInterestTemplateTest` adds the same `asset` / `price_label` to its rental fixture (the rendered rental body
  still says *un logement* and `<b>Loyer</b>`). New `WelcomeTemplateTest` renders `/mail/agent/welcome.mjml` and asserts
  *Renforcer la confiance des locataires, acheteurs, bailleurs et vendeurs*. Each rental bot case asserts today's line
  (`" — 150 000 XAF / mois"`, no *Prix : *).

- [ ] **Step 6: Run the tests** — `mvn test -Dtest='MatchInterestMailetTest,MatchInterestTemplateTest,ListingPublishedMailetTest,ListingPublishedTemplateTest,WelcomeMailetTest,MandateOfferBotletTest,MandateApprovedBotletTest,ListingTelegramLabelsTest,MatchInterestBotletTest,MatchAnsweredBotletTest'` → PASS.

- [ ] **Step 7: Commit**

  ```bash
  git add modules/ndopify-server
  git commit -m "feat(server): word the sale interest, published and Telegram messages" -m "Refs #259"
  ```

---

### Task 13: Verify the three modules and the rental regression

**Files:**
- No production change.

- [ ] **Step 1: Run the web app's gate**

  Run: `cd modules/ndopify-web && mvn verify`
  Expected: ktlint, every test, the JaCoCo gate pass; `BundledAssetsTest` confirms the new CSS made it into the bundle.

- [ ] **Step 2: Run the agent app's gate**

  Run: `cd modules/ndopify-partner-central && mvn verify`
  Expected: PASS (no Spring context, no MySQL needed).

- [ ] **Step 3: Run the server's gate**

  Run: `cd modules/ndopify-server && mvn verify` (MySQL on `localhost`, root/no password, as `CLAUDE.md` says)
  Expected: PASS, including the endpoint tests and the mail/bot tests.

- [ ] **Step 4: Walk the rental regression**

  Render a rental listing, a rental match, a rental visit, a rental mandate page, a rental card, a rental e-mail and a
  rental bot message, and diff each against the strings the tests pinned before this story (the existing tests are the
  reference; no fixture was edited in a rental branch). Only the site shell (nav, footer tagline) and the hero switch
  may differ on the web pages.

- [ ] **Step 5: Walk the sale copy against the approved prototype**

  Compare the served pages with `modules/ndopify-web/src/main/html/v2` and `modules/ndopify-partner-central/src/main/html/v2`
  page by page: goal picker, `/louer`, `/acheter`, `/owner`, `/owner/sell`, a sale listing (published and sold), a sale
  proposal (every state), a sale visit, the agent's offer/mandate/edit/listing cards and pages, the match cards. Fix a
  mismatch in the task that owns it, with its test.

- [ ] **Step 6: Commit any fix** (only the fix's files; no `git add -A`)

  ```bash
  git add <the files the fix touched>
  git commit -m "fix(<scope>): align <page> with the approved prototype" -m "Refs #259"
  ```

---

## Done when

- The public site serves the goal picker at `/`, the renter page at `/louer`, the buyer page at `/acheter`, the rental
  owner page at `/owner` and the seller page at `/owner/sell`, each with its own title, description and call to action.
- A SALE listing, a SALE search's proposal and its visit read as sales; a RENTAL one is byte-identical.
- The agent's sale cards, mandate page, edit page, checklist and match cards read as sales; a rental's are unchanged.
- The sale mandate, interest, published and welcome e-mails and the sale Telegram messages carry the sale wording; the
  contract signature e-mail says *Mandat de vente*; the rental ones are unchanged.
- `mvn verify` passes in `ndopify-web`, `ndopify-partner-central` and `ndopify-server`.
- No DTO, migration, `ndopify-web-core` or `ndopify-esignature` file changed.
