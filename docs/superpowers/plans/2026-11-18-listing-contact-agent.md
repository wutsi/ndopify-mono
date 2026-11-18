# Contact an agent from a listing, as an accepted match — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A visitor of a published listing who already has a search of that city and type is recorded as an **ACCEPTED** match — reopened from any prior status — the search's agent is told to call, and the customer gets the booking link; the listing page's CTA becomes *Être contacté par un agent*.

**Architecture:** `MatchService` owns the row: a new `acceptInterest` evaluates the pair with a new `Matcher.scoreInterest` (ruled out only for the customer's own property), reopens any existing row to `ACCEPTED` + `answeredAt`, or inserts it. `MatchInterestService` orchestrates (agent notification + a `MatchInterestAcceptedEvent`); `MatchProposalService` sends the customer the accepted proposal e-mail after commit, reusing `MatchProposedMailet`. The web app's controller does not change: it still posts `POST /v1/mandates` with `originListingId`, and the server decides between a new mandate, an accepted match, or the origin path.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Hibernate multi-tenancy, Thymeleaf, `kotlin.test`, Mockito-Kotlin, Maven, ktlint, JaCoCo.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/195

## Global Constraints

- Read the root `CLAUDE.md`, `modules/{ndopify-server,ndopify-web}/CLAUDE.md` before starting. Their rules win over this plan.
- **No library change**: nothing in `modules/ndopify-dto/` or `modules/ndopify-web-core/`. If one becomes necessary, stop and ask. Never commit a `-SNAPSHOT` in the root `pom.xml`.
- **No migration**: `T_MATCH` already has `answered_at`, `listing_price`, `rejection_reason_code` and `rejection_reason`.
- Every behaviour change has a test; `mvn verify` in each touched module is green (ktlint + tests + JaCoCo). `ndopify-server` needs local MySQL.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. Tests mirror the main package.
- UI text, e-mails and Telegram messages are French; code, comments, KDoc and docs are English. Dates and amounts go through `TenantFormatter`.
- The public page must work without JavaScript; scripts only enhance.
- **Prototype gate:** Task 1 is the prototype. Stop after it, show it to the user, and wait for their OK before any server or served-template edit. If their feedback changes the design, update this plan (and push it to `main`) before Task 2.
- Never `Closes`/`Fixes` in the PR: `Refs #195` only.
- The listing interest form still posts `POST /listings/{id}/interest` → `NdMandateClient.create`; no controller, client or DTO change.

## Review Focus

1. **Two identical submits (or a retry)** must leave one `T_MATCH` row, one agent message and one customer e-mail: the unique (mandate, listing) constraint plus `Interest.changed` drive it — Task 3's concurrent-insert test and Task 4's "already accepted" test.
2. **A customer who clicks the listing page again on an already-ACCEPTED pair** gets the same confirmation but no second e-mail nor notification — Task 3 and Task 4.
3. **A reopened REJECTED or DISMISSED row** must no longer carry the old rejection reason, and Partner Central must show it *Intéressé* — Task 3 (status + fields) and Task 6 (docs; the label already exists).
4. **The customer's own property** must never be accepted (an owner called about its own listing) — Task 2's `scoreInterest` test and Task 3.
5. **A search not yet APPROVED** records ACCEPTED now, but its match page must not offer the visit form before approval (existing `MatchPages` check) — Task 3 (accepts whatever the mandate status) and Task 8's web verification.
6. **Co-brokerage**: an ACCEPTED co-brokerage row is already in the listing agent's notice (the job's status set includes ACCEPTED) and the interest message goes to the *search* agent only, with the customer's WhatsApp — no code change; `CoBrokerageNotificationServiceTest` and Task 5's tests keep it.

---

### Task 1: Prototype — the listing page's contact-an-agent CTA

**Files:**
- Modify: `modules/ndopify-web/src/main/html/mvp/annonce.html`
- Modify: `modules/ndopify-web/src/main/html/mvp/README.md`

**Interfaces:**
- Produces: the approved copy for Task 7: CTA and popup title `Être contacté par un agent`; price-card hint and confirmation `Un agent vous contacte sous 24 h pour organiser la visite.`; confirmation sentence `Un agent vous contacte sous 24 h par WhatsApp ou par e-mail pour organiser la visite.`; no steps list.

- [ ] **Step 1: Change the call to action**

In the price card and the bottom bar, replace `Je cherche un bien comme celui-ci` with `Être contacté par un agent` (keep the search icon). Replace the price-card hint with `Un agent vous contacte sous 24 h pour organiser la visite.`

- [ ] **Step 2: Change the popup and the confirmation**

In `#visit`: the title becomes `Être contacté par un agent`; the submit stays `Envoyer ma demande`; the privacy line becomes `Un agent vous contacte sous 24 h. Seul l'agent voit vos coordonnées.` In `[data-interest-done]`: heading `Merci, votre demande est enregistrée`, text `Un agent vous contacte sous 24 h par WhatsApp ou par e-mail pour organiser la visite.`, and delete the `interest-done__steps` list. Update the HTML comments that describe the popup.

- [ ] **Step 3: Update the prototype's README**

In `modules/ndopify-web/src/main/html/mvp/README.md`: the `annonce.html` row of *Changes from v1* now says the call to action is `Être contacté par un agent`; remove `un agent vous contacte sous 24 h` from *Out of the MVP, never promised here* (the page now promises it); in *Not built yet*, replace the `listing.html` line with one saying the served page still offers the old *Visiter ce logement* form until story #195 lands.

- [ ] **Step 4: Show it and stop**

Open `modules/ndopify-web/src/main/html/mvp/annonce.html` in a browser, show the CTA, the popup and the confirmation. **Do not edit server or served-template files before the user approves.** No commit yet: the prototype is committed with Task 7.

---

### Task 2: A score for an explicit interest (Matcher)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Produces: `fun scoreInterest(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>, today: LocalDate): MatchScore?` — the pair's score whatever rule it fails, `null` only when the listing is the search customer's own property.

- [ ] **Step 1: Write the failing tests**

Add to `MatcherTest` (it already has `search`, `listingA`, `distances`, `TODAY`):

```kotlin
@Test
fun `scoreInterest scores an over-budget pair`() {
    val score = matcher.scoreInterest(search, listingA.copy(price = 290_000), distances, TODAY)

    assertNotNull(score)
    assertEquals(0.0, score.price, DELTA) // 250 000 + 10 % is the limit; 290 000 is over
}

@Test
fun `scoreInterest refuses the customer's own property`() {
    val listing = listingA.copy(mandate = listingA.mandate.copy(customerId = search.customerId))

    assertNull(matcher.scoreInterest(search, listing, distances, TODAY))
}

@Test
fun `scoreInterest keeps a vetoed property type, scored zero`() {
    val score = matcher.scoreInterest(search, listingA.copy(propertyType = PropertyType.LAND), distances, TODAY)

    assertNotNull(score)
    assertEquals(0.0, score.type, DELTA)
    assertEquals(0.0, score.total, DELTA)
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: FAIL — `unresolved reference: scoreInterest`.

- [ ] **Step 3: Implement it**

Extract the `MatchScore` construction of `evaluate` into `private fun partials(search, listing, distances, today): MatchScore` (it computes `typeScore` itself), and make `evaluate` return `MatchResult.Scored(partials(...))` after its rules. Add:

```kotlin
/**
 * The score of a pair the customer explicitly asked for: no rule refuses it but the customer's own property, which
 * isn't a lead. The partials still show the gap (an over-budget price scores 0, a vetoed type multiplies by 0).
 */
fun scoreInterest(
    search: MandateEntity,
    listing: ListingEntity,
    distances: Map<Long, Double>,
    today: LocalDate,
): MatchScore? = if (listing.mandate.customerId == search.customerId) {
    null
} else {
    partials(search, listing, distances, today)
}
```

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: PASS (every existing case unchanged).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): score a pair the customer explicitly asked for" -m "Refs #195"
```

---

### Task 3: Record the interest as an ACCEPTED match (MatchService)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Interest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt:396-428`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

**Interfaces:**
- Consumes: `Matcher.scoreInterest` (Task 2).
- Produces: `data class Interest(val match: MatchEntity, val changed: Boolean)` (`changed`: the row moved to ACCEPTED now); `fun acceptInterest(mandateId: Long, listingId: Long): Interest?`; `MatchService.ensureInterest` is removed (only `MatchInterestService` used it).

- [ ] **Step 1: Write the failing tests**

In `MatchServiceTest`, change `givenInterest` to stub `matcher.scoreInterest(...)` instead of `matcher.score(...)`, add `doAnswer { it.arguments[0] }.whenever(matchDao).save(any())`, and replace the `ensureInterest` tests with:

```kotlin
@Test
fun `acceptInterest stores an ACCEPTED match under the min score`() {
    givenInterest(score(0.2), null)

    val interest = service.acceptInterest(MANDATE_ID, LISTING_ID)!!

    assertTrue(interest.changed)
    val captor = argumentCaptor<MatchEntity>()
    verify(matchDao).save(captor.capture())
    assertEquals(MatchStatus.ACCEPTED, captor.firstValue.status)
    assertNotNull(captor.firstValue.answeredAt)
    assertEquals(listing1.price, captor.firstValue.listingPrice)
}

@Test
fun `acceptInterest reopens an ACTIVE match`() {
    givenInterest(score(0.9), MatchEntity(id = 9L, status = MatchStatus.ACTIVE))

    val interest = service.acceptInterest(MANDATE_ID, LISTING_ID)!!

    assertTrue(interest.changed)
    // captured row: ACCEPTED, answeredAt set
}

@Test
fun `acceptInterest reopens a REJECTED match and clears its reason`() {
    givenInterest(score(0.9), MatchEntity(id = 9L, status = MatchStatus.REJECTED,
        rejectionReasonCode = MatchRejectionReason.TOO_EXPENSIVE, rejectionReason = "Trop cher"))

    val interest = service.acceptInterest(MANDATE_ID, LISTING_ID)!!

    assertTrue(interest.changed)
    // captured row: ACCEPTED, rejectionReasonCode / rejectionReason null
}

@Test
fun `acceptInterest reopens DISMISSED, SHORTLISTED, EXPIRED and PROPOSED`() {
    // one case each: the captured row is ACCEPTED and interest.changed is true
}

@Test
fun `an accepted match is left untouched`() {
    givenInterest(score(0.9), MatchEntity(id = 9L, status = MatchStatus.ACCEPTED))

    val interest = service.acceptInterest(MANDATE_ID, LISTING_ID)!!

    assertFalse(interest.changed)
    verify(matchDao, never()).save(any<MatchEntity>())
}

@Test
fun `acceptInterest refuses the customer's own property`() {
    givenInterest(null, null)

    assertNull(service.acceptInterest(MANDATE_ID, LISTING_ID))
    verify(matchDao, never()).save(any<MatchEntity>())
}

@Test
fun `acceptInterest accepts on a search that isn't approved`() {
    // search.status = ASSIGNED, an agent; the row is ACCEPTED
}

@Test
fun `a concurrent insert is read back, not accepted twice`() {
    givenInterest(score(0.9), null)
    doThrow(DataIntegrityViolationException("dup")).whenever(matchDao).save(any())
    doReturn(null, MatchEntity(id = 9L, status = MatchStatus.ACCEPTED))
        .whenever(matchDao).findByMandateAndListing(search, listing1)

    val interest = service.acceptInterest(MANDATE_ID, LISTING_ID)!!

    assertFalse(interest.changed)
}

@Test
fun `acceptInterest needs a published listing`() { /* SOLD → null, never saved */ }

@Test
fun `acceptInterest needs an agent`() { /* agentId null → null, never saved */ }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: FAIL — `unresolved reference: acceptInterest`.

- [ ] **Step 3: Implement it**

In `Interest.kt`, rename `created` to `changed` and document it as "the row moved to ACCEPTED now (created or reopened)". In `MatchService`, replace `ensureInterest` with:

```kotlin
/**
 * The match of (mandate, listing) the customer asked for on the listing's public page: recorded ACCEPTED,
 * `answeredAt` = now, whatever the row's prior status (ACTIVE, PROPOSED, SHORTLISTED, EXPIRED, DISMISSED, REJECTED:
 * the old rejection reason is cleared). An already-ACCEPTED row is left as is (`changed = false`). The explicit
 * interest bypasses `min-score` and every Matcher rule but the customer's own property. [changed] tells the caller to
 * notify the agent and e-mail the customer once. Null when the mandate isn't a SEARCH with an agent, the listing isn't
 * PUBLISHED, or it's the customer's own property.
 */
@Transactional
fun acceptInterest(mandateId: Long, listingId: Long): Interest? {
    val mandate = mandateDao.findWithNeighborhoodsById(mandateId) ?: return null
    if (mandate.mandateType != MandateType.SEARCH || mandate.agentId == null) {
        return null
    }
    val listing = listingDao.findById(listingId).orElse(null) ?: return null
    if (listing.status != ListingStatus.PUBLISHED) {
        return null
    }
    val score = matcher.scoreInterest(mandate, listing, distances(mandate), today(mandate.tenantId!!)) ?: return null
    val existing = dao.findByMandateAndListing(mandate, listing)
    if (existing?.status == MatchStatus.ACCEPTED) {
        return Interest(existing, changed = false)
    }

    val now = Date(clock.millis())
    val match = existing ?: MatchEntity(
        mandate = mandate,
        listing = listing,
        agentId = mandate.agentId,
        coBrokerage = listing.agent.id != mandate.agentId,
        status = MatchStatus.ACCEPTED,
        createdAt = now,
    )
    return try {
        Interest(dao.save(accepted(match, listing, score, now)), changed = true)
    } catch (_: DataIntegrityViolationException) {
        // A concurrent request inserted the pair: read it, and reopen it only if it isn't accepted yet
        val row = dao.findByMandateAndListing(mandate, listing) ?: return null
        if (row.status == MatchStatus.ACCEPTED) {
            Interest(row, changed = false)
        } else {
            Interest(dao.save(accepted(row, listing, score, now)), changed = true)
        }
    }
}

private fun accepted(match: MatchEntity, listing: ListingEntity, score: MatchScore, now: Date): MatchEntity =
    match.copy(
        coBrokerage = listing.agent.id != match.mandate.agentId,
        status = MatchStatus.ACCEPTED,
        answeredAt = now,
        rejectionReasonCode = null,
        rejectionReason = null,
        score = score.total,
        typeScore = score.type,
        neighborhoodScore = score.neighborhood,
        priceScore = score.price,
        roomsScore = score.rooms,
        amenitiesScore = score.amenities,
        availabilityScore = score.availability,
        furnitureScore = score.furniture,
        distanceKm = score.distanceKm,
        missingAmenities = score.missingAmenities,
        listingPrice = listing.price,
        modifiedAt = now,
    )
```

Keep `store`, `discard`, `OPEN_STATUSES` and the matcher path untouched.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest,MatcherTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): record a customer's interest as an accepted match" -m "Refs #195"
```

---

### Task 4: Notify the agent and send the customer the link (MatchInterestService)

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchInterestAcceptedEvent.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchInterestServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`

**Interfaces:**
- Consumes: `MatchService.acceptInterest` (Task 3), `MatchInterestNotifier` (unchanged in this task).
- Produces: `data class MatchInterestAcceptedEvent(val matchId: Long, val tenantId: Long)`; `MatchProposalService.sendAcceptedEmail(matchId: Long)`; `registerInterest` and `proposeOrigin` publish the event only when `Interest.changed`.

- [ ] **Step 1: Write the failing service tests**

Rewrite `MatchInterestServiceTest` (add `eventPublisher = mock<ApplicationEventPublisher>()` and drop `proposalService`):

```kotlin
@Test
fun `a changed match is notified and its customer e-mailed`() {
    doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)

    assertEquals(9L, service.registerInterest(1L, 2L))

    verify(notifier).send(any(), any(), any(), any())
    verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
}

@Test
fun `an already accepted match is neither notified nor e-mailed`() {
    doReturn(Interest(match(createdStatus = MatchStatus.ACCEPTED), false)).whenever(matchService).acceptInterest(1L, 2L)

    service.registerInterest(1L, 2L)

    verify(notifier, never()).send(any(), any(), any(), any())
    verify(eventPublisher, never()).publishEvent(any<MatchInterestAcceptedEvent>())
}

@Test
fun `a notifier failure does not fail the interest`() {
    doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)
    doThrow(RuntimeException("smtp down")).whenever(notifier).send(any(), any(), any(), any())

    assertNotNull(service.registerInterest(1L, 2L))
    verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
}

@Test
fun `proposeOrigin accepts the origin and e-mails the customer, never the agent`() {
    doReturn(approvedMandate(originListingId = 2L)).whenever(mandateService).findByIdOrNull(1L)
    doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)

    assertEquals(9L, service.proposeOrigin(1L))

    verify(notifier, never()).send(any(), any(), any(), any())
    verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
}

@Test
fun `no origin, nothing accepted`() { /* originListingId null → null, never acceptInterest */ }

@Test
fun `a sold listing is not accepted`() {
    /* acceptInterest → null: null, no event */
}
```

In `MatchProposalServiceTest`, add:

```kotlin
@Test
fun `sendAcceptedEmail e-mails the customer an accepted match`() { /* status ACCEPTED → mailet.send(listOf(match), customer, agent) */ }

@Test
fun `sendAcceptedEmail ignores a match that isn't accepted`() { /* PROPOSED → never */ }

@Test
fun `sendAcceptedEmail ignores an unknown match`() { /* empty → never */ }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest,MatchProposalServiceTest`
Expected: FAIL — unresolved `acceptInterest`, `MatchInterestAcceptedEvent`, `sendAcceptedEmail`.

- [ ] **Step 3: Implement it**

`MatchInterestAcceptedEvent.kt`:

```kotlin
package com.wutsi.ndopify.match.server.event

/** A customer's interest recorded as ACCEPTED (new or reopened): its booking-link e-mail follows after commit. */
data class MatchInterestAcceptedEvent(val matchId: Long, val tenantId: Long)
```

`MatchInterestService`: drop `MatchProposalService`, add `private val eventPublisher: ApplicationEventPublisher`. `registerInterest` calls `acceptInterest`, and when `changed` calls `notify(match)` then `eventPublisher.publishEvent(MatchInterestAcceptedEvent(match.id!!, match.tenantId!!))`; log `match_changed`. `proposeOrigin` does the same without `notify`. Replace both KDocs' "proposes" language with "records ACCEPTED" and note that an unapproved search is accepted at once.

`MatchProposalService.sendAcceptedEmail`:

```kotlin
/** E-mails the customer an ACCEPTED match's link, through the accepted proposal e-mail; called by MatchEventListener. */
@Transactional
fun sendAcceptedEmail(matchId: Long) {
    val match = dao.findById(matchId).orElse(null) ?: return
    if (match.status != MatchStatus.ACCEPTED) {
        return
    }
    mailet.send(
        listOf(match),
        customerService.findById(match.mandate.customerId),
        agentService.findById(match.agentId),
    )
}
```

`MatchEventListener`: add `onMatchInterestAccepted`, an `@Async @TransactionalEventListener(AFTER_COMMIT)` copying `onMatchResent`'s shape (set `TenantContext` + `DefaultKVLogger`, log the event and `match_id`, call `proposalService.sendAcceptedEmail(event.matchId)`, `logger.log()`, remove both).

- [ ] **Step 4: Update the wiring endpoint test**

In `CreateMandateEndpointTest`, `an existing search of the same city and type gets the listing as a match`: after `awaitAsyncTasks()`, assert the match's `status = 'ACCEPTED'` (new helper `matchStatus(mandateId, listingId)`), and **two** e-mails: one to the search's agent containing `Contactez-le pour organiser la visite` (Task 5's copy; until then assert the recipient only), and one to `existing.search@gmail.com` (the accepted proposal e-mail). Replace `smtp.receivedMessages.single()` with a filter on recipients.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestServiceTest,MatchProposalServiceTest,CreateMandateEndpointTest`
Expected: PASS (MySQL required for the endpoint test).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): tell the agent and e-mail the customer on an accepted interest" -m "Refs #195"
```

---

### Task 5: The agent's message — contact the customer

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/match/interest.mjml`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestTemplateTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt` (create)

**Interfaces:**
- Produces: the message `« <Client> s'intéresse à un logement. Contactez-le pour organiser la visite. »`; the mailet's context gains `whatsapp_url` (`PhoneNumberUtils.toWhatsappUrl(customer.whatsappNumber)`, null when blank); Telegram buttons: `Contacter sur WhatsApp` (same URL, when present) then `Voir le mandat`.

- [ ] **Step 1: Write the failing tests**

`MatchInterestMailetTest`: assert the resolved context's `whatsapp_url` is `https://wa.me/237600000001` and that a customer without a number gives `null`. Keep the existing subject/recipient assertions.

`MatchInterestTemplateTest`: the rendered HTML contains `Contactez-le pour organiser la visite`, contains `<a href="https://wa.me/237600000001"` and `Contacter sur WhatsApp` when `whatsapp_url` is in the context, and neither when it's absent; `Voir le mandat` still appears only with `mandate_url`.

`MatchInterestBotletTest` (mirror `MatchAnsweredBotletTest`'s setup):

```kotlin
@Test
fun `tells the agent to contact the customer, with WhatsApp and the mandate`() {
    botlet.send(agent, customer, mandate, listing, 99L)

    val captor = argumentCaptor<String>()
    verify(client).sendMessage(eq(99L), captor.capture(), eq(listOf(
        TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
        TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1"),
    )))
    assertTrue(captor.firstValue.contains("Contactez-le pour organiser la visite."))
    assertTrue(captor.firstValue.contains("Ray Sponsible"))
}

@Test
fun `no WhatsApp number, no WhatsApp button`() { /* customer.whatsappNumber = null → only Voir le mandat */ }

@Test
fun `no partner central, only the WhatsApp button`() { /* tenant.partnerCentralUrl = null */ }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestMailetTest,MatchInterestTemplateTest,MatchInterestBotletTest`
Expected: FAIL.

- [ ] **Step 3: Implement it**

`MatchInterestMailet`: add to the context `"whatsapp_url" to customer.whatsappNumber?.takeIf { it.isNotBlank() }?.let { PhoneNumberUtils.toWhatsappUrl(it) }` (import `com.wutsi.ndopify.platform.util.PhoneNumberUtils`; keep the existing `TenantFormatter`, which formats the price).

`interest.mjml`: replace the paragraph "Ce logement a été ajouté à son mandat de recherche…" with `Contactez-le pour organiser la visite.` and add, before the `mandate_url` button:

```html
{{#whatsapp_url}}
<mj-button href="{{whatsapp_url}}" css-class="btn-primary" align="center">
    Contacter sur WhatsApp
</mj-button>
{{/whatsapp_url}}
```

`MatchInterestBotlet`: the text line becomes `« ${customer.party.fullName()} s'intéresse à un logement. Contactez-le pour organiser la visite. »`; the buttons become `listOfNotNull(whatsapp, mandate)` with `TelegramButton("Contacter sur WhatsApp", url = PhoneNumberUtils.toWhatsappUrl(...))` first, then `Voir le mandat`. Update both KDocs.

- [ ] **Step 4: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestMailetTest,MatchInterestTemplateTest,MatchInterestBotletTest,MatchInterestServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(match): ask the agent to contact the interested customer" -m "Refs #195"
```

---

### Task 6: The served listing page

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/css/listing.css:355-367`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt:20-34,71-76`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`

**Interfaces:**
- Consumes: the approved prototype (Task 1).
- Produces: the same view model (no controller signature change).

- [ ] **Step 1: Write the failing template tests**

In `ListingTemplateTest`:

```kotlin
@Test
fun `a published listing shows the contact call to action`() {
    // .price-card__cta text == "Être contacté par un agent"; the .cta-bar__actions link has the same text
}

@Test
fun `the confirmation has no steps`() {
    // done = true → contains "pour organiser la visite" and "Merci, votre demande est enregistrée"
    // select(".interest-done__steps").isEmpty()
}
```

Update `a published listing shows the visit call to action` and `the confirmation replaces the form` to the new copy.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest`
Expected: FAIL.

- [ ] **Step 3: Change the template**

In `listing.html`: price-card CTA and bottom-bar link text `Être contacté par un agent`; price-card hint `Un agent vous contacte sous 24 h pour organiser la visite.`; `#visit` title `Être contacté par un agent`; privacy line `Un agent vous contacte sous 24 h. Seul l'agent voit vos coordonnées.`; confirmation `<p>` `Un agent vous contacte sous 24 h par WhatsApp ou par e-mail pour organiser la visite.` with the `<ol class="interest-done__steps">` removed. Update the popup's HTML comment (no longer "un agent vous contacte sous 24 h" as the promise — now the CTA's meaning). Update `ListingController`'s KDoc: the form is "Être contacté par un agent", and the server records an accepted match when the e-mail has a search of that city and type (no controller code change).

- [ ] **Step 4: Remove the dead CSS**

Delete `.interest-done__steps` (and its `li` rules) from `listing.css`; keep `.interest-done .btn { margin-top: var(--space-xl); }`.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingTemplateTest,ListingControllerTest,MatchTemplateTest`
Expected: PASS (the match page is unchanged).

- [ ] **Step 6: Commit (with the approved prototype)**

```bash
git add modules/ndopify-web
git commit -m "feat(web): the listing page's contact-an-agent call to action" -m "Refs #195"
```

---

### Task 7: Document the behaviour

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **match** section, around lines 385-392)
- Modify: `modules/ndopify-web/CLAUDE.md` (the `ui/listing/` bullet)

- [ ] **Step 1: Update the server doc**

In **match**, replace the `ensureInterest` paragraph: `ExistingSearchInterestEvent` → `MatchInterestService.registerInterest` records the pair `ACCEPTED` (`MatchService.acceptInterest`; `answeredAt`; any prior status reopened, the rejection reason cleared; an ACCEPTED row untouched; every Matcher rule but the customer's own property bypassed; `min-score` too), tells the search's agent once when the row moved, and e-mails the customer the accepted proposal link (`MatchInterestAcceptedEvent`). A search without an agent keeps the listing as its origin and `proposeOrigin` accepts it at approval (no agent notification). Nothing is proposed for an existing search any more.

- [ ] **Step 2: Update the web doc**

In `ui/listing/`: the CTA and popup say *Être contacté par un agent*; the confirmation is `Un agent vous contacte sous 24 h …` with no steps; a submission from an e-mail that already has a search of the listing's city and type records an ACCEPTED match server-side (no client change).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-web/CLAUDE.md
git commit -m "docs(match): document the accepted interest from a listing" -m "Refs #195"
```

---

### Task 8: Verify the whole branch

- [ ] **Step 1: Format and verify each module**

Run: `cd modules/ndopify-server && mvn verify` (local MySQL required), then `cd ../ndopify-web && mvn verify`
Expected: BUILD SUCCESS in each; no ktlint violation; JaCoCo at or above the module thresholds.

- [ ] **Step 2: Check the tree**

`git status` is clean; `git log --oneline origin/main..HEAD` lists only this story's commits, none touching `modules/ndopify-dto/` or `modules/ndopify-web-core/`; the root `pom.xml` still pins the released versions.

- [ ] **Step 3: Report**

Tell the user the story is ready for `push 195`; do not push.
