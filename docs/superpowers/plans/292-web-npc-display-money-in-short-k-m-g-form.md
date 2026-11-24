# Short Money Display (K/M/G) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every amount `ndopify-web` and `ndopify-partner-central` display outside an input reads short — "150K FCFA", "1,25M FCFA", "1,5G FCFA" — in the tenant's locale and currency, while every input keeps its exact digits.

**Architecture:** `TenantFormatter.compactMoney` in `ndopify-web-core` becomes the only amount renderer: it gains the G unit and the unit-after-rounding rule, and the full-form `money` is removed once no display calls it. Both web apps switch every displayed amount to it — Partner Central through `MandateLabels.price`, which loses its dead `compact` flag — and Partner Central's three page scripts format the amounts they rewrite in the browser through one shared `NdUtil.compactNumber`.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, `kotlin.test` + `mockito-kotlin`, plain ES5 page scripts bundled by esbuild, `ndopify-web-core` released to GitHub Packages.

**Spec:** GitHub issue #292 (`https://github.com/wutsi/ndopify-mono/issues/292`) — the enriched description holds the intent, the decisions and the acceptance criteria; the approved prototype is its design baseline:
`modules/ndopify-web/src/main/html/v2/{proposition,visite,annonce,acheter,vendre,bailleur,agent}.html` and `modules/ndopify-partner-central/src/main/html/v2/{index,visit,agent}.html` + `v2/assets/js/{agent,find-property,offer-property,partner-central,visit}.js`.

## Global Constraints

- **Two pull requests, in this order, never the reverse.**
  1. `ndopify-web-core` only (Task 1), branch `feat/292-web-core-compact-money`. Its merge publishes `ndopify-web-core-0.0.9` and leaves the module at `0.0.10-SNAPSHOT`.
  2. The apps and the version bump (Tasks 2–6), branch `feat/292-web-npc-short-money`, whose body says `Closes #292`.
  The apps resolve `ndopify-web-core` from GitHub Packages, never from the reactor: a call site changed before its helper is published cannot compile in that module's CI.
- **The compact rule, verbatim** — K keeps at most one decimal, M and G at most two; rounding is half-up; trailing zeros are dropped; the unit is the smallest whose rounded value reads under 1 000 (`999 950` → `1M`, `999 949` → `999,9K`, `999 995 000` → `1G`, `999 950 000` → `999,95M`); no unit above G; an amount under 1 000 stays exact (`500`); 0 reads `0`.
- **One renderer.** A displayed amount goes through `TenantFormatter.compactMoney` (Kotlin) or `NdUtil.compactNumber` (page scripts). No app formats, rounds or appends a currency itself.
- **The currency is the tenant's**: its symbol (`currencySymbol`, else `currencyCode`) and its locale's decimal separator, joined with U+00A0 so an amount never wraps before its currency.
- **Inputs never shrink.** A field's value, its prefill and the script that regroups digits while typing keep the exact digits ("45 000 000"); no compact string ever reaches an `input`, a `value` attribute or a request.
- **Figures that are not money do not change**: areas ("800 m²"), room and unit counts ("12 unités"), percentages ("95 %"), dates.
- **The prototype is the design baseline**: production strings must equal the approved prototype's strings. It is never served, never deleted, and needs no further change in these tasks.
- **Out of scope:** `ndopify-esignature`, `ndopify-server` (its own `TenantFormatter` keeps formatting e-mails and contracts in full), `ndopify-dto`, the static `th:text` fallbacks inside the templates.
- Assertions are `kotlin.test` (`assertEquals`, …); mocks are `mockito-kotlin`; UI text is French, code, comments and docs English.
- Every touched module ends green: `mvn -s ../../settings.xml verify` from the module directory (ktlint + tests + JaCoCo). From the repo root use `mvn -s settings.xml verify -pl modules/<module>`.
- Commits carry `Refs #292`.

## Review Focus

1. **A boundary amount** (`999 949`, `999 950`, `999 995 000`, `999 950 000`) — never "1000K"/"1000M", and no premature G. Pinned by the `TenantFormatterTest` table in Task 1 and by Task 5's script table.
2. **A tenant with no locale, an English locale, or another currency code** — French comma vs English dot, and the code rather than the symbol when it isn't the tenant's own. Task 1.
3. **An amount the agent typed** — the field still shows "45 000 000" (never "45M"), and the summary beside it reads short. Task 3 keeps the `th:value` and the existing input assertions; the check that no `th:value` calls a price label is a step there.
4. **A page script that rewrites a server-rendered amount** (the per-m² line, the mandate-edit "Budget" summary) — it must render exactly what the server would have. Task 5's `node` table.
5. **Figures that are not money** (m², unit counts, percentages) — left as they are; the sweep steps in Tasks 3 and 4 must not touch them.

---

### Task 1: The short amount reaches milliards and never reads 1000

**Files:**
- Modify: `modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt` (`compactMoney`, plus a private `compactNumber`)
- Test: `modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatterTest.kt` (test `amount in short`)

**Interfaces:**
- Consumes: `TenantFormatter(tenant)`, `locale` (the tenant's first locale, French when blank), `currency()`.
- Produces: `fun compactMoney(amount: Long, currencyCode: String = tenant.currencyCode): String` — unchanged signature, new output for G and for the boundaries. Tasks 2–6 call it.

- [ ] **Step 1: Replace the test with the full table**

In `TenantFormatterTest`, replace `amount in short` with:

```kotlin
    @Test
    fun `amount in short`() {
        val formatter = TenantFormatter(TENANT)
        assertEquals("0\u00A0FCFA", formatter.compactMoney(0))
        assertEquals("500\u00A0FCFA", formatter.compactMoney(500))
        assertEquals("999\u00A0FCFA", formatter.compactMoney(999))
        assertEquals("1K\u00A0FCFA", formatter.compactMoney(1_000))
        assertEquals("80K\u00A0FCFA", formatter.compactMoney(80_000))
        assertEquals("150K\u00A0FCFA", formatter.compactMoney(150_000))
        assertEquals("152,5K\u00A0FCFA", formatter.compactMoney(152_500))
        assertEquals("999,9K\u00A0FCFA", formatter.compactMoney(999_949))
        assertEquals("1M\u00A0FCFA", formatter.compactMoney(999_950))
        assertEquals("1,2M\u00A0FCFA", formatter.compactMoney(1_200_000))
        assertEquals("1,25M\u00A0FCFA", formatter.compactMoney(1_250_000))
        assertEquals("2M\u00A0FCFA", formatter.compactMoney(2_000_000))
        assertEquals("999,99M\u00A0FCFA", formatter.compactMoney(999_994_999))
        assertEquals("999,95M\u00A0FCFA", formatter.compactMoney(999_950_000))
        assertEquals("1G\u00A0FCFA", formatter.compactMoney(999_995_000))
        assertEquals("1,5G\u00A0FCFA", formatter.compactMoney(1_500_000_000))
        assertEquals("1.25M\u00A0EUR", TenantFormatter(TENANT.copy(locales = listOf("en"))).compactMoney(1_250_000, "EUR"))
        assertEquals("1.5G\u00A0EUR", TenantFormatter(TENANT.copy(locales = listOf("en"))).compactMoney(1_500_000_000, "EUR"))
    }
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `cd modules/ndopify-web-core && mvn -s ../../settings.xml test -Dtest=TenantFormatterTest`
Expected: FAIL on the first G and the boundary assertions (`expected:<1G FCFA> but was:<1500M FCFA>`, `expected:<1M FCFA> but was:<1000K FCFA>`).

- [ ] **Step 3: Implement the unit choice**

In `TenantFormatter.kt`, add above the class:

```kotlin
/** A unit a short amount counts in: [shift] decimal places left of one unit, its [suffix], the decimals it shows. */
private data class CompactUnit(val shift: Int, val suffix: String, val decimals: Int)

private val COMPACT_UNITS = listOf(
    CompactUnit(3, "K", 1),
    CompactUnit(6, "M", 2),
    CompactUnit(9, "G", 2),
)
private val THOUSAND = BigDecimal(1_000)
```

and make `compactMoney` call:

```kotlin
/**
 * 150K, 1,25M, 1,5G: K with one decimal at most, M and G with two, half-up, trailing zeros dropped. The unit is the
 * smallest whose rounded value reads under 1000 (999 950 → 1M, never 1000K; 999 995 000 → 1G); under 1 000 stays
 * exact and 0 reads 0. Built on [BigDecimal] so a boundary is exact, never a double's approximation.
 */
private fun compactNumber(amount: Long): String {
    val size = abs(amount)
    val symbols = DecimalFormatSymbols.getInstance(locale)
    if (size < 1_000) return DecimalFormat("0", symbols).format(amount)
    for ((index, unit) in COMPACT_UNITS.withIndex()) {
        val value = BigDecimal.valueOf(size).movePointLeft(unit.shift).setScale(unit.decimals, RoundingMode.HALF_UP)
        if (value < THOUSAND || index == COMPACT_UNITS.lastIndex) {
            val format = DecimalFormat("0." + "#".repeat(unit.decimals), symbols)
            return format.format(if (amount < 0) value.negate() else value) + unit.suffix
        }
    }
    return DecimalFormat("0", symbols).format(amount)
}
```

and:

```kotlin
fun compactMoney(amount: Long, currencyCode: String = tenant.currencyCode): String {
    val currency = if (currencyCode == tenant.currencyCode) currency() else currencyCode
    return listOf(compactNumber(amount), currency).filter { it.isNotEmpty() }.joinToString("\u00A0")
}
```

Imports to add: `java.math.BigDecimal`. `RoundingMode`, `DecimalFormat`, `DecimalFormatSymbols`, `kotlin.math.abs` are already imported.

- [ ] **Step 4: Run the tests again**

Run: `cd modules/ndopify-web-core && mvn -s ../../settings.xml verify`
Expected: BUILD SUCCESS, `TenantFormatterTest` green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt \
        modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatterTest.kt
git commit -m "feat(web-core): render short amounts up to milliards" -m "Refs #292"
```

---

### Task 2: Consume the released web-core

**Files:**
- Modify: `pom.xml:53` (`<ndopify-web-core.version>`)

**Interfaces:**
- Consumes: the `ndopify-web-core` release Task 1's merge produced.
- Produces: every module of the reactor resolves the released jar whose `compactMoney` follows the rule; Tasks 3–6 build on it.

- [ ] **Step 1: Confirm the release exists**

Run: `gh release list --limit 5`
Expected: `ndopify-web-core-0.0.9` (or the next free number if another release went out meanwhile — use the number this task's release actually carries, and `grep '<version>' modules/ndopify-web-core/pom.xml` to see the module now at that release + 1 `-SNAPSHOT`).

- [ ] **Step 2: Bump the property**

Set `<ndopify-web-core.version>` in the root `pom.xml` to the released version (`0.0.9`).

- [ ] **Step 3: Prove the released jar resolves**

Run: `cd modules/ndopify-partner-central && mvn -s ../../settings.xml -q -DskipTests compile`
Expected: BUILD SUCCESS with no "Could not resolve" error (it needs the `GITHUB_USER`/`GITHUB_TOKEN` environment `settings.xml` reads).

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "chore(deps): ndopify-web-core 0.0.9" -m "Refs #292"
```

---

### Task 3: Every amount Partner Central renders is short

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt` (`price`, its KDoc; `:250` stays as it is, it calls `price`)
- Modify: `.../ui/home/AssignmentViews.kt:33`, `.../ui/mandate/MandateCards.kt:77`, `.../ui/visit/VisitViews.kt:210` (drop `compact = true`)
- Modify: `.../ui/visit/VisitViews.kt:90` (visit fee), `.../ui/listing/PublishedListingViews.kt:102` (caution/advance lambda), `.../ui/mandate/MandateCloseViews.kt:218` (closing recap), `.../ui/mandate/MandateViews.kt:144` (contract visit fee), `.../ui/mandate/MandateEditRules.kt:153` (budget summary) and `:204` (minimum-price message), `.../ui/findproperty/FindPropertyController.kt:382` (review budget), `.../ui/findproperty/FindPropertyRules.kt:184,190` (messages), `.../ui/offerproperty/OfferPropertyController.kt:470,472,478` (review price, per-m², review rent), `.../ui/offerproperty/OfferPropertyRules.kt:188,194` (messages)
- Modify: `modules/ndopify-partner-central/CLAUDE.md:403-404` (the `compact = true` sentence)
- Test: `ui/findproperty/FindPropertyRulesTest.kt`, `ui/offerproperty/OfferPropertyRulesTest.kt`, `ui/mandate/MandateEditRulesTest.kt`, `ui/mandate/MandateLabelsTest.kt`, and the sweep in Step 5

**Interfaces:**
- Consumes: `TenantFormatter.compactMoney(amount: Long, currencyCode: String = …)` from Task 2.
- Produces: `fun MandateLabels.price(money: Money, formatter: TenantFormatter): String` — the `compact` parameter is gone (one caller shape for the whole module). Tasks 4–6 rely on it: no npc display builds its own amount string.

- [ ] **Step 1: Write the failing expectations for the rules and the helper**

In `FindPropertyRulesTest`, expect the short messages: `"Indiquez un budget d'au moins 1M\u00A0FCFA."` and `"Indiquez un loyer d'au moins 5K\u00A0FCFA par mois."` (the same two for `OfferPropertyRulesTest`). In `MandateLabelsTest`'s `price` test, the four assertions become: `"150K\u00A0FCFA / mois"` for 150 000 XAF monthly, `"2,5K\u00A0CAD"` for 2 500 CAD, `"152,5K\u00A0FCFA / mois"` for 152 500 XAF monthly — with the `compact = true` argument gone, and the second CAD case dropped (it now duplicates the first).

- [ ] **Step 2: Run them and watch them fail**

Run: `cd modules/ndopify-partner-central && mvn -s ../../settings.xml test -Dtest='FindPropertyRulesTest,OfferPropertyRulesTest,MandateLabelsTest'`
Expected: FAIL on every assertion above ("1 000 000 FCFA" where "1M FCFA" is expected).

- [ ] **Step 3: Make `MandateLabels.price` always short**

```kotlin
    /** "150K FCFA / mois" in the tenant's format, or "45M FCFA" for a sale: without " / mois" unless monthly. */
    fun price(money: Money, formatter: TenantFormatter): String {
        val amount = formatter.compactMoney(money.amount, money.currency)
        return if (money.periodicity == PeriodicityType.MONTHLY) "$amount / mois" else amount
    }
```

Drop `, compact = true` at the three call sites of the old flag (`AssignmentViews`, `MandateCards`, `VisitViews`'s `meta`).

- [ ] **Step 4: Switch the direct `formatter.money(...)` displays to `formatter.compactMoney(...)`**

Only the calls that render money: `VisitViews` (fee), `PublishedListingViews` (rename the local `val money = { months: Int -> … }` to `val amount = { … }` and call `compactMoney`), `MandateCloseViews` (recap), `MandateViews` (contract visit fee), `MandateEditRules` (budget summary and the seller's floor message), `FindPropertyController` (review budget), `FindPropertyRules`, `OfferPropertyController` (review price, per-m², review rent), `OfferPropertyRules`. The per-m² line keeps `MandateLabels.formatNumber` for the surface: `"≈ " + formatter.compactMoney(rate) + "/m² pour " + MandateLabels.formatNumber(answers.area!!) + " m²"`.

- [ ] **Step 5: Sweep the expectations**

Run: `cd modules/ndopify-partner-central && mvn -s ../../settings.xml test`
Expected: a list of failing assertions, each a money string in the long form. Update **only** money strings, in these files: `ui/home/AssignmentViewsTest.kt`, `ui/home/HomeTemplateTest.kt`, `ui/findproperty/FindPropertyTemplateTest.kt`, `ui/findproperty/FindPropertyControllerTest.kt`, `ui/match/MatchViewsTest.kt`, `ui/agent/AgentProfileRulesTest.kt`, `ui/agent/AgentProfileViewsTest.kt`, `ui/offerproperty/OfferPropertyControllerTest.kt`, `ui/offerproperty/OfferPropertyTemplateTest.kt`, `ui/visit/VisitTemplateTest.kt`, `ui/visit/VisitViewsTest.kt`, `ui/mandate/MandateEditTemplateTest.kt`, `ui/mandate/MandateTemplateTest.kt`, `ui/mandate/MandateCloseTemplateTest.kt`, `ui/mandate/MandateCloseControllerTest.kt`, `ui/mandate/MandateCloseViewsTest.kt`, `ui/mandate/MandateCardsTest.kt`, `ui/mandate/MandateViewsTest.kt`, `ui/listing/ListingSectionTemplateTest.kt`, `ui/listing/ListingRulesTest.kt`, `ui/listing/ListingSectionControllerTest.kt`, `ui/listing/ListingViewsTest.kt`, `ui/listing/PublishedListingViewsTest.kt`, `ui/listing/ListingFormRulesTest.kt`.
Examples of the mapping: `"150\u00A0000\u00A0FCFA"` → `"150K\u00A0FCFA"`, `"45\u00A0000\u00A0000\u00A0FCFA"` → `"45M\u00A0FCFA"`, `"5\u00A0000\u00A0FCFA"` → `"5K\u00A0FCFA"`, `"240\u00A0000\u00A0FCFA / mois"` → `"240K\u00A0FCFA / mois"`. Keep every area, count, percentage, date and currency-only assertion as it is (`"95\u00A0%"`, `"800 m²"`, `"FCFA / mois"` on a field's suffix).

- [ ] **Step 6: Prove no input lost its digits, and run the module**

Run: `grep -rn 'th:value="\${.*price\|th:value="\${.*amount' modules/ndopify-partner-central/src/main/resources/templates`
Expected: raw model values (`${answers.priceAmount}`, `${draft.price}`), no call to `MandateLabels.price` or a formatter.

Run: `cd modules/ndopify-partner-central && mvn -s ../../settings.xml verify`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Fix the module doc**

In `modules/ndopify-partner-central/CLAUDE.md:403-404`, replace "the rent in bold and in short, `compact = true`: \"245K FCFA / mois\"" with "the rent in bold and in short: \"245K FCFA / mois\" (every displayed amount is short, #292)".

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): display every amount in short form" -m "Refs #292"
```

---

### Task 4: Every amount the public site renders is short

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt:84` (visit fee), `.../ui/visit/VisitPages.kt:76` (visit fee), `.../ui/buyer/BuyerController.kt:30` and `.../ui/owner/OwnerController.kt:40` (`zeroAmount`)
- Test: `ui/match/MatchPagesTest.kt`, `ui/match/MatchTemplateTest.kt`, `ui/visit/VisitPagesTest.kt`, `ui/visit/VisitTemplateTest.kt`, `ui/buyer/BuyerControllerTest.kt`, `ui/buyer/BuyerTemplateTest.kt`, `ui/owner/OwnerControllerTest.kt`, `ui/owner/SellTemplateTest.kt`

**Interfaces:**
- Consumes: `TenantFormatter.compactMoney` (Task 2).
- Produces: nothing new; the two fee strings and `zeroAmount` change value only. `ListingPages` and `MatchPages`'s search summary already call `compactMoney` and pick up G and the boundary rule without a change.

- [ ] **Step 1: Write the failing expectation**

In `MatchPagesTest`'s `the visit fee is stated before the request`, expect `"5K FCFA"` in `page.visitFee` and `"Les visites sont organisées par votre agent. Frais de visite : 5K FCFA par visite, réglés directement à lui."` in the terms note (that test normalises U+00A0 to a space); in `BuyerControllerTest`, `zeroAmount` stays `"0\u00A0FCFA"` (it must not change).

- [ ] **Step 2: Run it and watch it fail**

Run: `cd modules/ndopify-web && mvn -s ../../settings.xml test -Dtest=MatchPagesTest`
Expected: FAIL — "5 000 FCFA" where "5K FCFA" is expected.

- [ ] **Step 3: Switch the four sites**

`formatter.money(` → `formatter.compactMoney(` in `MatchPages`, `VisitPages`, `BuyerController`, `OwnerController`. Nothing else changes in those files.

- [ ] **Step 4: Sweep the expectations**

Run: `cd modules/ndopify-web && mvn -s ../../settings.xml test`
Expected: failures in the eight test files above. Fix the money strings only (`"5\u00A0000\u00A0FCFA"` → `"5K\u00A0FCFA"`, `"240\u00A0000\u00A0FCFA"` → `"240K\u00A0FCFA"`); `BuyerTemplateTest`'s and `SellTemplateTest`'s fixture `TenantFormatter(euroTenant).money(0)` becomes `.compactMoney(0)` (it still reads `"0\u00A0EUR"`).

- [ ] **Step 5: Run the module**

Run: `cd modules/ndopify-web && mvn -s ../../settings.xml verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): display every amount in short form" -m "Refs #292"
```

---

### Task 5: The page scripts render the same short amounts

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/util.js` (add `compactNumber`, expose it on `window.NdUtil`)
- Modify: `.../assets/js/mandate.js:109` (`formatRent`), `.../assets/js/find-property.js:465` and `.../assets/js/offer-property.js:444` (the per-m² line)

**Interfaces:**
- Produces: `window.NdUtil.compactNumber(amount: number|string): string` — the K/M/G suffix alone ("150K", "1,25M", "1,5G", "500"), French comma, exact at the boundaries. Callers append `"\u00A0" + currency`.

- [ ] **Step 1: Add the helper to `util.js`**

Next to `maskEmail`, and added to the `window.NdUtil` object as `compactNumber: compactNumber`:

```javascript
    /*
     * An amount as a page shows it (#292): "500", "150K", "1,25M", "1,5G". K keeps at most one decimal, M and G
     * two, half-up, trailing zeros dropped; the unit is the smallest whose rounded value reads under 1000
     * ("1M", never "1000K"). Integer scaling, so no double rounding drifts at a boundary.
     * The caller adds the currency (the tenant's, from the body's data-currency).
     */
    function compactNumber(value) {
        var n = Math.abs(Math.round(Number(value) || 0));
        var sign = Number(value) < 0 ? "-" : "";
        var units = [["K", 3, 1], ["M", 6, 2], ["G", 9, 2]];
        if (n < 1000) return sign + String(n);
        for (var i = 0; i < units.length; i++) {
            var suffix = units[i][0], shift = units[i][1], decimals = units[i][2];
            var factor = Math.pow(10, decimals);
            var scaled = Math.round(n * factor / Math.pow(10, shift));
            if (scaled < 1000 * factor || i === units.length - 1) {
                return sign + (scaled / factor).toFixed(decimals).replace(/\.?0+$/, "").replace(".", ",") + suffix;
            }
        }
    }
```

- [ ] **Step 2: Use it at the three call sites**

`mandate.js`, `formatRent` (the mandate-edit "Budget" summary): `return window.NdUtil.compactNumber(amount) + "\u00A0" + currency() + " / mois";`

`find-property.js` and `offer-property.js`, the per-m² text: `"Soit ≈ " + window.NdUtil.compactNumber(rate) + "\u00A0" + currency + "/m² pour " + formatNumber(area) + " m²"` (the surface keeps `formatNumber`).

- [ ] **Step 3: Check the scripts parse and the table is right**

Run: `node --check modules/ndopify-partner-central/src/main/assets/js/util.js && node --check modules/ndopify-partner-central/src/main/assets/js/mandate.js && node --check modules/ndopify-partner-central/src/main/assets/js/find-property.js && node --check modules/ndopify-partner-central/src/main/assets/js/offer-property.js`
Expected: no output.

Run:

```bash
node -e "global.window={};require('./modules/ndopify-partner-central/src/main/assets/js/util.js');const c=window.NdUtil.compactNumber;[0,500,999,1000,5000,150000,152500,999949,999950,1250000,45000000,999950000,999995000,1500000000,10000000000].forEach(v=>console.log(v,c(v)))"
```

Expected, in order: `0`, `500`, `999`, `1K`, `5K`, `150K`, `152,5K`, `999,9K`, `1M`, `1,25M`, `45M`, `999,95M`, `1G`, `1,5G`, `10G`.

- [ ] **Step 4: Check the three call sites read like the prototype**

Run: `grep -n "compactNumber\|Soit ≈\|formatRent" modules/ndopify-partner-central/src/main/assets/js/{mandate,find-property,offer-property}.js`
Expected: each per-m² line builds `"Soit ≈ " + window.NdUtil.compactNumber(rate) + "\u00A0" + currency + "/m² pour " + formatNumber(…) + " m²"`, and `formatRent` starts with `window.NdUtil.compactNumber(amount)`. Compare them with the approved prototype's `v2/assets/js/offer-property.js` `rateText` and `v2/assets/js/partner-central.js` `formatMoney` — the rendered strings must be identical.

If a signed-in local session is at hand (ndopify-server running, an agent's e-mail in Mailpit), walk it: a sale's *Proposer un bien* wizard with a 45 000 000 asking price on a 800 m² plot reads "Soit ≈ 56,3K FCFA/m² pour 800 m²" under the surface and "45M FCFA" then the same rate on the review, with the field above still "45 000 000"; a mandate's *Modifier* page with 15 000 000 in the budget reads "15M FCFA" in its folded summary.

- [ ] **Step 5: Run the module (the bundle builds in `generate-resources`)**

Run: `cd modules/ndopify-partner-central && mvn -s ../../settings.xml verify`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/assets/js
git commit -m "feat(npc): render the page scripts' amounts short" -m "Refs #292"
```

---

### Task 6: Remove the full-form formatter

**Files:**
- Modify: `modules/ndopify-web-core/src/main/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatter.kt` (delete `money` and the private `number` it alone uses; update the class KDoc)
- Test: `modules/ndopify-web-core/src/test/kotlin/com/wutsi/ndopify/web/core/service/TenantFormatterTest.kt` (delete the three `money` tests)

**Interfaces:**
- Consumes: nothing — this task proves nothing calls `money` any more.
- Produces: a `TenantFormatter` whose only amount renderer is `compactMoney`; counts still go through `count`. Its merge releases `ndopify-web-core-0.0.10`; the apps keep pinning the previous version until the routine dependency bump, which changes nothing for them.

- [ ] **Step 1: Prove the method has no caller left**

Run: `grep -rn "\.money(" modules --include='*.kt' | grep -v "/test/" | grep -v "ndopify-server"`
Expected: no output except `modules/ndopify-web-core/.../TenantFormatter.kt` itself (its declaration). If an app still calls it, that is a missed site: fix it with `compactMoney` before going on.

- [ ] **Step 2: Delete it**

Remove `fun money(...)`, the private `fun number(amount: Long, pattern: String)` it alone uses, and the three tests `amount in the tenant's monetary format, never wrapping`, `amount in another currency`, `amount without a monetary format`. In the class KDoc, drop `monetaryFormat` from the patterns it honours (it is no longer read) and say that an amount is shown short, in the tenant's locale and currency.

- [ ] **Step 3: Run the library**

Run: `cd modules/ndopify-web-core && mvn -s ../../settings.xml verify`
Expected: BUILD SUCCESS; `TenantFormatterTest` green with the remaining date, time, count, currency and short-amount tests.

- [ ] **Step 4: Run both apps against the library they pin**

Run: `mvn -s settings.xml verify -pl modules/ndopify-web,modules/ndopify-partner-central`
Expected: both BUILD SUCCESS (they resolve `ndopify-web-core` at the pinned release; this proves no app still needs the removed method in the source tree either, since Task 3 and Task 4 already switched every call).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web-core
git commit -m "refactor(web-core): drop the unused full amount formatter" -m "Closes #292"
```
