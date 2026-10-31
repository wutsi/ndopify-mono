# Match Proposal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The agent proposes a match to the customer, who accepts or rejects it.

**Architecture:** Three transitions on `MatchEntity`: `POST /v1/matches/{id}/propose` (the mandate's agent: ACTIVE →
PROPOSED, the customer e-mailed the listing), `POST /v1/matches/{id}/accept` and `POST /v1/matches/{id}/reject` (the
mandate's customer only, signed in: PROPOSED → ACCEPTED / REJECTED). The matcher already leaves non-ACTIVE rows alone.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway (MySQL), MJML + Mustache e-mails.

**Spec:** the *Out* list of `docs/superpowers/plans/2026-10-31-search-listing-matching.md` ("proposing to the customer
and the customer's accept / reject (statuses `PROPOSED`, `ACCEPTED`, `REJECTED` ...)"), and the decisions of
2026-10-31: propose is in scope; the customer answers signed in (JWT of its own party); a rejection is final, with an
optional reason.

## Global Constraints

- **Who answers:** only the mandate's customer: the JWT's party (`CallerService.findPartyIdOrNull`) must be the
  customer's party. No admin bypass, and the agent can't answer for the customer. Any application's token (today the
  customer can only sign in to the e-signature portal, whose membership it got when asked to sign the mandate).
- **Who proposes:** as dismiss, `MandateAccessService.ensureCanUpdate` (the mandate's agent, or an admin).
- Error order: 404 `MATCH_NOT_FOUND`, 401 / 403 `MANDATE_ACCESS_DENIED`, then the 409s.
- Propose: 409 `MATCH_NOT_ACTIVE`, `MANDATE_NOT_APPROVED`, `LISTING_NOT_PUBLISHED` (new). Accept / reject: 409
  `MATCH_NOT_PROPOSED` (new). Reject: `reason` optional, at most 1000 characters (400), blank stored as null.
- Propose and reject set `listingPrice` to the listing's price (the price the customer saw, then said no to).
- E-mail to the customer (`MatchProposedMailet`, `mail/match/proposed.mjml`), async after commit
  (`MatchProposedEvent`): its agent's name, the listing's title and price, a link to `{publicPortalUrl}{listing.url}`
  (none without either). French; price through `TenantFormatter`.
- DTO: `MatchStatus` + `PROPOSED`, `ACCEPTED`, `REJECTED`; `Match` + `proposedAt`, `answeredAt`, `rejectionReason`;
  `RejectMatchRequest(reason)`; `ErrorCode.MATCH_NOT_PROPOSED`, `LISTING_NOT_PUBLISHED`. Needs a DTO release before
  the server deploys.
- Out of scope: the customer's UI (no public-portal sign-in yet), e-mailing the agent the answer, proposing several
  matches in one e-mail, the customer reading the match (`GET`).

## Review Focus

1. **The agent or an admin answers for the customer** → 403, the match stays PROPOSED (accept and reject tests).
2. **Answering twice / accept after reject** → 409 `MATCH_NOT_PROPOSED`.
3. **A re-run of the matcher after a proposal or an answer** → the row is untouched (propose test).
4. **Proposing a match whose listing was rented, or whose search closed** → 409, no e-mail.
5. **Tenant without `publicPortalUrl`, or listing without `url`** → the e-mail is sent without the link (mailet test).

---

### Task 1: DTO

**Files:** `modules/ndopify-dto/.../match/dto/MatchStatus.kt`, `Match.kt`, new `RejectMatchRequest.kt`,
`.../error/dto/ErrorCode.kt`; root `pom.xml` (`ndopify-dto.version` = `0.0.75-SNAPSHOT` while developing).

- [ ] Add the statuses, fields, request (`@get:Size(max = 1000) val reason: String? = null`) and error codes;
  `mvn install` the DTO.

### Task 2: Propose

**Files:** `V9_0__match.sql` (pre-launch, edited in place: `proposed_at`, `answered_at` DATETIME NULL,
`rejection_reason` TEXT NULL); `MatchEntity`; `MatchMapper`; `MatchService.propose(id)` (`@Transactional`, publishes
`MatchProposedEvent(matchId, tenantId)`) and `sendProposedEmail(id)`; `MatchEventListener.onMatchProposed`;
`MatchProposedMailet` + template; `MatchEndpoints.propose`; the listing URL shared with `ListingPublishedMailet`.
Tests: `ProposeMatchEndpointTest` (+ `ProposeMatchEndpoint.sql`, shared with tasks 3 and 4),
`MatchProposedMailetTest`, `MatchEventListenerTest`.

- [ ] Tests: propose (PROPOSED, `proposedAt`, `listingPrice`, the customer's e-mail with the link), a run leaves it
  PROPOSED, not active, mandate closed, listing rented, another agent's, not found, anonymous.
- [ ] Implement; `mvn test -Dtest='*Match*'` → PASS; commit.

### Task 3: Accept and reject

**Files:** `MandateAccessService.ensureIsCustomer(mandate)`; `MatchService.accept(id)`, `reject(id, request)`;
`MatchEndpoints.accept`, `reject` (`@RequestBody @Valid`). Tests: `AcceptMatchEndpointTest`,
`RejectMatchEndpointTest`.

- [ ] Tests: as the customer (ACCEPTED / REJECTED, `answeredAt`, reason, blank reason → null); as the agent → 403;
  as an admin → 403; not proposed → 409; twice → 409; reason too long → 400; not found; anonymous.
- [ ] Implement; tests PASS; update `modules/ndopify-server/CLAUDE.md` (match) and the matching plan's *Out*;
  `mvn verify` → BUILD SUCCESS; commit.
