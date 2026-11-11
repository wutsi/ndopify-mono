# Co-Brokerage Notifications — Design

**Date:** 2026-11-11
**Slice:** #2 of the co-brokerage decomposition, after eligibility (PR #96)
**Status:** proposed, awaiting approval

## Intent and why

The eligibility slice (PR #96) creates matches between a search and another agent's published listing, flagged
`coBrokerage = true`. It deliberately keeps those rows out of the existing own-inventory alert, so today nobody is
notified. This slice makes co-brokerage actionable:

- the **search agent** learns that another agent's listing matches their search, and gets that agent's contact so the
  two can talk directly;
- the **listing agent** learns that another agent is interested in their listing, on shortlist or proposal, so the
  contact is driven by real intent rather than every passive match.

The customer experience does not change: the search agent still proposes the property to their customer as their own.

## Goals

- Notify the search agent once per co-brokerage match, on the existing hourly match-notification job.
- Notify the listing agent once when a search agent shows interest in their co-brokerage listing (shortlist or
  proposal).
- Show each recipient the counterpart agent's identity and contact.
- Keep the existing own-inventory notification pipeline and template untouched.
- Never expose a customer (search or listing side) to the counterpart agent.

## Non-goals

- The co-brokerage read-only listing page, visits, closing, commission.
- Real-time/immediate delivery. Both alerts ride the existing cron; the reverse alert can lag up to one interval.
- Per-agent notification preferences, unsubscribe, push/SMS/in-app notifications.
- Re-alerting when a co-brokerage match is re-scored, its score improves, or its price drops.
- Any change to the customer-facing proposal (`MatchProposedMailet`) or to the `Match` DTO.

## Anti-goals

- A customer's name or contact reaching the counterpart agent, or the counterpart agent's identity/contact reaching a
  customer. This is the anti-sautage rule: neither side may bypass the other agent.
- Merging co-brokerage content into the "votre portefeuille" email, or changing that email's wording.
- A second notification for the same match/recipient.
- A new scheduler or a new `ndopify.match.*` cron property.

## Anti-sautage guard (hard invariant)

Co-brokerage must never let a customer and the counterpart agent reach each other. Agents may know and contact each
other (that is the point of co-brokerage); customers may not know the other agent exists. This invariant outranks
convenience and applies to every co-brokerage channel, not just these emails.

| Party | May see | Must never see |
|-------|---------|----------------|
| Search agent A | their own customer, the listing, co-agent B's identity/contact | B's customer |
| Listing agent B | their own customer, their own listing, co-agent A's identity/contact | A's customer |
| Customer of A (search) | their own agent A, the property/listing details | co-agent B, B's customer, B's agency/contact |
| Customer of B (owner) | their own agent B, their own property | co-agent A, A's customer, A's agency/contact |

Enforcement for this slice:

- **Forward email (to A):** shows A's own customer, the listing, and B's identity/contact. It never reads or renders
  the listing's customer.
- **Reverse email (to B):** shows A's identity/contact and the search criteria. It never reads or renders A's customer,
  their contact, or a link to their search/proposal.
- **Customer proposal (`MatchProposedMailet`, unchanged):** presents only A as the proposing agent. It must never
  contain B's identity, agency, contact, or the listing mandate's customer. A regression test pins this.
- **Templates:** every co-brokerage template gets a leak test asserting the counterpart customer's name/e-mail is
  absent from the rendered body.
- **Future slices** (read-only listing page, visits, closing, commission) inherit this invariant.

## Constraints

- Multi-tenant: the job already iterates active tenants with `TenantContext`; all queries are tenant-scoped by
  `@TenantId`.
- `MatchEntity.notifiedAt` keeps its current meaning: "the search agent was told about this match". A second, separate
  timestamp is needed for the listing agent.
- One row per `(mandate, listing)`; a co-brokerage row may move `ACTIVE → SHORTLISTED → PROPOSED → ACCEPTED`, or
  `ACTIVE → PROPOSED`, `PROPOSED → REJECTED`, `PROPOSED → EXPIRED`, `SHORTLISTED → DISMISSED`.
- Server-only slice: no `ndopify-dto` release is needed.
- Tests: `kotlin.test`, mockito-kotlin; coverage gate of `ndopify-server` (line 0.95, class 0.90); endpoint/job tests
  need local MySQL.

## Decisions made with the product owner

1. **Separate co-brokerage email**, with its own template; the existing own-inventory email is unchanged. When both
   kinds are pending, the agent receives two emails in the same run.
2. **Show the counterpart agent's identity and contact to the other agent**, in both directions, so the agents can
   communicate. Never to a customer.
3. **Customers and counterpart agents never learn each other.** The counterpart's customer/mandate is never exposed to
   an agent, and the counterpart agent is never exposed to a customer. See the anti-sautage guard.
4. **Reuse the existing `notifyAgents` job and cron**; no new scheduler.
5. **Customer proposal is unchanged**: the search agent is presented as proposing the property, not the co-broker.
6. **Reverse notification is in scope.**
7. **One-shot per match and recipient**: `notifiedAt` for the search agent, a new timestamp for the listing agent.

### Calls made during design (correctable)

These were left open; the design takes the recommended option so it can be planned. Say so if any is wrong.

- **Contact fallback:** show WhatsApp when present; when the agent has no WhatsApp, show their account e-mail as a
  `mailto:` link. `MailService` keeps no Reply-To (by design); the address is visible in the body only.
- **Reverse email shape:** one e-mail per listing agent per run, grouped by listing. An interested search agent
  appears once per listing even when several of their searches match; the row shows their best-scoring search's
  criteria plus a "N recherches" indicator. The subject count is the number of distinct interested agents. All
  selected rows are marked after the send.
- **Reverse eligibility at run time:** notify only while the search mandate is still APPROVED and the listing still
  PUBLISHED. If either left that state before the run, the agent is not notified and the row stays unmarked; the
  query excludes it, so there is no repeat-send path. This avoids alerting about a closed search or a rented listing.
- **First run:** suppress matches that predate the deploy (see Rollout), so agents get only new opportunities.

## Approaches considered

### 1. Co-brokerage notification service on the existing hourly job (chosen)

`MatchJobs.notifyAgents()` keeps the own-inventory service as-is and then calls a new
`CoBrokerageNotificationService` with two methods: `notifySearchAgents()` (forward) and `notifyListingAgents()`
(reverse). Reuses the tenant loop, cron, per-agent failure isolation and mark-only-after-send semantics. Adds one
`T_MATCH` column for the reverse timestamp.

- Pros: smallest new surface; own-inventory code untouched; consistent with the rest of match mailing; easy to test
  through the job.
- Cons: the reverse alert can lag up to one cron interval (≤1h in prod).

### 2. Event-driven reverse alert

Forward stays on the job; the reverse alert is sent immediately from `shortlist`/`propose` through an application
event (there is already `MatchProposedEvent`).

- Pros: immediate.
- Cons: no retry/outbox; a failed mail is lost unless new retry state is added; couples mailing into the proposal
  path; harder to test and to keep idempotent.

### 3. Dedicated co-brokerage scheduler

A separate scheduled job and cron property.

- Pros: independent cadence.
- Cons: a new config property and a second tenant iteration, contradicting decision 4; more operational surface.

Approach 1 is chosen.

## Design

### Data model

New migration `V10_6__match-listing-agent-notified.sql`:

```sql
ALTER TABLE T_MATCH
    ADD COLUMN listing_agent_notified_at DATETIME DEFAULT NULL;

-- Rows that predate this slice: keep them visible, but do not e-mail a one-time backlog
UPDATE T_MATCH
    SET notified_at = COALESCE(created_at, NOW())
    WHERE co_brokerage = TRUE AND notified_at IS NULL;

UPDATE T_MATCH
    SET listing_agent_notified_at = modified_at
    WHERE co_brokerage = TRUE AND listing_agent_notified_at IS NULL
      AND status IN ('SHORTLISTED', 'PROPOSED', 'ACCEPTED', 'EXPIRED');
```

- `MatchEntity`: `val listingAgentNotifiedAt: Date? = null`, next to `notifiedAt`.
- `notifiedAt` (existing): when the **search agent** was told about this match.
- `listingAgentNotifiedAt` (new): when the **listing agent** was told that another agent's search matched their
  listing.
- Rollout: backfill both timestamps for rows that predate the deploy (see Rollout), so the first run does not send a
  backlog of historical matches or interests.

### Selection

`MatchRepository`, two read queries and one bulk update:

```kotlin
// Forward: co-brokerage matches the search agent has not been told about
@Query(
    "SELECT m FROM MatchEntity m WHERE m.coBrokerage = TRUE AND m.status = :status " +
        "AND m.notifiedAt IS NULL AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus"
)
fun findCoBrokerageToNotify(status: MatchStatus, mandateStatus: MandateStatus, listingStatus: ListingStatus): List<MatchEntity>

// Reverse: co-brokerage rows where the search agent showed interest, the listing agent not told yet
@Query(
    "SELECT m FROM MatchEntity m WHERE m.coBrokerage = TRUE AND m.listingAgentNotifiedAt IS NULL " +
        "AND m.status IN :statuses AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus"
)
fun findCoBrokerageInterestToNotify(
    statuses: Collection<MatchStatus>,
    mandateStatus: MandateStatus,
    listingStatus: ListingStatus,
): List<MatchEntity>

@Transactional
@Modifying
@Query("UPDATE MatchEntity m SET m.listingAgentNotifiedAt = :now WHERE m.id IN :ids")
fun markListingAgentNotified(@Param("ids") ids: Collection<Long>, @Param("now") now: Date): Int
```

- Forward filters: `status = ACTIVE`, not `notifiedAt`, mandate `APPROVED`, listing `PUBLISHED`.
- Reverse filters: status in `SHORTLISTED`, `PROPOSED`, `ACCEPTED`, `EXPIRED`, not `listingAgentNotifiedAt`, mandate
  `APPROVED`, listing `PUBLISHED`. `REJECTED` and `DISMISSED` are excluded: the interest was declined or withdrawn.
  `PROPOSED` is included because `MatchProposalService.propose` can go straight from `ACTIVE` to `PROPOSED`.
- The existing `markNotified` is unchanged.

### Service and job wiring

New `CoBrokerageNotificationService` (same package as `MatchNotificationService`):

```kotlin
fun notifySearchAgents(): Int      // number of agents e-mailed
fun notifyListingAgents(): Int
```

Each method:
1. loads its pending rows;
2. groups them by recipient agent id (`match.agentId` forward, `match.listing.agent.id` reverse);
3. per agent, calls its mailet then marks the rows; an exception is logged and leaves that agent's rows for the next
   run, like the existing service;
4. returns the number of emails sent.

`MatchJobs.notifyAgents()` becomes:

```kotlin
notificationService.notifyAgents()
coBrokerageNotificationService.notifySearchAgents()
coBrokerageNotificationService.notifyListingAgents()
```

with the counts logged separately per tenant. No new cron property; the existing `ndopify.match.notify.cron` applies.

### Forward email — to the search agent

`CoBrokerageMatchMailet` + `/mail/match/co_brokerage_matches.mjml`.

- One email per search agent, grouped by search mandate (best score first), up to 5 listings per mandate, the rest
  counted.
- Body: "Des biens publiés par d'autres agents correspondent aux recherches de vos clients."
- Each listing row: cover image, title, price, score %, and the listing agent's name, photo and WhatsApp link (or
  `mailto:` account e-mail when they have no WhatsApp). The search customer's name is shown (the recipient's own
  customer); the listing's customer is never read or rendered.
- CTA: `{partnerCentralUrl}/mandates/{searchMandateId}` — the search agent's own mandate page.
- Subjects:
  - one: `{{tenant_name}}: 1 bien d'un autre agent correspond à vos recherches`
  - many: `{{tenant_name}}: {{count}} biens d'autres agents correspondent à vos recherches`
- Marks `notifiedAt` after the email is sent.

### Reverse email — to the listing agent

`CoBrokerageInterestMailet` + `/mail/match/co_brokerage_interest.mjml`.

- One email per listing agent, grouped by their listing, up to 5 interested agents per listing, the rest counted.
- Body: "Votre bien intéresse d'autres agents."
- Each interested-agent row: agent name, photo, WhatsApp link (or `mailto:` account e-mail when no WhatsApp), and an
  anonymized search summary (listing type, property type, place, rooms, budget) built only from `match.mandate`.
  When the same agent has several searches matching the listing, they appear once with their best-scoring search's
  criteria and a "N recherches" indicator.
- **No customer name, contact, or link to the search mandate.** The reverse email never calls `CustomerService` for
  the search customer and never renders `match.mandate.customerId`.
- CTA: `{partnerCentralUrl}/mandates/{listingMandateId}` — the listing agent's own mandate, when configured.
- Subjects:
  - one: `{{tenant_name}}: 1 agent s'intéresse à votre bien`
  - many: `{{tenant_name}}: {{count}} agents s'intéressent à vos biens`
  - `{{count}}` is the number of distinct interested agents, not the number of match rows.
- Marks `listingAgentNotifiedAt` after the email is sent, for every row included in that agent's email.

### Privacy invariant

The two emails only use:

- forward: `match.mandate` (the recipient's own search), `match.listing` (title, price, photo, city), and
  `match.listing.agent` (the counterpart agent's identity/contact);
- reverse: `match.listing` (the recipient's own listing), `match.mandate` (criteria only), and the search agent's
  identity/contact resolved from `match.mandate.agentId`.

The counterpart agent's contact is their WhatsApp number when present, otherwise their account e-mail. Neither email
reads or renders the counterpart's customer. Neither links to the counterpart's mandate page. The customer-facing
proposal email never renders the counterpart agent. See the anti-sautage guard for the full matrix.

### Idempotence and failure

- Forward is one-shot on `notifiedAt`; reverse is one-shot on `listingAgentNotifiedAt`. Re-scores never clear either.
- If a match is shortlisted and then proposed, the first run that sees an interest status sends once and marks it; the
  proposal does not send again.
- Emails are marked only after a successful send; a failed send leaves the rows for the next run.

## What is left to the builder

- Exact French copy and layout of both templates, following the existing MJML/mustache patterns.
- How the reverse summary is phrased when it shows the "N recherches" indicator.
- Whether the two job methods share a helper or stay duplicated; either is fine if the per-agent failure semantics are
  preserved.
- The mailer/service test layout (new test class vs extending `NotifyMatchesJobTest`).
- The exact backfill conditions in `V10_6` (see Rollout).

## Testing

- Unit tests for `CoBrokerageNotificationService`: filters, grouping, marking after send, per-agent failure isolation,
  second run sends nothing.
- Mailet unit tests for both emails: recipients, subjects (1 vs many), grouping, max-5 cap, agent contact fields.
- Template render tests: no customer name/contact anywhere in either HTML output.
- Anti-sautage leak tests: the forward template never contains the listing customer's name/e-mail; the reverse
  template never contains the search customer's name/e-mail; `MatchProposedMailet` never contains the listing agent's
  identity/contact or the listing customer.
- Job/integration test: forward row sent and marked; reverse sent for `SHORTLISTED` and `PROPOSED`, not for `ACTIVE`,
  `REJECTED`, `DISMISSED`; an agent with several matching searches appears once in the reverse email; own-inventory
  pipeline unchanged; second run silent; pre-existing rows suppressed by the migration.
- Migration test: `V10_6` backfills historical `notified_at` and `listing_agent_notified_at` without touching own
  inventory rows.
- `mvn verify` with the JaCoCo gate.
- Existing tests that must stay green: `NotifyMatchesJobTest`, `RematchJobTest`, `MatchNotificationMailetTest`,
  `CoBrokerageMatchEndpointTest`.

## Rollout

Server-only; no DTO release. The migration is additive with a null default. Because both alerts ride the existing
cron, production sees the forward alert on the next hourly run and the reverse alert on the next run after an
interest.

To avoid a one-time blast on the first run, `V10_6` also backfills rows that predate the deploy:

- co-brokerage `ACTIVE` rows with `notified_at IS NULL` get `notified_at = created_at` (forward suppressed);
- co-brokerage rows in an interest status (`SHORTLISTED`, `PROPOSED`, `ACCEPTED`, `EXPIRED`) get
  `listing_agent_notified_at = modified_at` (reverse suppressed).

They remain visible in `/v1/matches` and Partner Central; only the e-mail is suppressed. New matches and new interest
after deploy notify normally.

## Out of scope

Read-only listing page, visits, closing, commission, customer proposal wording, real-time delivery, notification
preferences, price-drop/re-score re-alerts.
