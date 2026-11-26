# Co-brokerage "Voir la visite" opens the visit on the listing agent's home page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The listing agent (B) of a co-brokerage visit reaches that visit from its Telegram message and e-mail: the
home page's *Visites d'autres agents* card, anchored `#co-visit-{id}`, instead of the locked `/visits/{id}` or the
dead `/listings/{id}#visits-title`.

**Architecture:** Partner Central adds `id="co-visit-{id}"` to the shared co-brokerage card fragment (from a new
`ListingVisitView.id`) and drops the dead anchor from the card's listing link. ndopify-server points
`CoBrokerageVisitBotlet`'s "Voir la visite" and `CoBrokerageVisitMailet`'s new "Voir la visite" button at
`{partnerCentralUrl}/#co-visit-{id}`, on the confirmation and the reminder only.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf (Partner Central), MJML + Mustache mails, Telegram buttons, JUnit +
mockito-kotlin + `kotlin.test`.

**Spec:** issue #336 (the enriched story body). Prototype waived by the user (anchor only, no visual change).

## Global Constraints

- Anchor format: `co-visit-{id}` (ex: `co-visit-8001`); link: `{partnerCentralUrl without trailing /}/#co-visit-{id}`.
- Telegram button label: `Voir la visite`; e-mail button label: `Voir la visite` (replaces `Voir l'annonce`).
- Confirmation + reminder: visit link present. Cancellation + no-show: no visit link at all (no Telegram "Voir la
  visite", no e-mail button, e-mail photo not linked).
- No `partnerCentralUrl` on the tenant: no visit link anywhere (unchanged).
- Never carry A's customer in a message or e-mail (unchanged).
- Prototype (`src/main/html/`): add lines only, never remove or "clean up" anything.
- Assertions with `kotlin.test`; mocks with `mockito-kotlin`. ktlint must pass (`mvn validate`).
- No DTO or web-core change.

## Review Focus

- A tenant `partnerCentralUrl` with a trailing `/` must give `https://…/#co-visit-1`, not `//#…` (the botlet test
  tenant already has a trailing slash; Task 2 asserts the exact URL).
- A blank `partnerCentralUrl` must give no link (botlet already uses `takeIf { isNotBlank() }`; the mailet must too —
  Task 3 test).
- The cancelled / no-show e-mail must not keep a linked photo through a leftover `href` (Task 3 template test asserts
  the visit URL is absent from the whole HTML).
- The mandate page's block shares the fragment: it must still render with the new id (Task 1 asserts on
  `MandateTemplateTest`'s card).
- The home card's listing link must lose only the anchor, still `/listings/{listingId}` (Task 1).

---

### Task 1: Partner Central — per-visit anchor and the listing link without the dead anchor

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
  (`ListingVisitView`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
  (`visits`, ~line 167)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/co-brokerage-visit.html`
- Modify (prototype, add one line each): `modules/ndopify-partner-central/src/main/html/assets/js/visit.js` and
  `modules/ndopify-partner-central/src/main/html/v2/assets/js/visit.js` (`renderCoBrokerageVisits`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`,
  `.../ui/home/HomeTemplateTest.kt`, `.../ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Produces: `ListingVisitView.id: Long` (first property, default `-1`), the fragment's `<li id="co-visit-{id}">`.

- [ ] **Step 1: Write the failing tests**

In `ListingViewsTest`, the test around line 1140 (visit `id = 1`, listing 501):

```kotlin
assertEquals(1L, rows.single().id)
assertEquals("/listings/501", rows.single().listingUrl)   // was "/listings/501#visits-title"
```

In `HomeTemplateTest.\`a hosted co-brokerage visit is on the home block\`` (visit `id = 8001`):

```kotlin
assertEquals("co-visit-8001", card.id())
assertEquals("/listings/501", card.selectFirst(".co-visit-card__listing a")?.attr("href"))
```

In `MandateTemplateTest` (the `.co-visit-card` test near line 556): `assertEquals("co-visit-8001", card.id())`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingViewsTest,HomeTemplateTest,MandateTemplateTest'`
Expected: FAIL (no `id`, listing URL still has `#visits-title`).

- [ ] **Step 3: Implement**

- `ListingVisitView`: add `val id: Long = -1` (KDoc: "The visit's id: the card's `co-visit-{id}` anchor, which the
  co-brokerage Telegram message and e-mail open.").
- `ListingViews.visits`: pass `id = visit.id`; `listingUrl = title?.let { "/listings/${visit.listingId}" }`.
- Fragment: `<li th:fragment="coBrokerageVisit(v)" class="co-visit-card" th:id="|co-visit-${v.id}|">`.
- Prototype, both `visit.js`: in `renderCoBrokerageVisits`, right after the `cloneNode(true)` line, add
  `item.id = "co-visit-" + visit.id;`. Nothing else changes.
- Fix any other test still asserting `/listings/501#visits-title` as a `listingUrl` (the `back` argument
  `"/listings/501#visits-title"` in `ListingViewsTest` is a caller-supplied back link, leave it).

- [ ] **Step 4: Run the module's tests**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests, coverage gate).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): anchor each co-brokerage visit card on the home page" -m "Refs #336"
```

### Task 2: Server, Telegram — "Voir la visite" opens the home page card, only while the visit is on it

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt`,
  `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt`

**Interfaces:**
- Consumes: the `co-visit-{id}` anchor of Task 1 (by convention only, no code dependency).

- [ ] **Step 1: Write the failing tests**

`CoBrokerageVisitBotletTest` (tenant `partnerCentralUrl = "https://partner.ndopify.com/"`, visit 1):
- `confirmed - B is told …` and `reminder - the day and the time`: buttons are
  `[TelegramButton("Voir la visite", url = "https://partner.ndopify.com/#co-visit-1"), TelegramButton("Contacter sur WhatsApp", …)]`.
- `cancelled - …` and `no-show - the customer did not come`: buttons are exactly
  `[TelegramButton("Contacter sur WhatsApp", …)]`.
- New: `` `cancelled - an organizer without a number has no button` ``: A without `whatsappNumber` → `emptyList()`.
- `an organizer without a number keeps only the visit button` (confirmed): URL becomes `…/#co-visit-1`.

`TelegramCoBrokerageVisitEndpointTest`: the confirmation's button URL becomes `https://partner.ndopify.com/#co-visit-3`.

Update the class KDoc: the visit button opens B's home page card (`/#co-visit-{id}`), on the confirmation and the
reminder only; B can't read `/visits/{id}`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='CoBrokerageVisitBotletTest,TelegramCoBrokerageVisitEndpointTest'`
Expected: FAIL on the button URL / the extra button.

- [ ] **Step 3: Implement**

`send(…)` takes `withVisitLink: Boolean`; `sendConfirmed` / `sendReminder` pass `true`, `sendCancelled` /
`sendNoShow` pass `false`. `buttons(visit, agent, tenant, withVisitLink)` builds the visit button only when
`withVisitLink`, with `url = "$url/#co-visit-${visit.id}"`.

- [ ] **Step 4: Run the tests**

Run the Step 2 command. Expected: PASS (MySQL must be running for the endpoint test).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt
git commit -m "fix(server): open the co-brokerage visit on the listing agent's home page from Telegram" -m "Refs #336"
```

### Task 3: Server, e-mail — "Voir la visite" on the confirmation and the reminder, no link otherwise

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt`
- Modify: `modules/ndopify-server/src/main/resources/mail/visit/co_brokerage_confirmed.mjml`,
  `co_brokerage_reminder.mjml`, `co_brokerage_cancelled.mjml`, `co_brokerage_no_show.mjml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailetTest.kt`,
  `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitTemplateTest.kt`

**Interfaces:**
- Produces: the mail context key `visit_url` (replaces `listing_url`), set only on the confirmation and the reminder.

- [ ] **Step 1: Write the failing tests**

`CoBrokerageVisitMailetTest` (tenant `https://partner.ndopify.com`, listing 101, the test's visit id):
- confirmed and reminder: `assertEquals("https://partner.ndopify.com/#co-visit-<id>", context["visit_url"])` and
  `assertFalse(context.containsKey("listing_url"))`.
- cancelled and no-show: `assertNull(context["visit_url"])`.
- New: `` `no Partner Central - no visit link` ``: tenant `partnerCentralUrl = null` and `= " "` → `visit_url` null.

`CoBrokerageVisitTemplateTest`: the shared context sets `"visit_url" to "https://partner.ndopify.com/#co-visit-1"`
instead of `listing_url`; `assertCommon` drops the `/listings/101#visits-title` check. Then:
- confirmed and reminder: the HTML contains `https://partner.ndopify.com/#co-visit-1` on the button
  (`Jsoup` `a:contains(Voir la visite)` href) and on the photo's link; the text has no `Voir l'annonce`.
- cancelled and no-show (even with `visit_url` in the context): the HTML doesn't contain `#co-visit-1` nor
  `Voir la visite` nor `Voir l'annonce`, and the photo `img` is not inside an `a`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='CoBrokerageVisitMailetTest,CoBrokerageVisitTemplateTest'`
Expected: FAIL.

- [ ] **Step 3: Implement**

- Mailet: drop `listing_url` from the common context. `sendConfirmed` / `sendReminder` pass
  `extra = mapOf("visit_url" to visitUrl(visit, tenant))`, with
  `private fun visitUrl(visit: VisitEntity, tenant: TenantEntity): String?` =
  `partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { "$it/#co-visit-${visit.id}" }` (the
  tenant is resolved in `send`; pass a `(TenantEntity) -> Map<String, Any?>` or resolve the URL there from a
  `withVisitLink: Boolean` — implementer's choice, the tests pin the result). Update the KDoc: the confirmation and
  the reminder open B's home page card; the cancellation and the no-show carry no link.
- `co_brokerage_confirmed.mjml`, `co_brokerage_reminder.mjml`: `listing_url` → `visit_url` in the photo's `href`
  section and the button; button text `Voir la visite`; the photo comment says it links to the visit.
- `co_brokerage_cancelled.mjml`, `co_brokerage_no_show.mjml`: remove the `{{#listing_url}}…{{/listing_url}}` `href`
  from `mj-image` and the whole button block (server templates, not the prototype).

- [ ] **Step 4: Run the module's verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS (needs local MySQL).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail modules/ndopify-server/src/main/resources/mail/visit modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail
git commit -m "fix(server): link the co-brokerage e-mails to the visit on the listing agent's home page" -m "Refs #336"
```

### Task 4: Module docs

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the *Visites d'autres agents* paragraph, ~line 481): the card
  carries `id="co-visit-{id}"`, which the co-brokerage Telegram message and e-mail open; its listing link is
  `/listings/{id}`.
- Modify: `modules/ndopify-server/CLAUDE.md` (the `CoBrokerageVisitNotifier` paragraph, ~line 621): B's Telegram message and e-mail open
  `{partnerCentralUrl}/#co-visit-{id}` on the confirmation and the reminder only.

- [ ] **Step 1: Edit both files with targeted edits; run `mvn validate` at the root.**
- [ ] **Step 2: Commit** `docs: document the co-brokerage visit anchor` with `Refs #336`.
