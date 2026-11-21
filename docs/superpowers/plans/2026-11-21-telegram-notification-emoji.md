# Telegram Notification Emoji And Property Line Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every agent Telegram notification opens with an emoji and one property line (`{title} ({neighbourhood}) — {price}`), and drops the blank blocks and closing sentences that only repeat its button.

**Architecture:** A shared `ListingTelegramLabels` service renders a listing's title and property line. Each `*Botlet` keeps building its own list of French lines, but its first line becomes the event emoji plus that property line; the mandate botlets render the mandate's criteria instead. The co-brokerage digest carries the listing's neighbourhood separately so its header can use the same property line. The e-mails, notifiers, `TelegramClient`, buttons and `ndopify-dto` / `ndopify-web-core` stay untouched.

**Tech Stack:** Kotlin, Spring Boot, `kotlin.test`, `mockito-kotlin` (`com.nhaarman.mockitokotlin2`), `TenantFormatter`, `MandateMailLabels`. No new dependency.

**Spec:** GitHub issue #227 — https://github.com/wutsi/ndopify-mono/issues/227. The design lives in the story; there is no spec file.

## Global Constraints

- Only `modules/ndopify-server`. No `ndopify-dto` / `ndopify-web-core` change, no root `pom.xml` change, no Flyway migration, no `src/main/html/` change, no Partner Central change.
- Text is French; code, comments, KDoc and commits are English.
- `TelegramClient` sends plain text: no `parse_mode`, no markdown, no HTML. Emoji are plain characters and count toward the 1024-codepoint caption / 4096-character message limits.
- A listing's neighbourhood is omitted when unknown (no empty `()`); its price is omitted when `price < 0` or `currencyCode` is blank (no trailing ` — `). `TenantFormatter` and `MandateMailLabels.price` format every amount; never a hard-coded pattern or `FCFA`.
- A mandate's property line uses its criteria as the title and its budget as the price. `MandateMailLabels` keeps formatting the criteria, neighbourhoods and amounts.
- The e-mails (`*Mailet`), their MJML templates and subjects, and the shared labels (`MandateMailLabels`, `MatchAnsweredLabels`, `CoBrokerageVisitLabels`, `VisitFeedbackLabels`) are unchanged.
- Buttons, callbacks, keyboard layout and the Telegram `/start` / `/connect` replies are out of scope.
- `kotlin.test` assertions only (`assertEquals`, `assertTrue`, `assertFalse`), never `org.junit.jupiter.api.Assertions.*`.
- Conventional commits with `Refs #227` in the body. Each commit touches one concern and only `ndopify-server`.
- Run Maven from `modules/ndopify-server`. Unit tests need no MySQL; endpoint / job tests and the final `mvn verify` need local MySQL (`root`, no password).
- Never log the Telegram token nor its URL (not touched here; just don't add logging).

## File Structure

- Create `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt` — the shared title / property-line renderer.
- Modify the eleven `*Botlet.kt` files under `listing/server/bot/`, `match/server/bot/`, `mandate/server/bot/` and `visit/server/bot/` — first line, body blocks and closing sentences.
- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt` and `CoBrokerageInterestDigestBuilder.kt` — carry the listing's neighbourhood separately.
- Update the matching `*BotletTest.kt` files, `CoBrokerageInterestDigestBuilderTest.kt`, and the endpoint / job tests that assert the changed text.

## Review Focus

- A listing with no price (`price = -1`) or a blank `currencyCode` → the property line ends at the title / neighbourhood, with no ` — `. Test: Task 1.
- A listing with no resolvable neighbourhood → no empty `()`. Test: Task 1.
- A mandate with no neighbourhoods, or an unknown city → no empty `()` and no dangling ` à `. Tests: Tasks 6 and 7.
- Optional facts (note, reason, comment, message) → no dangling ` · `, no empty line, and no `Motif` / `Commentaire` line when absent. Tests: Tasks 3, 8, 9, 11 and 12.
- A digest split over 4096 characters → the lead starts with `👥`, every continuation with `🏠`, no part over 4096, and each part keeps only its own listings' buttons. Test: Task 13.
- The publication's photo caption and its text fallback carry the same line: the botlet builds one string, `ListingTelegramSender` uses it for both. Test: Task 2.

---

### Task 1: Shared listing label helper

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabelsTest.kt`

**Interfaces:**
- Consumes: `ListingEntity.title`, `.id`, `.neighborhoodId`, `.price`, `.currencyCode`, `.pricePeriodicityType`; `LocationService.findByIdOrNull(id)`; `TenantFormatter`; `MandateMailLabels.price(amount, currencyCode, periodicity, formatter)`.
- Produces: `@Service class ListingTelegramLabels(private val locationService: LocationService)` with
  `fun title(listing: ListingEntity): String` and
  `fun titleAndPrice(listing: ListingEntity, formatter: TenantFormatter): String`.

- [ ] **Step 1: Write the failing `ListingTelegramLabelsTest`**

Test names and the values they pin:

```kotlin
class ListingTelegramLabelsTest {
    private val locationService = mock<LocationService>()
    private val labels = ListingTelegramLabels(locationService)
    private val formatter = TenantFormatter(
        TenantEntity(currencyCode = "XAF", monetaryFormat = "#,###,##0 FCFA"),
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        neighborhoodId = 1001L,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )

    @BeforeTest
    fun setUp() {
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    // title: the title, its blank / null fallback, and the id fallback
    // titleAndPrice: "Nice apartment (Bastos) — 150 000 FCFA / mois"
    // no neighbourhood: "Nice apartment — 150 000 FCFA / mois", assertFalse(contains("()"))
    // no price: listing.copy(price = -1) -> "Nice apartment (Bastos)", assertFalse(contains(" — "))
    // blank currency: listing.copy(currencyCode = " ") -> no " — "
    // blank title: listing.copy(title = " ") -> "Bien n° 101 (Bastos) — 150 000 FCFA / mois"
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingTelegramLabelsTest`
Expected: FAIL — `ListingTelegramLabels` is unresolved.

- [ ] **Step 3: Implement `ListingTelegramLabels`**

`title` returns `listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"`.
`titleAndPrice` appends ` (${name})` when `locationService.findByIdOrNull(listing.neighborhoodId)?.name?.ifBlank { null }` is non-null,
then ` — ${MandateMailLabels.price(listing.price, listing.currencyCode, listing.pricePeriodicityType, formatter)}`
only when `listing.price >= 0 && listing.currencyCode.isNotBlank()`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingTelegramLabelsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabels.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramLabelsTest.kt
git commit -m "feat(listing): add the Telegram listing label helper

Refs #227"
```

---

### Task 2: Listing published

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: no new public API. `ListingPublishedBotlet` now takes `(tenantService: TenantService, labels: ListingTelegramLabels, sender: ListingTelegramSender)`; `locationService` leaves its constructor.

- [ ] **Step 1: Write the failing `ListingPublishedBotletTest` expectations**

Replace the fixture and the expected caption:

```kotlin
private val listing = ListingEntity(
    id = 1L,
    tenantId = 1L,
    cityId = 2370201L,
    neighborhoodId = 111L,
    title = "Appartement à louer, 3 chambres",
    price = 150000,
    currencyCode = "XAF",
    pricePeriodicityType = PeriodicityType.MONTHLY,
    url = "/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
)
private val caption = "🏠 Votre annonce est publiée : " +
    "Appartement à louer, 3 chambres (Bastos) — 150 000 FCFA / mois"
```

Keep the button test. Replace the city / blank-title / neighbourhood tests with:

- blank title → `🏠 Votre annonce est publiée : Bien n° 1 (Bastos) — 150 000 FCFA / mois`;
- no neighbourhood → no `(` in the text;
- no price (`price = -1`) → no ` — ` in the text.

The tenant fixture gains `currencyCode = "XAF"` and `monetaryFormat = "#,###,##0 FCFA"`.

Update `TelegramPublishListingsJobTest`'s assertions to:

```kotlin
assertTrue(caption.firstValue.startsWith("🏠 Votre annonce est publiée :"))
assertTrue(caption.firstValue.contains("Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
assertFalse(caption.firstValue.contains("Annonce:"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest,TelegramPublishListingsJobTest`
Expected: FAIL — the old greeting / `Annonce:` caption.

- [ ] **Step 3: Implement the one-line caption**

In `send`, load the tenant once, build `TenantFormatter(tenant)`, and set the caption to:

```kotlin
"🏠 Votre annonce est publiée : " + labels.titleAndPrice(listing, TenantFormatter(tenant))
```

Drop the city lookup, the owner sentence, the `Annonce:` / `Ville:` / `Quartier:` lines and the next-step sentence. Keep the `Voir l'annonce` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest,TelegramPublishListingsJobTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt
git commit -m "feat(listing): shorten the publication Telegram message

Refs #227"
```

---

### Task 3: Match answered

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramMatchAnsweredEndpointTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: `MatchAnsweredBotlet(tenantService, labels, sender)`; `locationService` leaves its constructor. The first line is the only line for an accepted proposal.

- [ ] **Step 1: Write the failing expected texts**

In `MatchAnsweredBotletTest`, add `neighborhoodId = 1001L` to the `listing` fixture, then assert:

```kotlin
assertEquals(
    "✅ Ray Sponsible a accepté la proposition : " +
        "Small apartment (Bastos) — 140 000 FCFA / mois",
    text(),
)
```

For a rejection assert:

```kotlin
assertEquals(
    """
    ❌ Ray Sponsible a refusé la proposition : Small apartment (Bastos) — 140 000 FCFA / mois
    Motif : trop cher · « Trop loin du travail. »
    """.trimIndent(),
    text(),
)
```

Keep a no-comment test asserting `Motif : autre raison` and no `Commentaire`. Replace the blank-title test with `Bien n° 102`. In `TelegramMatchAnsweredEndpointTest`, replace the old `Bien:` / `Loyer:` / `Recherche:` / `Ville:` / `Quartier(s):` / `Budget:` assertions with:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "✅ Ray Sponsible a accepté la proposition : Small apartment (Bastos) — 140 000 FCFA / mois",
    ),
)
assertTrue(text.firstValue.contains("Motif : trop cher"))
assertTrue(text.firstValue.contains("« Trop loin du travail. »"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest,TelegramMatchAnsweredEndpointTest`
Expected: FAIL — the old first line and search block.

- [ ] **Step 3: Implement the first line and the optional rejection line**

Build:

```kotlin
val accepted = match.status == MatchStatus.ACCEPTED
val emoji = if (accepted) "✅" else "❌"
val verb = if (accepted) "accepté" else "refusé"
val first = "$emoji ${customer.party.fullName()} a $verb la proposition : " +
    labels.titleAndPrice(match.listing, formatter)
val reason = MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode)
val comment = match.rejectionReason?.ifBlank { null }
```

Add one second line when present: both → `Motif : $reason · « $comment »`; reason only → `Motif : $reason`; comment only → `Commentaire : « $comment »`. Drop the search block, the city, and `Cette réponse est définitive.` Keep the `Voir le mandat` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAnsweredBotletTest,TelegramMatchAnsweredEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramMatchAnsweredEndpointTest.kt
git commit -m "feat(match): shorten the answered-proposal Telegram message

Refs #227"
```

---

### Task 4: Match interest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: `MatchInterestBotlet(tenantService, labels, sender)`.

- [ ] **Step 1: Write the failing expected text**

Give the `listing` fixture `neighborhoodId = 1001L`, `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`, give the tenant `currencyCode = "XAF"` and `monetaryFormat = "#,###,##0 FCFA"`, mock `locationService.findByIdOrNull(1001L)`, and assert:

```kotlin
assertEquals(
    "👀 Ray Sponsible s'intéresse à : Nice apartment (Bastos) — 150 000 FCFA / mois",
    text.firstValue,
)
```

Blank title → `👀 Ray Sponsible s'intéresse à : Bien n° 101 (Bastos) — 150 000 FCFA / mois`. Keep the WhatsApp / mandate button tests.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest`
Expected: FAIL — the old body and closing sentence.

- [ ] **Step 3: Implement the one-line message**

`text` returns `"👀 ${customer.party.fullName()} s'intéresse à : " + labels.titleAndPrice(listing, formatter)`. Drop the `Bien:` line and `Contactez-le pour organiser la visite.` Keep both buttons.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt
git commit -m "feat(match): shorten the interest Telegram message

Refs #227"
```

---

### Task 5: Match visit nudge

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotletTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: `MatchVisitAgentNudgeBotlet(tenantService, labels, sender)`.

- [ ] **Step 1: Write the failing expected text**

Give the listing fixture `neighborhoodId = 1001L`, `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`; mock location 1001. Assert:

```kotlin
assertEquals(
    """
    ⏰ Ray Sponsible attend une visite : Nice apartment (Bastos) — 150 000 FCFA / mois
    Acceptée il y a plus de 48 h. Appelez-le, puis planifiez la visite.
    """.trimIndent(),
    text(),
)
```

Blank title → `⏰ Ray Sponsible attend une visite : Bien n° 501 (Bastos) — 150 000 FCFA / mois`. Keep the WhatsApp / visit button tests.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchVisitAgentNudgeBotletTest`
Expected: FAIL — the old long sentence and `— Ndopify`.

- [ ] **Step 3: Implement the two lines**

First line `"⏰ ${customer.party.fullName()} attend une visite : " + labels.titleAndPrice(listing, formatter)`. Second line exactly `Acceptée il y a plus de 48 h. Appelez-le, puis planifiez la visite.` Drop the repeated name / title sentence and `— Ndopify`. Keep both buttons.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchVisitAgentNudgeBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotletTest.kt
git commit -m "feat(match): shorten the visit nudge Telegram message

Refs #227"
```

---

### Task 6: Mandate approved

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramApprovedMandateEndpointTest.kt`

**Interfaces:**
- Consumes: `MandateMailLabels.listingType`, `.propertyType`, `.neighbourhoods`, `.budget`; `TenantFormatter`.
- Produces: no new public API. `MandateApprovedBotlet` keeps `(tenantService, locationService, client)`; it no longer reads the city.

- [ ] **Step 1: Write the failing expected texts**

In `MandateApprovedBotletTest`, assert the search approval exactly:

```kotlin
assertEquals(
    """
    ✅ Le client a signé le mandat. Il va commencer à recevoir les offres.
    Recherche : Location - Appartement (Bastos, Mvan) — 1 500 000 XAF / mois
    """.trimIndent(),
    text.firstValue,
)
```

The listing approval exactly:

```kotlin
assertEquals(
    """
    ✅ Le client a signé le mandat. Il va commencer à recevoir les offres.
    Bien : Vente - Villa (Bastos) — 25 000 000 XAF
    """.trimIndent(),
    text.firstValue,
)
```

The no-neighbourhood search → `Recherche : Location — 1 500 000 XAF / mois`. Remove the old `Votre mandat à Yaounde est approuvé` / `Ville:` / `Quartier(s):` / `Budget:` assertions. In `TelegramApprovedMandateEndpointTest`, replace them with:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "✅ Le client a signé le mandat. Il va commencer à recevoir les offres.",
    ),
)
assertTrue(text.firstValue.contains("Recherche : Location — 150 000 FCFA / mois"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest,TelegramApprovedMandateEndpointTest`
Expected: FAIL — the old greeting and city lines.

- [ ] **Step 3: Implement the two lines**

First line exactly `✅ Le client a signé le mandat. Il va commencer à recevoir les offres.`
Second line: `${if (LISTING) "Bien" else "Recherche"} : $criteria` + ` ($neighbourhoods)` when non-null + ` — ${MandateMailLabels.budget(mandate, formatter)}`.
Drop `Votre mandat … est approuvé`, `Votre client a signé le mandat. Vous pouvez commencer.`, the city, the separate `Quartier(s):` / `Budget:` / `Prix:` lines. Keep `criteria` and the `Voir le mandat` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateApprovedBotletTest,TelegramApprovedMandateEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateApprovedBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramApprovedMandateEndpointTest.kt
git commit -m "feat(mandate): shorten the approval Telegram message

Refs #227"
```

---

### Task 7: Mandate offer

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt`

**Interfaces:**
- Consumes: `MandateMailLabels.listingType`, `.propertyType`, `.neighbourhoods`, `.budget`; `TenantFormatter`.
- Produces: no new public API. `MandateOfferBotlet` keeps `(tenantService, locationService, client)`.

- [ ] **Step 1: Write the failing expected texts**

For the listing offer (add `rooms = "4"` to the fixture), assert exactly:

```kotlin
assertEquals(
    """
    📋 Nouveau mandat : Vente - Villa (Bastos) — 25 000 000 XAF
    4 pièces · 250 m² · disponible le 01/12/2026
    Expire le ${formatter.date(assignment.expiresAt)} à ${formatter.time(assignment.expiresAt)}.
    """.trimIndent(),
    text.firstValue,
)
```

For the search offer, assert:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "📋 Nouveau mandat : Location - Appartement (Bastos) — 150 000 XAF / mois",
    ),
)
```

Keep the `Accepter` / `Refuser` button assertions. Replace the no-city test with: starts with `📋 Nouveau mandat : Location`, no ` à `, no `()`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest`
Expected: FAIL — the old headline, exclusivity sentence and deadline paragraph.

- [ ] **Step 3: Implement the new body**

First line `📋 Nouveau mandat : $criteria` + ` ($neighbourhoods)` when non-null + ` — ${MandateMailLabels.budget(mandate, formatter)}`.
For a LISTING only, add one details line joining the present facts with ` · `: `$rooms pièce(s)`, `$area m²`, `disponible le ${formatter.date(availableFrom)}`.
Close with `Expire le ${formatter.date(expiresAt)} à ${formatter.time(expiresAt)}.`
Drop the exclusivity sentence, the city, the separate `Pièces:` / `Surface:` / `Disponible à partir du:` lines and the long deadline sentence. Keep the two buttons.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt
git commit -m "feat(mandate): shorten the offer Telegram message

Refs #227"
```

---

### Task 8: Visit requested

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitRequestedEndpointTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: `VisitRequestedBotlet(tenantService, labels, sender)`.

- [ ] **Step 1: Write the failing expected text**

Give the listing fixture `neighborhoodId = 1001L`, `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`; mock location 1001. Assert:

```kotlin
assertEquals(
    """
    📅 Ray Sponsible demande une visite : Nice apartment (Bastos) — 150 000 FCFA / mois
    01/12/2026 à 10:00 · Sur place
    Note : Je viens avec ma femme
    """.trimIndent(),
    text.firstValue,
)
```

Video → second line `… · Visite vidéo`. Blank title → `Bien n° 101`. No note → no `Note :` line. In `TelegramVisitRequestedEndpointTest`, assert:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "📅 Ray Sponsible demande une visite : Nice apartment (Bastos) — 150 000 FCFA / mois",
    ),
)
assertTrue(text.firstValue.contains(" · Sur place"))
assertTrue(text.firstValue.contains("Note : Je viens avec ma femme"))
```

The video test asserts `contains(" · Visite vidéo")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest,TelegramVisitRequestedEndpointTest`
Expected: FAIL — the old `Bien:` / `Type:` labelled lines and closing sentence.

- [ ] **Step 3: Implement the new body**

First line `"📅 ${customer.party.fullName()} demande une visite : " + labels.titleAndPrice(listing, formatter)`.
Second line `"${formatter.date(requestedAt)} à ${formatter.time(requestedAt)} · ${typeLabel(type)}"`.
Optional `Note : $note`.
Drop `Date souhaitée:`, `Type:`, the `Bien:` line and `Confirmez la visite…`. Keep both buttons.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest,TelegramVisitRequestedEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitRequestedEndpointTest.kt
git commit -m "feat(visit): shorten the requested-visit Telegram message

Refs #227"
```

---

### Task 9: Visit cancelled

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitCancelledEndpointTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`.
- Produces: `VisitCancelledBotlet(tenantService, labels, sender)`; `locationService` leaves its constructor.

- [ ] **Step 1: Write the failing expected text**

Give the listing fixture `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`. Assert:

```kotlin
assertEquals(
    """
    ❌ Ray Sponsible a annulé la visite : Nice apartment (Bastos) — 150 000 FCFA / mois
    02/12/2026 à 10:00 · Motif : Empêchement
    """.trimIndent(),
    text.firstValue,
)
```

No reason → second line is only `02/12/2026 à 10:00`; no neighbourhood → no `(`; blank title → `Bien n° 101`; null `scheduledAt` with a reason → only `Motif : …`. In `TelegramVisitCancelledEndpointTest`, assert:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "❌ Ray Sponsible a annulé la visite : Small apartment (Bastos) — 140 000 FCFA / mois",
    ),
)
assertFalse(text.firstValue.contains("Motif:"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest,TelegramVisitCancelledEndpointTest`
Expected: FAIL — the old `Le client a annulé la visite` / `Bien:` / `Ville:` / `Client:` lines.

- [ ] **Step 3: Implement the new body**

First line `"❌ ${customer.party.fullName()} a annulé la visite : " + labels.titleAndPrice(listing, formatter)`.
Second line: join with ` · ` the present `slot` (`date à time`) and `Motif : $reason`. If neither, no second line.
Drop `Bien:`, `Ville:`, `Quartier:`, `Client:` and `Date:`. Keep the `Voir la visite` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitCancelledBotletTest,TelegramVisitCancelledEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitCancelledEndpointTest.kt
git commit -m "feat(visit): shorten the cancelled-visit Telegram message

Refs #227"
```

---

### Task 10: Visit reminder

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`; the existing `Clock` and `dayWord`.
- Produces: `VisitReminderBotlet(tenantService, labels, sender, clock)`; `locationService` leaves its constructor.

- [ ] **Step 1: Write the failing expected texts**

Give the listing fixture `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`. Assert the on-site reminder exactly:

```kotlin
assertEquals(
    """
    ⏰ Rappel : Nice apartment (Bastos) — 150 000 FCFA / mois, demain à 10:00 avec Ray Sponsible
    Sur place
    """.trimIndent(),
    text.firstValue,
)
```

Video → `Visite vidéo` then the existing WhatsApp-call paragraph. Later today → contains `, aujourd'hui à 20:00 avec Ray Sponsible`; the tenant-calendar test → `, aujourd'hui à 08:00 avec Ray Sponsible`. Blank title → contains `⏰ Rappel : Bien n° 101 (Bastos)`. Keep the button tests and the no-code / no-address test. In `TelegramVisitReminderJobTest`, assert:

```kotlin
assertTrue(text.firstValue.startsWith("⏰ Rappel : Nice apartment (Bastos) — 150 000 FCFA / mois, demain à 10:00"))
assertTrue(text.firstValue.contains("avec Ray Sponsible"))
assertTrue(text.firstValue.endsWith("\nSur place"))
assertFalse(text.firstValue.contains("Lieu:"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest,TelegramVisitReminderJobTest`
Expected: FAIL — the old `Rappel de la visite avec …` / `La visite du bien …` / `Type:` / `Lieu:` lines.

- [ ] **Step 3: Implement the new body**

First line `"⏰ Rappel : ${labels.titleAndPrice(listing, formatter)}, ${dayWord(visit, tenant)} à ${formatter.time(scheduledAt)} avec ${customer.party.fullName()}"`.
Second line the type label (`Sur place` / `Visite vidéo`). For a VIDEO visit, add the existing WhatsApp-call paragraph.
Drop `Rappel de la visite avec …`, `La visite du bien … a lieu …`, `Type:`, `Lieu:` and the `placeOf` helper. Keep both buttons.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitReminderBotletTest,TelegramVisitReminderJobTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitReminderJobTest.kt
git commit -m "feat(visit): shorten the reminder Telegram message

Refs #227"
```

---

### Task 11: Visit feedback

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitFeedbackEndpointTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`; `VisitFeedbackLabels`.
- Produces: `VisitFeedbackBotlet(tenantService, labels, sender)`.

- [ ] **Step 1: Write the failing expected text**

Give the listing fixture `neighborhoodId = 1001L`, `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`; mock location 1001. Assert the not-interested answer exactly:

```kotlin
assertEquals(
    """
    ⭐ Avis de Ray Sponsible : Nice apartment (Bastos) — 150 000 FCFA / mois
    Pas intéressé
    Raison : trop cher
    Commentaire : « Trop cher pour moi »
    """.trimIndent(),
    text.firstValue,
)
```

Interested → second line `Intéressé`, no `Raison` / `Commentaire`. Blank title → `Bien n° 101`. Unknown reason → no `Raison` line. In `TelegramVisitFeedbackEndpointTest`, assert:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "⭐ Avis de Ray Sponsible : Nice apartment (Bastos) — 150 000 FCFA / mois",
    ),
)
assertTrue(text.firstValue.contains("Pas intéressé"))
assertTrue(text.firstValue.contains("Raison : autre raison"))
assertTrue(text.firstValue.contains("Commentaire : « Trop cher »"))
assertFalse(text.firstValue.contains("Date:"))
assertFalse(text.firstValue.contains("Client:"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest,TelegramVisitFeedbackEndpointTest`
Expected: FAIL — the old `L'avis de … sur la visite` / `Bien:` / `Date:` / `Client:` / `Avis:` lines.

- [ ] **Step 3: Implement the new body**

First line `"⭐ Avis de ${customer.party.fullName()} : " + labels.titleAndPrice(listing, formatter)`.
Second line `VisitFeedbackLabels.interest(visit.interest!!)`.
For NOT_INTERESTED only: `Raison : $reason` when present, then `Commentaire : « $comment »` when present.
Drop the date, `Bien:`, `Client:` and `Avis:`. Keep the `Voir la visite` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitFeedbackBotletTest,TelegramVisitFeedbackEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitFeedbackEndpointTest.kt
git commit -m "feat(visit): shorten the feedback Telegram message

Refs #227"
```

---

### Task 12: Co-brokerage visit

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt`

**Interfaces:**
- Consumes: Task 1's `ListingTelegramLabels.titleAndPrice(listing, formatter)`; `CoBrokerageVisitLabels`.
- Produces: `CoBrokerageVisitBotlet(tenantService, labels, sender)`. The four `sendX` signatures are unchanged.

- [ ] **Step 1: Write the failing expected texts**

Give the listing fixture `cityId = 2370201L`, `neighborhoodId = 1001L`, `price = 150000`, `currencyCode = "XAF"`, `pricePeriodicityType = PeriodicityType.MONTHLY`; give the tenant `currencyCode` / `monetaryFormat`; mock locations 2370201 / 1001. Assert:

```kotlin
assertEquals(
    """
    ✅ Visite confirmée : Villa Bastos (Bastos) — 150 000 FCFA / mois, 02/12/2026 à 10:00
    Sur place — je vous y retrouve avec mon client.
    Visite organisée par Kim Possible.
    """.trimIndent(),
    text.firstValue,
)
```

Reminder → first line `⏰ Rappel : …`; cancelled → `❌ Visite annulée : …` then `Le client a annulé la visite.`; no-show → `⚠️ Visite non honorée : …` then the shared no-show label. Video → second line `Visite vidéo — je serai sur place pour vous ouvrir ; le client suivra en visio.` Blank title → `Bien n° 101`. In `TelegramCoBrokerageVisitEndpointTest`, assert:

```kotlin
assertTrue(
    text.firstValue.startsWith(
        "✅ Visite confirmée : Nice apartment (Bastos) — 150 000 FCFA / mois, ",
    ),
)
assertTrue(text.firstValue.contains("Sur place — je vous y retrouve avec mon client."))
assertTrue(text.firstValue.contains("Visite organisée par John Doe."))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest,TelegramCoBrokerageVisitEndpointTest`
Expected: FAIL — the old four headlines.

- [ ] **Step 3: Implement the four headlines**

Each `sendX` builds `line = labels.titleAndPrice(listing, formatter)` and `slot = "${formatter.date(scheduledAt)} à ${formatter.time(scheduledAt)}"`, then:

- confirmed: `✅ Visite confirmée : $line, $slot` / `$type — ${onSite(visit.type)}`;
- reminder: `⏰ Rappel : $line, $slot` / `$type — ${onSite(visit.type)}`;
- cancelled: `❌ Visite annulée : $line, $slot` / `CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy)`;
- no-show: `⚠️ Visite non honorée : $line, $slot` / `CoBrokerageVisitLabels.noShowReason(visit.noShowReason)`.

`send` appends `Visite organisée par ${agent.party.fullName()}.` as the last line, with no blank line. Drop the old headlines, the `($type)` parenthesis and the blank blocks. Keep the `Voir l'annonce` button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest,TelegramCoBrokerageVisitEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCoBrokerageVisitEndpointTest.kt
git commit -m "feat(visit): shorten the co-brokerage visit Telegram messages

Refs #227"
```

---

### Task 13: Co-brokerage interest digest

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilder.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilderTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt`

**Interfaces:**
- Consumes: `LocationService.findByIdOrNull`; `ListingInterest.title`, `.neighbourhood`, `.price`, `.listingId`.
- Produces: `ListingInterest(..., place: String, neighbourhood: String, ...)` — the new field sits right after `place`; `place` keeps its `neighbourhood (city)` value for the e-mail. The botlet's public `send(agent, digest, chatId)` is unchanged.

- [ ] **Step 1: Write the failing digest and botlet expectations**

In `CoBrokerageInterestDigestBuilderTest`, assert `assertEquals("Bastos", interest.neighbourhood)` and, for a listing without a neighbourhood, `assertEquals("", interest.neighbourhood)`.

In `CoBrokerageInterestBotletTest`, give the `ListingInterest` fixture `neighbourhood = "Bastos"` and assert:

```kotlin
assertEquals(
    """
    👥 1 agent s'intéresse à votre bien

    🏠 Villa Bastos (Bastos) — 90 000 FCFA / mois
    • Kim Possible — Location - Appartement · 2 pièces · 100 000 FCFA / mois
    """.trimIndent(),
    text,
)
```

Update the other tests: `6 agents s'intéressent…`; no title → `🏠 Bien n° 101 (Bastos)`; no partner portal → no button; > 4096 → first message starts with `👥`, every continuation starts with `🏠`, every part ≤ 4096, each title and row exactly once, each part only its own buttons; one listing over 4096 → each group part starts with `🏠` and contains the repeated header. Remove the tail assertion (`Retrouvez les coordonnées…`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestDigestBuilderTest,CoBrokerageInterestBotletTest`
Expected: FAIL — `neighbourhood` is unresolved and the old lead / header / rows.

- [ ] **Step 3: Implement the neighbourhood, header, lead and rows**

Add `val neighbourhood: String` to `ListingInterest` after `place`. The builder sets it from `listing.neighborhoodId` through `locationService` (blank when unknown), leaving `place` as is. The botlet:

- `lead`: `👥 1 agent s'intéresse à votre bien` / `👥 ${count} agents s'intéressent à vos biens`, then `\n\n`;
- `header`: `🏠 $title` + ` ($neighbourhood)` when non-blank + ` — ${listing.price}`, then `\n`;
- `row`: `• ${agent.fullName} — ${criteria(agent)}` where criteria = `listingType`, optional ` - $propertyType`, optional ` · $roomsLabel`, ` · ${agent.budget}`, optional ` · ${searchCount} recherches`; drop the agent's `place`;
- remove `tail` and its call;
- keep `more`, the split logic and the `Voir le mandat — <title>` buttons.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageInterestDigestBuilderTest,CoBrokerageInterestBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigest.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilder.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/CoBrokerageInterestDigestBuilderTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/CoBrokerageInterestBotletTest.kt
git commit -m "feat(match): shorten the co-brokerage interest Telegram digest

Refs #227"
```

---

### Task 14: Full module verification

**Files:**
- No source change.

**Interfaces:**
- Consumes: every task above.
- Produces: a green `ndopify-server` build with no changed e-mail / DTO / web-core / prototype / migration file.

- [ ] **Step 1: Run the whole module build**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all unit / endpoint / job tests, JaCoCo gate.

- [ ] **Step 2: Check the diff scope**

Run: `git status --short && git diff origin/main --stat`
Expected: only `modules/ndopify-server/` Kotlin files and the plan; no `*Mailet`, MJML, `pom.xml`, Flyway, `ndopify-dto`, `ndopify-web-core` or `src/main/html/` change.

- [ ] **Step 3: Report**

Tell the user the story is ready for `push #227`, with the green `mvn verify` and the changed files. Do not push the branch.
