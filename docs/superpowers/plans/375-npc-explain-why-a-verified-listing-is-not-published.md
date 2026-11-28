# The listing page explains a verified listing's publication in progress — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status:** revised for story #375's design gate answer **option (a)**; revised French wording and prototype **approved by the user**. Finalized in place; same file — never a second plan. Not implemented.

**Goal:** On the agent's own listing page, a `VERIFIED` listing that is not yet published reads a neutral hold tag and one *Publication en cours* block, valid whether the publication gate is on or off, links to the mandate's Documents, and claims no owner verification — without JavaScript and without any ownership read.

**Architecture:** `modules/ndopify-partner-central` only. No `ndopify-dto`, `ndopify-server`, `ndopify-web-core`, nor server contract change. `ListingRules.tag` gains an opt-in hold variant; `ListingViews` sets `publicationHold` only for `!readOnly && listing.status == VERIFIED`; a server-rendered fragment renders one neutral block above the checklist. **No `NdMandateClient.ownership` call. No `GET /v1/mandates/{id}/ownership`. No ownership verdict, no ownership read. No reason list.** The *En vérification* panel gives way only on this hold variant; every other status and the read-only lens are unchanged. CSS only adds wrapping/action layout.

**Tech Stack:** Kotlin, Spring Boot BFF (`ndopify-partner-central`), Thymeleaf, `kotlin.test` + `mockito-kotlin`, MockMvc, jsoup template tests, esbuild bundle, `mvn verify`. No JavaScript required.

**Spec:** GitHub issue #375 (unchanged original text preserved; read it with its comments) plus the revised prototype baseline in `modules/ndopify-partner-central/src/main/html/v2/`:
- `listing.html`
- `assets/js/ownership-copy.js`
- `assets/js/partner-central.js`
- `assets/css/listing.css`
- `README.md`

**Prototype (approved):** user approved revised French wording + prototype unchanged. `listing.html?id=706`; `mandate.html?id=312#documents-title`; `listing.html?id=5001`; no-JS; 390 px. No loading, empty or error state; no verified-unblocked variant; no `?ownership=` switch. `ownership-copy.js` remains for mandate page #298/#376/#377; listing does not use it. The prototype's `VERIFIED_HOLD` label matches production's hold tag.

## Global Constraints

- One module: `modules/ndopify-partner-central`. No `ndopify-dto`, `ndopify-server`, `ndopify-web-core`, nor prototype change in production implementation; production template/CSS/test changes only.
- **No ownership read**: do not call `NdMandateClient.ownership`, `GET /v1/mandates/{id}/ownership`, or any ownership verdict endpoint. Do not re-derive ownership from documents. Partner Central cannot read whether publication is blocked; do not claim it can.
- `Shared code: none`. `UI: yes`. Public page untouched; listing stays invisible until published.
- Only `!readOnly && listing.status == ListingStatus.VERIFIED`. `DRAFT`, `PENDING_VERIFICATION`, `UNDER_VERIFICATION`, `PUBLISHING`, `PUBLISHED`, `RENTED`, `SOLD`, `EXPIRED`, `CANCELLED`, and another agent's read-only lens render as today. `PUBLISHING` is past publication start; no hold. `ListingRules.tag` default keeps plain *Vérifiée* for mandate line / cards; hold opt-in listing-page only.
- French wording exact:
  - tag: `Vérifiée — publication en cours`
  - title: `Publication en cours`
  - body: `L'annonce est vérifiée. Sa publication est en cours et peut prendre quelques minutes. Si elle n'apparaît pas, vérifiez les documents du mandat, qui sont la cause probable.`
  - action: `Voir les documents du mandat`
- Wording is **neutral**, valid whether publication is automatic or waiting on a check; valid whether the publication gate is on or off. Not owner-verification. Do not say `publication en attente de la vérification du propriétaire`, `Propriétaire non vérifié`, `Vérification du propriétaire indisponible`, `Propriétaire vérifié`, `manual review`, `blocked`, or any owner-verification reason. Documents are **only a likely cause**, not an assertion; no reason is listed.
- One action opens `/mandates/{mandateId}#documents-title`, not `#ownership-title`; no publish button; no retry; no manual review.
- No leak: no customer name, document id, type, title, page, path, storage URL, extracted value, verdict JSON, reason code. Only exact French copy above.
- Server-rendered; no JavaScript required; no page script; no JS bundle entry. Works with JS disabled. Narrow viewport 390 px: tag and block wrap, no sideways scrolling, action own line, focus ring preserved, `h2` hierarchy. No hard-coded `FCFA`; no dates/amounts added.
- UI text French; code, comments, docs English. `kotlin.test`; mocks `com.nhaarman.mockitokotlin2`; tests mirror package. `mvn validate` (ktlint) and `mvn verify` in `modules/ndopify-partner-central` pass. Clean run may need network for `npm ci`.
- Prototype is baseline; first implementation step ports revised prototype, does not redesign. Approved prototype comments in `src/main/html/v2` are not production proof.

## Review Focus

1. **A `VERIFIED` own-agent listing not yet published** — hold tag + neutral block above checklist; *En vérification* panel gives way; page still answers 200; no ownership call. Tasks 1/2/3.
2. **Publication gate on or off** — no read of gate; wording doesn't claim blocked by owner; no ownership verdict; no *Propriétaire non vérifié*; no unavailable state. Tasks 1/3.
3. **Other statuses / read-only** — `PUBLISHING`, `PUBLISHED`, etc. unchanged; another agent's lens unchanged; no hold; no ownership call. Tasks 1/3.
4. **No invented cause / no leak** — documents likely cause only; no reason codes; no ids/titles/paths/extracted values; no manual review. Tasks 2/3.
5. **No JS / narrow mobile** — complete server render; 390 px wrap; own-line action; focus ring; `h2`. Tasks 2/3.

---

## File Structure

- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt` — opt-in hold tag, no ownership input.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt` — `publicationHold: Boolean = false`; no ownership field.
- Modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt` — set hold, remove *En vérification* panel only when hold; no ownership read.
- Do **not** modify `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt` for ownership. No controller ownership read.
- Do **not** create `OwnershipCopy`, `ListingOwnershipViews`, or any ownership-derived listing view. Existing `ui/mandate` ownership copy remains untouched.
- Create `src/main/resources/templates/fragments/listing-publication.html` — neutral server-rendered block.
- Modify `src/main/resources/templates/listing.html` — include fragment above checklist only when hold; replace panel suppression.
- Modify `src/main/assets/css/listing.css` — hold pill + `.listing-publication*` layout; no unrelated rules.
- Modify `modules/ndopify-partner-central/CLAUDE.md` — listing paragraph; no ownership read.
- Tests mirror main package: `ListingRulesTest`, `ListingViewsTest`, `ListingTemplateTest`, `ListingControllerTest`; no ownership test additions to mandate ownership views.
- `mvn verify`.

---

### Task 1: Opt-in hold tag

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`

**Interfaces:**
- Consumes: existing `Listing`, `ListingStatus`, `Tag`.
- Produces: `ListingRules.tag(listing: Listing, publicationHold: Boolean = false): Tag`; `HOLD_TAG = Tag("Vérifiée — publication en cours", "warning")`; hold only when `publicationHold && listing.status == ListingStatus.VERIFIED`. Status overload `tag(status)` and every existing caller keep behavior. No ownership parameter.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test
fun `a verified listing not yet published reads the neutral hold tag`() {
    assertEquals(
        ListingRules.Tag("Vérifiée — publication en cours", "warning"),
        ListingRules.tag(Listing(status = ListingStatus.VERIFIED), publicationHold = true),
    )
    assertEquals(
        ListingRules.Tag("Vérifiée", "success"),
        ListingRules.tag(Listing(status = ListingStatus.VERIFIED)),
    )
    assertEquals(
        ListingRules.Tag("Vérifiée", "success"),
        ListingRules.tag(Listing(status = ListingStatus.VERIFIED), publicationHold = false),
    )
}

@Test
fun `the hold tag never leaks to other statuses`() {
    assertEquals(
        ListingRules.Tag("En vérification", "warning"),
        ListingRules.tag(Listing(status = ListingStatus.PUBLISHING), publicationHold = true),
    )
    assertEquals(
        ListingRules.Tag("Publiée", "success"),
        ListingRules.tag(Listing(status = ListingStatus.PUBLISHED), publicationHold = true),
    )
}
```

Expected exact names match current `ListingRules` (`Tag`; current labels verify in code; adjust only if the file already names constants differently, not copy).

- [ ] **Step 2: Run `mvn test -Dtest=ListingRulesTest`** — fails: `publicationHold` unresolved.
- [ ] **Step 3: Implement `publicationHold`, default `false`, `HOLD_TAG`, and hold-first `when`; no ownership logic.**
- [ ] **Step 4: Run `mvn test -Dtest=ListingRulesTest`** — passes.
- [ ] **Step 5: Commit** `feat(npc): add neutral verified-listing hold tag` + `Refs #375`.

---

### Task 2: `publicationHold` on the listing view; no ownership read

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`

**Interfaces:**
- Consumes: `ListingRules.tag(listing, publicationHold)`.
- Produces: `ListingView.publicationHold: Boolean = false`. `ListingViews.of(..., publicationHold`? no — **derive inside `ListingViews.of`**; no caller flag. Existing trailing parameters keep defaults; no `ownership` parameter; no ownership DTO.
- Produces: `publicationHold = !readOnly && listing.status == ListingStatus.VERIFIED`.
- Produces: `panel == null` when `publicationHold`, before existing panel branches, after `readOnly`; existing `PANEL_SENT` for `VERIFIED` otherwise.
- Does **not** change controller; no `mandateClient.ownership` call anywhere in this feature.

- [ ] **Step 1: Write failing `ListingViewsTest`**

```kotlin
@Test
fun `a verified own-agent listing is a neutral publication hold, not an ownership block`() {
    val view = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT),
        IMAGES,
        locations,
        formatter,
    )

    assertEquals(ListingRules.Tag("Vérifiée — publication en cours", "warning"), view.tag)
    assertTrue(view.publicationHold)
    assertNull(view.panel)
}

@Test
fun `publication hold does not apply to read-only or other statuses`() {
    val readOnly = ListingViews.of(
        COMPLETE.copy(status = ListingStatus.PUBLISHED, agentId = 999, publishedAt = SENT, url = "/x"),
        IMAGES, locations, formatter, readOnly = true,
    )
    assertFalse(readOnly.publicationHold)
    assertEquals(ListingRules.Tag("Publiée", "success"), readOnly.tag)

    val publishing = ListingViews.of(COMPLETE.copy(status = ListingStatus.PUBLISHING), IMAGES, locations, formatter)
    assertFalse(publishing.publicationHold)
}
```

Match `ListingViews.of` existing parameter names/order in code; do not invent extra args.

- [ ] **Step 2: Controller test proves no ownership read**

Add to `ListingControllerTest` (and reuse in `ListingTemplateTest` where relevant): a `VERIFIED` own-agent listing and all other statuses / read-only never call `verify(mandateClient, never()).ownership(anyLong())`. `ListingController` must not require any new ownership constructor dependency.

- [ ] **Step 3: Run `mvn test -Dtest=ListingViewsTest,ListingControllerTest`** — fails: `publicationHold` unresolved.
- [ ] **Step 4: Implement derivation + panel suppression; no controller change.**
- [ ] **Step 5: Run `mvn test -Dtest=ListingViewsTest,ListingControllerTest`** — passes.
- [ ] **Step 6: Commit** `feat(npc): derive neutral publication hold without ownership read` + `Refs #375`.

---

### Task 3: Server-rendered neutral block + prototype port

**Files:**
- Create: `src/main/resources/templates/fragments/listing-publication.html`
- Modify: `src/main/resources/templates/listing.html`
- Modify: `src/main/assets/css/listing.css`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: `l.publicationHold`, `l.mandateId`.
- Produces: `[data-listing-publication]`, `.listing-publication`, `.listing-publication__title`, `.listing-publication__intro`, `.listing-publication__actions`, `data-listing-publication-link`, `.status-tag--hold`, `.alert--info`; `h2` title; `a[href=/mandates/{id}#documents-title]`.
- Produces exactly one block above checklist when `l.publicationHold`; hides `[data-listing-panel=SENT]`/`PENDING` as today via `l.panel == null`; no ownership fragment.
- Consumes revised prototype `v2/listing.html`, `v2/assets/css/listing.css`, `v2/assets/js/partner-central.js`, `v2/assets/js/ownership-copy.js`, `v2/README.md` as baseline. **First implementation step ports revised prototype; no redesign.** `ownership-copy.js` not used by listing; no `#ownership-title`; no `?ownership=`; no `GET /v1/mandates/{id}/ownership`.

- [ ] **Step 1: Write failing template test**

```kotlin
@Test
fun `a verified listing not yet published shows neutral publication in progress`() {
    doReturn(COMPLETE.copy(status = ListingStatus.VERIFIED, modifiedAt = SENT)).whenever(listingClient).get(501)

    val page = render()
    val tag = page.selectFirst(".mandate-intro .status-tag")!!
    val block = page.selectFirst("[data-listing-publication]")!!

    assertEquals("Vérifiée — publication en cours", tag.text())
    assertTrue(tag.hasClass("status-tag--warning"))
    assertTrue(tag.hasClass("status-tag--hold"))
    assertEquals("Publication en cours", block.selectFirst("h2")!!.text())
    assertEquals(
        "L'annonce est vérifiée. Sa publication est en cours et peut prendre quelques minutes. Si elle n'apparaît pas, vérifiez les documents du mandat, qui sont la cause probable.",
        block.selectFirst(".listing-publication__intro")!!.text(),
    )
    assertEquals("/mandates/203#documents-title", block.selectFirst("a")!!.attr("href"))
    assertEquals("Voir les documents du mandat", block.selectFirst("a")!!.text())
    assertTrue(page.select("[data-listing-panel=SENT]").isEmpty())
    assertFalse(page.html().contains("Propriétaire non vérifié"))
    assertFalse(page.html().contains("Vérification du propriétaire"))
    assertFalse(page.html().contains("ownership-title"))
    verify(mandateClient, never()).ownership(anyLong())
}

@Test
fun `every other status and read-only lens show no publication block`() {
    // render DRAFT/PUBLISHING/PUBLISHED/read-only; assert null [data-listing-publication]; unchanged tag/panel; never ownership.
}
```

Use `anyLong` import if `ownership` exists on `NdMandateClient`; do not add ownership read.

- [ ] **Step 2: Run `mvn test -Dtest=ListingTemplateTest`** — fails: no `[data-listing-publication]`.
- [ ] **Step 3: Port revised prototype markup.** Fragment `th:fragment="publication(mandateId)"`, `class="alert alert--info listing-publication"`, `data-listing-publication`, info icon, `h2.alert__title.listing-publication__title`, exact body, one `btn btn--outline btn--compact`, `th:href="@{|/mandates/${mandateId}#documents-title|}"`, `data-listing-publication-link`, label exact. Include `th:if="${l.publicationHold}"` at call site. Hide panel when `l.panel == null` as existing. No script.
- [ ] **Step 4: Port CSS.** `.status-tag--hold { max-width:100%; white-space:normal; line-height:1.4; text-align:left; }`, `.listing-publication__title { font-family:var(--font-body); font-size:var(--step-body); font-weight:600; line-height:1.4; }`, `.listing-publication__intro { overflow-wrap:anywhere; }`, `.listing-publication__actions { margin-top:var(--space-3); overflow-wrap:anywhere; }`, `.listing-publication__actions .btn { display:inline-block; max-width:100%; white-space:normal; text-align:left; }`, `.listing-publication__actions .btn:focus-visible { outline:2px solid var(--color-focus); outline-offset:2px; }`. No `build-assets.mjs` change.
- [ ] **Step 5: Run `mvn test -Dtest=ListingTemplateTest`** — passes.
- [ ] **Step 6: Verify no-JS + 390 px in prototype** `listing.html?id=706`, no `ownership-copy.js` requirement for listing; static block complete; no horizontal scroll; `h2`; focus. No production JS.
- [ ] **Step 7: Commit** `feat(npc): explain verified listing publication in progress` + `Refs #375`.

---

### Task 4: Documentation + full verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`
- (Docs only; no production code.)

- [ ] **Step 1: Update listing paragraph.** A `VERIFIED` own-agent listing not yet published reads `Vérifiée — publication en cours` + one neutral `Publication en cours` block above checklist (`fragments/listing-publication.html`, `ListingView.publicationHold`), exact body + `Voir les documents du mandat` → `/mandates/{id}#documents-title`; **no ownership read**, no `GET /v1/mandates/{id}/ownership`, no ownership verdict, no owner-verification wording; wording valid whether publication gate on/off; documents likely cause only; *En vérification* panel gives way; no JS; other statuses/read-only unchanged; no publish/retry/manual review; no leak. Keep revised prototype reference.
- [ ] **Step 2: `mvn validate`.**
- [ ] **Step 3: `mvn verify` in `modules/ndopify-partner-central`.**
- [ ] **Step 4: `mvn test` page-script tests if run separately; built app bundle exists.**
- [ ] **Step 5: Commit** `docs(npc): document neutral verified-listing publication block` + `Refs #375`.

---

## Self-Review

- **Spec coverage:** neutral hold tag + block; exact French; documents likely cause; no owner verification; no ownership read; no gate read; other statuses unchanged; read-only unchanged; no JS; 390 px; no leak; no publish/retry/manual review; prototype baseline; module docs.
- **No production code before prototype approval:** revised prototype paths listed; user approved revised French + prototype unchanged; plan finalized only after approval.
- **Type consistency:** `publicationHold`, `ListingRules.tag(listing, publicationHold=false)`, `HOLD_TAG`, `data-listing-publication`, `mandateId`; no ownership types.
- **No second plan:** revise this file only; never `docs/superpowers/specs`.
- **Review Focus:** each line has a task/test.
- **No ownership read:** `ListingController` unchanged; `MandateOwnershipViews` / `OwnershipCopy` untouched; `NdMandateClient.ownership` not called for listing.
