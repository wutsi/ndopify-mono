# The owner verification section on the mandate page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show, on a LISTING mandate's page, whether its owner is verified — `PENDING` with what is missing, `VERIFIED` with what it allows, `FAILED` with the reason and the way out — by wording `GET /v1/mandates/{id}/ownership` (#297) as approved in the prototype.

**Architecture:** `NdMandateClient` reads the verdict with the mandate page's other reads (in parallel, joined at the end). `ui/mandate/MandateOwnershipViews` turns the DTO into an `OwnershipSectionView` — the French copy, the check's kind, and the `*_UNUSABLE` problem instructions reusing `DocumentFieldLabels` — and `MandateController` adds it to the model only for a LISTING mandate in `MandateRules.ACTIVE`. A new `fragments/mandate-ownership.html` renders it immediately before the Documents section, and the prototype's `ownership.css` joins the CSS bundle. No JavaScript, no DTO change, no server change: the page words the verdict and never re-derives it.

**Tech Stack:** Kotlin, Spring Boot BFF (`ndopify-partner-central`), Thymeleaf, `RestClient`, `Parallel` (virtual threads), `kotlin.test` + `mockito-kotlin`, MockMvc, jsoup template tests, esbuild bundle.

**Spec:** GitHub issue #298 — the enriched story *is* the design (its *Verdict contract*, *Decisions and assumptions*, *Refined acceptance criteria*, *Prototype (approved)*), plus the approved prototype in `modules/ndopify-partner-central/src/main/html/v2/`: `mandate.html` (the section and its PROTOTYPE comment), `assets/js/mandate-ownership.js` (the copy and the state switches), `assets/css/ownership.css`, `README.md` (the per-state review table). The verdict contract is #297's, shipped in `main` (PR #383, `d1c8a360`, `ndopify-dto` 0.0.112).

## Global Constraints

- One module: `modules/ndopify-partner-central`. No `ndopify-dto`, `ndopify-server`, `ndopify-web-core` or prototype change.
- The verdict is `OwnershipVerification(status, reasons)`; each reason is `code` + `details` (the document's `problemCodes`, `*_UNUSABLE` only). It carries **no date, no document type or title** — the section renders none and never guesses (re-scope decided 2026-11-27).
- Shown only on a LISTING mandate whose status is in `MandateRules.ACTIVE` (ASSIGNED, PENDING_APPROVAL, APPROVED). Absent, with **no call**, on a SEARCH mandate and on CLOSED / CANCELLED.
- Server-rendered: no page script, no JS bundle entry. Every state reads without JavaScript.
- The section carries no document id, type, title, page, path, storage URL or extracted value; reason codes and statuses only.
- UI text French; code, comments, docs English. Assertions `kotlin.test`; mocks `com.nhaarman.mockitokotlin2`; tests mirror the main package.
- `mvn validate` (ktlint) and the JaCoCo gate must pass: `mvn verify` in the module.
- The approved prototype is the baseline: port its markup, copy and styles; do not redesign.

## Review Focus

The failure modes this section's users will meet, most likely first; each gets its test in the task named.

1. **The verdict read fails or hangs while the rest of the page is fine** — a 5xx, an unreachable server or a 403 must leave only this section saying *Vérification indisponible*, the page still answering 200. Task 3 (controller) and Task 4 (template).
2. **A SEARCH mandate reaching the endpoint** — the server answers `400 MANDATE_NOT_LISTING`; this app must never call it outside a LISTING mandate in an in-flight status. Task 3 (`verify(never())`).
3. **`UNKNOWN`** — the DTO's default, never returned, must read as *indisponible*, not as an empty `FAILED` block with no reason. Task 1 and Task 3.
4. **`*_UNUSABLE` details** — several codes, duplicates, an unknown code, and `DOCUMENT_EXPIRED` (a warning that never reaches a verdict reason): each known code reads its #331 instruction, in the server's order, an unknown one the generic text. Task 1 and Task 4.
5. **A reason code this app doesn't know, or `PENDING` with no reason at all** — a generic line with no empty kind heading; and the in-flight sentence instead of an empty list. Task 1 and Task 4.
6. **A leak** — no document id, type or title in the section's HTML, though the page has them in its Documents rows. Task 4.

---

## File Structure

- Modify `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt` — one read.
- Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt` — the section's view model.
- Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt` — the copy and the view builder (one responsibility: word the verdict).
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt` — the read and the model attribute.
- Create `src/main/resources/templates/fragments/mandate-ownership.html` — the section's markup.
- Modify `src/main/resources/templates/mandate.html` — include the fragment before the Documents one.
- Create `src/main/assets/css/ownership.css`; modify `build-assets.mjs` — the stylesheet and its bundle entry.
- Tests: `NdMandateClientTest`, `MandateOwnershipViewsTest`, `MandateControllerTest`, `MandateTemplateTest`.

---

### Task 1: The section's view and copy

Port the approved prototype's content — its states, tags and French sentences — into one view model and one builder, tested on its own.

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt`

**Interfaces:**
- Consumes: `com.wutsi.ndopify.mandate.dto.{OwnershipVerification, OwnershipVerificationStatus}`; `com.wutsi.ndopify.npc.ui.document.DocumentFieldLabels.instruction(code: String?)`; `MandateRules.words(type, listingType).the`.
- Produces:
  - `data class OwnershipSectionView(status: OwnershipVerificationStatus, unavailable: Boolean, tagLabel: String?, tagStyle: String?, intro: String, missing: List<String>, waiting: String?, documentsLabel: String?, reasons: List<OwnershipReasonView>, listingId: Long?)`
  - `data class OwnershipReasonView(check: String?, text: String, action: String, problems: List<String>)`
  - `MandateOwnershipViews.section(mandate: Mandate, verification: OwnershipVerification?, listingId: Long?, labels: DocumentFieldLabels): OwnershipSectionView`

- [ ] **Step 1: Write the failing tests**

In a new `MandateOwnershipViewsTest`, with `labels = DocumentFieldLabels(ResourceBundleMessageSource().apply { setBasename("messages"); setDefaultEncoding("UTF-8") })` (as `DocumentFieldLabelsTest` builds it) and a LISTING SALE mandate (`Mandate(id = 301, status = APPROVED, mandateType = LISTING, listingType = SALE)`):

```kotlin
@Test
fun `pending names the missing pieces`() {
    val view = MandateOwnershipViews.section(
        mandate,
        OwnershipVerification(PENDING, listOf(reason("OWNERSHIP_ID_MISSING"), reason("OWNERSHIP_DOCUMENT_MISSING"))),
        listingId = null,
        labels = labels,
    )

    assertEquals("En cours", view.tagLabel)
    assertEquals("info", view.tagStyle)
    assertEquals(
        listOf(
            "Il manque la pièce d'identité du propriétaire : une carte nationale d'identité (recto et verso) " +
                "ou un passeport, à son nom.",
            "Il manque le document de propriété : un titre foncier ou un certificat de propriété, à son nom.",
        ),
        view.missing,
    )
    assertEquals("Ajouter ces pièces dans Documents", view.documentsLabel)
    assertNull(view.waiting)
    assertTrue(view.reasons.isEmpty())
}

@Test
fun `pending with no reason is in flight`() {
    val view = MandateOwnershipViews.section(mandate, OwnershipVerification(PENDING), null, labels)

    assertTrue(view.missing.isEmpty())
    assertEquals(
        "La lecture des pièces est en cours : la vérification se fera d'elle-même dès qu'elles seront analysées.",
        view.waiting,
    )
    assertEquals("Voir les documents", view.documentsLabel)
}

@Test
fun `verified has no date and links the listing`() {
    val view = MandateOwnershipViews.section(mandate, OwnershipVerification(VERIFIED), 501, labels)

    assertEquals("Vérifié", view.tagLabel)
    assertEquals("success", view.tagStyle)
    assertEquals(501L, view.listingId)
    assertTrue(view.reasons.isEmpty())
    // The verdict has no timestamp: the copy carries no date.
    assertFalse(view.toString().let { Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(it) })
}

@Test
fun `each failed reason names its check, its wording and its action`() {
    val view = MandateOwnershipViews.section(
        mandate,
        OwnershipVerification(
            FAILED,
            listOf(
                reason("OWNERSHIP_ID_EXPIRED"),
                reason("OWNERSHIP_DOCUMENT_UNUSABLE", listOf("DOCUMENT_TYPE_MISMATCH", "DOCUMENT_PAGE_MISSING")),
            ),
        ),
        null,
        labels,
    )

    assertEquals("Non vérifié", view.tagLabel)
    assertEquals("warning", view.tagStyle)
    assertEquals("La pièce d'identité", view.reasons[0].check)
    assertEquals("La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire.", view.reasons[0].text)
    assertEquals("Le document de propriété", view.reasons[1].check)
    assertEquals(
        listOf(
            "Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec le bon type.",
            "Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, " +
                "ou supprimez le document et ajoutez-le avec toutes ses pages.",
        ),
        view.reasons[1].problems,
    )
}

@Test
fun `an unknown code reads the generic line with no kind`() {
    val view = MandateOwnershipViews.section(mandate, OwnershipVerification(FAILED, listOf(reason("OWNERSHIP_NEW"))), null, labels)

    assertNull(view.reasons[0].check)
    assertEquals("La vérification du propriétaire a échoué sur ce document.", view.reasons[0].text)
    assertTrue(view.reasons[0].problems.isEmpty())
}

@Test
fun `an unavailable verdict has no tag and no reason`() {
    val nulled = MandateOwnershipViews.section(mandate, null, null, labels)
    val unknown = MandateOwnershipViews.section(mandate, OwnershipVerification(), null, labels)

    for (view in listOf(nulled, unknown)) {
        assertTrue(view.unavailable)
        assertNull(view.tagLabel)
        assertNull(view.tagStyle)
        assertTrue(view.reasons.isEmpty())
        assertTrue(view.missing.isEmpty())
    }
}
```

Also one test on the intro: for a SALE LISTING mandate it reads *le vendeur*, for a RENTAL one *le bailleur* (`MandateRules.words`), in `"Ndopify vérifie que le vendeur est bien le propriétaire du bien, à partir des pièces de la section Documents."`.

- [ ] **Step 2: Run them to make sure they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest`
Expected: compilation failure — `MandateOwnershipViews` unresolved.

- [ ] **Step 3: Write the view model**

`MandateOwnershipView.kt`: the two data classes above, KDoc stating that the verdict carries no date or document, so the view has none, and that `listingId` is the mandate's listing when one exists (the *Voir l'annonce* link).

- [ ] **Step 4: Write the copy and the builder**

`MandateOwnershipViews.section(...)` — `object` with private constants, every string exactly as approved:

- `PENDING`: tag `("En cours", "info")`; one `missing` line per `OWNERSHIP_ID_MISSING` / `OWNERSHIP_DOCUMENT_MISSING`, `MISSING_OTHER` ("Une pièce manque pour vérifier le propriétaire : vérifiez les documents du mandat.") for any other code; `waiting` (and no `missing`) when `reasons` is empty; `documentsLabel` "Ajouter ces pièces dans Documents" with a reason, "Voir les documents" without.
- `VERIFIED`: tag `("Vérifié", "success")`; no reason, no date; `listingId` passed through (null when the mandate has none).
- `FAILED`: tag `("Non vérifié", "warning")`; per reason, `check` = "La pièce d'identité" for `OWNERSHIP_ID_*`, "Le document de propriété" for `OWNERSHIP_DOCUMENT_*`, null otherwise; `text` / `action` from these exact pairs:
  - `OWNERSHIP_ID_EXPIRED` — "La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire." / "Reprenez la photo d'une pièce en cours de validité, puis ajoutez-la dans Documents."
  - `OWNERSHIP_ID_UNUSABLE` — "La pièce n'a pas pu être vérifiée." / "Corrigez la pièce dans Documents : reprenez sa photo, ou changez son type."
  - `OWNERSHIP_ID_NAME_MISMATCH` — "La pièce ne porte pas le nom du propriétaire : elle ne peut pas l'identifier." / "Ajoutez une pièce d'identité au nom du propriétaire."
  - `OWNERSHIP_DOCUMENT_UNUSABLE` — "Le document n'a pas pu être vérifié." / "Corrigez le document dans Documents : reprenez sa photo, ou changez son type."
  - `OWNERSHIP_DOCUMENT_NAME_MISMATCH` — "Le document ne désigne pas le propriétaire du bien." / "Ajoutez un titre foncier ou un certificat de propriété au nom du propriétaire."
  - any other code — "La vérification du propriétaire a échoué sur ce document." / "Vérifiez la pièce dans Documents."
  `problems` = `reason.details.map(labels::instruction)` (the #331 wording, in the server's order; empty for the others).
- `null` verification or `UNKNOWN` status: `unavailable = true`, `tagLabel` / `tagStyle` null, everything empty.
- `intro` for every state: "Ndopify vérifie que ${MandateRules.words(mandate.mandateType, mandate.listingType).the} est bien le propriétaire du bien, à partir des pièces de la section Documents."

- [ ] **Step 5: Run the tests, then the module's style check**

Run: `mvn test -Dtest=MandateOwnershipViewsTest` — Expected: PASS.
Run: `mvn validate` — Expected: BUILD SUCCESS (ktlint).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView*.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt
git commit -m "feat(npc): word the owner verification of a listing mandate" -m "Refs #298"
```

---

### Task 2: The verdict read

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`

**Interfaces:**
- Produces: `fun ownership(id: Long): OwnershipVerification` — `GET /v1/mandates/{id}/ownership`, `GetOwnershipVerificationResponse.verification`, `OwnershipVerification()` when the body is absent.
- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun ownership() {
    server.expect(requestTo("http://localhost:8080/v1/mandates/201/ownership"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                {"verification":{"status":"FAILED","reasons":[{"code":"OWNERSHIP_ID_EXPIRED"}]}}
                """.trimIndent(),
                MediaType.APPLICATION_JSON,
            ),
        )

    val verdict = client.ownership(201)

    assertEquals(OwnershipVerificationStatus.FAILED, verdict.status)
    assertEquals(listOf("OWNERSHIP_ID_EXPIRED"), verdict.reasons.map { it.code })
    server.verify()
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `mvn test -Dtest=NdMandateClientTest#ownership`
Expected: compilation failure — `ownership` unresolved.

- [ ] **Step 3: Implement the read**

In `NdMandateClient`, beside `get(id)`, in the class's existing `rest.get().uri(...).retrieve().body<T>()` style; KDoc: the verdict of a LISTING mandate's owner (#297), status and reasons only, read on every page view, never cached.

- [ ] **Step 4: Run it to make sure it passes**

Run: `mvn test -Dtest=NdMandateClientTest` — Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt
git commit -m "feat(npc): read a listing mandate's ownership verdict" -m "Refs #298"
```

---

### Task 3: Wire the read into the mandate page

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt` (constructor, `render`, `companion`)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`

**Interfaces:**
- Consumes: Task 1's `MandateOwnershipViews.section(...)` and `OwnershipSectionView`; Task 2's `NdMandateClient.ownership(id)`; the controller's existing `parallel`, `lookup(what, call)`, `listingId(mandate)`, `DocumentFieldLabels`.
- Produces: model attribute `ownershipSection: OwnershipSectionView?` — present only for `mandate.mandateType == LISTING && mandate.status in MandateRules.ACTIVE`.

- [ ] **Step 1: Write the failing tests**

In `MandateControllerTest` (add the dependency, stub the verdict, and read `result.modelAndView!!.model["ownershipSection"]`):

```kotlin
@Test
fun `the owner verification is shown on an active listing mandate`() {
    doReturn(OwnershipVerification(VERIFIED)).whenever(mandateClient).ownership(201)

    val result = render(LAND_SALE)   // the class's existing helper for an APPROVED LISTING mandate

    val section = result.modelAndView!!.model["ownershipSection"] as OwnershipSectionView
    assertEquals(OwnershipVerificationStatus.VERIFIED, section.status)
}

@Test
fun `a search mandate never asks for a verdict`() {
    render(APPROVED)                 // the class's SEARCH mandate fixture

    verify(mandateClient, never()).ownership(anyLong())
}

@Test
fun `a closed listing mandate has no section and asks for no verdict`() {
    val result = render(LAND_SALE.copy(status = MandateStatus.CLOSED))

    assertNull(result.modelAndView!!.model["ownershipSection"])
    verify(mandateClient, never()).ownership(anyLong())
}

@Test
fun `a failed verdict read only makes the section unavailable`() {
    doThrow(RestClientException("down")).whenever(mandateClient).ownership(201)

    val result = render(LAND_SALE)

    val section = result.modelAndView!!.model["ownershipSection"] as OwnershipSectionView
    assertTrue(section.unavailable)
    assertEquals("mandate", result.modelAndView!!.viewName)
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `mvn test -Dtest=MandateControllerTest`
Expected: FAIL/compilation — `ownership` not called into the page, constructor without the labels bean.

- [ ] **Step 3: Implement the wiring**

In `render`, beside the documents read: when `mandate.mandateType == MandateType.LISTING && mandate.status in MandateRules.ACTIVE`, start `parallel.async { lookup("ownership verdict of mandate ${mandate.id}") { mandateClient.ownership(mandate.id) } }`; join it where `documentSection` is built, inside `try { ... } catch (ex: Exception) { null }`, and add `model.addAttribute("ownershipSection", MandateOwnershipViews.section(mandate, verdict, listingId, labels))` — the attribute `null` (not the empty view) when the status or type doesn't qualify, so the template omits the whole section. Add `DocumentFieldLabels` to the constructor; update every `MandateController(...)` construction (tests included).

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest=MandateControllerTest` — Expected: PASS.
Run: `mvn validate` — Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt
git commit -m "feat(npc): read the owner verdict on the mandate page" -m "Refs #298"
```

---

### Task 4: The approved section on the page

Port the approved prototype's markup and stylesheet — states, pills, sentences, layout — and prove the rendered page.

**Files:**
- Create: `src/main/resources/templates/fragments/mandate-ownership.html`, `src/main/assets/css/ownership.css`
- Modify: `src/main/resources/templates/mandate.html` (include before `fragments/mandate-documents`), `build-assets.mjs` (`CSS` list)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: the model attribute `ownershipSection: OwnershipSectionView` (Task 3).
- Produces: the rendered section — `<section class="home-section ownership" aria-labelledby="ownership-title">` with a `h2` *Vérification du propriétaire* and one block per state.

- [ ] **Step 1: Write the failing template tests**

In `MandateTemplateTest`, render the page for each state (stub `mandateClient.ownership`) and read `document.selectFirst("section.ownership")`. Add two helpers to the class:

```kotlin
private fun reason(code: String, details: List<String> = emptyList()) = OwnershipVerificationReason(code, details)

// The rendered section of a LISTING mandate (LAND_SALE, the class's APPROVED SALE fixture), any state stubbed before.
private fun verificationSection(mandate: Mandate = LAND_SALE): Element =
    render(mandate).selectFirst("section.ownership")!!
```

```kotlin
@Test
fun `the owner verification renders its states`() {
    doReturn(OwnershipVerification(PENDING, listOf(reason("OWNERSHIP_ID_MISSING"))))
        .whenever(mandateClient).ownership(201)
    val pending = verificationSection()

    assertTrue(pending.text().contains("Vérification du propriétaire"))
    assertTrue(pending.text().contains("Vérification du propriétaire en cours"))
    assertTrue(pending.text().contains("Il manque la pièce d'identité du propriétaire"))
    assertEquals("#documents-title", pending.selectFirst("a[href=#documents-title]")!!.attr("href"))

    doReturn(OwnershipVerification(VERIFIED)).whenever(mandateClient).ownership(201)
    val verified = verificationSection()
    assertTrue(verified.text().contains("Propriétaire vérifié"))
    assertTrue(verified.text().contains("Un propriétaire vérifié est ce qui permet de publier l'annonce."))
    // No date, and no document of the Documents rows named here.
    assertFalse(Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(verified.text()))

    doReturn(OwnershipVerification(FAILED, listOf(reason("OWNERSHIP_ID_UNUSABLE", listOf("DOCUMENT_UNREADABLE")))))
        .whenever(mandateClient).ownership(201)
    val failed = verificationSection()
    assertTrue(failed.text().contains("Propriétaire non vérifié"))
    assertTrue(failed.text().contains("La pièce d'identité"))
    assertTrue(failed.text().contains("Le document n'a pas pu être lu."))
    assertFalse(failed.text().contains("Carte nationale d'identité"))
}

@Test
fun `the owner verification is absent on a search mandate`() {
    val page = render(APPROVED)        // the class's SEARCH fixture

    assertNull(page.selectFirst("section.ownership"))
}
```

Also assert the section sits immediately before `section.documents`: `assertEquals(page.selectFirst("section.documents"), verified.nextElementSibling())`.

- [ ] **Step 2: Run them to make sure they fail**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: FAIL — no `section.ownership` in the page.

- [ ] **Step 3: Port the markup**

Create `fragments/mandate-ownership.html` from the approved `v2/mandate.html` section: the same class names, `th:fragment="ownership(section)"`, `th:if` on status/unavailable in place of the prototype's `hidden` switches, the same sentences (from `MandateOwnershipViews`), `#documents-title` as the pending/failed link, and `th:href="@{/listings/{id}(id=${section.listingId})}"` for *Voir l'annonce* (rendered only when `listingId != null`). No `data-*` switch attributes from the prototype (they exist only for its script), and no script.

- [ ] **Step 4: Include it on the page**

In `templates/mandate.html`, immediately before the `th:block` that replaces `fragments/mandate-documents`, add `th:block th:replace="~{fragments/mandate-ownership :: ownership(${ownershipSection})}" th:if="${ownershipSection}"`.

- [ ] **Step 5: Run the tests**

Run: `mvn test -Dtest=MandateTemplateTest` — Expected: PASS.

- [ ] **Step 6: Port the stylesheet**

Copy `src/main/html/v2/assets/css/ownership.css` to `src/main/assets/css/ownership.css`; drop rules for elements the production fragment doesn't render (the prototype's `[data-*]` selectors) and keep the tokens/components it uses. Add `"ownership"` after `"document"` in the `CSS` array of `build-assets.mjs` (the JS list is untouched: no production script).

- [ ] **Step 7: Verify the module**

Run: `mvn verify` (in `modules/ndopify-partner-central`) — Expected: BUILD SUCCESS (ktlint, all tests, coverage gate, the asset bundle built). Then open the built page at 390 px and by keyboard once: the title and pill wrap under each other when they must, a long reason wraps with no sideways scroll, every link takes the focus ring (AC 7).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html \
        modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/assets/css/ownership.css \
        modules/ndopify-partner-central/build-assets.mjs \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show the owner verification on the mandate page" -m "Refs #298"
```
