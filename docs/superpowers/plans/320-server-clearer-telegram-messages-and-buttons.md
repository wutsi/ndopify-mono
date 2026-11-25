# Clearer Telegram Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the agent's Telegram messages in `ndopify-server` — compact prices, one property line, WhatsApp and detail buttons, one shared visit format — and stop sending the co-brokerage interest digest.

**Architecture:** Server only. `TenantFormatter` gains the web-core compact-number rule, the mandate and property labels move to Telegram-specific helpers (`MandateMailLabels.compactPrice`, a rewritten `ListingTelegramLabels`, a new `MandateTelegramLabels` and `VisitTelegramLabels`), the botlets listed in the issue are rewritten on top of them, and the digest classes, template and job call are deleted. No API, DTO, schema, UI or e-mail change.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, `platform/telegram`'s `TelegramClient`, JUnit 5 + `kotlin.test` + `mockito-kotlin`, Maven with ktlint and JaCoCo.

**Spec:** GitHub issue [#320](https://github.com/wutsi/ndopify-mono/issues/320) — the requester's text, the settled decisions, the fourteen verbatim messages, the scope (items 1–4, 5 = digest, 6 = docs), the acceptance criteria, the risky points and the assumptions. `UI: no`, `Shared code: none`: nothing under `ndopify-partner-central`'s `src/main/html` or production templates is touched. B's `[Voir la visite]` button ships as specified even though `/visits/{id}` still answers the locked view for B; the server access and page are a separate follow-up story (the issue's *Dependencies*).

## Before you start

Confirm the landing state (any unexpected result: stop and report):

```bash
cd modules/ndopify-server
git grep -n "fun compactNumber" -- src/main/kotlin/com/wutsi/ndopify/refdata/server/service || echo "compactNumber absent"
git grep -n "class CoBrokerageInterestBotlet" -- src/main/kotlin
git grep -n "notifyListingAgents" -- src/main/kotlin/com/wutsi/ndopify/match
git grep -c "titleAndPrice" -- src/main/kotlin/com/wutsi/ndopify
git status --porcelain
```

Expected: `compactNumber` absent, `CoBrokerageInterestBotlet` present, `notifyListingAgents` called from `MatchJobs`, at least eight `titleAndPrice` call sites, a clean tree. The plan lives on `main`; the implementation lands on a branch such as `feat/320-server-telegram-messages`, each commit saying `Refs #320`.

## Global Constraints

- **`ndopify-server` only.** No change under `modules/ndopify-partner-central`, `modules/ndopify-dto`, `modules/ndopify-web`, `modules/ndopify-esignature` or `modules/ndopify-web-core`; no migration, no configuration, no dependency.
- **Telegram only.** The e-mails (and the contracts) keep their wording and their full amounts: `MandateMailLabels.price`, `MatchAnsweredLabels`, `VisitFeedbackLabels` and `CoBrokerageVisitLabels` are shared with the mailets — their existing functions and constants do not change. New functions are added next to them; a Telegram-specific capitalisation or wording happens in the botlet, never in the shared object.
- **Compact amount, verbatim rules:** K with one decimal at most, M and G with two, half-up, trailing zeros dropped, the smallest unit whose rounded value reads under 1000 (999 950 → `1M`, 999 995 000 → `1G`), under 1 000 exact, 0 → `0`; the tenant's decimal separator (`,` in French) and **no currency**.
- **Price suffix:** `/{period}` for a recurring amount (`500K/mois`, `1,5M/an`), none for a sale (`25M`). One line for it in the messages: ` - {price}` (spaces around one hyphen, no `Prix :`/`Budget :`/`Vente —`/`Achat —` before a property title).
- **Property title:** a listing's `title` as written; blank, the fallback `<type> à louer|à vendre, <city> (<neighbourhood>)` — `Bien` without a type, the city alone without a neighbourhood, no dangling comma, parenthesis or hyphen.
- **WhatsApp:** every button labelled exactly `Contacter sur WhatsApp`, built only with `PhoneNumberUtils.toWhatsappUrl`; nothing when the party's number is blank. Order: after `Voir la visite` on visit messages, before `Voir le mandat` on mandate/proposal messages.
- **App link buttons:** `Voir le mandat` → `{partnerCentralUrl}/mandates/{id}`, the nudge's → `{partnerCentralUrl}/mandates/{id}#match-{matchId}`, the offer's `Voir détails` → `{partnerCentralUrl}/#assignment-{assignmentId}`, `Voir la visite` → `{partnerCentralUrl}/visits/{visitId}`. Without `partnerCentralUrl`, the URL button is omitted, never empty.
- **Images:** a botlet that already goes through `ListingTelegramSender` keeps doing so (the cover as caption); `MandateApprovedBotlet` and `ListingSentBackBotlet` keep `TelegramClient.sendMessage`, `MandateOfferBotlet` keeps `TelegramClient` (no photo) and uses `sendMessageRows` for its two rows. `ListingSentBackBotlet` and its message are unchanged.
- **Visit format, shared:** line 1 `<object> : <date/time>`, line 2 `<property line> - <Sur place|Vidéo>`, line 3 `Agent : <organizer>` on a co-brokerage visit only; then the event's own lines. `visit.agentId != listing.agent.id` means co-brokerage; the visit's agent is the organizer A, the listing's agent is B.
- **Tests and gates:** `kotlin.test` assertions, `mockito-kotlin` mocks; every botlet and label touched has its unit test updated/additions. From `modules/ndopify-server`: `mvn -s ../../settings.xml verify -Dheadless=true` (ktlint + tests + JaCoCo; MySQL from `AGENTS.md`). Each task ends green.

## Review Focus

1. **The compact boundaries** — 999 950 reads `1M`, never `1000K`; 999 995 000 reads `1G`; 1 500 000 reads `1,5M`; 500 000 reads `500K`; a non-French tenant reads `1.5M`. Task 1.
2. **A listing with no title, no type, no city** — `Bien à louer` (no comma), `Bien à louer, Yaoundé` (no parenthesis), `Appartement à louer, Yaoundé (Mballa II)`; no trailing ` - ` without a price. Task 2.
3. **A party with no WhatsApp number** — no button at all (not an empty `wa.me/`), while the other buttons stay. Tasks 4, 5 and the offer's missing `partnerCentralUrl`. Tests pin the exact list of buttons.
4. **The co-brokerage direction** — on A's visit messages the WhatsApp button opens B and line 3 names B; on B's messages it opens A and line 3 names A; A's customer (name, number, note, address, code, feedback) never appears in B's text. Tests set `listing.agent` explicitly, since a fixture without it (id `null`) would make every visit look co-brokerage. Tasks 5 and 6.
5. **An offer whose tenant has no Partner Central URL** — the accept/refuse row still ships (`sendMessageRows`), the `Voir détails` row is absent, and the text is unchanged. Task 3.

---

### Task 1: `TenantFormatter.compactNumber`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatter.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatterTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `fun compactNumber(amount: Long): String` on `TenantFormatter` (public), the tenant's locale decimal separator, no currency.

- [ ] **Step 1: Write the failing tests**

Add to `TenantFormatterTest` (the tenant is French, `fr-CM`):

```kotlin
@Test
fun `compact number - the smallest unit that reads under 1000`() {
    val formatter = TenantFormatter(tenant)

    assertEquals("0", formatter.compactNumber(0))
    assertEquals("999", formatter.compactNumber(999))
    assertEquals("500K", formatter.compactNumber(500_000))
    assertEquals("1,5M", formatter.compactNumber(1_500_000))
    assertEquals("25M", formatter.compactNumber(25_000_000))
    assertEquals("1M", formatter.compactNumber(999_950))
    assertEquals("1G", formatter.compactNumber(999_995_000))
    assertEquals("1,25G", formatter.compactNumber(1_250_000_000))
}

@Test
fun `compact number follows the tenant's decimal separator`() {
    assertEquals("1.5M", TenantFormatter(tenant.copy(locales = listOf("en-CM"))).compactNumber(1_500_000))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -s ../../settings.xml test -Dtest=TenantFormatterTest -Dheadless=true`
Expected: compilation error, `compactNumber` unresolved.

- [ ] **Step 3: Implement it**

Port `ndopify-web-core`'s private `compactNumber` verbatim (`modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt`): the private `CompactUnit` data class, `COMPACT_UNITS` (K 1 decimal, M and G 2), `THOUSAND`, exact `BigDecimal` arithmetic and `DecimalFormat("0." + "#".repeat(decimals), DecimalFormatSymbols.getInstance(locale))`. Make it `fun compactNumber(amount: Long): String`; keep the existing `number()` untouched (it is the e-mail path).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest=TenantFormatterTest -Dheadless=true`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatter.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/refdata/server/service/TenantFormatterTest.kt
git commit -m "feat(server): format an amount in short for Telegram" -m "Refs #320"
```

---

### Task 2: Compact price and the property line

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabelsTest.kt`
- Test updates (expected property line only, message frames unchanged until their own task): `MatchInterestBotletTest`, `MatchAnsweredBotletTest`, `MatchVisitAgentNudgeBotletTest`, `VisitRequestedBotletTest`, `VisitReminderBotletTest`, `VisitCancelledBotletTest`, `VisitFeedbackBotletTest`, `CoBrokerageVisitBotletTest`, `ListingPublishedBotletTest`

**Interfaces:**
- Consumes: `TenantFormatter.compactNumber` (Task 1).
- Produces:
  - `MandateMailLabels.compactPrice(amount: Long, periodicity: PeriodicityType?, formatter: TenantFormatter): String` (`500K/mois`, `25M`)
  - `MandateMailLabels.propertyType(type: PropertyType?): String?` (the existing map, public; `propertyType(mandate)` delegates to it)
  - `ListingTelegramLabels.title(listing: ListingEntity): String` (blank-title fallback)
  - `ListingTelegramLabels.price(listing: ListingEntity, formatter: TenantFormatter): String?`
  - `ListingTelegramLabels.titleAndPrice(listing: ListingEntity, formatter: TenantFormatter): String` = `"${title} - ${price}"` (no price: the title alone)

- [ ] **Step 1: Write the failing label tests**

Replace the expectations of `ListingTelegramLabelsTest` with (same fixtures: `Nice apartment`, neighbourhood 1001 = Bastos, 150 000 / mois):

```kotlin
@Test
fun `the property line is the title and the compact price`() {
    assertEquals("Nice apartment - 150K/mois", labels.titleAndPrice(listing, formatter))
}

@Test
fun `a sale reads its compact price without a currency or a label`() {
    val sale = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)
    assertEquals("Nice apartment - 150K", labels.titleAndPrice(sale, formatter))
}

@Test
fun `a blank title falls back to the type, the usage and the place`() {
    doReturn(LocationEntity(id = 1002L, name = "Yaounde")).whenever(locationService).findByIdOrNull(1002L)
    val titled = listing.copy(title = null, propertyType = PropertyType.APARTMENT, cityId = 1002L,
        listingType = ListingType.RENTAL)

    assertEquals(
        "Appartement à louer, Yaounde (Bastos) - 150K/mois",
        labels.titleAndPrice(titled, formatter),
    )
    assertEquals(
        "Bien à louer, Yaounde (Bastos) - 150K/mois",
        labels.titleAndPrice(titled.copy(propertyType = null), formatter),
    )
    assertEquals(
        "Appartement à louer, Yaounde - 150K/mois",
        labels.titleAndPrice(titled.copy(neighborhoodId = null), formatter),
    )
    assertEquals(
        "Appartement à louer - 150K/mois",
        labels.titleAndPrice(titled.copy(neighborhoodId = null, cityId = -1), formatter),
    )
}

@Test
fun `a sale's blank title says a vendre`() {
    val titled = listing.copy(title = null, propertyType = PropertyType.LAND, listingType = ListingType.SALE,
        pricePeriodicityType = PeriodicityType.NONE, cityId = 1002L)
    assertEquals("Terrain à vendre, Yaounde (Bastos) - 150K", labels.titleAndPrice(titled, formatter))
}
```

Keep the "no price" test (`price = -1` → no ` - `) and the blank-currency test (`currencyCode = " "` → no price); delete the old `Bien n° 101` fallback test, and the neighbourhood-in-parenthesis and `Prix :` assertions, which the new line contradicts.

- [ ] **Step 2: Run the label test to verify it fails**

Run: `mvn -s ../../settings.xml test -Dtest=ListingTelegramLabelsTest -Dheadless=true`
Expected: FAIL, `Nice apartment (Bastos) — 150 000 FCFA / mois` versus `Nice apartment - 150K/mois`.

- [ ] **Step 3: Implement the labels**

In `MandateMailLabels`, make `propertyType(type: PropertyType?): String?` the map lookup, have `propertyType(mandate)` call it, and add:

```kotlin
// Ex: "500K/mois", "25M": a Telegram line's amount, compact and without currency. The e-mails keep [price].
fun compactPrice(amount: Long, periodicity: PeriodicityType?, formatter: TenantFormatter): String =
    formatter.compactNumber(amount) + (PERIODICITIES[periodicity]?.let { "/$it" } ?: "")
```

In `ListingTelegramLabels`: `title` returns `listing.title?.ifBlank { null }` or `fallbackTitle` = `"${MandateMailLabels.propertyType(listing.propertyType) ?: "Bien"} ${if (listing.listingType == ListingType.SALE) "à vendre" else "à louer"}"`, then `, ${city}` when the city name is non-blank, then ` (${neighbourhood})` when known. `price` returns null when `price < 0` or `currencyCode` is blank, else `MandateMailLabels.compactPrice(listing.price, listing.pricePeriodicityType, formatter)`. `titleAndPrice` appends ` - $price` only when non-null. Remove the old neighbourhood and `Prix :` code and update the KDoc.

- [ ] **Step 4: Update the nine botlet tests' expected property lines**

The botlets' code is untouched in this task: only their expected strings change. With the fixtures already in the tests, the line becomes the title + ` - ` + compact price, e.g. `Nice apartment - 150K/mois` (add `, Bastos` only through a blank-title fallback, which no fixture uses); a sale fixture reads `… - 150K` or `… - 25M`, never `— Prix : …`. `ListingPublishedBotletTest`'s caption becomes `🏠 Votre annonce est publiée : Appartement à louer, 3 chambres - 150K/mois`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest='*Telegram*Test,*BotletTest' -Dheadless=true`
Expected: PASS (the digest tests are unaffected so far).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabelsTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt
git commit -m "feat(server): one compact property line for the Telegram messages" -m "Refs #320"
```

---

### Task 3: The mandate offer and the signed mandate

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateTelegramLabels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateApprovedNotifier.kt`
- Test: `MandateOfferBotletTest.kt`, `MandateApprovedBotletTest.kt`, `MandateApprovedNotifierTest.kt` (same packages), new `MandateTelegramLabelsTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramLabels` (unchanged), `MandateMailLabels.compactPrice` / `propertyType` / `neighbourhoods` / `financingType` / `titleDocuments`, `PhoneNumberUtils.toWhatsappUrl`.
- Produces:
  - `MandateTelegramLabels.line(mandate: MandateEntity, formatter: TenantFormatter): String` — criteria, `(neighbourhoods)`, ` - {compact price}`, no label
  - `MandateApprovedBotlet.send(mandate, customer, agent, chatId)` (the customer added)

- [ ] **Step 1: Write the failing tests**

`MandateTelegramLabelsTest` (mocks `LocationService`, `Yaounde`/`Bastos` as today): a rental search line is `Location - Appartement (Bastos) - 150K/mois`; a sale search of 250 000 000 is `Achat - Appartement (Bastos) - 250M`; no property type is `Location (Bastos) - 150K/mois`; no neighbourhood has no parenthesis.

`MandateOfferBotletTest` — replace the `verify(client).sendMessage` with `sendMessageRows` and the text with:

```kotlin
assertEquals(
    """
    📋 Nouveau mandat : Location - Appartement (Bastos) - 150K/mois
    Expire le 15/01/2027 à 09:00.
    """.trimIndent(),
    text.firstValue,
)
assertEquals(listOf(listOf("Accepter", "Refuser")), rows.firstValue.map { row -> row.map { it.text } })
assertEquals(listOf(listOf("accept:99", "decline:99")), rows.firstValue.map { row -> row.map { it.callbackData } })

// tenant with a partnerCentralUrl
assertEquals(
    listOf(
        listOf("Accepter", "Refuser"),
        listOf("Voir détails"),
    ),
    rows.firstValue.map { row -> row.map { it.text } },
)
assertEquals("https://partner.ndopify.com/#assignment-99", rows.firstValue[1][0].url)
```

The land-purchase text keeps its purchase answers on line 1: `📋 Nouveau mandat : Achat - Terrain (Bastos) - 10M · Financement : Comptant · Documents : Titre foncier · Plan de bornage`. The two sale tests drop their ` — Budget : …` / ` — Prix : …` assertions for ` - 250M`. Add a test that a listing mandate no longer has a `details` line (no `pièces`, `m²`, `disponible le`, `document de propriété` on a LISTING assignment).

`MandateApprovedBotletTest` — add `private val customer = CustomerEntity(id = 10L, party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"), whatsappNumber = "+237600000001")`, call `botlet.send(mandate, customer, agent, 1000L)`, and expect:

```kotlin
✅ Le client a signé le mandat.
Recherche : Location - Appartement (Bastos, Mvan) - 1,5M/mois
```
with buttons `[Contacter sur WhatsApp → https://wa.me/237600000001, Voir le mandat → …/mandates/1]`; the listing approval reads `Bien : Vente - Villa (Bastos) - 25M`; no WhatsApp number → only `Voir le mandat`.

`MandateApprovedNotifierTest`: the botlet is now called with the customer — `verify(botlet).send(mandate, customer, agent, 1000L)` (and the `doThrow(...).whenever(botlet).send(mandate, customer, agent, 1000L)`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -s ../../settings.xml test -Dtest='Mandate*BotletTest,MandateApprovedNotifierTest,MandateTelegramLabelsTest' -Dheadless=true`
Expected: FAIL (unresolved `MandateTelegramLabels`, wrong signatures, old texts).

- [ ] **Step 3: Implement the labels and the offer**

`MandateTelegramLabels` (`@Service`, `LocationService` injected) owns `line(...)`, private `criteria(...)` (the current "Location - Appartement" logic) and a null amount when `price < 0` or `currencyCode` is blank.

`MandateOfferBotlet`: inject `MandateTelegramLabels`, drop its private `criteria` and the `details` block (keep `purchase`), build `"📋 Nouveau mandat : " + labels.line(...) + purchase(...)`, then the expiry line. Send with `client.sendMessageRows(chatId, text, listOfNotNull(listOf(accept, decline), tenant.partnerCentralUrl?.removeSuffix("/")?.let { url -> listOf(TelegramButton("Voir détails", url = "$url/#assignment-${assignment.id}")) }))`.

- [ ] **Step 4: Implement the signed message**

`MandateApprovedBotlet`: inject `MandateTelegramLabels`; `send` takes the customer. Text is the fixed `"✅ Le client a signé le mandat."` then `"${if (LISTING) "Bien" else "Recherche"} : ${labels.line(mandate, formatter)}"`. Buttons `[Contacter sur WhatsApp]` (customer's number, `PhoneNumberUtils.toWhatsappUrl`, omitted when blank) then `[Voir le mandat]`. `MandateApprovedNotifier` passes its `customer` through.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest='Mandate*Test' -Dheadless=true`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate
git commit -m "feat(server): shorten the mandate Telegram messages" -m "Refs #320"
```

---

### Task 4: Interest, proposal answer and the 48 h nudge

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt`
- Test: the three `*BotletTest.kt` next to them

**Interfaces:**
- Consumes: `ListingTelegramLabels.titleAndPrice`, `MatchAnsweredLabels.rejectionReason`, `PhoneNumberUtils.toWhatsappUrl`.
- Produces: no signature change; the three texts and button lists are the story's messages 3, 4 and 5.

- [ ] **Step 1: Write the failing assertions**

`MatchInterestBotletTest` (`Ray Sponsible`, `Nice apartment`, 150 000 / mois):

```kotlin
assertEquals("👀 Ray Sponsible s'intéresse à : Nice apartment - 150K/mois", text.firstValue)
```

the sale test drops `Vente — ` and `Prix : `: `👀 Ray Sponsible s'intéresse à : Nice apartment - 150K`.

`MatchAnsweredBotletTest`: accepted → `✅ Ray Sponsible a accepté la proposition : Nice apartment - 150K/mois`; rejected → the same with `❌ … refusé …` and `Motif : Trop cher · « Hors budget »`. Buttons always `[Contacter sur WhatsApp, Voir le mandat]` when the customer has a number, the mandat alone otherwise. A sale line drops `Achat — ` / `Vente — `.

`MatchVisitAgentNudgeBotletTest`:

```kotlin
"""
⏰ Appelez Ray Sponsible pour planifier la visite
Nice apartment - 150K/mois
Proposition acceptée il y a plus de 48 h.
""".trimIndent()
```

buttons `[Contacter sur WhatsApp → https://wa.me/237600000001, Voir le mandat → https://partner.ndopify.com/mandates/5#match-1]`.

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -s ../../settings.xml test -Dtest='Match*BotletTest' -Dheadless=true`
Expected: FAIL on the old text/buttons.

- [ ] **Step 3: Implement**

- `MatchInterestBotlet`: remove `typeWord` and its imports; the text is `"👀 ${customer.party.fullName()} s'intéresse à : " + labels.titleAndPrice(listing, formatter)`. Buttons unchanged.
- `MatchAnsweredBotlet`: remove `typeWord`; add the customer's WhatsApp button ahead of `Voir le mandat`; capitalize the reason with `reason.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }` (Telegram only, the shared labels keep lowercase).
- `MatchVisitAgentNudgeBotlet`: new three-line text; `PhoneNumberUtils.toWhatsappUrl(customer.whatsappNumber)` instead of the hand-made `wa.me`; the second button becomes `TelegramButton("Voir le mandat", url = "$baseUrl/mandates/${match.mandate.id}#match-${match.id}")`.

- [ ] **Step 4: Run them to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest='Match*BotletTest' -Dheadless=true`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot
git commit -m "feat(server): clearer proposal and nudge Telegram messages" -m "Refs #320"
```

---

### Task 5: One visit format for the search agent's messages

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitTelegramLabels.kt`
- Modify: `.../visit/server/bot/VisitRequestedBotlet.kt`, `VisitReminderBotlet.kt`, `VisitCancelledBotlet.kt`, `VisitFeedbackBotlet.kt`
- Test: the four `*BotletTest.kt`, new `VisitTelegramLabelsTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramLabels.titleAndPrice`, `VisitFeedbackLabels.interest` / `reason`, `VisitEntity`, `TenantFormatter`, `Clock`.
- Produces:
  - `VisitTelegramLabels.body(label: String, whenLabel: String, listing: ListingEntity, visit: VisitEntity, organizer: AgentEntity?, formatter: TenantFormatter): String` — the 1/2/3 lines
  - `VisitTelegramLabels.property(visit, listing, formatter): String` (`… - Sur place|Vidéo`)
  - `VisitTelegramLabels.organizer(visit, listing): AgentEntity?` — `listing.agent` only when `visit.agentId != listing.agent.id`
  - `VisitTelegramLabels.whatsappNumber(visit, listing, customer): String?` — the customer's, or the listing agent's on a co-brokerage visit
  - `VisitTelegramLabels.slot(date: Date, formatter): String` (`26/11/2026 à 10:30`)
  - `VisitTelegramLabels.dayWord(visit, tenant, clock): String` (`aujourd'hui` / `demain`)
  - `VisitTelegramLabels.cancelledBy(party: VisitParty?): String` (`Annulée par le client` / `Annulée par l'agent` / `Annulée`)
  - `VisitTelegramLabels.typeLabel(type: VisitType): String` (`Sur place` / `Vidéo`)

- [ ] **Step 1: Write the failing tests**

`VisitTelegramLabelsTest`: a non-co-brokerage visit (listing whose `agent.id` equals `visit.agentId`) yields two lines and no `Agent :`; the same visit on another agent's listing yields a third `Agent : Clarisse Mbarga` and `whatsappNumber` returns Clarisse's; `typeLabel` reads `Vidéo` for a video visit; `cancelledBy` reads the three wordings.

`VisitRequestedBotletTest` (fixture: `Nice apartment`, 150 000 / mois, `+237600000001`, requested 01/12/2026 10:00, note `Je viens avec ma femme`; set `listing.copy(agent = agent)` so the visit is not co-brokerage):

```kotlin
"""
📅 Demande de visite : 01/12/2026 à 10:00
Nice apartment - 150K/mois - Sur place
Note : « Je viens avec ma femme »
""".trimIndent()
```

buttons `[Voir la visite → …/visits/1, Contacter sur WhatsApp → https://wa.me/237600000001]`. Add a co-brokerage case (listing agent `B`, visit's agent `A`) whose text has `Agent : B` and whose WhatsApp button is B's number, and a case where that number is blank → only `Voir la visite`.

`VisitReminderBotletTest`: `⏰ Rappel de visite : demain à 10:30` / `Nice apartment - 150K/mois - Vidéo` / (co-brokerage) `Agent : …`; no video-call paragraph any more.

`VisitCancelledBotletTest`: `❌ Visite annulée : 26/11/2026 à 10:30` / property line / `Agent : …` when co-brokerage / `Annulée par le client · Motif : Empêchement`.

`VisitFeedbackBotletTest`: `⭐ Avis de visite : 26/11/2026 à 10:30` / property line / `Agent : …` when co-brokerage / `Pas intéressé · Raison : Trop cher` / `Commentaire : « Trop bruyant »`; an interested customer reads `Intéressé` alone.

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -s ../../settings.xml test -Dtest='Visit*BotletTest,VisitTelegramLabelsTest' -Dheadless=true`
Expected: FAIL (old texts, unresolved `VisitTelegramLabels`).

- [ ] **Step 3: Implement `VisitTelegramLabels`**

`body` joins the three `listOfNotNull` lines; `property` is `"${labels.titleAndPrice(listing, formatter)} - ${typeLabel(visit.type)}"`; `organizer` returns `listing.agent.takeIf { visit.agentId != it.id }`; `whatsappNumber` returns the organizer's number when co-brokerage, else the customer's, after `trim().ifBlank { null }`; `dayWord` mirrors `VisitReminderBotlet`'s current private helper (tenant zone, `Clock`).

- [ ] **Step 4: Rewrite the four botlets**

Each one keeps its `ListingTelegramSender` call and its button order `[Voir la visite, Contacter sur WhatsApp]`, with `PhoneNumberUtils.toWhatsappUrl` and no button when the number is blank. Replace the old first lines and the `typeLabel` / `dayWord` / `VisitFeedbackLabels` private helpers with the shared ones; the feedback reason is capitalised in the botlet; `Annulée par … · Motif : …` replaces `<customer> a annulé la visite`; the cancelled line uses `visit.scheduledAt ?: visit.requestedAt` so a request cancelled before confirmation still shows its slot.

- [ ] **Step 5: Run them to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest='Visit*BotletTest,VisitTelegramLabelsTest' -Dheadless=true`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot
git commit -m "feat(server): one format for the visit Telegram messages" -m "Refs #320"
```

---

### Task 6: The listing agent's co-brokerage visits

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt`

**Interfaces:**
- Consumes: `VisitTelegramLabels` (`body`, `slot`, `dayWord`, `cancelledBy`, `typeLabel`), `PhoneNumberUtils.toWhatsappUrl`, `Clock`.
- Produces: `CoBrokerageVisitBotlet(tenantService, labels: ListingTelegramLabels, visitLabels: VisitTelegramLabels, sender, clock)`; the four `sendX` signatures are unchanged; the `agent` parameter is A, `listing.agent` is B.

- [ ] **Step 1: Write the failing tests**

Fixture: listing `Nice apartment` 150 000 / mois whose `agent` is B (Clarisse Mbarga, `+237699123456`), organizing `agent` A (Ray Sponsible), visit 26/11/2026 10:30, ON_SITE:

```kotlin
"""
✅ Visite confirmée : 26/11/2026 à 10:30
Nice apartment - 150K/mois - Sur place
Agent : Ray Sponsible
""".trimIndent()
```

buttons `[Voir la visite → …/visits/1, Contacter sur WhatsApp → https://wa.me/237699123456]`. Reminder: `⏰ Rappel de visite : demain à 10:30` / `… - Vidéo` / `Agent : Ray Sponsible`; cancelled: `❌ Visite annulée : …` … `Annulée par l'agent`; no-show: `⚠️ Visite non honorée : …` … `Le client ne s'est pas présenté`. Assert that no text contains a customer name, note, address or code.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -s ../../settings.xml test -Dtest=CoBrokerageVisitBotletTest -Dheadless=true`
Expected: FAIL on the old `Voir l'annonce` text/button.

- [ ] **Step 3: Implement**

Replace the private `send`/`onSite` with `VisitTelegramLabels.body(...)` (label, slot, listing, visit, organizer = A, formatter) plus the event's extra line (`cancelledBy` + ` · Motif`, or the Telegram no-show wording). Add `Clock` and use `dayWord` for the reminder. Buttons: `[Voir la visite]` → `{partnerCentralUrl}/visits/{id}`, then `[Contacter sur WhatsApp]` → A's number, both omitted without the URL / number. Keep `ListingTelegramSender` and the cover.

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -s ../../settings.xml test -Dtest=CoBrokerageVisitBotletTest -Dheadless=true`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt
git commit -m "feat(server): clearer co-brokerage visit Telegram messages" -m "Refs #320"
```

---

### Task 7: Drop the co-brokerage interest digest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `.../match/server/service/CoBrokerageNotificationService.kt`
- Modify: `.../match/server/dao/MatchRepository.kt`
- Modify: `.../match/server/domain/MatchEntity.kt`
- Delete: `.../match/server/bot/CoBrokerageInterestBotlet.kt`, `.../match/server/mail/CoBrokerageInterestMailet.kt`, `.../match/server/service/CoBrokerageInterestDigest.kt`, `.../match/server/service/CoBrokerageInterestDigestBuilder.kt`, `.../match/server/service/CoBrokerageInterestNotifier.kt`, `modules/ndopify-server/src/main/resources/mail/match/co_brokerage_interest.mjml`
- Delete tests: `CoBrokerageInterestBotletTest.kt`, `CoBrokerageInterestMailetTest.kt`, `CoBrokerageInterestDigestBuilderTest.kt`, `CoBrokerageInterestNotifierTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `CoBrokerageNotificationService.notifySearchAgents()` only; `MatchJobs.notifyAgents()` sums `MatchNotificationService.notifyAgents()` and `notifySearchAgents()`.

- [ ] **Step 1: Write the failing test change**

In `NotifyCoBrokerageJobTest`, remove the `CoBrokerageInterestNotifier` mock, its import, the two interest tests (`notifies the listing agent when a search agent is interested`, `a closed search or a rented listing is not notified`) and the `listingAgentNotifiedAt` helper. In `nothing is sent on the next run`, reset only `ownMailet`/`coBrokerageMailet` and assert `never()` on those two. Add:

```kotlin
@Test
fun `a co-brokerage interest sends no e-mail nor Telegram message`() {
    jobs.notifyAgents()

    // rows 4 and 5 are the listing agent's interest fixtures: nothing marks them, nothing is sent for them
    assertNull(listingAgentNotifiedAtOrNull(4L))
    assertNull(listingAgentNotifiedAtOrNull(5L))
}
```

with a private helper reading `listing_agent_notified_at` through `jdbc` (the column stays in the database; only the mapping goes).

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -s ../../settings.xml test -Dtest=NotifyCoBrokerageJobTest -Dheadless=true`
Expected: FAIL (the current job still marks the listing agent / the mock is still injected).

- [ ] **Step 3: Delete the digest path**

Remove from `MatchJobs.notifyAgents` the `+ coBrokerageNotificationService.notifyListingAgents()` term; remove `notifyListingAgents`, `INTEREST_STATUSES`, the `interestNotifier` / `digestBuilder` constructor dependencies and their KDoc from `CoBrokerageNotificationService`; remove `findCoBrokerageInterestToNotify` and `markListingAgentNotified` from `MatchRepository`; remove the `listingAgentNotifiedAt` property (and its comment) from `MatchEntity` — **no migration**, the `listing_agent_notified_at` column stays in `T_MATCH`. Delete the five main files, the MJML template and the four tests. Leave `T_MATCH` test SQL as is.

- [ ] **Step 4: Run the match tests to verify they pass**

Run: `mvn -s ../../settings.xml test -Dtest='NotifyCoBrokerageJobTest,NotifyMatchesJobTest,RematchJobTest' -Dheadless=true`
Expected: PASS; `grep -rn "CoBrokerageInterest\|listingAgentNotifiedAt\|listing_agent_notified" src/main` prints only the migration and test SQL.

- [ ] **Step 5: Commit**

```bash
git rm modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt \
       modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt \
       modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt \
       modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilder.kt \
       modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifier.kt \
       modules/ndopify-server/src/main/resources/mail/match/co_brokerage_interest.mjml \
       modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt \
       modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt \
       modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilderTest.kt \
       modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestNotifierTest.kt
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageNotificationService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/NotifyCoBrokerageJobTest.kt
git commit -m "feat(server): drop the co-brokerage interest digest" -m "Refs #320"
```

---

### Task 8: Documentation

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (*Telegram*, *match*, *visit*)
- Modify: the KDoc of every botlet / label class touched (`ListingTelegramLabels`, `MandateTelegramLabels`, `VisitTelegramLabels`, the eleven botlets)

**Interfaces:**
- Consumes: the code of Tasks 1–7.
- Produces: prose only.

- [ ] **Step 1: Update `CLAUDE.md`**

In the *Telegram* paragraph and the match/visit domains:
- amounts in the agent's messages are compact and without currency (`500K/mois`, `25M`), the e-mails keep `TenantFormatter.money`;
- one property line `{title} - {price}`, no neighbourhood, no `Prix :`/`Budget :`, the blank-title fallback;
- the offer/answer buttons, `Contacter sur WhatsApp` on every customer or co-agent message, `Voir le mandat`, `Voir la visite`;
- the visit format's three lines and the co-brokerage rule (`Agent :`, A↔B WhatsApp);
- the co-brokerage interest digest is gone: `MatchJobs.notifyAgents` no longer tells listing agents, `notifySearchAgents` stays;
- B's `Voir la visite` opens `/visits/{id}`, still locked for B until the follow-up story (one sentence, no promise).

- [ ] **Step 2: Update the KDoc**

Each botlet's class doc states its final format (object, lines, buttons) and what it never carries (the customer's code, the exact address, the customer on B's side); `ListingTelegramLabels` documents the fallback and the compact price; `MandateTelegramLabels` and `VisitTelegramLabels` document their one-line contract. No comment restating a line of code.

- [ ] **Step 3: Run the whole module**

Run: `mvn -s ../../settings.xml verify -Dheadless=true` (MySQL running)
Expected: BUILD SUCCESS — ktlint, every test, the coverage gate.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-server/src/main/kotlin
git commit -m "docs(server): document the agent Telegram messages" -m "Refs #320"
```
