# Standard Co-Broker Agent Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One standard card for another agent (photo, linked name, WhatsApp) everywhere Partner Central names one, remove the listing page's *Visites d'autres agents* block, and box the visit page's agent.

**Architecture:** A shared `AgentCardView` built by `AgentCardViews.of(agent, back)`, one Thymeleaf fragment (`fragments/agent-card.html`) and one CSS block (`.agent-card`, `.agent-card--standalone`). The card views carry an `AgentCardView?`; the controllers load the full `Agent` where they loaded only a name. A null agent renders the generic "un autre agent" footer. No `ndopify-dto`, `ndopify-web-core` or `ndopify-server` change.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, `mockito-kotlin`, `kotlin.test`, Maven (ktlint + JaCoCo).

**Spec:** GitHub issue #122 (enriched description) and the approved prototype on local branch `prototype/story-122`, commit `7afe574b`.

## Global Constraints

- Only `modules/ndopify-partner-central/`: no `ndopify-dto`, `ndopify-web-core` or `ndopify-server` change; no version change in the root `pom.xml`.
- French UI text; code, comments, logs and docs in English.
- Tests mirror the main package; `kotlin.test` assertions; `mockito-kotlin` mocks.
- Every behaviour change has a test; `mvn verify` (ktlint + tests + JaCoCo) is green in `modules/ndopify-partner-central`.
- `src/main/html/` is the approved prototype: apply it from `prototype/story-122`, never serve it, never delete anything there.
- Anti-sautage: a co-broker card carries the agent's identity only, never the other agent's customer, mandate, match, code or report.
- Conventional commits, one module at a time; branch `feat/npc-co-broker-agent-card`.
- Footer markup: gray top rule, avatar (photo, else initials, else generic person), linked name, WhatsApp text link with icon; no "Avec"/"Publié par" prefix; no phone number.

## Review Focus

1. **Unreadable agent** — the card must show the generic person icon and "un autre agent", unlinked, without WhatsApp; never an empty avatar or a broken link.
2. **Own-inventory visit** — a visit on the signed-in agent's own listing must have no agent footer.
3. **Co-brokerage match with a visit** — the listing agent appears once, in the card's footer; the visit block inside the card has no second footer.
4. **Another agent's client** — the landlord proposal card shows that agent's footer at the bottom, not under the title; the agent's own client has no footer.
5. **Listing page** — the owner's listing page no longer reads or renders other agents' visits, and still renders all its other blocks.

---

### Task 1: The shared agent card

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentCardView.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentCardViews.kt`
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/agent-card.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentCardViewsTest.kt`

**Interfaces:**
- Produces: `AgentCardView(id: Long, name: String, initials: String, photoUrl: String?, whatsappUrl: String?, profileUrl: String)` and `AgentCardViews.of(agent: Agent, back: String): AgentCardView`. Later tasks put an `AgentCardView?` in their card views.
- Produces: `th:fragment="agentCard(agent, standalone)"`, taking an `AgentCardView?` and a `Boolean` (true: the listing page's own box; false: a card footer).

- [ ] **Step 1: Write the failing builder test**

```kotlin
class AgentCardViewsTest {
    @Test
    fun `builds the card from the agent and the back link`() {
        val card = AgentCardViews.of(AgentProfileFixtures.AGENT, "/mandates/204#matches-title")

        assertEquals(30L, card.id)
        assertEquals("Clarisse Mbarga", card.name)
        assertEquals("CM", card.initials)
        assertEquals("https://cdn.ndopify.com/party/300/photo/a.jpg", card.photoUrl)
        assertEquals("https://wa.me/237699123456", card.whatsappUrl)
        assertEquals("/agent?id=30&back=%2Fmandates%2F204%23matches-title", card.profileUrl)
    }

    @Test
    fun `an agent without photo or WhatsApp leaves both null`() {
        val card = AgentCardViews.of(AgentProfileFixtures.SPARSE_AGENT, "/visits/7001")

        assertNull(card.photoUrl)
        assertNull(card.whatsappUrl)
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentCardViewsTest`
Expected: FAIL — `AgentCardViews` unresolved.

- [ ] **Step 3: Implement the view and its builder**

```kotlin
package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.profile.ProfileRules

/** Another agent as a card: photo or initials, name, WhatsApp, and the profile link back to [back]. */
data class AgentCardView(
    val id: Long,
    val name: String,
    val initials: String,
    val photoUrl: String?,
    val whatsappUrl: String?,
    val profileUrl: String,
)

object AgentCardViews {
    fun of(agent: Agent, back: String): AgentCardView =
        AgentCardView(
            id = agent.id,
            name = ProfileRules.fullName(agent),
            initials = KycRules.initials(agent.party.firstName, agent.party.lastName),
            photoUrl = agent.party.photoUrl?.ifBlank { null },
            whatsappUrl = AgentProfileRules.whatsappUrl(agent.whatsappNumber),
            profileUrl = AgentProfileRules.profileUrl(agent.id, back),
        )
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=AgentCardViewsTest`
Expected: PASS.

- [ ] **Step 5: Add the fragment**

Create `templates/fragments/agent-card.html` with the standard markup: the avatar (photo, else initials, else a generic person icon), the name linked to `agent.profileUrl` or the plain "un autre agent" when `agent == null`, and the WhatsApp text link when `agent.whatsappUrl != null`. The root carries `class="agent-card"` and `th:classappend="${standalone} ? 'agent-card--standalone'"`. Copy the accepted prototype markup from `prototype/story-122` (`modules/ndopify-partner-central/src/main/html/index.html`, the `data-agent-card` block), replacing the `data-*` hooks with `agent` fields.

- [ ] **Step 6: Add the CSS**

Append to `public/assets/css/partner-central.css` the `.agent-card` block from `prototype/story-122` (`src/main/html/assets/css/partner-central.css`): the grid (`auto minmax(0, 1fr) auto`), the top rule (`margin-top`/`padding-top`/`border-top: 1px solid var(--color-border)`), the round avatar, the name and WhatsApp rules, the focus outlines, and `.agent-card--standalone` (its own border, surface background and padding, no top rule).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent \
  modules/ndopify-partner-central/src/main/resources/templates/fragments/agent-card.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentCardViewsTest.kt
git commit -m "feat(npc): add the standard agent card"
```

---

### Task 2: Visit cards and the visit inside a card

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt`
- Modify: `src/main/resources/templates/fragments/visit-card.html`
- Modify: `src/main/resources/templates/fragments/visit-detail.html`
- Modify: `src/main/resources/public/assets/css/visit.css`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentCardView`, `AgentCardViews.of`, and the `agentCard(agent, standalone)` fragment from Task 1.
- Produces: `VisitCardView(coBrokerage: Boolean = false, agent: AgentCardView? = null)`, replacing `agentName` and `agentUrl`.

- [ ] **Step 1: Update the failing tests**

In `VisitCardsTest`, replace the `agentName`/`agentUrl` assertions with:

```kotlin
val card = cards.upcoming.single()
assertTrue(card.coBrokerage)
assertEquals("Clarisse Mbarga", card.agent?.name)
assertEquals("/agent?id=30&back=%2F%23visits-title", card.agent?.profileUrl)
```

Keep the existing own-inventory and unreadable-agent cases, asserting `assertFalse(card.coBrokerage)` / `assertNull(card.agent)` for an own visit, and `assertTrue(card.coBrokerage)` / `assertNull(card.agent)` when the listing's agent can't be read. A readable agent without a name is left out (`assertNull(card.agent)`).

In `HomeTemplateTest` and `MandateTemplateTest`, add assertions that a co-brokered visit card renders the footer (the agent's name link, the WhatsApp link, no text "Avec"), and that an own visit card has no `[data-agent-card]`/footer.

- [ ] **Step 2: Run them and verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitCardsTest,HomeTemplateTest,MandateTemplateTest`
Expected: FAIL — `coBrokerage`/`agent` unresolved or the footer absent.

- [ ] **Step 3: Replace the two agent fields with the card**

In `VisitCardView`, replace `agentName`/`agentUrl` by `coBrokerage: Boolean = false` and `agent: AgentCardView? = null`. In `VisitCards`, compute `coBrokerage` from the listing's `agentId != visit.agentId` and build `agent = AgentCardViews.of(it, back)` only when the agent is readable; keep the `back == null` guard (pages that don't show the footer).

- [ ] **Step 4: Use the fragment at the bottom of the card**

In `fragments/visit-card.html`, remove the `th:if="${v.agentName != null}"` line and put, as the last child of the card's `<article>`:

```html
<div th:if="${v.coBrokerage}" th:replace="~{fragments/agent-card :: agentCard(${v.agent}, false)}"></div>
```

Do the same in `fragments/visit-detail.html`, after the visit CTA. Remove the `.visit-card__agent` / `.visit-detail__agent` rules from `visit.css` (nothing else uses them).

- [ ] **Step 5: Run the tests and verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitCardsTest,HomeTemplateTest,MandateTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): use the standard agent card on visit cards"
```

---

### Task 3: Hosted co-brokerage visits and the listing page

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt`
- Modify: `src/main/resources/templates/fragments/co-brokerage-visit.html`
- Modify: `src/main/resources/templates/listing.html`
- Modify: `src/main/resources/public/assets/css/listing.css`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentCardView`, `AgentCardViews.of`, and the fragment from Task 1.
- Produces: `ListingVisitView(agent: AgentCardView? = null)`, replacing `agentName`, `agentUrl`, `whatsappUrl` and `agentInitials`; `ListingView.agent: AgentCardView?`; `ListingView` without `visits`.

- [ ] **Step 1: Update the failing tests**

In `ListingViewsTest`, assert that `ListingViews.visits(...)` returns `ListingVisitView.agent` with the name, photo/initials, WhatsApp and the back link `"/mandates/203#co-brokerage-visits-title"`, and `null` when the agent can't be read. In `ListingControllerTest`, assert that a published owner listing no longer calls `visitClient.coBrokerageOnListing`. In `ListingTemplateTest`, assert that `listing.html` has no `[data-listing-visits]` block; in `ListingViewsTest`/`ListingTemplateTest`, assert the co-broker listing's agent card is the boxed card (`agent-card--standalone`). In `HomeTemplateTest` and `MandateTemplateTest`, assert the hosted-visit card renders the footer, no "Avec", with the WhatsApp link.

- [ ] **Step 2: Run them and verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest,ListingControllerTest,ListingTemplateTest,HomeTemplateTest,MandateTemplateTest`
Expected: FAIL on the fields, the removed block or the missing footer.

- [ ] **Step 3: Carry the card in the hosted-visit views**

Replace `ListingVisitView`'s agent fields by `agent: AgentCardView?`. In `ListingViews.visits`, build `agent = AgentCardViews.of(agents[visit.agentId], back)` when that agent is readable, else `null`. Remove the listing page's visits: `ListingView.visits`, the `visits` parameter of `ListingViews.of`, and `ListingController.coBrokerageVisits`/the `visitClient.coBrokerageOnListing` read. Keep `ListingViews.visits` for the home and mandate blocks.

- [ ] **Step 4: Update the templates**

`fragments/co-brokerage-visit.html`: replace the `.co-visit-card__agent` block with `<div th:replace="~{fragments/agent-card :: agentCard(${v.agent}, false)}"></div>` as the card's last child; drop "Avec". `listing.html`: remove the whole `[data-listing-visits]` section, and inside the existing `th:if="${l.agent != null}"` agent section replace its `.listing-agent` card with:

```html
<div th:replace="~{fragments/agent-card :: agentCard(${l.agent}, true)}"></div>
```

(no "Publié par" eyebrow).

- [ ] **Step 5: Convert the listing agent's view and CSS**

`ListingView.agent` becomes `AgentCardView?`; `ListingViews.agent(agent, listing, match)` is replaced by `AgentCardViews.of(agent, "/listings/${listing.id}?match=${match.id}")` at the controller. Remove `ListingAgentView` from `PublishedListingView.kt` if nothing else uses it (Task 5 finishes the visit page). Delete the `.co-visit-card__agent*` rules from `listing.css`, and the `.listing-agent__photo`/`.listing-agent__name` rules only the replaced markup used (keep `.listing-agent`, `__info`, `__headline`, `__eyebrow`, `__contact` for the mandate's customer card).

- [ ] **Step 6: Run the tests and verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingViewsTest,ListingControllerTest,ListingTemplateTest,HomeTemplateTest,MandateTemplateTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): use the standard agent card for hosted visits and listings"
```

---

### Task 4: Proposal and match cards

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt` (`ProposalRow`)
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt` (`clients`)
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `src/main/resources/templates/mandate.html`
- Modify: `src/main/resources/public/assets/css/home.css`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentCardView`, `AgentCardViews.of`, and the fragment from Task 1.
- Produces: `ProposalRow(otherAgent: Boolean = false, agent: AgentCardView? = null)`, replacing `agentName`/`agentUrl`; `MatchView(agent: AgentCardView? = null)`, replacing `origin`/`originUrl`; `MatchViews.section(..., agents: Map<Long, Agent>)` and `ListingViews.clients(..., agents: Map<Long, Agent>, back: String?)`; `MandateController`'s `listingAgents`/`otherAgents` return `Map<Long, Agent>`.

- [ ] **Step 1: Update the failing tests**

In `MatchViewsTest`, build a co-brokerage match with an agent and assert `view.agent?.name`, `.profileUrl`, `.whatsappUrl`; assert an own match has `agent == null`. Assert the dedupe: when the match's client has a visit on that same listing, `view.visit?.coBrokerage` is false and `view.visit?.agent` is null. In `ListingViewsTest`, assert the proposal row of another agent's client has `otherAgent = true` and its `agent` card with the back link `"/mandates/203#proposals-title"`, while the agent's own client has `otherAgent = false` and `agent == null`. In `MandateControllerTest`, assert the agent reads (full `Agent`, not names) for the co-brokerage matches and the other agents' proposals. In `MandateTemplateTest`, assert the match/proposal footers are at the bottom and there is no `match__origin` line.

- [ ] **Step 2: Run them and verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MatchViewsTest,ListingViewsTest,MandateControllerTest,MandateTemplateTest`
Expected: FAIL on the fields or the missing footer.

- [ ] **Step 3: Carry the cards in the views**

`MatchView`: replace `origin`/`originUrl` with `agent: AgentCardView? = null`; build it from `agents[listing.agentId]` when `match.coBrokerage`. Suppress the visit's own footer there: `visit = if (match.coBrokerage) visit?.copy(coBrokerage = false, agent = null) else visit`. `ProposalRow`: replace `agentName`/`agentUrl` with `otherAgent: Boolean = false` and `agent: AgentCardView? = null`, set for another agent's client. `ListingViews.clients` takes `agents: Map<Long, Agent>` and `back`, and passes the card. Both keep the anti-sautage rules (another agent's mandate/customer never read or named).

- [ ] **Step 4: Load full agents in `MandateController`**

Rename `listingAgentNames` to `listingAgents` and `otherAgentNames` to `otherAgents`; return `Map<Long, Agent>` (cached known agents included; unreadable ones left out). Update the `MatchViews.section` and `ListingViews.clients` calls. A failed agent read must still render the card with `agent == null` (the generic footer).

- [ ] **Step 5: Move the agent lines to the card's bottom**

In `mandate.html`, remove the `[data-match-origin]` paragraph and the `[data-proposal-agent]` paragraph; add, as the last element of each card's `<article>`:

```html
<div th:if="${match.coBrokerage}" th:replace="~{fragments/agent-card :: agentCard(${match.agent}, false)}"></div>
<div th:if="${row.otherAgent}" th:replace="~{fragments/agent-card :: agentCard(${row.agent}, false)}"></div>
```

Remove `.match__origin` from `home.css`.

- [ ] **Step 6: Run the tests and verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MatchViewsTest,ListingViewsTest,MandateControllerTest,MandateTemplateTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): use the standard agent card on proposal and match cards"
```

---

### Task 5: The visit page

**Files:**
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitPage.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViews.kt`
- Modify: `src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt` (delete `ListingAgentView`)
- Modify: `src/main/resources/templates/visit.html`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitViewsTest.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `AgentCardView`, `AgentCardViews.of`, and the fragment from Task 1.
- Produces: `VisitPage.agent: AgentCardView?`.

- [ ] **Step 1: Update the failing tests**

In `VisitViewsTest`, assert `page.agent` has the name, initials, photo, WhatsApp and `profileUrl = "/agent?id=30&back=%2Fvisits%2F7025"`. In `VisitTemplateTest`, assert the co-brokerage visit page renders the boxed card (`agent-card--standalone`) with the name link and the WhatsApp text link, no "Visite organisée avec", and no phone number in the card; an own visit has no agent section.

- [ ] **Step 2: Run them and verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitViewsTest,VisitTemplateTest`
Expected: FAIL.

- [ ] **Step 3: Replace the view and the template**

`VisitPage.agent` becomes `AgentCardView?`; `VisitViews` builds it with `AgentCardViews.of(agent, "/visits/${visit.id}")`. In `visit.html`, replace the `.listing-agent` card by `<div th:replace="~{fragments/agent-card :: agentCard(${p.agent}, true)}"></div>` inside `th:if="${p.agent != null}"`; delete the eyebrow and the number link. Delete `ListingAgentView` from `PublishedListingView.kt` now that nothing uses it.

- [ ] **Step 4: Run the tests and verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=VisitViewsTest,VisitTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): box the visit page's agent card"
```

---

### Task 6: Prototype, module docs and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/**` (from `prototype/story-122`)
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

**Interfaces:**
- Consumes: the approved prototype commit.

- [ ] **Step 1: Apply the approved prototype**

```bash
git checkout prototype/story-122 -- modules/ndopify-partner-central/src/main/html
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the standard co-broker agent card"
```

- [ ] **Step 2: Update the module docs**

In `modules/ndopify-partner-central/CLAUDE.md`, update the paragraphs that describe the co-broker agent line ("Avec", the WhatsApp number) and the listing page's *Visites d'autres agents* block: one standard `fragments/agent-card.html` footer on visit, visit-detail, hosted-visit, proposal and match cards, the boxed variant on the visit and listing pages, and the listing page no longer showing hosted visits (home and mandate still do).

- [ ] **Step 3: Verify the module**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests and JaCoCo).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the standard agent card"
```
