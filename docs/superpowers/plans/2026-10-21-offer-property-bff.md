# Offer Property BFF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the *Proposer un bien* prototype (`src/main/html/offer-property.html`) from Partner Central as a public,
server-driven wizard that creates a `LISTING` mandate, with the property's availability date stored by ndopify-server.

**Architecture:** A copy of the *Trouver un bien* integration (`ui/findproperty/`): one Thymeleaf page rendering one step
at a time, `POST /offer-property/{step}` validating into the session's `PendingListing`, the review submitting the
session (`NdMandateClient.create`), never the page. The prototype's JS becomes an enhancer only. ndopify-server gets an
`availableFrom` date on mandates (DTO, entity, migration), since the prototype asks for a date and `MovingPeriod` can't
hold one.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, Spring Data JPA + Flyway (MySQL), JUnit 5 + mockito-kotlin, Jsoup.

**Spec:** the prototype — `modules/ndopify-partner-central/src/main/html/offer-property.html`,
`src/main/html/assets/js/offer-property.js`, `src/main/html/assets/css/offer-property.css` — plus `CLAUDE.md`'s
*Trouver un bien* paragraph, which this page mirrors.

## Global Constraints

- Never delete or modify anything under `modules/ndopify-partner-central/src/main/html/` (the prototype); copy from it.
- Assertions with `kotlin.test` only. `ktlint` must pass (`mvn validate`), JaCoCo gates must pass (`mvn verify`).
- No "Précédent" button on step 1 (`property`).
- Copy, labels and error messages verbatim from the prototype (French). Amounts / dates through `TenantFormatter`;
  the currency through `formatter.currency()`, never a hard-coded "FCFA".
- Mandate sent: `mandateType = LISTING`, `listingType = RENTAL`, `price.periodicity = MONTHLY`, currency = tenant's
  `currencyCode`, exactly one neighbourhood.
- Thresholds (from `offer-property.js`): `MIN_RENT = 5000`, advance rent 1..12 months (optional), security deposit
  1..6 months (required), availability today..today+365 days **in the tenant's `timeZone`**, area 5..1 000 000 m².
- `/offer-property` and `/offer-property/**` are public (`SecurityConfiguration`), like `/find-property`.
- Every step posts and works without JavaScript.
- The header's "Quitter" button (every step and the confirmation) and the confirmation's CTA both go to
  `{tenant.publicPortalUrl}/owner`; both are hidden when the tenant has no `publicPortalUrl`.

## Review Focus

- A posted `availableFrom` that isn't a real date (`2026-02-31`, `abc`, blank) → a field error, never a 500 (Task 3).
- Editing a LISTING mandate in Partner Central (`/mandates/{id}/edit`) must keep its `availableFrom`: the update
  replaces every field from the request, so a missing value wipes it (Task 2).
- Without JS the "features" step shows every question of the category; answers that don't apply to the posted type
  (rooms of a `ROOM`, parking of a `ROOM`, furnishing of a `BUILDING`) are dropped, never errors (Task 3).
- "Non" to parking stores `parkings = "0"`; "Oui" without a number of places is an error (Task 3).
- `BUILDING` is RESIDENTIAL here (unlike `FindPropertyRules`, where it is COMMERCIAL): a COMMERCIAL `BUILDING` posted is
  rejected (Task 3).

---

## File Structure

ndopify-dto / ndopify-server (Task 1):
- Modify `ndopify-dto/.../mandate/dto/CreateMandateRequest.kt`, `UpdateMandateRequest.kt`, `Mandate.kt`: add
  `val availableFrom: LocalDate? = null` after `movingPeriod`.
- Create `ndopify-server/src/main/resources/db/migration/common/V7_2__mandate-available-from.sql`.
- Modify `MandateEntity.kt`, `MandateService.kt` (create + update), `MandateMapper.kt`.

ndopify-partner-central:
- `service/TenantFormatter.kt` — add `date(LocalDate)` and `today(Clock)` (Task 2).
- `ui/mandate/MandateLabels.kt`, `ui/mandate/MandateEditRules.kt` — show / keep `availableFrom` (Task 2).
- `ui/offerproperty/OfferPropertyRules.kt` — field rules (Task 3).
- `ui/offerproperty/PendingListing.kt` — session answers, completeness, request mapping (Task 3).
- `ui/offerproperty/OfferPropertyController.kt` + `PropertyForm.kt`, `AddressForm.kt`, `TermsForm.kt`,
  `FeaturesForm.kt`, `AmenitiesForm.kt`, `ContactForm.kt` (Task 4).
- `config/SecurityConfiguration.kt` — permit `/offer-property/**` (Task 4).
- `templates/offer-property.html`, `public/assets/css/offer-property.css` (Task 5).
- `public/assets/js/offer-property.js` (Task 6).
- `CLAUDE.md` — *Proposer un bien* paragraph (Task 6).

Reused as is: `findproperty/SubmittedMandate` (confirmation data, under its own session key), `join/JoinRules` (names,
e-mail, WhatsApp), `ui/location/SectorValidator` if it fits the one-neighbourhood check (else the same check as
`FindPropertyController.areaErrors`), `mandate/MandateLabels` (property types, rooms, furniture, amenities, contact
periods), `client/ndopify/NdMandateClient`, `NdLocationClient`, `LocationController` (`GET /locations`).

---

### Task 1: `availableFrom` on mandates (dto + server)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/{CreateMandateRequest,UpdateMandateRequest,Mandate}.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V7_2__mandate-available-from.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`,
  `.../service/MandateService.kt` (`create`, `update`), `.../mapper/MandateMapper.kt`
- Test: `CreateMandateEndpointTest.kt`, `UpdateMandateEndpointTest.kt`, `GetMandateEndpointTest.kt` (+ their SQL
  fixtures under `src/test/resources/db/test/mandate/`)

**Interfaces:**
- Produces: `CreateMandateRequest.availableFrom: LocalDate?`, `UpdateMandateRequest.availableFrom: LocalDate?`,
  `Mandate.availableFrom: LocalDate?` (JSON `"2026-11-03"`), `MandateEntity.availableFrom: LocalDate?`.

- [ ] **Step 1: Write the failing tests**
  - `CreateMandateEndpointTest.create`: set `availableFrom = LocalDate.of(2026, 11, 3)` in `request`; add
    `assertEquals(request.availableFrom, mandate.availableFrom)`.
  - `UpdateMandateEndpointTest`: in its main update test, send `availableFrom = LocalDate.of(2027, 1, 15)` and assert
    it's saved; add `update without availableFrom clears it` (fixture mandate with a date, request with `null` →
    `assertNull`), documenting that update replaces the field.
  - `GetMandateEndpointTest`: set `available_from = '2026-11-03'` on the fixture's mandate; assert
    `assertEquals(LocalDate.of(2026, 11, 3), mandate.availableFrom)`.
- [ ] **Step 2: Run them, expect compile failure** — `cd modules/ndopify-server && mvn test -Dtest='CreateMandateEndpointTest,UpdateMandateEndpointTest,GetMandateEndpointTest'`
- [ ] **Step 3: Implement**
  - Migration: `ALTER TABLE T_MANDATE ADD COLUMN available_from DATE;` (new file; don't edit `V7_0`).
  - Add the field to the three DTOs and the entity (after `movingPeriod`), copy it in `MandateService.create` /
    `update` and `MandateMapper`. A `DATE` column + `LocalDate`: no time zone involved.
  - Not in scope: `MandateContractGenerator` (there's no LISTING contract template yet — see *Out of scope*).
- [ ] **Step 4: Run the tests again, expect PASS**, then `mvn install -Dheadless=true -pl modules/ndopify-dto` so
  partner-central sees the new field.
- [ ] **Step 5: Commit** — `feat(mandate): store the availability date of a listing`

---

### Task 2: Partner Central shows and keeps `availableFrom`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/TenantFormatter.kt`
- Modify: `.../npc/ui/mandate/MandateLabels.kt` (`criteria`), `.../npc/ui/mandate/MandateEditRules.kt` (request build)
- Test: `TenantFormatterTest.kt`, `MandateLabelsTest.kt`, `MandateEditRulesTest.kt`

**Interfaces:**
- Produces: `TenantFormatter.date(date: LocalDate): String` (tenant `dateFormat` + first locale),
  `TenantFormatter.today(clock: Clock): LocalDate` (`LocalDate.now(clock.withZone(zone))`).

- [ ] **Step 1: Write the failing tests**
  - `TenantFormatterTest`: `date(LocalDate.of(2026, 11, 3))` with `TenantFixtures.TENANT` (`dd MMM yyyy`) →
    `"03 nov. 2026"`; `today` with a fixed clock at `2026-10-21T23:30:00Z` → `2026-10-22` (Africa/Douala is UTC+1).
  - `MandateLabelsTest`: a LISTING mandate with `availableFrom = 2026-11-03` → criterion
    `"Disponibilité" to "Dès le 03 nov. 2026"`; a LISTING without it → the `MOVE_IN_LABELS` value as today; a SEARCH
    mandate is unchanged ("Emménagement").
  - `MandateEditRulesTest`: the built `UpdateMandateRequest.availableFrom` equals the loaded mandate's.
- [ ] **Step 2: Run, expect FAIL** — `cd modules/ndopify-partner-central && mvn test -Dtest='TenantFormatterTest,MandateLabelsTest,MandateEditRulesTest'`
- [ ] **Step 3: Implement** — in `criteria`, for `owner && mandate.availableFrom != null` the value is
  `"Dès le " + formatter.date(it)`; in `MandateEditRules`, `availableFrom = mandate.availableFrom` next to
  `securityDeposit` / `advanceRent` (the edit page doesn't edit it).
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `feat(npc): show and keep a listing's availability date`

---

### Task 3: `OfferPropertyRules` and `PendingListing`

**Files:**
- Create: `.../npc/ui/offerproperty/OfferPropertyRules.kt`, `.../npc/ui/offerproperty/PendingListing.kt`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`, `PendingListingTest.kt`

**Interfaces:**
- Consumes: `CreateMandateRequest.availableFrom` (Task 1); `JoinRules.firstName/lastName/email/whatsappNumber`.
- Produces:
  - `object OfferPropertyRules` — each returns the French error or `null`:
    - `val CATEGORIES_BY_TYPE: Map<PropertyType, PropertyCategory>` in form order: APARTMENT, HOUSE, ROOM, STUDIO,
      DUPLEX, BUILDING → RESIDENTIAL; OFFICE, RETAIL, INDUSTRIAL, LAND → COMMERCIAL.
    - `ROOMS = listOf("1","2","3","4","5+")`, `PARKINGS = listOf("1","2","3+")`, `NO_PARKING = "0"`,
      `ROAD_ACCESS = listOf("roadPaved","trackAccessibleByCar","trackInaccessibleByCar")`,
      `AMENITIES` = the 11 other booleans in the prototype's `AMENITIES` order.
    - `needsFurniture(category, type)` = RESIDENTIAL && type != BUILDING; `needsRooms(category, type)` = RESIDENTIAL &&
      type ∉ {ROOM, STUDIO, BUILDING}; `needsParking(category, type)` = RESIDENTIAL && type != ROOM;
      `needsArea(category)` = COMMERCIAL.
    - `propertyCategory(PropertyCategory?)`, `cityId(Long?)` ("Choisissez la ville."),
      `neighborhoodId(Long?)` ("Choisissez le quartier."), `priceAmount(Long?, TenantFormatter)`,
      `advanceRent(Int?)`, `securityDeposit(Int?)`, `availableFrom(LocalDate?, today: LocalDate)`,
      `propertyType(category, String?)`, `furnitureType(String?)`, `rooms(String?)`, `parkings(String?)` (accepts
      `NO_PARKING`), `area(Int?)`, `roadAccess(String?)`, `preferredContactPeriod(ContactPeriodType?)`,
      `parseDate(String?): LocalDate?` (strict ISO `yyyy-MM-dd`, `null` for anything else).
  - `data class PendingListing(propertyCategory, cityId, neighborhoodId, priceAmount: Long?, advanceRent: Int?,
    securityDeposit: Int?, availableFrom: LocalDate?, propertyType: String?, furnitureType: String?, rooms: String?,
    parkings: String?, area: Int?, roadAccess: String?, amenities: Set<String>, firstName, lastName, email,
    whatsappNumber, preferredContactPeriod) : Serializable` with `SESSION_ATTRIBUTE = "npc.pending-listing"`,
    `isComplete(step: String, today: LocalDate): Boolean`, `firstIncompleteStep(today: LocalDate): String`,
    `toCreateMandateRequest(currency: String): CreateMandateRequest`.
  - Step names (constants on `OfferPropertyController`, Task 4, but used here): `property`, `address`, `terms`,
    `features`, `amenities`, `contact`, `review`.

Messages (verbatim from the prototype): category "Indiquez si le bien est pour y habiter ou pour une activité.";
price "Indiquez le loyer mensuel souhaité." / "Indiquez un loyer d'au moins {formatter.money(5000)} par mois.";
deposit "Indiquez la caution demandée, en mois de loyer."; availability "Indiquez à partir de quand le bien est
libre." / "Choisissez une date entre aujourd'hui et dans 365 jours."; type "Choisissez le type de bien.";
furniture "Indiquez si le bien est meublé."; rooms "Indiquez le nombre de chambres."; parking "Indiquez si le bien a
un parking ou un garage." (no answer) / "Indiquez le nombre de places." ("Oui" without places); area "Indiquez la
surface, même approximative." / "Indiquez une surface d'au moins 5 m²."; road "Indiquez comment on accède au bien.";
contact period "Indiquez quand votre agent peut vous contacter.". Advance rent outside 1..12 is "Choisissez une
avance proposée." (the prototype's select can't produce it, so no copy exists).

- [ ] **Step 1: Write the failing tests** — `OfferPropertyRulesTest`, one test per rule and boundary:
  `priceAmount(4999)` error / `5000` ok; `advanceRent(null)` ok, `0`/`13` error, `12` ok; `securityDeposit(null)`/`7`
  error, `6` ok; `availableFrom(today)` ok, `today.plusDays(365)` ok, `plusDays(366)` / `minusDays(1)` error;
  `parseDate("2026-02-31") == null`, `parseDate("2026-11-03") == LocalDate.of(2026,11,3)`;
  `propertyType(COMMERCIAL, "BUILDING")` error, `(RESIDENTIAL, "BUILDING")` ok, `(RESIDENTIAL, "VILLA")` error (not
  offered); `needsFurniture(RESIDENTIAL, "BUILDING") == false`, `needsRooms(RESIDENTIAL, "STUDIO") == false`,
  `needsParking(RESIDENTIAL, "ROOM") == false`, `needsArea(COMMERCIAL)`; `parkings("0")` ok, `parkings("4")` error;
  `area(4)` error, `area(5)` ok, `area(null)` error; `roadAccess("roadPaved")` ok, `roadAccess("x")` error.
  `PendingListingTest` with a `COMPLETE` fixture (RESIDENTIAL APARTMENT, Yaoundé/Bastos, 150 000, advance 3, deposit 2,
  available `TODAY.plusDays(10)`, UNFURNISHED, rooms "3", parkings "0", roadPaved + waterMeter, contact MORNING):
  `firstIncompleteStep(TODAY) == "review"`; each step blank in turn → that step; COMPLETE with
  `availableFrom = TODAY.minusDays(1)` → `"terms"` (a date valid yesterday expires); `toCreateMandateRequest("XAF")`
  asserts `mandateType = LISTING`, `listingType = RENTAL`, `neighbourhoodIds = listOf(1001)`,
  `price = Money(150000, "XAF", MONTHLY)`, `advanceRent = 3`, `securityDeposit = 2`, `availableFrom`,
  `roadPaved = true`, `trackAccessibleByCar = false`, `waterMeter = true`, `parkings = "0"`, `area = null`; a
  COMMERCIAL OFFICE with stale `rooms`/`furnitureType`/`parkings` sends them `null` and its `area`.
- [ ] **Step 2: Run, expect compile failure** — `mvn test -Dtest='OfferPropertyRulesTest,PendingListingTest'`
- [ ] **Step 3: Implement** both files, modelled on `FindPropertyRules` / `PendingMandate`. `isComplete` takes `today`
  because the terms step's validity moves with the day.
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `feat(npc): rules of the offer-property wizard`

---

### Task 4: `OfferPropertyController`

**Files:**
- Create: `.../npc/ui/offerproperty/OfferPropertyController.kt` and the forms `PropertyForm(propertyCategory)`,
  `AddressForm(cityId, neighborhoodId)`, `TermsForm(priceAmount: String?, advanceRent: String?, securityDeposit: String?,
  availableFrom: String?)`, `FeaturesForm(propertyType, furnitureType, rooms, hasParking, parkings, area: String?)`,
  `AmenitiesForm(roadAccess: String?, amenities: List<String>)`, `ContactForm(firstName, lastName, email,
  whatsappNumber, preferredContactPeriod)`
- Modify: `.../npc/config/SecurityConfiguration.kt` — `.requestMatchers("/offer-property", "/offer-property/**").permitAll()`
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt` (standalone MockMvc,
  like `FindPropertyControllerTest`; fixed `Clock`)

**Interfaces:**
- Consumes: Task 3; `NdMandateClient.create`, `NdLocationClient.search`, `TenantContext.getTenant()`, `Clock` bean.
- Produces: `@Controller @RequestMapping("/offer-property") class OfferPropertyController(mandateClient,
  locationClient, tenantContext, clock)`, `VIEW = "offer-property"`, step constants, `STEPS`,
  `STEP_CONFIRMATION = "confirmation"`, `SUBMITTED_ATTRIBUTE = "npc.submitted-listing"` (a `SubmittedMandate`).
  Model attributes for the template (Task 5): `step`, `stepNumber`, `stepCount`, `answers`, `errors`, `returning`,
  `tenant`, `exitUrl` (every step and the confirmation: `{tenant.publicPortalUrl}/owner`, trailing `/` of the portal
  URL dropped; `null` when the tenant has no `publicPortalUrl`), `currency` (terms), `availableMin` / `availableMax` (ISO, terms), `city` / `neighborhood` (`Location?`,
  address + review), `propertyTypes` (types of the chosen category, features), `propertyTypeLabels`, `amenityLabels`,
  and on the review `categoryLabel`, `locationLabel`, `termsLines: List<String>`, `propertyLabel`, `amenitiesLabel`,
  `contactLines: List<String>`; on the confirmation `maskedEmail`, `whatsappNumber`, `contactPeriodLabel`; `error` on a
  failed submit.

Behaviour to copy from `FindPropertyController`: URL never trusted (`min(requested, firstIncompleteStep)`), `returnTo
= review`, `save` / `step` / `redirect` helpers, best-effort `namesOrEmpty`, submit errors → "Envoi impossible pour le
moment. Réessayez dans quelques instants.", session cleared on success, confirmation only while
`SUBMITTED_ATTRIBUTE` is set. Specific to this page:
- `address` checks the city is a CITY of the tenant's country and the neighbourhood a NEIGHBORHOOD whose `parentId`
  is that city (errors on `cityId` / `neighborhoodId` as in `FindPropertyController.areaErrors`).
- `terms`: amounts via `FindPropertyRules.parseAmount`; `advanceRent` blank → `null`; `availableFrom` via
  `OfferPropertyRules.parseDate`, a non-blank unparsable value is the "entre aujourd'hui…" error; `today =
  formatter.today(clock)`.
- `features`: drop answers that don't apply to the posted type; parkings = `"0"` when `hasParking == "NO"`, the posted
  `parkings` when `"YES"`, missing `hasParking` → the "parking ou un garage" error.
- `amenities`: unknown names dropped (not errors).
- Review labels as the prototype's `describe*`: category "Location, usage résidentiel"; location
  "Bastos, Yaoundé"; terms lines "{money} par mois", "Avance de 3 mois" / "Sans avance de loyer", "Caution de 2 mois",
  "Disponible dès maintenant" (today) / "Disponible dès le {formatter.date}"; property "Appartement · Non meublé ·
  3 chambres · Sans parking" (labels from `MandateLabels`, "Villa, maison" for HOUSE as in the prototype); amenities
  road access first, then the checked ones; contact lines name, e-mail, WhatsApp, "Contact le matin".

- [ ] **Step 1: Write the failing tests** (names are the contract): `show` (step 1, no "Précédent" model flag needed),
  `exit url is the owner page of the public portal` (`publicPortalUrl = "https://www.ndopify.com/"` →
  `exitUrl == "https://www.ndopify.com/owner"`, also on the confirmation), `no exit url without public portal`
  (blank `publicPortalUrl` → `exitUrl == null`),
  `show skips to the first incomplete step`, `property saves and goes to address`, `property without category`,
  `address rejects a neighbourhood of another city`, `address rejects a city of another country`, `terms saves`,
  `terms with an unparsable date` (`availableFrom=2026-02-31` → error on `availableFrom`, session unchanged),
  `terms with a date past 365 days`, `terms below the minimum rent`, `features drops what doesn't apply`
  (RESIDENTIAL ROOM posted with rooms + parkings → saved `rooms = null`, `parkings = null`), `features parking no`
  (→ `"0"`), `features parking yes without places`, `features commercial requires the area`,
  `amenities requires the road access`, `amenities drops unknown names`, `contact normalizes the phone and e-mail`,
  `review shows the labels` (asserts `termsLines`, `propertyLabel`, `locationLabel`), `submit sends a listing`
  (captor: `mandateType == LISTING`, `availableFrom`, currency = `TENANT.currencyCode`) then redirects to
  `?step=confirmation` and clears `PendingListing`, `submit while incomplete redirects`, `submit failure shows the
  error` (`HttpServerErrorException`, `ResourceAccessException`), `confirmation without submission shows step 1`,
  `returnTo review comes back to the review`.
- [ ] **Step 2: Run, expect FAIL** — `mvn test -Dtest=OfferPropertyControllerTest`
- [ ] **Step 3: Implement** the controller, forms and the security matcher.
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `feat(npc): offer-property wizard controller`

---

### Task 5: Template and CSS

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html` (from the prototype,
  structured like `templates/find-property.html`)
- Create: `.../public/assets/css/offer-property.css` (copy of the prototype's, header comment pointing at
  `templates/offer-property.html`; loaded after `partner-central.css`, `join.css`, `find-property.css`)
- Test: `src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt` (real Thymeleaf engine,
  like `FindPropertyTemplateTest`)

**Interfaces:**
- Consumes: Task 4's model attributes.
- Produces: per step a `<form class="form" data-offer-form method="post" th:action="@{/offer-property/{step}}">` with
  `data-step="{step}"` on its fieldset, the prototype's field names (`propertyCategory`, `cityId`, `neighborhoodId`,
  `priceAmount`, `advanceRent`, `securityDeposit`, `availableFrom`, `propertyType`, `furnitureType`, `rooms`,
  `hasParking`, `parkings`, `area`, `roadAccess`, amenity checkboxes as `name="amenities" value="waterMeter"` …,
  `firstName`, `lastName`, `email`, `whatsappNumber`, `preferredContactPeriod`), `data-locations-url` for the JS, and
  the review's "Modifier" links as `GET /offer-property?step={step}&returnTo=review`.

Template rules: header logo as `find-property.html` (tenant logo, falling back to Ndopify's); the header's
"Quitter" button (`<a class="btn btn--outline" data-exit>`, the prototype's icon) is on every step and the
confirmation, with `th:href="${exitUrl}"` — the owner page of the tenant's public portal, `{publicPortalUrl}/owner`
— and hidden (`th:if="${exitUrl}"`) when there's none; only the current step
rendered (`th:if="${step == '…'}"`); "Étape N sur 7" from the model; fields pre-filled from `answers`, errors from
`errors` with the prototype's `field__error` markup and `aria-invalid`; step 1 has no "Précédent"; the others link
"Précédent" to the previous step (`<a class="btn btn--outline">`); the address step's `<select>`s carry the saved
city / neighbourhood as their only option (slim-select loads the rest); `availableFrom` is `type="date"` with
`min`/`max` from the model; the features step without JS shows every question of the category; the currency suffix
is `|${currency} / mois|`; the confirmation is the prototype's `<section data-confirmation>`, with `maskedEmail`,
`whatsappNumber`, `contactPeriodLabel`, and its CTA "Retour à l'accueil" (`btn btn--primary`) to the same
`exitUrl` as "Quitter" (hidden without one).

- [ ] **Step 1: Write the failing tests**: `every step has a Quitter button to the owner page` (parameterized over
  the 7 steps: `a[data-exit]` text "Quitter", `href="https://www.ndopify.com/owner"`), `confirmation CTA goes to the
  owner page` (`.cta-row a.btn--primary` has the same `href`), `no Quitter nor CTA without public portal`,
  `property step has no previous button`, `address step pre-fills the city
  and neighbourhood`, `terms step shows the tenant currency and date bounds` (`min` = today ISO, `max` = today+365),
  `terms step shows the errors` (`aria-invalid="true"` on `availableFrom`, message text), `features step without JS
  shows every residential question` (rooms, furnitureType, hasParking, parkings present for RESIDENTIAL; area absent),
  `features step for commercial shows only the area`, `amenities step pre-checks the saved answers`,
  `review shows every section and the edit links`, `review shows the submit error`, `confirmation shows the masked
  e-mail`, `every step posts to its own action` (parameterized over the 7 steps: `form[action=/offer-property/{step}]`).
- [ ] **Step 2: Run, expect FAIL** — `mvn test -Dtest=OfferPropertyTemplateTest`
- [ ] **Step 3: Implement** the template and copy the CSS.
- [ ] **Step 4: Run, expect PASS**
- [ ] **Step 5: Commit** — `feat(npc): offer-property page`

---

### Task 6: JS enhancer, docs, full build

**Files:**
- Create: `.../public/assets/js/offer-property.js`
- Modify: `CLAUDE.md` (a *Proposer un bien* bullet after *Trouver un bien* in the `ndopify-partner-central` section;
  mention `availableFrom` in the mandate domain paragraph)

**Interfaces:**
- Consumes: Task 5's markup; `GET /locations` (`LocationController`).

The served script only enhances, like `public/assets/js/find-property.js` (start from it, not from the prototype's
state machine — no `sessionStorage`, no history slides, no simulated server): slim-select over `/locations` for the
city (type-ahead) and the neighbourhoods of the chosen city (single choice); "150 000" formatting on rent and area;
features: show only the questions that apply to the chosen category/type (`needsFurniture` / `needsRooms` /
`needsParking` / `needsArea`, **same rules as `OfferPropertyRules`** — say so in a comment) and the number of places
only after "Oui", disabling hidden inputs so they don't post; intl-tel-input posting E.164 into the hidden
`whatsappNumber`; "Suivant" inactive until the step's required fields are filled; the review's submit can't be pressed
twice ("Envoi en cours…").

- [ ] **Step 1: Write the script**, porting the prototype's field logic step by step.
- [ ] **Step 2: Run the app and walk the wizard** — `cd modules/ndopify-server && mvn spring-boot:run`, then
  `cd modules/ndopify-partner-central && mvn spring-boot:run`, open `http://localhost:8081/offer-property`: complete it
  once with JS, once with JS disabled (every step must still post), check the mandate in
  `GET http://localhost:8080/v1/mandates/{id}` has `mandateType: LISTING` and `availableFrom`.
- [ ] **Step 3: Update `CLAUDE.md`.**
- [ ] **Step 4: Full verification** — `mvn install -Dheadless=true` at the root (ktlint + tests + JaCoCo gates). Expected:
  BUILD SUCCESS.
- [ ] **Step 5: Commit** — `feat(npc): offer-property enhancer and docs`

---

## Out of scope (known gaps, not to fix here)

- There is no LISTING contract template (`MandateContractGenerator.TEMPLATES` only has SEARCH): requesting the
  approval of a LISTING mandate fails until one is written.
- ndopify-server still doesn't validate mandates: `OfferPropertyRules` is the only validation a listing gets.
- No entry point links to `/offer-property` yet (website / login page).
