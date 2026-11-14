# Co-brokerage visits — Design

**Date:** 2026-11-14
**Status:** proposed, awaiting approval
**Size:** project (server read + notifications + mandate-ending cancellation + Partner Central + DTO release)
**Branch:** `feat/npc-co-brokerage-visits`
**Base:** `origin/main` at `0d9273ae` (revalidated after DTO 0.0.88, `Match.agentId`, the co-brokerage
listing page and the mail-voice fix)

## Intent and why

A co-brokerage match is a search agent's mandate matched to another agent's published listing
(`Match.coBrokerage`). The customer only ever deals with its own agent, **A**. When the customer asks for a visit, A
organizes it with the listing agent, **B**. B hosts the visit on site, with A and A's customer, and **never with B's
own customer**. Today the visit belongs entirely to A:

- `VisitEntity.agentId` is `match.agentId` (A), and `VisitAccessService` gives every agent action to A;
- B cannot see the visit, is not notified, and cannot prepare to open the property;
- the listing page is B's page but says nothing about the visits another agent is organizing there.

This slice makes B a **read-only backstage participant**: he is told about the visit, sees it on his listing page, and
hosts it. A keeps the customer relationship, the confirmation, the code, the check-in, the report and the feedback.

## Product rule (anti-sautage)

The platform **never discloses an agent's or customer's contact details across a co-brokerage boundary unless those
two parties have a business relationship**.

| Party | May see | Must never see |
|-------|---------|----------------|
| A (search agent) | its customer, the listing, B's name/contact | B's customer |
| B (listing agent) | its listing, the visit slot/type, A's name/contact | A's customer's name, contact, code, note, report, feedback |
| Customer of A | A only, the property details | B, B's agency, B's customer, any co-brokerage mention |
| Customer of B | B only, its property | A, A's customer |

A and B have a business relationship (the co-brokerage match); they may exchange contacts. The customer and B have no
business relationship; B meets the customer in person during the visit, which is outside the platform and acceptable.

## Goals

- Notify B when a co-brokerage visit is **confirmed**, on the **24 h reminder**, and when it is **cancelled** or
  **does not happen because of the customer** (absent, or code refused). Both `ON_SITE` and `VIDEO` visits are
  included: A is at the property in both cases, so B opens it and hosts.
- Show B the upcoming co-brokerage visits on his listing page, with the slot, the type and A's name/contact.
- Keep A's customer experience **exactly unchanged**: A is still the only agent named, the only contact and the only
  code holder.
- Keep every action (schedule, check-in, no-show, check-out, cancel) with A; add no state and no participant role.
- **Cancel the open visits when either mandate ends** (the search mandate or the listing's mandate, `CLOSED` or
  `CANCELLED`), so a confirmed visit can never outlive the agreement that made it possible. Both sides are told,
  and B too when he was already notified.
- Fix A's visit page so its listing link opens the co-brokerage listing (it currently lands on the locked page).

## Non-goals

- B accepting, refusing, rescheduling, checking in, checking out, cancelling or writing the visit report.
- A co-brokerage visit state machine, a second agent on `VisitEntity`, or a `listing_agent_id` column.
- Showing B's own visits on the listing page (they are already on the mandate page's *Propositions et visites*).
- Notifying B on `REQUESTED`, `EXPIRED`, `COMPLETED`, or on the agent's own no-show.
- Cancelling a `CHECKED_IN` visit when a mandate ends: the visit is already on; A closes it normally.
- Backfilling cancellation e-mails or visits for mandates ended before the deploy.
- Editing the mandate's own cancellation reason into the visit e-mails (it may belong to the other side).
- Commission, closing, customer proposal wording, real-time delivery, push/SMS/in-app notifications.
- Any change to the public portal (`ndopify-web`).

## Anti-goals

- A customer learning B's identity, agency or contact, in any page or e-mail.
- B learning the customer's name, contact, code, note, private report or feedback.
- Merging co-brokerage content into the existing customer/agent visit e-mails, or changing their voice.
- A new visit status, a new event, or a new scheduler: the mandate ending reuses `MandateClosedEvent` and
  `VisitCancelledEvent`.
- A mixed list of B's own visits and co-brokerage visits.
- A visit left `CONFIRMED` on a mandate that ended.

## Constraints

- Multi-tenant: every read and mail runs with `TenantContext`; `T_VISIT` is `@TenantId`-scoped.
- The DTO change needs a `ndopify-dto` release (`0.0.88` is current); the other modules need the released version.
- Coverage gates of `ndopify-server`: line 0.95, class 0.90; endpoint/job tests need local MySQL.
- Partner Central reads are secondary: a failed visits read leaves the block out, never fails the page.
- The listing page is rendered for a co-broker (A's lens) too; the block is owner-only.
- French UI and e-mail wording, matching the existing visit pages.
- The upstream code already names A on B's co-brokerage proposal cards (`Match.agentId`,
  `AgentProfileRules.profileUrl`); reuse that convention for the visits block.

## Decisions made with the product owner

1. **B is informed, not asked.** A calls B, they agree, A confirms in the app. There is no B acceptance step, no
   delay and no new state.
2. **B sees the visit in Partner Central**, in a new block on the listing page, not only by e-mail.
3. **The block is co-brokerage only.** B's own visits already live on the mandate page; mixing both would duplicate
   and mix two access lenses.
4. **B gets no customer identity at all**: not the name, not the contact, not the code, not the note, not the report.
   The block says "avec [A]" and nothing about the customer.
5. **A and B may exchange contacts** (business relationship). B's e-mail and block show A's name, profile link and
   WhatsApp.
6. **No new state, no new participant field.** A remains the only actor; B is read-only.
7. **Video visits include B.** A is at the property for a `VIDEO` call too, so B opens it and hosts; B's copy has a
   video variant (the customer is remote), and the block labels it "Visite vidéo".
8. **A mandate ending cancels its open visits.** `MandateClosedEvent` (published by `MandateService.close` and
   `cancel`) cancels the `REQUESTED`/`CONFIRMED` visits of that mandate *and* of its listing; a `CHECKED_IN` visit is
   left alone. Both sides are told; B too when the visit was confirmed. This keeps a confirmed visit from outliving
   the agreement.

### Calls made while designing (correctable)

- Notify B **only once confirmed**: A has already agreed with him by phone, so a `REQUESTED` notification would be
  noise and would show B a slot nobody agreed to.
- The reminder rides the existing 24 h reminder; a visit confirmed less than 24 h ahead is its own reminder, so B
  learns about it in the confirmation.
- No e-mail on `EXPIRED` (B never knew), `COMPLETED` (B was there) or the agent's no-show (B was there).
- The block shows `CONFIRMED` and `CHECKED_IN` visits, soonest first; `CHECKED_IN` carries an "En cours" tag.
- A's name links to the existing agent profile page, with a back link to the listing page; A's WhatsApp is a
  `wa.me` link. The e-mail carries A's profile link too
  (`{partnerCentralUrl}/agent?id={A.id}`), built server-side. An unreadable agent leaves the name out and keeps the
  row (generic "un autre agent").
- Admin-initiated confirmation or cancellation keeps A's voice ("J'ai organisé…", "J'ai annulé…"). The visit belongs
  to A, and the existing customer e-mails already speak in A's voice when an admin acts; this slice does not add an
  actor to the events.
- The listing's agent is resolved from `listing.agent` at read/send time; no snapshot column. If a mandate is
  reassigned, the current owner of the listing is the host.
- A's visit page listing link gains `?match={matchId}`: an owner ignores it, a co-broker needs it to open the
  read-only listing page.
- The system cancellation uses `VisitParty.SYSTEM` (already in the DTO) and a neutral reason in the e-mails; the
  mandate's own cancellation reason is never copied into a visit e-mail, since it may belong to the other side.
- Cancelling on mandate `CLOSED` as well as `CANCELLED`: both end the agreement, and a pending visit is moot. The
  block's listing-status gate still hides visits on a listing that ended before the deploy; new endings cancel them.

## Approaches considered

### 1. B's role: informed, asked, or full co-agent

- **Informed (chosen).** A confirms; B sees the confirmed visit and hosts. Smallest surface, customer promise
  unchanged, no new state.
- **Asked.** B accepts before A can confirm. Records the agreement, but adds a state, a deadline for B's silence,
  and lets an agent the customer cannot see delay or expire the request.
- **Full co-agent.** B accepts, checks in, writes the report. Most faithful to "B hosts", but duplicates
  responsibilities and gives B customer-facing actions.

### 2. B's visibility: e-mails only, or a listing-page block

- **E-mails only.** No UI. B has to search his inbox to know what he is hosting.
- **Listing-page block (chosen).** B opens his property and sees the upcoming visits; one read over existing data,
  no new route or queue.

### 3. The read path: widen the existing search, or a dedicated endpoint

- **Widen `GET /v1/visits`.** An agent's search is forced to its own `agent-id`; allowing `listing-id` across agents
  would widen the same restriction that protects the visit detail page.
- **Dedicated endpoint (chosen).** `GET /v1/visits/co-brokerage?listing-id=` is listing-agent-only and returns a
  minimal projection. The existing search and `GET /v1/visits/{id}` stay untouched.

### 4. The response type: reuse `VisitSummary`, or a new summary

- **Reuse `VisitSummary`.** It carries `customerId`, `note`, `cancellationReason` and other fields B must not get;
  blanking them is a contract lie.
- **New `CoBrokerageVisitSummary` (chosen).** Only what B may see, enforced by the type.

### 5. The notification: extend the existing mailets, or a new one

- **Extend `VisitConfirmedMailet` et al.** Would mix B into customer/agent templates and risk changing their voice.
- **New `CoBrokerageVisitMailet` (chosen).** Its own templates, called only for co-brokerage visits; existing
  customer and agent e-mails untouched.

## Design

### Server — the read

**DTO (`ndopify-dto` 0.0.89):**

```kotlin
data class CoBrokerageVisitSummary(
    val id: Long = -1,
    val listingId: Long = -1,
    val agentId: Long = -1,          // A, the search agent; never its customer
    val status: VisitStatus = VisitStatus.UNKNOWN,
    val type: VisitType = VisitType.UNKNOWN,
    val scheduledAt: Date? = null,
    val checkedInAt: Date? = null,
)

data class SearchCoBrokerageVisitResponse(
    val visits: List<CoBrokerageVisitSummary> = emptyList(),
)
```

No `customerId`, `mandateId`, `matchId`, `note`, `code`, address, report, feedback or cancellation reason.

**Endpoint:** `GET /v1/visits/co-brokerage?listing-id={id}`

- `listingId` required; a missing or non-positive id is `400 HTTP_MISSING_PARAMETER` / invalid parameter.
- `listingService.findById(listingId)` first: `404 LISTING_NOT_FOUND` when gone.
- `VisitAccessService.ensureIsListingAgent(listing)`: the caller must be an agent and the listing's agent
  (`listing.agent.id`), else `403 VISIT_ACCESS_DENIED`. No admin bypass; a customer is denied.
- `VisitService.findCoBrokerageOnListing(listing)`: visits with `listingId = listing.id`, `agentId != listing.agent.id`
  and status in `CONFIRMED`, `CHECKED_IN`, sorted by `scheduledAt` ascending, then `id`.
- Returns the minimal summary; an empty list is a normal answer.
- Tenant-scoped by Hibernate, like every visit read.

**Repository:**

```kotlin
fun findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
    listingId: Long,
    agentId: Long,
    statuses: Collection<VisitStatus>,
): List<VisitEntity>
```

**Why a dedicated endpoint:** the existing search is "my visits"; this is "visits on my property", a different
authorization axis. Keeping it separate avoids widening the agent search, which also protects the detail page.

### Server — the notifications

New `CoBrokerageVisitMailet` (`visit/server/mail/`), recipient = `listing.agent.party`, sender =
`AgentMailContext.from(A)` so the From display name is A's (the tenant address stays, SPF/DKIM unchanged), plus
`AgentMailContext.of(A, formatter)` for the footer. Voice: A speaking to B ("J'ai organisé une visite de votre
bien…"), matching the upstream "customer e-mails in the agent's voice" rule.

Methods:

- `sendConfirmed(visit, listing, agent)` → `/mail/visit/co_brokerage_confirmed.mjml`
- `sendReminder(visit, listing, agent)` → `/mail/visit/co_brokerage_reminder.mjml`
- `sendCancelled(visit, listing, agent)` → `/mail/visit/co_brokerage_cancelled.mjml`
- `sendNoShow(visit, listing, agent)` → `/mail/visit/co_brokerage_no_show.mjml`

Common context: tenant logo/name, listing title and cover image, visit date/time in the tenant's time zone, type
(ON_SITE / VIDEO), A's full name, A's profile link (`{partnerCentralUrl}/agent?id={A.id}`), A's WhatsApp (formatted
and `wa.me`), and a link to the listing page (`{partnerCentralUrl}/listings/{listing.id}#visits-title`). **Never**
the customer's name, e-mail, WhatsApp, code, note, address, report or feedback.

Per-kind wording:

- **confirmed** — "J'ai organisé une visite de votre bien le … à … . Je vous y retrouve avec mon client." (the
  customer is mentioned only as "mon client", never named). A `VIDEO` visit says "Je serai sur place pour vous
  ouvrir ; le client suivra en visio." — B still opens the property.
- **reminder** — the same facts, "Rappel : la visite de votre bien a lieu demain …", with the video variant.
- **cancelled** — date/time + "J'ai annulé la visite." when A cancelled, "Le client a annulé la visite." when the
  customer did, "La visite est annulée." when the system ended the mandate. No name, no mandate reason.
- **no-show** — two labels, as the existing `VisitNoShowMailet` does: "Le client ne s'est pas présenté à la visite
  du … à … ." (`CUSTOMER_ABSENT`) and "Le client a refusé de donner le code de visite." (`CODE_REFUSED`). No name.

Integration in `VisitService` (the existing event listener is unchanged):

- `sendConfirmedEmails`: after the existing mailet, if `visit.agentId != listing.agent.id`, send the B confirmation.
- `sendReminderEmails`: same condition, send the B reminder.
- `sendCancelledEmail`: same condition **and** `visit.scheduledAt != null` (B only knew a confirmed visit), send the
  B cancellation.
- `sendNoShowEmails`: same condition **and** `visit.noShowParty == VisitParty.CUSTOMER` (both `CUSTOMER_ABSENT` and
  `CODE_REFUSED`), send the B no-show with the matching label.
- `sendExpiredEmails`, `sendCompletedEmail`: no B mail.
- No new event, no new timestamp, no one-shot column: each existing event fires once.

### Server — the mandate ending

`MandateService.close` and `cancel` already publish `MandateClosedEvent` inside their transaction, and the listing
domain already has a synchronous listener for it. Add a second synchronous listener,
`VisitMandateEventListener` (`visit/server/event/`), calling `VisitService.cancelForEndedMandate(mandateId)`.

`cancelForEndedMandate`:

- Finds the `REQUESTED`/`CONFIRMED` visits where `mandateId = mandateId` (the search side) **or** whose
  `listingId` is the listing of that mandate (the listing side), via
  `VisitRepository.findOpenByMandateOrListingMandate(mandateId, OPEN_TO_CANCEL)`.
- Saves each as `CANCELLED`, `cancelledBy = VisitParty.SYSTEM`, `cancelledAt = now`,
  `cancellationReason = null` — **never** copies the mandate's own reason, which may belong to the other side.
- Publishes `VisitCancelledEvent` per visit; the existing after-commit listener sends the mails.
- Skips `CHECKED_IN` visits: the visit is already on; A closes it normally.
- Runs synchronously, inside the mandate's transaction, like `ListingMandateEventListener`: a failure (ex: a
  concurrent `@Version` change on a visit) rolls the mandate ending back, so a mandate never commits with an open
  visit it just ended. The caller retries.
- The `ListingJobs.close` catch-up path is legacy (mandates ended before this listener existed); it is not
  backfilled. Every new `close`/`cancel` goes through the event.

`VisitCancelledMailet` gains the `SYSTEM` case: e-mails **both** the customer and A with the neutral
"La visite est annulée." (never the mandate's reason). The existing `CUSTOMER`/`AGENT` behavior is unchanged. For a
confirmed co-brokerage visit, `CoBrokerageVisitMailet.sendCancelled` tells B too; a `REQUESTED` visit never reached B,
so he gets nothing.

### Partner Central — the listing-page block

**Read:** `NdVisitClient.coBrokerageOnListing(listingId): List<CoBrokerageVisitSummary>` calling the new endpoint.

**Controller:** in `ListingController.show`, when the lens is `Owner` **and** the listing is published or closed
(`PUBLISHED`, `RENTED`, `SOLD`), read the co-brokerage visits as a secondary lookup (`lookup`, failure → `null`).
Never for the `CoBroker` lens. Then read A's agent (`NdAgentClient.get(agentId)`) once per distinct `agentId`, in
parallel with the existing `parallel` executor pattern; a failed agent read leaves the name/contact out but keeps
the row.

**View model:**

```kotlin
data class ListingVisitView(
    val at: String,             // "samedi 22 novembre 2026 à 10:30", tenant time zone
    val typeLabel: String,      // "Sur place" / "Visite vidéo"
    val inProgress: Boolean,    // CHECKED_IN: "En cours" tag
    val agentName: String?,     // A's full name; null when unreadable
    val agentUrl: String?,      // AgentProfileRules.profileUrl(A, "/listings/{id}#visits-title")
    val whatsappUrl: String?,   // A's wa.me link, null without a number
)
```

`ListingView` gains `visits: List<ListingVisitView> = emptyList()`, built by `ListingViews` with
`VisitSlots.dateTimeLabel` and the existing type labels. The list is empty when the read fails or there are no
visits; the template then renders nothing.

**Template:** a `home-section` on `listing.html`, owner lens only, hidden when `l.visits` is empty:

```html
<section class="home-section listing-visits" aria-labelledby="visits-title" th:if="${!#lists.isEmpty(l.visits)}"
         data-listing-visits>
    <h2 id="visits-title">Visites d'autres agents</h2>
    <p>Un autre agent organise ces visites de votre bien : vous accueillez l'agent et son client sur place.</p>
    <ul>
        <li th:each="v : ${l.visits}">
            <p><strong th:text="${v.at}">samedi 22 novembre 2026 à 10:30</strong> ·
                <span th:text="${v.typeLabel}">Sur place</span>
                <span class="status-tag status-tag--info" th:if="${v.inProgress}">En cours</span></p>
            <p>avec
                <a th:if="${v.agentUrl}" th:href="${v.agentUrl}" th:text="${v.agentName}">Serge Ngono</a>
                <span th:unless="${v.agentUrl}" th:text="${v.agentName} ?: 'un autre agent'">un autre agent</span>
                <a th:if="${v.whatsappUrl}" th:href="${v.whatsappUrl}" rel="noopener" target="_blank">WhatsApp</a>
            </p>
        </li>
    </ul>
</section>
```

Placement: after the gallery, before the published description/sheet. Owner-only; the co-broker lens never gets the
section. No customer name, contact or link to a visit page. Styling in `listing.css`, rows stacked on phones, 44 px
targets for the profile and WhatsApp links.

### Partner Central — A's visit page

`VisitViews` currently links `listingUrl = "/listings/{listingId}"`. A co-brokerage listing page requires
`?match={matchId}` (`PublishedListingAccess`); without it, A lands on the locked page. Change the link to
`/listings/{listingId}?match={visit.matchId}`. An owner lens ignores the parameter, so the same link works for
own-inventory visits.

Acceptance criterion: **the link works for an active co-brokerage visit** — the match is still in `MatchRules.ORDER`,
its search mandate is still `APPROVED` and owned by A, and the listing is still `PUBLISHED`. Once the listing closes
(`SOLD`/`RENTED`/`CANCELLED`) or the mandate closes, the co-brokerage listing page is locked by its existing rules;
that is consistent with the listing page, not a new gap. Widening listing access beyond that is out of scope.

### What B never receives

- No customer name, e-mail, WhatsApp, address or any customer identifier.
- No visit code (A asks for it at check-in), no note, no `agentComment`, no feedback/interest, no cancellation
  reason written by the customer.
- No link to `GET /v1/visits/{id}`: B has no visit page. The only link is the listing page.
- No A's customer's mandate, match or search criteria.

### Data

No migration. `T_VISIT` keeps `agent_id = A`; B is `listing.agent`, resolved at read/send time. The visit already
stores the listing id and the match id, so the story remains readable. A reassigned listing moves the hosting duty
to its new agent, which is the correct operational owner.

### Edge cases

- **Own-inventory visit** (`visit.agentId == listing.agent.id`): excluded by the query; no B mail.
- **Cancelled while `REQUESTED`**: `scheduledAt == null`; B never knew, no cancellation mail.
- **Cancelled after `CONFIRMED`**: B gets the cancellation, whether A, the customer or the system cancelled.
- **System cancellation**: `cancelledBy = SYSTEM`; the customer and A both get the neutral cancellation, B too when
  confirmed; the mandate's own reason is never rendered.
- **Mandate `CLOSED` or `CANCELLED`**: its open visits (search side and listing side) become `CANCELLED`; a
  `CHECKED_IN` visit stays and is closed by A.
- **Customer no-show**: B gets the no-show mail; **agent no-show**: no B mail (B was there).
- **Video visit**: B is notified and hosted as for an on-site visit; the copy says the customer is remote.
- **B's agent read fails**: the block row keeps the date/type and says "un autre agent"; no profile/WhatsApp link.
- **Visits read fails**: no block; the rest of the listing page renders.
- **Listing not `PUBLISHED`/`RENTED`/`SOLD`**: the controller skips the read. After the listener runs, such a
  listing has no open visits; legacy rows from before the listener are not backfilled and stay hidden.
- **Visit page (A)**: the listing link now carries `?match=`; an owner lens ignores it. A listing that has closed
  under the existing access rules stays locked, as for any listing page.
- **Admin action**: the B e-mail keeps A's voice; no actor is added to the events.
- **Existing co-brokerage visits**: they appear in the block after deploy; no backfill e-mail is sent.
- **Concurrent change**: a visit changing while its mandate ends rolls the mandate ending back (synchronous
  listener), and the caller retries.

## Testing

**Server**

- `CoBrokerageVisitEndpointTest` (MySQL): 401 without a token, 403 for another agent/customer, 404 for a missing
  listing; the listing's agent gets only `CONFIRMED`/`CHECKED_IN`, only visits of another agent, sorted by
  `scheduledAt`; the JSON has no customer, note, code, address, report or feedback; an empty listing returns `[]`.
- `VisitRepositoryTest`: the derived query excludes own visits and other statuses; the mandate-ending query finds
  both the search side and the listing side, and leaves `CHECKED_IN`/`COMPLETED` alone.
- `VisitServiceTest`: B mail on confirmed/reminder/cancelled-after-confirmed/customer-absent/code-refused; no B mail
  on own visits, a `REQUESTED` cancellation, an expiry, a completion or the agent's no-show; a `SYSTEM` cancellation
  mails the customer and A, and B once when confirmed; `cancelForEndedMandate` cancels only `REQUESTED`/`CONFIRMED`
  and publishes one event per visit.
- `VisitMandateEventListenerTest` / a mandate-endpoint integration test: cancelling a search mandate cancels its
  visits; cancelling a listing mandate cancels the visits on its listing; no mandate reason leaks into the mails.
- `CoBrokerageVisitMailetTest`: recipient B, A's name/profile/WhatsApp, listing/date/type context, on-site and video
  variants, both no-show labels, no customer fields.
- Template tests: each MJML renders, and a fixture with a customer name/e-mail/code asserts their absence (leak
  test).

**Partner Central**

- `NdVisitClientTest`: the endpoint URI and response.
- `ListingControllerTest`: owner + published loads the block; co-broker lens does not; draft does not; a visits read
  failure leaves it empty; an agent read failure keeps the row without contact; A is read once per distinct id.
- `ListingViewsTest`: formatting, type labels, "En cours", name fallback, sorting, no customer field.
- `ListingTemplateTest`: the section, title, agent link, WhatsApp, and the absence of any customer name/contact;
  hidden when empty; absent on the co-broker lens.
- `VisitViewsTest`: the listing link carries `?match=`.

**DTO**

- Release `0.0.89`; bump the root `<ndopify-dto.version>`.

## Rollout

1. `ndopify-dto` 0.0.89 (new summary types).
2. Server endpoint + notifications + `MandateClosedEvent` listener; BFF block + visit link fix.
3. No migration, no feature flag. Existing upcoming co-brokerage visits show up for B after deploy; no e-mail
   backfill. Existing open visits on already-ended mandates are not backfilled; if needed, a one-off cleanup marks
   them `CANCELLED` by `SYSTEM`.
4. Update `ndopify-server/CLAUDE.md` (visit domain, mandate ending) and `ndopify-partner-central/CLAUDE.md` (listing
   page).

## What's left to the builder

- Final CSS/placement of the block within the page, within the stated constraints.
- The exact MJML layouts/subjects and the neutral system-cancellation wording, following the existing visit
  templates and the agent-voice footer.
- Whether the co-brokerage row shows A's photo/initials alongside the name (the profile card pattern exists).
- The test fixtures and SQL rows for the endpoint.
- Whether the synchronous mandate-ending cancellation is too strict for a concurrent visit change; the fallback is
  one transaction per visit with a logged skip, but that weakens "a mandate never commits with an open visit it just
  ended". The design picks strict, like the listing listener.

## Revalidation against `origin/main` (`0d9273ae`)

- The visit domain, `VisitAccessService`, `VisitEndpoints`, `VisitService` and `NdVisitClient` are unchanged; the
  design stands.
- Upstream added `Match.agentId` (search agent) and names A on B's co-brokerage *Propositions* cards; the visits
  block reuses the same `AgentProfileRules.profileUrl` convention.
- Upstream added the co-brokerage read-only listing page; A's visit page link now needs `?match=`, which this slice
  fixes.
- Upstream changed customer e-mails to the agent's voice; the new B mails follow the same voice and
  `AgentMailContext`.
- `MandateClosedEvent` already exists and is published by both `close` and `cancel`, with a synchronous listing
  listener; the visit cancellation plugs into the same event, no new event.
- `VisitParty.SYSTEM` already exists in the DTO, so the system cancellation needs no enum change.
- `ndopify-dto` is at 0.0.88; this slice releases 0.0.89.

## Revision 2026-11-14 — asynchronous cancellation and catch-up sweep

This revision supersedes the synchronous mandate-ending decision above ("Runs synchronously, inside the mandate's
transaction… a mandate never commits with an open visit it just ended") and the matching plan task.

**Why:** closing or cancelling a mandate must never fail or slow down because a visit could not be cancelled. The
event is a side effect; a visit left open is recoverable, a mandate closing rolled back is not.

**What changed**

- `VisitMandateEventListener.onMandateClosed` is now `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`, shaped
  like `VisitEventListener`: it sets the event's tenant, logs the outcome, and swallows failures. It runs after the
  mandate transaction commits, so the cancellation can no longer roll the closing back.
- `VisitService.cancelForEndedMandate` is no longer one batch transaction: it cancels each visit through the existing
  `update()` helper (one transaction per visit, optimistic-lock-safe), the same pattern as `expireDue` /
  `markAgentNoShows`. A bad visit is skipped and retried by the sweep, never overwriting a concurrent change.
- New catch-up: `VisitRepository.findOpenByEndedMandate` finds up to 100 open (`REQUESTED`/`CONFIRMED`) visits whose
  search mandate, or whose listing's mandate, is `CLOSED`/`CANCELLED`. `VisitJobs.cancelEndedMandates` runs it hourly
  per tenant (`ndopify.visit.cancel-ended-mandates.cron`) and cancels through the same path. It catches a failed async
  task, a crash, or a mandate ended before the listener existed.

**Trade-offs accepted**

- A mandate can commit with a `CONFIRMED` visit still open for a few seconds until the async task (or the next sweep)
  cancels it. A check-in that slips into that window wins and is left `CHECKED_IN`, as before.
- The first sweep after deploy back-fills: stale open visits on already-ended mandates are cancelled and their neutral
  cancellation e-mails **are** sent. This is accepted (the earlier "no back-fill" non-goal is lifted).
- The sweep is bounded (100 per tenant per run, hourly), so a large backlog drains over several runs instead of
  e-mailing everything at once.

**Unchanged:** the cancellation is `VisitParty.SYSTEM`, `cancelled_at = now`, `cancellation_reason = null`; `CHECKED_IN`
stays; both sides get the neutral « La visite est annulée. » and B gets the co-brokerage mail when the visit was
confirmed and organized by another agent.
