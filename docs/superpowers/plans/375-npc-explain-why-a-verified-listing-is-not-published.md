# The listing page explains a verified listing's wait on the owner — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the agent's listing page, a `VERIFIED` listing whose mandate's owner isn't verified shows why the
publication waits — the hold tag, the verdict's missing pieces or reasons in #298's French copy, and one link to the
mandate — exactly as the approved prototype does, without JavaScript and without a new server or DTO contract.

**Architecture:** `ListingController` reads `GET /v1/mandates/{id}/ownership` (through #298's `NdMandateClient.ownership`)
only when the page is the listing's own agent's and the listing is `VERIFIED`. A new `ui/listing/ListingOwnershipViews`
turns the verdict into a `ListingOwnershipView` using the same French wording as the mandate page (extracted into one
`ui/mandate/OwnershipCopy`), `ListingRules` gains an opt-in hold tag, and `ListingViews` sets it and hides the *En
vérification* panel while the block shows. A new `fragments/listing-ownership.html` renders the block; no page script,
no JS bundle entry.

**Tech Stack:** Kotlin, Spring Boot BFF (`ndopify-partner-central`), Thymeleaf, `RestClient`, `kotlin.test` +
`mockito-kotlin`, MockMvc, jsoup template tests, esbuild bundle, `mvn verify`.

**Spec:** GitHub issue #375 — the enriched story *is* the design (its *Decisions and assumptions*, *Refined acceptance
criteria* and *Prototype (approved 2026-11-27)*), plus the approved prototype in
`modules/ndopify-partner-central/src/main/html/v2/`: `listing.html` (the hold tag and the block, with its PROTOTYPE
comment), `assets/js/ownership-copy.js` and `assets/js/partner-central.js` (the sample listing 706 and the state
switch), `assets/css/listing.css`, and `README.md` (the per-state review table).

## Global Constraints

- One module: `modules/ndopify-partner-central`. No `ndopify-dto`, `ndopify-server`, `ndopify-web-core` nor prototype
  change. The verdict is #297's `OwnershipVerification(status, reasons)`, each reason `code` + `details`
  (`*_UNUSABLE` only); the gate is #299's.
- The ownership read happens **only** when the page is the listing's own agent's (`!readOnly`) and
  `listing.status == ListingStatus.VERIFIED`. Every other status, and another agent's read-only lens, makes **no call**
  (the server answers `400 MANDATE_NOT_LISTING` for a SEARCH mandate and `403` for a foreign one).
- The block is shown only for a verdict that isn't `VERIFIED`: `PENDING`, `FAILED`, or unavailable (the read failed or
  answered `UNKNOWN`). A `VERIFIED` verdict leaves today's page (tag *Vérifiée*, panel *En vérification*).
- The French wording is #298's, one source: `OwnershipCopy` feeds both `MandateOwnershipViews` and
  `ListingOwnershipViews`; no sentence is copied by hand.
- The block names no customer name, document id, type, title, page, path, storage URL, extracted value, nor verdict
  JSON — reason codes only through their French labels.
- Server-rendered, no JavaScript: do not add a page script nor a JS bundle entry. The CSS sources are bundled by
  `build-assets.mjs`; `listing.css` is already in its `CSS` list, `ownership.css` follows it.
- UI text French; code, comments and docs English. Assertions `kotlin.test`; mocks `com.nhaarman.mockitokotlin2`; tests
  mirror the main package.
- `mvn validate` (ktlint) and the JaCoCo gate must pass: `mvn verify` in the module (it builds the asset bundle; a
  clean run needs network for `npm ci`).
- The approved prototype is the baseline: port its markup, copy and styles; do not redesign.

## Review Focus

The failure modes this page's users will meet, most likely first; each gets its test in the task named.

1. **The ownership read fails while the listing is fine** — a 5xx, an unreachable server or a 403/404 must cost only
   the block (*Vérification du propriétaire indisponible*): the page still answers 200, with its tag, checklist and
   return link. Task 3 (controller) and Task 4 (template).
2. **A call where it must not happen** — a DRAFT / PENDING_VERIFICATION / UNDER_VERIFICATION / PUBLISHING / PUBLISHED /
   RENTED / SOLD / EXPIRED / CANCELLED listing, and another agent's read-only lens, must never reach
   `GET /v1/mandates/{id}/ownership`. Task 3 (`never()`).
3. **The hold tag leaking elsewhere** — `ListingRules.tag` also feeds the mandate page's *Annonce* line and the Espace
   agent's cards; the variant is an opt-in parameter whose default keeps *Vérifiée*. Task 2.
4. **A reason this page doesn't know, a `FAILED` verdict with no reason, an `*_UNUSABLE` detail** — a generic line with
   no empty heading, and the document's own problem instructions as the document page words them. Task 1, Task 2 and
   Task 4.
5. **A leak while the block is the only thing on the page** — the block must not name an id, a type, a title, a page,
   a path or a storage URL, though the mandate page's Documents rows do. Task 4.

---

## File Structure

- Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopy.kt` — the verdict's French wording, one source.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt` — read `OwnershipCopy`, no copy left.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt` — `ListingOwnershipView`, `ListingView.ownership`.
- Create `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingOwnershipViews.kt` — the block's view builder.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt` — the hold tag, opt-in.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt` — the tag, the panel, the block.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt` — one conditional read.
- Create `src/main/resources/templates/fragments/listing-ownership.html` — the approved block.
- Modify `src/main/resources/templates/listing.html` — include it above the checklist.
- Modify `src/main/assets/css/listing.css` — the hold pill, the block's title and its action.
- Modify `modules/ndopify-partner-central/CLAUDE.md` — the listing page's paragraph.
- Tests: `OwnershipCopyTest`, `MandateOwnershipViewsTest` (unchanged, proving no copy move), `ListingOwnershipViewsTest`,
  `ListingRulesTest`, `ListingViewsTest`, `ListingControllerTest`, `ListingTemplateTest`.

---

### Task 1: One source for the verdict's French copy

Move #298's wording out of `MandateOwnershipViews` into `OwnershipCopy`, verbatim, so the listing page can word the same
verdict without copying a sentence.

**Files:**
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopy.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopyTest.kt`

**Interfaces:**
- Produces:
  - `object OwnershipCopy` with `data class Wording(val text: String, val action: String)`, `const val WAITING: String`,
    `val MISSING_OTHER: String`, `val REASON_OTHER: Wording`, `fun missing(code: String): String`,
    `fun check(code: String): String?`, `fun reason(code: String): Wording`.
  - `missing` answers the identity / ownership piece sentence for `OWNERSHIP_ID_MISSING` / `OWNERSHIP_DOCUMENT_MISSING`
    and `MISSING_OTHER` otherwise; `check` answers *La pièce d'identité* for `OWNERSHIP_ID_*`, *Le document de
    propriété* for `OWNERSHIP_DOCUMENT_*`, `null` otherwise; `reason` answers the code's wording or `REASON_OTHER`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnershipCopyTest {
    @Test
    fun `a missing piece reads what counts`() {
        assertTrue(
            OwnershipCopy.missing("OWNERSHIP_ID_MISSING")
                .contains("une carte nationale d'identité (recto et verso) ou un passeport, à son nom"),
        )
        assertTrue(
            OwnershipCopy.missing("OWNERSHIP_DOCUMENT_MISSING")
                .contains("un titre foncier ou un certificat de propriété, à son nom"),
        )
        assertEquals(OwnershipCopy.MISSING_OTHER, OwnershipCopy.missing("OWNERSHIP_SOMETHING_NEW"))
    }

    @Test
    fun `a decisive reason reads its wording and its action`() {
        assertEquals(
            OwnershipCopy.Wording(
                "La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire.",
                "Reprenez la photo d'une pièce en cours de validité, puis ajoutez-la dans Documents.",
            ),
            OwnershipCopy.reason("OWNERSHIP_ID_EXPIRED"),
        )
        assertEquals(OwnershipCopy.REASON_OTHER, OwnershipCopy.reason("OWNERSHIP_SOMETHING_NEW"))
    }

    @Test
    fun `a reason names its check's kind, and an unknown code none`() {
        assertEquals("La pièce d'identité", OwnershipCopy.check("OWNERSHIP_ID_UNUSABLE"))
        assertEquals("Le document de propriété", OwnershipCopy.check("OWNERSHIP_DOCUMENT_UNUSABLE"))
        assertNull(OwnershipCopy.check("OWNERSHIP_SOMETHING_NEW"))
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `mvn test -Dtest=OwnershipCopyTest`
Expected: compilation failure — `OwnershipCopy` unresolved.

- [ ] **Step 3: Move the copy, verbatim**

In `OwnershipCopy.kt`, move `MISSING_ID`, `MISSING_DOCUMENT`, `MISSING_OTHER`, `WAITING`, `ID_CHECK`,
`DOCUMENT_CHECK`, `REASON_WORDING` and `REASON_OTHER` from `MandateOwnershipViews` without changing a character (the
mandate page's approval covers these sentences; `MandateOwnershipViewsTest` must pass untouched). Then
`MandateOwnershipViews` calls `OwnershipCopy.missing(reason.code)`, `OwnershipCopy.check(reason.code)`,
`OwnershipCopy.reason(reason.code).text/.action` and `OwnershipCopy.WAITING`; delete its private copies. Its KDoc gains
one line: the copy lives in `OwnershipCopy`, shared with the listing page's block (#375).

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest=OwnershipCopyTest` — Expected: PASS.
Run: `mvn test -Dtest=MandateOwnershipViewsTest` — Expected: PASS, unchanged.
Run: `mvn validate` — Expected: BUILD SUCCESS (ktlint).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopy.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/OwnershipCopyTest.kt
git commit -m "refactor(npc): share the owner verification copy" -m "Refs #375"
```

---

### Task 2: The block's view model and the hold tag

Port the approved prototype's three states into a view model built from the verdict, and make the tag variant opt-in.

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Create: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingOwnershipViews.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingOwnershipViewsTest.kt`,
  `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`,
  `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: Task 1's `OwnershipCopy`; `com.wutsi.ndopify.mandate.dto.{OwnershipVerification, OwnershipVerificationStatus, OwnershipVerificationReason}`;
  `com.wutsi.ndopify.npc.ui.mandate.OwnershipReasonView` and `OwnershipCopy`; `DocumentFieldLabels.instruction(code)`.
- Produces:
  - `data class ListingOwnershipView(val status: OwnershipVerificationStatus, val unavailable: Boolean, val style: String, val title: String, val intro: String?, val missing: List<String>, val waiting: String?, val reasons: List<OwnershipReasonView>)` — `style` is `"info"` or `"warning"`.
  - `ListingOwnershipViews.of(verification: OwnershipVerification?, labels: DocumentFieldLabels): ListingOwnershipView?` — `null` only when the verdict is `VERIFIED`; a `null`/`UNKNOWN` verdict is the unavailable block.
  - `ListingRules.tag(listing: Listing, ownershipBlocked: Boolean = false): Tag` — the hold tag only for a `VERIFIED` listing with `ownershipBlocked`.
  - `ListingViews.of(..., ownership: ListingOwnershipView? = null): ListingView`; `ListingView.ownership: ListingOwnershipView? = null`.

- [ ] **Step 1: Write the failing view-builder test**

```kotlin
package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.mandate.dto.OwnershipVerification
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason
import com.wutsi.ndopify.mandate.dto.OwnershipVerificationStatus
import com.wutsi.ndopify.npc.ui.document.DocumentFieldLabels
import com.wutsi.ndopify.npc.ui.mandate.OwnershipCopy
import org.springframework.context.support.ResourceBundleMessageSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingOwnershipViewsTest {
    private val labels = DocumentFieldLabels(
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        },
    )

    private fun reason(code: String, details: List<String> = emptyList()) = OwnershipVerificationReason(code, details)

    @Test
    fun `a pending verdict lists the missing pieces, info-styled`() {
        val block = ListingOwnershipViews.of(
            OwnershipVerification(
                OwnershipVerificationStatus.PENDING,
                listOf(reason("OWNERSHIP_ID_MISSING"), reason("OWNERSHIP_DOCUMENT_MISSING")),
            ),
            labels,
        )!!

        assertEquals("Publication en attente de la vérification du propriétaire", block.title)
        assertEquals("info", block.style)
        assertFalse(block.unavailable)
        assertEquals(
            listOf(
                OwnershipCopy.missing("OWNERSHIP_ID_MISSING"),
                OwnershipCopy.missing("OWNERSHIP_DOCUMENT_MISSING"),
            ),
            block.missing,
        )
        assertNull(block.waiting)
    }

    @Test
    fun `a pending verdict with no reason says the reading is in progress`() {
        val block = ListingOwnershipViews.of(OwnershipVerification(OwnershipVerificationStatus.PENDING), labels)!!

        assertEquals("info", block.style)
        assertEquals(emptyList(), block.missing)
        assertEquals(OwnershipCopy.WAITING, block.waiting)
    }

    @Test
    fun `a failed verdict words each reason with its kind and its problem instructions`() {
        val block = ListingOwnershipViews.of(
            OwnershipVerification(
                OwnershipVerificationStatus.FAILED,
                listOf(
                    reason("OWNERSHIP_ID_UNUSABLE", listOf("DOCUMENT_UNREADABLE")),
                    reason("OWNERSHIP_DOCUMENT_NAME_MISMATCH"),
                    reason("OWNERSHIP_SOMETHING_NEW"),
                ),
            ),
            labels,
        )!!

        assertEquals("Propriétaire non vérifié", block.title)
        assertEquals("warning", block.style)
        assertEquals(listOf("La pièce d'identité", "Le document de propriété", null), block.reasons.map { it.check })
        assertEquals(listOf(labels.instruction("DOCUMENT_UNREADABLE")), block.reasons[0].problems)
        assertEquals(OwnershipCopy.reason("OWNERSHIP_DOCUMENT_NAME_MISMATCH").text, block.reasons[1].text)
        assertEquals(OwnershipCopy.REASON_OTHER.action, block.reasons[2].action)
    }

    @Test
    fun `a failed verdict with no reason still reads its block`() {
        val block = ListingOwnershipViews.of(OwnershipVerification(OwnershipVerificationStatus.FAILED), labels)!!

        assertEquals("Propriétaire non vérifié", block.title)
        assertEquals(emptyList(), block.reasons)
    }

    @Test
    fun `a verified verdict has no block`() {
        assertNull(ListingOwnershipViews.of(OwnershipVerification(OwnershipVerificationStatus.VERIFIED), labels))
    }

    @Test
    fun `a failed read or an unknown status is the unavailable block`() {
        val missing = ListingOwnershipViews.of(null, labels)!!
        val unknown = ListingOwnershipViews.of(OwnershipVerification(OwnershipVerificationStatus.UNKNOWN), labels)!!

        listOf(missing, unknown).forEach {
            assertEquals("Vérification du propriétaire indisponible", it.title)
            assertEquals("warning", it.style)
            assertTrue(it.unavailable)
            assertTrue(it.intro!!.startsWith("L'état de publication de l'annonce ne peut pas être lu"))
            assertEquals(emptyList(), it.missing)
            assertEquals(emptyList(), it.reasons)
        }
    }
}
```

- [ ] **Step 2: Add the failing tag and view tests**

```kotlin
// ListingRulesTest
@Test
fun `a verified listing blocked by its owner reads the hold tag`() {
    assertEquals(
        ListingRules.Tag("Vérifiée — publication en attente", "warning"),
        ListingRules.tag(Listing(status = ListingStatus.VERIFIED), ownershipBlocked = true),
    )
    assertEquals(
        ListingRules.Tag("Vérifiée", "success"),
        ListingRules.tag(Listing(status = ListingStatus.VERIFIED)),
    )
    assertEquals(
        ListingRules.Tag("Brouillon", "info"),
        ListingRules.tag(Listing(status = ListingStatus.DRAFT), ownershipBlocked = true),
    )
}
```

Add the imports `ListingOwnershipView` and `OwnershipVerificationStatus` to the file.

```kotlin
// ListingViewsTest
@Test
fun `a verified listing blocked by its owner reads the hold tag, not the sent panel`() {
    val block = ListingOwnershipView(
        status = OwnershipVerificationStatus.PENDING,
        unavailable = false,
        style = "info",
        title = "Publication en attente de la vérification du propriétaire",
        intro = "L'annonce est vérifiée.",
        missing = listOf("Il manque la pièce d'identité du propriétaire."),
        waiting = null,
        reasons = emptyList(),
    )

    val view = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT),
        IMAGES,
        locations,
        formatter,
        ownership = block,
    )

    assertEquals(ListingRules.Tag("Vérifiée — publication en attente", "warning"), view.tag)
    assertNull(view.panel)
    assertEquals(block, view.ownership)
}

@Test
fun `a verified listing whose owner is verified keeps today's tag and panel`() {
    val view = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT),
        IMAGES,
        locations,
        formatter,
    )

    assertEquals(ListingRules.Tag("Vérifiée", "success"), view.tag)
    assertEquals(ListingView.PANEL_SENT, view.panel)
    assertNull(view.ownership)
}
```

- [ ] **Step 3: Run them to make sure they fail**

Run: `mvn test -Dtest=ListingOwnershipViewsTest,ListingRulesTest,ListingViewsTest`
Expected: compilation failure — `ListingOwnershipViews`, `ListingOwnershipView`, `ownershipBlocked` and `ownership`
unresolved.

- [ ] **Step 4: Implement the view model and the builder**

In `ListingView.kt`, add `ListingOwnershipView` (KDoc: the publication block of a blocked `VERIFIED` listing, worded as
the mandate page words the verdict, no date and no document) and `val ownership: ListingOwnershipView? = null` on
`ListingView`.

In `ListingOwnershipViews.kt`, `object ListingOwnershipViews` with the approved copy — `PENDING_TITLE = "Publication en
attente de la vérification du propriétaire"`, `PENDING_INTRO = "L'annonce est vérifiée. Sa publication attend que le
propriétaire soit vérifié, à partir des pièces de la section Documents de son mandat\u00a0; elle se fera alors
d'elle-même."`, `FAILED_TITLE = "Propriétaire non vérifié"`, `FAILED_INTRO = "L'annonce est vérifiée, mais sa
publication attend un propriétaire vérifié. La vérification n'a pas abouti\u00a0:"`, `UNAVAILABLE_TITLE = "Vérification
du propriétaire indisponible"`, `UNAVAILABLE_INTRO = "L'état de publication de l'annonce ne peut pas être lu pour le
moment. Rechargez la page dans quelques instants\u00a0; la vérification du propriétaire reprendra d'elle-même."` — and:

```kotlin
fun of(verification: OwnershipVerification?, labels: DocumentFieldLabels): ListingOwnershipView? {
    val status = verification?.status ?: OwnershipVerificationStatus.UNKNOWN
    if (verification != null && status == OwnershipVerificationStatus.VERIFIED) return null
    if (verification == null || status == OwnershipVerificationStatus.UNKNOWN) {
        return ListingOwnershipView(
            status = OwnershipVerificationStatus.UNKNOWN,
            unavailable = true,
            style = "warning",
            title = UNAVAILABLE_TITLE,
            intro = UNAVAILABLE_INTRO,
            missing = emptyList(),
            waiting = null,
            reasons = emptyList(),
        )
    }
    // PENDING, FAILED: one block per verdict, as the approved prototype
}
```

For `PENDING`: `missing = verification.reasons.map { OwnershipCopy.missing(it.code) }`, `waiting = if
(verification.reasons.isEmpty()) OwnershipCopy.WAITING else null`, `reasons = emptyList()`. For `FAILED`: `reasons =
verification.reasons.map { OwnershipReasonView(OwnershipCopy.check(it.code), OwnershipCopy.reason(it.code).text,
OwnershipCopy.reason(it.code).action, it.details.map(labels::instruction)) }`, `missing = emptyList()`, `waiting =
null`. Both titled as above; `PENDING` style `"info"`, `FAILED` style `"warning"`.

In `ListingRules`, add `private val HOLD_TAG = Tag("Vérifiée — publication en attente", "warning")` beside the other
tags and change the listing overload to
`fun tag(listing: Listing, ownershipBlocked: Boolean = false): Tag = when { ownershipBlocked && listing.status == ListingStatus.VERIFIED -> HOLD_TAG; rejections(listing).isNotEmpty() -> TO_FIX_TAG; else -> tag(listing.status) }`
(the status overload and every caller keep their behavior).

In `ListingViews.of`, add the trailing parameter `ownership: ListingOwnershipView? = null`; set `tag = ListingRules.tag(listing, ownershipBlocked = ownership != null)`, make `panel` `null` when `ownership != null` (before the
existing branches, after `readOnly`), and set `ownership = ownership.takeIf { !readOnly }`.

- [ ] **Step 5: Run the tests**

Run: `mvn test -Dtest=ListingOwnershipViewsTest` — Expected: PASS.
Run: `mvn test -Dtest=ListingRulesTest` — Expected: PASS.
Run: `mvn test -Dtest=ListingViewsTest` — Expected: PASS.
Run: `mvn validate` — Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingOwnershipViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingOwnershipViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): word a verified listing's wait on its owner" -m "Refs #375"
```

---

### Task 3: Read the verdict on the listing page

Add the conditional read to `ListingController` and prove when it happens and when it must not.

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Modify: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt` (constructor, this task's compile fix)

**Interfaces:**
- Consumes: Task 2's `ListingOwnershipViews.of(...)`, `ListingView.ownership`; the existing `NdMandateClient.ownership(id)`
  (#298), the controller's `lookup(what, call)`.
- Produces: `ListingController(..., private val documentLabels: DocumentFieldLabels)`; the model's `listing` carries
  `ownership` only for the owner's `VERIFIED` listing; no new model attribute.

- [ ] **Step 1: Write the failing controller tests**

```kotlin
@Test
fun `a verified listing whose owner isn't verified carries the block`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    doReturn(
        OwnershipVerification(
            OwnershipVerificationStatus.FAILED,
            listOf(OwnershipVerificationReason("OWNERSHIP_ID_EXPIRED")),
        ),
    ).whenever(mandateClient).ownership(203)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertEquals(ListingRules.Tag("Vérifiée — publication en attente", "warning"), view.tag)
    assertEquals("Propriétaire non vérifié", view.ownership?.title)
    verify(mandateClient).ownership(203)
}

@Test
fun `a verified listing whose owner is verified keeps today's page`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    doReturn(OwnershipVerification(OwnershipVerificationStatus.VERIFIED)).whenever(mandateClient).ownership(203)

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertNull(view.ownership)
    assertEquals(ListingView.PANEL_SENT, view.panel)
}

@Test
fun `a failed verdict read only makes the block unavailable`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).ownership(203)

    val result = mvc.perform(get("/listings/501")).andReturn()
    val view = model(result)["listing"] as ListingView

    assertEquals("listing", result.modelAndView?.viewName)
    assertEquals(true, view.ownership?.unavailable)
}

@Test
fun `only a verified listing of the page's agent asks for the verdict`() {
    listOf(
        ListingStatus.DRAFT,
        ListingStatus.PENDING_VERIFICATION,
        ListingStatus.UNDER_VERIFICATION,
        ListingStatus.PUBLISHING,
        ListingStatus.PUBLISHED,
        ListingStatus.RENTED,
        ListingStatus.SOLD,
        ListingStatus.EXPIRED,
        ListingStatus.CANCELLED,
    ).forEach { status ->
        doReturn(COMPLETE.copy(status = status, publishedAt = SENT, url = "/douala/bonapriso/listing/501/appartement"))
            .whenever(listingClient).get(501)

        mvc.perform(get("/listings/501")).andReturn()

        verify(mandateClient, never()).ownership(anyLong())
    }
}

@Test
fun `another agent's published listing asks for no verdict`() {
    doReturn(COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT))
        .whenever(listingClient).get(501)

    mvc.perform(get("/listings/501")).andReturn()

    verify(mandateClient, never()).ownership(anyLong())
}
```

Add the imports for `OwnershipVerification`, `OwnershipVerificationReason`, `OwnershipVerificationStatus` and
`anyLong`, and add the `documentLabels` mock to the controller construction:
`DocumentFieldLabels(ResourceBundleMessageSource().apply { setBasename("messages"); setDefaultEncoding("UTF-8") })`
(a real instance: its text is asserted).

- [ ] **Step 2: Run them to make sure they fail**

Run: `mvn test -Dtest=ListingControllerTest`
Expected: compilation failure — the controller has no `documentLabels`, the view no `ownership`.

- [ ] **Step 3: Implement the read**

Add `private val documentLabels: DocumentFieldLabels` to the constructor (last parameter), import
`com.wutsi.ndopify.npc.ui.document.DocumentFieldLabels`, `ListingStatus` (already imported) and the ownership DTOs. In
`show`, after `val readOnly = ...` and before `ListingViews.of`, add:

```kotlin
// #375: a verified listing is published by ListingJobs.publish only once #297's verdict answers VERIFIED (#299).
// Only its own agent's page asks, and only while it is VERIFIED: another status is past or before the gate, and a
// foreign lens reads only public statuses. A failed read costs only the block.
val ownership = if (!readOnly && listing.status == ListingStatus.VERIFIED) {
    ListingOwnershipViews.of(
        lookup("ownership verdict of listing ${listing.id}") { mandateClient.ownership(listing.mandateId) },
        documentLabels,
    )
} else {
    null
}
```

Pass `ownership = ownership` to `ListingViews.of`. KDoc on `show`: one line saying the block's read follows the same
per-section degradation. Update the `ListingController(...)` construction in `ListingTemplateTest` (add the same real
`DocumentFieldLabels`) so the module compiles.

- [ ] **Step 4: Run the tests**

Run: `mvn test -Dtest=ListingControllerTest` — Expected: PASS.
Run: `mvn validate` — Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt
git commit -m "feat(npc): read the owner verdict on the listing page" -m "Refs #375"
```

---

### Task 4: The approved block on the page

Port the approved prototype's block, its hold pill and its action into the production template and stylesheet, then
prove the rendered page.

**Files:**
- Create: `src/main/resources/templates/fragments/listing-ownership.html`
- Modify: `src/main/resources/templates/listing.html`
- Modify: `src/main/assets/css/listing.css`
- Modify: `modules/ndopify-partner-central/CLAUDE.md`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: the model's `listing.ownership: ListingOwnershipView?` (Task 3).
- Produces: the rendered block — `[data-listing-ownership="PENDING" | "FAILED" | "UNAVAILABLE"]`, an `h2` title, the
  pieces or reasons, and `a[href=/mandates/{id}#ownership-title]`; the tag `status-tag status-tag--warning
  status-tag--hold`.

- [ ] **Step 1: Write the failing template tests**

```kotlin
private fun verification(status: OwnershipVerificationStatus, vararg reasons: OwnershipVerificationReason) =
    doReturn(OwnershipVerification(status, reasons.toList())).whenever(mandateClient).ownership(203)

@Test
fun `a verified listing blocked by its owner explains the wait`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    verification(OwnershipVerificationStatus.PENDING, OwnershipVerificationReason("OWNERSHIP_ID_MISSING"))

    val page = render()
    val tag = page.selectFirst(".mandate-intro .status-tag")!!
    val block = page.selectFirst("[data-listing-ownership]")!!

    assertEquals("Vérifiée — publication en attente", tag.text())
    assertTrue(tag.hasClass("status-tag--warning"))
    assertTrue(tag.hasClass("status-tag--hold"))
    assertEquals("Publication en attente de la vérification du propriétaire", block.selectFirst("h2")!!.text())
    assertTrue(block.text().contains("Il manque la pièce d'identité du propriétaire"))
    assertEquals("/mandates/203#ownership-title", block.selectFirst("a")!!.attr("href"))
    assertEquals("Vérifier le propriétaire dans le mandat", block.selectFirst("a")!!.text())
    assertTrue(page.select("[data-listing-panel=SENT]").isEmpty())
}

@Test
fun `a failed verdict names each reason and its problems, never a document`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    verification(
        OwnershipVerificationStatus.FAILED,
        OwnershipVerificationReason("OWNERSHIP_ID_UNUSABLE", listOf("DOCUMENT_UNREADABLE")),
        OwnershipVerificationReason("OWNERSHIP_DOCUMENT_NAME_MISMATCH"),
    )

    val block = render().selectFirst("[data-listing-ownership]")!!

    assertEquals("Propriétaire non vérifié", block.selectFirst("h2")!!.text())
    assertTrue(block.text().contains("La pièce d'identité"))
    assertTrue(block.text().contains("Le document n'a pas pu être lu."))
    assertTrue(block.text().contains("Le document ne désigne pas le propriétaire du bien."))
    // No document id, type or title anywhere in the block.
    assertFalse(block.text().contains("Carte nationale d'identité"))
    assertFalse(block.text().contains("Titre foncier"))
    assertFalse(Regex("\\b\\d{3,}\\b").containsMatchIn(block.text()))
}

@Test
fun `an unavailable verdict only makes the block unavailable`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).ownership(203)

    val page = render()

    assertEquals("Vérification du propriétaire indisponible", page.selectFirst("[data-listing-ownership] h2")!!.text())
    assertNotNull(page.selectFirst(".checklist"))
    assertEquals("/mandates/203#ownership-title", page.selectFirst("[data-listing-ownership] a")!!.attr("href"))
}

@Test
fun `a verified listing whose owner is verified renders as today`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)
    verification(OwnershipVerificationStatus.VERIFIED)

    val page = render()

    assertNull(page.selectFirst("[data-listing-ownership]"))
    assertEquals("Vérifiée", page.selectFirst(".mandate-intro .status-tag")!!.text())
    assertNotNull(page.selectFirst("[data-listing-panel=SENT]"))
}

@Test
fun `a draft asks for no verdict and shows no block`() {
    val page = render()

    assertNull(page.selectFirst("[data-listing-ownership]"))
    verify(mandateClient, never()).ownership(anyLong())
}
```

Also extend the existing read-only test (another agent's published listing) with
`verify(mandateClient, never()).ownership(anyLong())`.

- [ ] **Step 2: Run them to make sure they fail**

Run: `mvn test -Dtest=ListingTemplateTest`
Expected: FAIL — no `[data-listing-ownership]` in the rendered page.

- [ ] **Step 3: Port the block**

Create `fragments/listing-ownership.html`, `th:fragment="ownership(section, mandateId)"`, from the approved
`v2/listing.html` block: the same classes (`alert`, `alert--info` / `alert--warning`, `listing-ownership`,
`listing-ownership__title`, `ownership__intro`, `ownership__pieces`, `ownership__reasons`, `ownership__reason`,
`ownership__check`, `ownership__problems`, `ownership__action`, `ownership__actions`), the same icons
(`fragments/icon :: info | warning`), `data-listing-ownership` on each block; `th:if` on
`section.status.name() == 'PENDING'` (and `!section.unavailable`), `'FAILED'`, and `section.unavailable`; the `h2`
title from `section.title`; the intro `th:text`; the missing list `th:each`; the reading sentence
`th:if="${section.waiting}"`; the reasons list with the check `th:if="${reason.check}"`, the wording, the problems
`th:unless="${#lists.isEmpty(reason.problems)}"`, and the action; the unavailable text. The one action:
`th:href="@{|/mandates/${mandateId}#ownership-title|}"`, label *Vérifier le propriétaire dans le mandat*.

- [ ] **Step 4: Include it on the page**

In `templates/listing.html`, immediately after `<div th:replace="~{:: flash}"></div>` and before the DRAFT panel, add
`<th:block th:replace="~{fragments/listing-ownership :: ownership(${l.ownership}, ${l.mandateId})}" th:if="${l.ownership}"></th:block>`.
Update the template's comment block: a blocked `VERIFIED` listing explains the wait (the block, one link to the
mandate), no script.

- [ ] **Step 5: Run the tests**

Run: `mvn test -Dtest=ListingTemplateTest` — Expected: PASS.

- [ ] **Step 6: Port the styles**

In `src/main/assets/css/listing.css`, add the prototype's rules verbatim from `v2/assets/css/listing.css`:
`.status-tag--hold { max-width: 100%; white-space: normal; line-height: 1.4; text-align: left; }`,
`.listing-ownership__title { font-family: var(--font-body); font-size: var(--step-body); font-weight: 600; line-height: 1.4; }`
and `.listing-ownership .ownership__actions .btn { max-width: 100%; white-space: normal; }`. No `build-assets.mjs`
change: `listing` is already in `CSS`, and `ownership` follows it.

- [ ] **Step 7: Document the page**

In `modules/ndopify-partner-central/CLAUDE.md`, in the listing page's paragraph, add two or three sentences: a
`VERIFIED` listing whose mandate's owner isn't verified reads the hold tag and one block above the checklist
(`fragments/listing-ownership.html`, `ListingOwnershipViews`) read from #297's verdict only on the owner's `VERIFIED`
page, worded by #298's `OwnershipCopy`, with one link to `/mandates/{id}#ownership-title`; every other status and the
read-only lens are unchanged.

- [ ] **Step 8: Verify the module**

Run: `mvn verify` (in `modules/ndopify-partner-central`) — Expected: BUILD SUCCESS (ktlint, all tests, coverage gate,
the asset bundle built).

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/listing-ownership.html \
        modules/ndopify-partner-central/src/main/resources/templates/listing.html \
        modules/ndopify-partner-central/src/main/assets/css/listing.css \
        modules/ndopify-partner-central/CLAUDE.md \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt
git commit -m "feat(npc): explain a verified listing's wait on the owner" -m "Refs #375"
```

---

## Self-Review

- **Spec coverage:** the hold tag (Task 2/4), the three verdict states (Tasks 2/4), the one link to
  `/mandates/{id}#ownership-title` (Task 4), the read only on the owner's `VERIFIED` page (Task 3), the shared copy
  (Task 1), the unavailable verdict (Tasks 2/3/4), no JavaScript and the narrow viewport (Task 4's CSS, the
  prototype), no leak (Task 4), the module doc (Task 4).
- **Type consistency:** `ListingOwnershipView` fields as defined in Task 2 are the ones Task 4's fragment reads;
  `ListingRules.tag(listing, ownershipBlocked)` and `ListingViews.of(..., ownership)` are the signatures Task 3 calls.
- **No production code before the prototype:** the prototype is approved and committed; Task 4's first step ports it.
- **Review Focus:** each of the five lines has its test in the task named (controller `never()` and failure, tag
  default, unknown/`*_UNUSABLE`/empty reasons, leak assertions).
