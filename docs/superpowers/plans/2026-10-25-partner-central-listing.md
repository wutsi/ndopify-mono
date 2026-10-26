# Partner Central Listing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the listing prototype (`listing.html` + `listing/*.html`) from Partner Central: the agent creates the
listing of an approved LISTING mandate, completes its checklist on site (six forms + photos), then sends it for
verification, all against ndopify-server's `/v1/listings`.

**Architecture:** A new `ui/listing/` feature package, built like `ui/mandate/`: `ListingController` (checklist page,
create, send), `ListingSectionController` (one form per `ListingSection`), `ListingPhotoController` (upload / delete),
pure `ListingRules` / `ListingViews`, one `NdListingClient`. No session state: the listing on ndopify-server *is* the
state, each form posts the `version` it was loaded with (optimistic lock, like the mandate edit page). The mandate
page's *Actif* panel and the home card's next step get the listing's status. The prototype's JS becomes an enhancer
(`listing.js`).

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, Spring `RestClient`, Apache Tika, JUnit 5 + mockito-kotlin, MockMvc;
ndopify-server endpoint tests against MySQL.

**Spec:** the prototype — `modules/ndopify-partner-central/src/main/html/listing.html`, `src/main/html/listing/*.html`,
`src/main/html/mandate.html` (the *Actif* panel's `data-listing-actions`), and the listing part of
`src/main/html/assets/js/partner-central.js` (`LISTING_SECTIONS` … `showListingReadonly`, lines ~2222–2990: copy,
rules, error messages) — plus the *listing* domain paragraph of `modules/ndopify-server/CLAUDE.md`.

## Global Constraints

- Never modify or delete anything under `modules/ndopify-partner-central/src/main/html/` (the prototype); copy from it.
  The working tree already has an uncommitted change to `src/main/html/assets/js/partner-central.js` (`formatRent`):
  leave it as is, it's the user's.
- Copy, labels and error messages verbatim from the prototype (French). Dates and amounts through `TenantFormatter`;
  the currency through `formatter.currency()` — the prototype's "FCFA / mois" becomes `"${currency} / mois"`.
- Every page works without JavaScript; `listing.js` only enhances, and every rule it mirrors is re-checked by
  `ListingRules`.
- `kotlin.test` assertions, `mockito-kotlin` mocks; `mvn validate` (ktlint) and `mvn verify` (JaCoCo) pass in both
  modules.
- Pinned values: `MIN_PHOTOS = 5` (same as `ListingSectionService.MIN_PHOTOS`), `MAX_PHOTOS = 20`, photo ≤ 5 Mo,
  JPEG / PNG / WEBP only (sniffed with Tika), `availableFrom` ≤ today + 365 days in the tenant's time zone (`Clock`),
  `landmark` ≤ 100 chars, `accessDirections` ≤ 500, `agentRemarks` ≤ 1000.
- Routes: `GET /listings/{id}`, `GET|POST /listings/{id}/{section}` with `section` ∈ `location`, `gps`, `property`,
  `amenities`, `terms`, `remarks` (the `ListingSection` name, lower case), `GET /listings/{id}/photos`,
  `POST /listings/{id}/photos`, `POST /listings/{id}/photos/{imageId}/delete`, `POST /listings/{id}/verify`,
  `POST /mandates/{id}/listing` (create). All require authentication (nothing to add to `SecurityConfiguration`).
- **Access**: ndopify-server doesn't check who reads or writes a listing. Every listing page and action loads the
  listing and requires `listing.agentId == currentAgent.id`; otherwise it's the locked view (GET) or the `changed`
  outcome (POST). Agent id from the token (`CurrentAgent`), never from a form.
- Out of scope (no server support): the prototype's "À corriger" / `REJECTED` panel and rejection reason, the
  "Simuler …" prototype buttons, the AI-generated texts beyond showing `title` / `summary` when the server has them.

## Review Focus

- A listing that isn't the signed-in agent's (another agent's id typed in the URL, or posted to): locked view / no
  write, since ndopify-server would accept the update (Tasks 4, 5, 6).
- A form posted after the listing changed (another tab, or sent meanwhile): 409 `LISTING_VERSION_CONFLICT` shows the
  form again with the agent's answers and the new version; 409 `LISTING_NOT_DRAFT` goes back to the listing with the
  `changed` outcome — never a 500 (Task 5).
- Without JS, the *Le bien* form shows every count question: those that don't apply to the posted type
  (`ListingRules.applies`) are sent as `null`, never errors (Task 3).
- Hand-edited values (`rooms=abc`, `availableFrom=2026-02-31`, `latitude=200`, an unknown `section` slug, a non-numeric
  `{id}`): field errors or 404/locked, never a 500 or a binding 400 (Tasks 3, 5).
- Deleting an image id that isn't one of this listing's (`DELETE /v1/listings/images/{id}` doesn't check): refused by
  checking it against `GET /v1/listings/{id}/images` first (Task 6).

---

## File Structure

ndopify-dto / ndopify-server (Task 1, finishing the uncommitted `verify` work):
- `ndopify-dto/.../error/dto/ErrorCode.kt` — add `LISTING_INCOMPLETE`.
- `ndopify-server/.../listing/server/service/ListingService.kt` — `verify` checks the checklist.
- `ndopify-server/src/test/.../listing/server/endpoint/VerifyListingEndpointTest.kt` + `db/test/listing/VerifyListingEndpoint.sql`.

ndopify-partner-central (`com.wutsi.ndopify.npc`):
- `client/ndopify/NdListingClient.kt` — the `/v1/listings` calls.
- `ui/listing/ListingRules.kt` — sections, statuses, tags, applicability, field validation, `UpdateListingRequest` building.
- `ui/listing/ListingSectionForm.kt` — the one form every section posts (raw strings, parsed by the rules).
- `ui/listing/ListingViews.kt`, `ListingView.kt` — checklist page and section page view models.
- `ui/listing/ListingController.kt`, `ListingSectionController.kt`, `ListingPhotoController.kt`.
- `ui/kyc/KycUploadErrorHandler.kt` — also redirect a too-large listing photo.
- `ui/mandate/MandateViews.kt`, `MandateView.kt`, `MandateRules.kt`, `MandateController.kt`, `ui/home/HomeController.kt`,
  `ui/mandate/MandateCards.kt` — the listing on the mandate page and on the home card.
- `templates/listing.html`, `templates/listing/{location,gps,property,amenities,terms,remarks,photos}.html`,
  `templates/mandate.html`.
- `public/assets/js/listing.js`, `public/assets/css/listing.css` (copied from the prototype's).
- Tests mirror each file under `src/test/kotlin/.../ui/listing/` and `client/ndopify/`.

---

### Task 1: ndopify-server — send a listing only once its checklist is done

The working tree already adds `ListingStatus.UNDER_VERIFICATION` / `VERIFIED`, `POST /v1/listings/{id}/verify` and
`ListingService.verify` (DRAFT → UNDER_VERIFICATION), untested, and `ndopify-dto.version` `0.0.66-SNAPSHOT`. Finish it:
the page's "Envoyer pour vérification" is only enabled when every required section is VERIFIED, and the server must
enforce the same rule.

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (after `LISTING_NOT_DRAFT`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt` (`verify`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt` (KDoc)
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/VerifyListingEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/listing/VerifyListingEndpoint.sql`
- Modify: `modules/ndopify-server/CLAUDE.md` (*listing* paragraph)

**Interfaces:**
- Produces: `ErrorCode.LISTING_INCOMPLETE = "$PREFIX:listing:incomplete"`; `POST /v1/listings/{id}/verify` → 200, or
  404 `LISTING_NOT_FOUND`, 409 `LISTING_NOT_DRAFT`, 409 `LISTING_INCOMPLETE` (checked in that order).

- [ ] **Step 1: Write the failing endpoint test** (`VerifyListingEndpointTest`, `AuthenticatedEndpointTest`-style
  base like `UpdateListingEndpointTest`, `@Sql(["/db/test/clean.sql", "/db/test/listing/VerifyListingEndpoint.sql"])`).
  SQL fixtures: listing 100 DRAFT with LOCATION, GPS, PROPERTY, AMENITIES, TERMS in `T_LISTING_SECTION` and 5
  `PENDING_VERIFICATION` images; 101 DRAFT missing GPS; 102 DRAFT complete but with 4 images + 1 REJECTED; 103
  `UNDER_VERIFICATION`. Tests:
  - `verify` — 100 → 200, reloaded `status == UNDER_VERIFICATION`, `modifiedAt` changed.
  - `missingSection` — 101 → 409 `LISTING_INCOMPLETE`, status still DRAFT.
  - `rejectedPhoto` — 102 → 409 `LISTING_INCOMPLETE`.
  - `notDraft` — 103 → 409 `LISTING_NOT_DRAFT`.
  - `notFound` — 999 → 404 `LISTING_NOT_FOUND`.
- [ ] **Step 2: Run** `cd modules/ndopify-server && mvn test -Dtest=VerifyListingEndpointTest` — expect FAIL on
  `missingSection` / `rejectedPhoto` (200 instead of 409), and a compile error until the `ErrorCode` exists.
- [ ] **Step 3: Implement** — add the error code; in `ListingService.verify`, after the DRAFT check, throw
  `ConflictException(Error(ErrorCode.LISTING_INCOMPLETE))` when any `sectionService.summaries(id)` entry is `required`
  and not `VERIFIED`. KDoc lists the three codes in order; the endpoint's KDoc says "Sends a DRAFT listing for
  verification". Update the CLAUDE.md *listing* paragraph: the verify endpoint, its errors, and that `ListingStatus`
  now has `UNDER_VERIFICATION` / `VERIFIED` (no code moves it past `UNDER_VERIFICATION` yet).
- [ ] **Step 4: Run** `mvn test -Dtest='*Listing*EndpointTest'` — PASS; then `mvn verify` in `ndopify-server`.
- [ ] **Step 5: Commit** (includes the user's pending `ListingStatus`, `ListingEndpoints`, `ListingService`,
  `pom.xml` changes) — `feat(listing): send a listing for verification once its checklist is done`. The DTO release
  (see `modules/ndopify-dto/CLAUDE.md`) is needed before deploying; the user does it.

### Task 2: `NdListingClient`

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt`

**Interfaces:**
- Produces (`@Service`, built like `NdMandateClient`; upload built like `NdKycClient.upload`):
  - `fun create(mandateId: Long): Long` — `POST /v1/listings` `{mandateId}` → `listingId`
  - `fun get(id: Long): Listing` — `GET /v1/listings/{id}`
  - `fun byAgent(agentId: Long, statuses: List<ListingStatus>): List<ListingSummary>` — `GET /v1/listings?agent-id=&status=…&limit=20`
  - `fun update(id: Long, request: UpdateListingRequest)` — `POST /v1/listings/{id}`
  - `fun verify(id: Long)` — `POST /v1/listings/{id}/verify`
  - `fun images(id: Long): List<ListingImage>` — `GET /v1/listings/{id}/images`
  - `fun upload(id: Long, content: ByteArray, mimeType: String, filename: String)` — multipart part `multipartFile`
  - `fun deleteImage(imageId: Long)` — `DELETE /v1/listings/images/{imageId}`

- [ ] **Step 1: Write `NdListingClientTest`** — `MockRestServiceServer.bindTo(RestClient.builder())`, one test per
  method asserting URL, method and JSON body (`create` sends `{"mandateId":203}` and returns 501; `byAgent` sends
  `agent-id=7&status=DRAFT&status=UNDER_VERIFICATION&limit=20`; `upload` is `multipart/form-data` with a part named
  `multipartFile` of the given content type).
- [ ] **Step 2: Run** `cd modules/ndopify-partner-central && mvn test -Dtest=NdListingClientTest` — FAIL (no class).
- [ ] **Step 3: Implement `NdListingClient`.**
- [ ] **Step 4: Run** the test — PASS.
- [ ] **Step 5: Commit** — `feat(npc): add the listing client`.

### Task 3: `ListingRules` and `ListingSectionForm`

**Files:**
- Create: `.../npc/ui/listing/ListingRules.kt`, `.../npc/ui/listing/ListingSectionForm.kt`
- Test: `.../npc/ui/listing/ListingRulesTest.kt`

**Interfaces:**
- Produces:
  - `data class ListingSectionForm(version: Long = -1, neighborhoodId: String?, landmark: String?, accessDirections: String?,
    latitude: String?, longitude: String?, accuracy: String?, propertyCategory: String?, propertyType: String?,
    furnitureType: String?, rooms: String?, bathrooms: String?, livingRooms: String?, kitchens: String?,
    parkings: String?, area: String?, levels: String?, floorLevel: String?, waterMeter: Boolean = false, … (the 11
    other amenity booleans), roadAccess: String?, price: String?, securityDeposit: String?, advanceRent: String?,
    leaseTerm: String?, noticePeriod: String?, availableFrom: String?, agentRemarks: String?)` — every value a
    `String?` so a hand-edited value is a field error, not a binding 400; `companion fun of(listing: Listing)`.
  - `object ListingRules`:
    - `val SECTIONS: List<ListingSection>` (checklist order, no `UNKNOWN`), `val FORM_SECTIONS` (without `PHOTOS`),
      `fun section(slug: String): ListingSection?` (`FORM_SECTIONS` only), `fun slug(section): String`
    - `val LABELS: Map<ListingSection, String>` and `val SAVED: Map<ListingSection, String>` — verbatim from
      `LISTING_SECTIONS` / `SECTION_SAVED`
    - `const val MIN_PHOTOS = 5`, `MAX_PHOTOS = 20`, `MAX_PHOTO_BYTES = 5L * 1024 * 1024`, `MAX_AVAILABILITY_DAYS = 365L`
    - `data class Tag(label: String, style: String)`; `fun tag(status: ListingStatus): Tag` — DRAFT `Brouillon`/`info`,
      UNDER_VERIFICATION, VERIFIED, PUBLISHING `En vérification`/`warning`, PUBLISHED `Publiée`/`success`, anything
      else `Fermée`/`neutral`
    - `fun editable(listing: Listing): Boolean` = DRAFT
    - `fun applies(question: String, category: PropertyCategory?, type: PropertyType?): Boolean` — `LISTING_APPLIES`
      verbatim (`furniture`, `rooms`, `bathrooms`, `kitchens`, `parkings`, `levels`, `floorLevel`); `livingRooms` follows `rooms`
    - `fun typeFits(category, type): Boolean` — the prototype's `data-categories` (LAND is COMMERCIAL)
    - `fun errors(section: ListingSection, form: ListingSectionForm, today: LocalDate, formatter: TenantFormatter): Map<String, String>`
    - `fun request(section: ListingSection, form: ListingSectionForm): UpdateListingRequest` — only the section's
      fields, parsed; questions that don't `apply` → `null`; `roadAccess` → exactly one of the three booleans
    - `fun missing(listing: Listing): List<String>` — labels of required sections not VERIFIED (from `listing.sections`)

- [ ] **Step 1: Write `ListingRulesTest`** — one test per rule, messages verbatim from `LISTING_RULES`:
  - `location`: no neighbourhood → `neighborhoodId` "Choisissez le quartier du bien."; blank landmark → "Indiquez un
    point de repère connu : école, carrefour, commerce…"; 101-char landmark, 501-char directions → errors.
  - `gps`: blank → `latitude` "Relevez la position GPS depuis le bien."; `latitude=200` / `abc` → same field error.
  - `property`: no category / type messages; `COMMERCIAL` + `APARTMENT` → `propertyType` error; RESIDENTIAL APARTMENT
    without `rooms` → "Indiquez ce nombre, compté sur place."; `rooms=abc` → "Indiquez un nombre entier."; `area=0` →
    "Indiquez la surface en m²."; `STUDIO` with `rooms=abc` → no error and `request(...).rooms == null`.
  - `amenities`: no `roadAccess` → `roadAccess` "Indiquez l'accès routier au bien."; `request` with
    `roadAccess=trackAccessibleByCar` sets only that boolean true; an unknown value → error.
  - `terms`: blank price → "Indiquez le loyer mensuel."; `150k` → "Indiquez le loyer en chiffres, en FCFA par mois."
    (tenant fixture's currency); `150 000` → `request.price == 150000`; no deposit → "Choisissez la caution demandée.";
    no date → "Indiquez la date à laquelle le bien est libre."; today+366 → "Choisissez une date dans les 12 prochains
    mois."; `2026-02-31` → the "Indiquez la date…" error.
  - `remarks`: 1001 chars → error; blank → `request.agentRemarks == null`.
  - `section("photos") == null`, `section("nope") == null`; `tag(...)` per status; `missing(...)` returns labels in
    checklist order and ignores REMARKS.
- [ ] **Step 2: Run** `mvn test -Dtest=ListingRulesTest` — FAIL.
- [ ] **Step 3: Implement** — reuse `FindPropertyRules.parseAmount` for the price; dates with `LocalDate.parse` in a
  `runCatching`.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `feat(npc): add the listing rules`.

### Task 4: Checklist page, create, send

**Files:**
- Create: `.../npc/ui/listing/ListingView.kt`, `ListingViews.kt`, `ListingController.kt`
- Create: `src/main/resources/templates/listing.html` (from `src/main/html/listing.html`)
- Create: `src/main/resources/public/assets/css/listing.css` (copy of the prototype's)
- Test: `ListingViewsTest.kt`, `ListingControllerTest.kt`, `ListingTemplateTest.kt`

**Interfaces:**
- Consumes: `NdListingClient` (Task 2), `ListingRules` (Task 3), `NdMandateClient.get`, `CurrentAgent`, `SectorValidator.locations`.
- Produces:
  - `data class ListingView(id, mandateId, title: String, tag: ListingRules.Tag, status: ListingStatus, createdAt: String,
    sentAt: String?, publishedAt: String?, generatedTitle: String?, generatedSummary: String?, rows: List<ChecklistRow>,
    progress: String, missing: String, sendable: Boolean, editable: Boolean)`;
    `data class ChecklistRow(slug, label, summary, state: ListingRules.Tag)`
  - `ListingViews.of(listing: Listing, images: List<ListingImage>, locations: Map<Long, Location>, formatter): ListingView`
    — title "Annonce · {short type}, {neighbourhood or city}" (`MandateLabels.shortPropertyType`), row summaries =
    `sectionSummary` (amounts via `formatter`), row states = `sectionState` without the "À corriger" branch, progress
    "{n} sections sur 6 vérifiées", missing "Encore à faire : … ." / "Tout est vérifié : vous pouvez envoyer
    l'annonce.", `sentAt` = `modifiedAt` while UNDER_VERIFICATION (no `submittedAt` on the server: nothing changes a
    listing once sent).
  - `ListingController`: `GET /listings/{id}` (`id: String`), `POST /listings/{id}/verify`, `POST /mandates/{id}/listing`;
    `const val OUTCOME = "outcome"`, outcomes `sent`, `changed`, `error`, plus `saved-<slug>` set by Task 5.

- [ ] **Step 1: Write the tests.**
  - `ListingViewsTest`: titles, each row's summary and state, progress and missing for a listing with GPS left and 3
    photos ("Encore à faire : Position GPS, 2 photos."), dates through `TenantFormatter`.
  - `ListingControllerTest` (standalone MockMvc, mocked clients):
    `show` → view `listing`, model `listing`; another agent's listing → `locked`; 404 / `abc` id → `locked`;
    `verify` → calls `verify(id)`, redirect `/listings/{id}`, flash `sent`; 409 → `changed`; another agent's → no call,
    `changed`; `create` on an APPROVED LISTING mandate → `create(mandateId)`, redirect `/listings/501`; 409
    `LISTING_ALREADY_EXISTS` → redirect to the mandate's `listingId`; a mandate not APPROVED / not LISTING / not the
    agent's → redirect `/mandates/{id}` with `changed`, no call.
  - `ListingTemplateTest` (real Thymeleaf): the 7 rows link to `/listings/{id}/{slug}` (photos → `/photos`), the send
    form posts to `/listings/{id}/verify` and its button is `disabled` with the missing text while not `sendable`, the
    UNDER_VERIFICATION panel shows "Envoyée le …", the locked view, the flash text "Annonce envoyée. Nous vous
    préviendrons dès sa publication.", and the account menu guarded by `signedIn`.
- [ ] **Step 2: Run** `mvn test -Dtest='Listing*Test'` — FAIL.
- [ ] **Step 3: Implement** the view model, controller (errors / lookups handled like `MandateController.act` /
  `lookup`; `ProfileRules.header`) and template (drop the prototype's REJECTED panel and simulate buttons; the
  "Retour au mandat" link is `/mandates/{mandateId}`; `body data-page="listing"`).
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `feat(npc): add the listing checklist page`.

### Task 5: Section pages

**Files:**
- Create: `.../npc/ui/listing/ListingSectionController.kt`
- Modify: `ListingViews.kt` (add `section(...)`), `ListingView.kt` (add `SectionView`)
- Create: `templates/listing/{location,gps,property,amenities,terms,remarks}.html` (from the prototype's French-named pages)
- Test: `ListingSectionControllerTest.kt`, `ListingSectionTemplateTest.kt`

**Interfaces:**
- Consumes: Task 3 (`errors`, `request`, `ListingSectionForm.of`), Task 4 (`OUTCOME`), `NdMandateClient.get` for the
  landlord's answers, `NdLocationClient.search` for the city's neighbourhoods (`type=NEIGHBORHOOD`, `parentId=cityId`).
- Produces:
  - `data class SectionView(listingId, slug, meta: String, editable: Boolean, readonlyText: String?, ownerHints: Map<String, String>)`
  - `ListingViews.ownerHints(form: ListingSectionForm, mandate: Mandate?, formatter): Map<String, String>` —
    "Indiqué par le propriétaire : …" for each field whose current value differs from the mandate's (`ownerText`
    formats); empty without a mandate.
  - `GET /listings/{id}/{section}` → view `listing/<slug>`; `POST /listings/{id}/{section}` → redirect `/listings/{id}`
    with flash `OUTCOME = "saved-<slug>"` (the checklist page shows `ListingRules.SAVED[section]`).

- [ ] **Step 1: Write the tests.**
  - Controller: GET pre-fills `answers` from the listing; an unknown slug or `photos` → 404 view; a non-DRAFT listing
    → `editable=false` (read-only); POST valid → `update(id, request)` with the posted `version`, redirect + flash;
    invalid → same view with `errors` and the agent's `answers`, no call; neighbourhood outside the city
    (`SectorValidator.errors`) → error; 409 `LISTING_VERSION_CONFLICT` → view again, answers kept, `version` = the
    reloaded listing's, `error` "Cette annonce a été modifiée entre-temps. Vos réponses sont conservées : vérifiez-les,
    puis enregistrez à nouveau."; 409 `LISTING_NOT_DRAFT` → redirect `/listings/{id}` `changed`; another agent's
    listing → `locked`, no call.
  - Template (one test per section): the form posts to `/listings/{id}/{slug}` with a hidden `version`, the fields are
    named as `ListingSectionForm`, errors render under `#<field>-error`, owner hints show, read-only mode has no
    submit button and disabled inputs. `gps.html` renders visible `latitude` / `longitude` inputs (no-JS path) that
    `listing.js` hides behind the "Relever la position" button; `terms.html`'s unit is "{currency} / mois";
    `property.html` without JS shows every count field.
- [ ] **Step 2: Run** `mvn test -Dtest='ListingSection*Test'` — FAIL.
- [ ] **Step 3: Implement** controller, views and the six templates (the read-only "answers only" view of
  `showListingReadonly` done server-side: unanswered groups left out, inputs `disabled`).
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `feat(npc): add the listing section pages`.

### Task 6: Photos

**Files:**
- Create: `.../npc/ui/listing/ListingPhotoController.kt`, `templates/listing/photos.html`
- Modify: `.../npc/ui/kyc/KycUploadErrorHandler.kt` — `/listings/{id}/photos` → `redirect:/listings/{id}/photos?error=too-large`
- Test: `ListingPhotoControllerTest.kt`, `ListingPhotoTemplateTest.kt`, `KycUploadErrorHandlerTest` (add a case)

**Interfaces:**
- Consumes: `NdListingClient.images|upload|deleteImage`, `ListingRules.MIN_PHOTOS|MAX_PHOTOS|MAX_PHOTO_BYTES`.
- Produces: `GET /listings/{id}/photos` (model `photos`: url, alt "Photo n", note "Vérifiée" / "Envoyée" / the
  rejection's `errorMessage`, `count` line from `initListingPhotos`), `POST /listings/{id}/photos` (part `photo`,
  `multiple`), `POST /listings/{id}/photos/{imageId}/delete`; both redirect to `/listings/{id}/photos`.

- [ ] **Step 1: Write the tests** — upload of a JPEG → `upload(...)` with Tika's sniffed type; a PDF renamed `.jpg` →
  error "x.jpg n'est pas une photo JPEG, PNG ou WEBP."; 6 Mo → "x.jpg dépasse 5 Mo."; at 20 photos → "20 photos au
  plus.", no call; `?error=too-large` → the size message; delete of an image of the listing → `deleteImage`; an image
  id not in `images(id)` → no call, `changed`; non-DRAFT → no actions rendered, POSTs `changed`; another agent's →
  `locked`. Template: count line "3 photos · encore 2 au moins", the upload form is `multipart/form-data` with a
  submit button (no-JS), each photo's delete is a `<form method="post">`.
- [ ] **Step 2: Run** `mvn test -Dtest='ListingPhoto*Test,KycUploadErrorHandlerTest'` — FAIL.
- [ ] **Step 3: Implement** (Tika as in `KycController`; errors passed back as a flash attribute `photoError`).
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `feat(npc): add the listing photos page`.

### Task 7: The listing on the mandate page and the home card

**Files:**
- Modify: `.../ui/mandate/MandateView.kt` (add `listing: MandateListingView?`), `MandateViews.kt`, `MandateController.kt`
  (load `listingClient.get(mandate.listingId)` through `lookup`), `templates/mandate.html` (*Actif* panel)
- Modify: `.../ui/mandate/MandateRules.kt` (`nextStep` takes `listing: ListingSummary?`), `MandateCards.kt`,
  `.../ui/home/HomeController.kt` (one `listingClient.byAgent(agentId, [DRAFT, UNDER_VERIFICATION, VERIFIED, PUBLISHING])`, by `mandateId`)
- Test: `MandateViewsTest`, `MandateTemplateTest`, `MandateRulesTest`, `HomeControllerTest`, `MandateControllerTest`

**Interfaces:**
- Produces: `data class MandateListingView(id: Long?, text: String, action: String, primary: Boolean)` — shown only for
  an APPROVED LISTING mandate.

- [ ] **Step 1: Write the tests** — wording verbatim from the prototype's `renderListing` / `nextStep`:
  - no listing: text "Visitez le bien pour vérifier ses caractéristiques et prendre les photos : l'annonce est publiée
    une fois vérifiée.", a `<form method="post" action="/mandates/{id}/listing">` button "Créer l'annonce";
  - DRAFT: "Annonce en brouillon : encore à faire, … ." / "prête à être envoyée.", link "Continuer l'annonce" (primary);
  - UNDER_VERIFICATION: "Annonce envoyée le {date}, en vérification.", link "Voir l'annonce" (outline);
  - PUBLISHED: "Annonce publiée le {date}.";
  - listing lookup failing → the block is left out, page still renders;
  - `MandateRules.nextStep` for an APPROVED LISTING: no listing → "Créez l'annonce du bien, puis visitez-le pour la
    compléter."; DRAFT → "Complétez l'annonce, puis envoyez-la pour vérification."; UNDER_VERIFICATION → "Annonce en
    vérification : elle sera publiée sous peu."; otherwise the current "Trouvez un locataire …".
  - `HomeController`: when the listing search fails, the listings are *unknown*, not absent — `nextStep` takes
    `listings: Map<Long, ListingSummary>?` (null = unknown) and an unknown keeps today's "Trouvez un locataire …"
    wording instead of asking to create a listing that may exist. Assert both cases.
- [ ] **Step 2: Run** `mvn test -Dtest='Mandate*Test,HomeControllerTest'` — FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Commit** — `feat(npc): show the listing on the mandate page and card`.

### Task 8: `listing.js` and documentation

**Files:**
- Create: `src/main/resources/public/assets/js/listing.js` (enhancer for `data-page` `listing`, `listing-section`,
  `listing-photos`)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (*Pages*: a *Listing* paragraph), `ui/` package list
- Test: `ListingSectionTemplateTest` (script tag present)

- [ ] **Step 1:** Port from the prototype only what enhances: `initListingProperty` (hide types of the other category
  and questions that don't `apply`), `initListingGps` (geolocation button, accuracy warning > 50 m, OpenStreetMap
  link; hides the manual inputs), owner hints live update (`syncOwnerHints`), photo shrink + auto-submit on change
  (as `kyc.js`), the field checks of `LISTING_RULES` before submit. No `store` / session code, no simulate buttons;
  amounts formatted with the body's `data-currency`.
- [ ] **Step 2:** Check manually: `mvn spring-boot:run` (server + partner-central), create a listing from an APPROVED
  LISTING mandate, save every section with JS on and off (browser setting), add 5 photos, send. Expected: the checklist
  goes to 6/6, "Envoyer" enables, then the *En vérification* panel.
- [ ] **Step 3:** Write the CLAUDE.md *Listing* paragraph (routes, access check by `agentId`, version posting, photos
  limits, what's out of scope) in the style of the *mandate page* one.
- [ ] **Step 4: Run** `mvn verify` in `ndopify-partner-central` — PASS (ktlint, tests, JaCoCo).
- [ ] **Step 5: Commit** — `feat(npc): enhance the listing pages, document them`.
