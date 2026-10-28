# ndopify-partner-central

The agent-facing web app (port 8081, French UI): a Spring Boot + Thymeleaf BFF with no database, which talks to
`ndopify-server` over REST (`ndopify.server.api.base-url`). It depends on `ndopify-dto` only.

## Code Organization

Package `com.wutsi.ndopify.npc`:

- `ui/<feature>/` — one package per page or wizard (`login`, `join`, `kyc`, `home`, `profile`, `mandate`,
  `assignment`, `listing`, `findproperty`, `offerproperty`, `location`, `logout`), shared page infrastructure in `ui/layout/`
  (`SignedInAdvice`, `HttpClientErrorHandler`). A feature package holds:
  - `<Feature>Controller` — the MVC controller. It renders one template, reads and writes the session, calls the
    clients, and redirects (post-redirect-get, with a flash attribute for the outcome).
  - `<Feature>Rules` — an `object` with the page's validation and display rules, pure functions, unit tested.
  - `<Feature>Form(s)` — the `data class`es a step's `POST` binds to.
  - `Pending<Thing>` — a wizard's answers, kept in the HTTP session under a `SESSION_ATTRIBUTE` constant
    (`npc.<thing>`), with `isComplete(step)` / `firstIncompleteStep()`; `Submitted<Thing>` for its confirmation.
  - `<Feature>Views` → `<Feature>View` — build the template's view model from the DTOs (labels, formatted dates).
- `client/ndopify/Nd<Entity>Client` — one `@Service` per ndopify-server resource, built on the Spring
  `RestClient.Builder` (so the interceptors apply); `NdTenantClient` is the exception (plain `RestTemplate`,
  tenants are global). `client/` also holds the logging, `Authorization` and `X-Tenant-ID` interceptors, added by
  `config/RestConfiguration`.
- `service/` — `TenantContext`, `TenantFormatter`; `service/security/` — the JWT cookie (`AccessTokenService`),
  `JwtDecoder`, `JwtAuthenticationFilter`, and the signed-in user (`CurrentParty`, `CurrentAgent`).
- `config/` — security (which paths are public), REST clients, `Clock`.

Resources: one Thymeleaf template per page in `src/main/resources/templates/` (sub-pages in a folder named after
the feature, ex: `profile/edit.html`; `error/403.html` / `404.html`), and page scripts in `public/assets/js/`, one per
page (`<page>.js`, picked by the body's `data-page`) plus `partner-central.js` / `util.js`. Every page works without
JS: a script only enhances it, and the rules it mirrors are always re-checked server-side by `<Feature>Rules`.
`src/main/html/` is the prototype (see below).

## Testing Patterns

Plain unit tests, no Spring context, no server: `mvn test` needs neither MySQL nor ndopify-server. Tests mirror the
main package, with `mockito-kotlin` (`com.nhaarman.mockitokotlin2`) mocks and `kotlin.test` assertions.

- `<Feature>ControllerTest` — `MockMvcBuilders.standaloneSetup(controller)` with the mocked clients and an
  `InternalResourceViewResolver`: asserts the view name, the model (`result.modelAndView!!.model`), the redirect and
  the flash attributes, and what was sent to the clients (`argumentCaptor`). ndopify-server errors are simulated with
  `HttpClientErrorException` whose body converts to an `ErrorResponse` (`setBodyConvertFunction`).
- `<Feature>TemplateTest` — the same controllers through a real Thymeleaf engine (`SpringTemplateEngine` +
  `ClassLoaderTemplateResolver` on `templates/`), asserting on the rendered HTML: catches template mistakes the
  controller tests can't see. Each page has one (login excepted, so far).
- `<Feature>RulesTest`, `Pending<Thing>Test`, `<Feature>ViewsTest` — the pure rules, case by case.
- `Nd<Entity>ClientTest` — `MockRestServiceServer.bindTo(RestClient.builder())`, asserting the URL, method and JSON
  body of each call and the parsing of the response.
- Shared fixtures are `object`s next to the tests (`TenantFixtures`, `ProfileFixtures`); `SignedInAdviceTest.signIn()`
  sets a signed-in user for a template test.

## Pages

- Sign-in is e-mail + one-time code on a single page, `templates/login.html` (`LoginController`: `/login` →
  `/login?step=code` → `/login/verify`). The code step is only shown while a `PendingLogin` (e-mail + expiry) is in
  the HTTP session, and verify reads the e-mail from there, not from the form. It calls
  `/v1/otp` then `/v1/auth` with `AuthType.OTP` and `ApplicationCode.PARTNER_CENTRAL`. The JWT is kept in the
  HTTP-only `__npc_access_token` cookie (`AccessTokenService`) and decoded by `JwtAuthenticationFilter`, which clears
  the cookie of a token that doesn't decode (expired) and marks the request `ACCESS_TOKEN_REJECTED`, so that
  `AuthorizationClientHttpRequestInterceptor` doesn't forward it (ndopify-server would answer 401); everything
  except `/assets/**`, `/login/**`, `/error`, `/locations/**`, `/join/**`, `/find-property/**`, `/offer-property/**` requires authentication. `POST /logout` (a form in the account menu, never a link: a GET could be forced by another site) clears the cookie
  and session, and so does a 401 from ndopify-server or a `SignInRequiredException` (`HttpClientErrorHandler`), which
  a page throws when `CurrentAgent` / `CurrentParty` finds nobody. Sign-in changes the session id.
  The header's account menu (`<nav class="user-menu">`) is wrapped in `th:if="${signedIn}"`, a model attribute
  that `SignedInAdvice` (`ui/layout/`) sets on every page: give any new page's menu the same guard.
- Tenant is resolved by `TenantContext` matching the request URL against each active tenant's `partnerCentralUrl`
  (from `GET /v1/tenants`). Dates and amounts shown to users go through `service/TenantFormatter` (built from the
  `Tenant`, like ndopify-server's): the tenant's `dateFormat` / `monetaryFormat` / `numberFormat`, first locale and
  `timeZone`, with no-break spaces; a field's unit or a message names the currency with `currency()` (the
  `currencySymbol`), never a hard-coded "FCFA". `mandate.js` reads it from the body's `data-currency`.
- `RestConfiguration` adds logging, `Authorization` and `X-Tenant-ID` interceptors to every Spring-built
  `RestClient`. `NdTenantClient` uses a plain `RestTemplate` instead, since tenants are global.
- Sign-up is the *Rejoindre Ndopify* wizard (`JoinController`, `ui/join/`), built like the login: one page
  (`join`), five steps (`identity` → `experience` → `area` → `payment` → `review`), `GET /join?step=…`
  shows a step and `POST /join/{step}` validates it (`JoinRules`) into the session's `PendingJoin`. A step only
  opens once all earlier ones are complete; the review submits the session (`POST /v1/agents`), never the page. The
  `templates/join.html` renders only the current step (each its own `<form>`), pre-filled from `answers`, with
  `errors` per field; `JoinTemplateTest` renders it with a real Thymeleaf engine. `public/assets/js/join.js`
  only enhances fields (slim-select city type-ahead and neighbourhoods over `/locations`, intl-tel-input posting
  E.164 through a hidden input, "same as WhatsApp"); every step still posts without it. `src/main/html/` (pages and
  assets, incl. the client-side wizard `rejoindre.js`) is the prototype: never serve it, and never delete or "clean
  up" anything in it. The login page links to `/join`.
- `LocationController` (`GET /locations`) is a public JSON proxy to `GET /v1/locations` for the city/neighbourhood
  dropdowns (slim-select): same query parameters, but only `CITY`/`NEIGHBORHOOD`, country forced to the tenant's,
  `limit` capped. Used by `join.js` and `find-property.js` on the wizards' area steps.
- *Trouver un bien* is `FindPropertyController` (`ui/findproperty/`), a **public** wizard for people looking for a
  rental, built like the join wizard: one page (`templates/find-property.html`), seven steps (`need` → `area` →
  `property` → `amenities` → `budget` → `contact` → `review`), `POST /find-property/{step}` validates
  (`FindPropertyRules`) into the session's `PendingMandate`, and the review submits the session as a TENANT/RENTAL
  mandate (`NdMandateClient` → `POST /v1/mandates`, price in the tenant's currency), then shows the confirmation while
  a `SubmittedMandate` is in the session. Since ndopify-server doesn't validate mandates yet, these rules and the
  city/neighbourhood checks are the only validation a mandate gets. "Indifférent" (`ANY`) is a UI-only answer, sent
  as `null`. Without JS the property step shows every follow-up question of the category and the server ignores
  those that don't apply to the type; `public/assets/js/find-property.js` hides them. Prototype:
  `src/main/html/find-property.html`.
- *Proposer un bien* is `OfferPropertyController` (`ui/offerproperty/`), a **public** wizard for owners offering a
  property for rent, built like *Trouver un bien*: one page (`templates/offer-property.html`), seven steps
  (`property` → `address` → `terms` → `features` → `amenities` → `contact` → `review`), `POST /offer-property/{step}`
  validates (`OfferPropertyRules`, the only validation a listing gets) into the session's `PendingListing`, and the
  review submits it as a LISTING / RENTAL mandate with a MONTHLY price in the tenant's currency (`NdMandateClient` →
  `POST /v1/mandates`), then shows the confirmation while a `SubmittedMandate` is in the session. `availableFrom` must
  be between today and 365 days later, in the tenant's time zone (`Clock`, so a saved date can expire). The header's
  *Quitter* and the confirmation's CTA go to `{publicPortalUrl}/owner` (none without a `publicPortalUrl`). Without JS
  every step posts; `public/assets/js/offer-property.js` only enhances (city / neighbourhood slim-select over
  `/locations`, "150 000", questions of the type, E.164 WhatsApp), mirroring `OfferPropertyRules`. Prototype:
  `src/main/html/offer-property.html`.
- KYC is `KycController` (`ui/kyc/`), one page (`templates/kyc.html`), reached from the Espace agent's banner. Wizard steps `front` →
  `back` → `selfie` → `review`: `GET /kyc?step=…` shows a step (never past the first photo the case lacks) and
  `POST /kyc/{step}` uploads that photo straight to the agent's PENDING_VERIFICATION case (`NdKycClient`), so the case is the
  state and the session holds no photos; `POST /kyc/review` submits it. The party is found by looking up the
  signed-in user (`CurrentParty` → `GET /v1/users/{id}`) and taking its newest case (`GET /v1/kyc/cases`, newest
  first). Once the case is no longer PENDING_VERIFICATION the page is a status view (`?step=confirmation` right after
  submitting); `POST /kyc/restart` opens a new case after a rejection. A user with no party is signed out (`SignInRequiredException`).
  Photos are sniffed with Tika (`tika-core`) and capped at 5 Mo (`KycUploadErrorHandler` catches the multipart
  limit). `public/assets/js/kyc.js` only enhances (shrinks photos, in-page selfie camera); every step posts without it.
- Login redirects to `/`, the *Espace agent* (`HomeController`, `ui/home/`, `templates/index.html`), whatever the
  KYC status: an agent who isn't `VERIFIED` gets a banner linking to `/kyc`, worded after its newest case (documents
  to send, under review, rejected), since the party's status only changes on verification. Its profile section shows the agent's photo, name,
  area and biography summary. The signed-in agent is `CurrentAgent` (`CurrentParty.id()`, the token's `partyId`, or
  `GET /v1/users/{id}` for a token without it → `GET /v1/agents?party-id=` → `GET /v1/agents/{id}`): agent and party ids always come from the token, never from a form, since ndopify-server doesn't
  check who updates an agent. The profile pages (`ProfileController`, `ui/profile/`) are open whatever the KYC
  status: `GET|POST /profile` (`templates/profile/edit.html`, `POST /v1/agents/{id}`; the name isn't editable, the party's e-mail is shown read-only until changing it checks ownership of both addresses) and
  `GET|POST /profile/photo` (`templates/profile/photo.html`, sniffed with Tika, 5 Mo, sent to `POST
  /v1/parties/{id}/photo`). Both go back to `/` with a `saved` flash attribute.
  `join.js` and `kyc.js` enhance them (`data-page` `profile-edit` / `profile-photo`). `SectorValidator`
  (`ui/location/`) is the city/neighbourhood check shared with the join wizard.
  A VERIFIED agent's Espace agent also lists the mandates offered to it (*Mandats proposés*): `HomeController` reads
  its PENDING assignments (`NdAssignmentClient` → `GET /v1/assignments?agent-id=&status=PENDING`, leaving out those
  past `expiresAt`), their mandates in one search (`GET /v1/mandates?offered-to=`, skipped without offer; an offer
  whose mandate isn't returned is left out, a failure is the section's "unavailable"), turned into `Mandate`s by
  `ui/mandate/MandateSummaries` for the labels, and the location names in one lookup, into `AssignmentView`s (`AssignmentViews`): no customer
  data (no id, no comments) before acceptance. `AssignmentController` posts `POST /assignments/{id}/accept|decline`
  and goes back to `/#assignments-title` with an `assignment` flash attribute (`accepted`, `declined`, `closed` for a 403/404/409,
  `error`); ndopify-server checks the caller is the offer's agent. Without JS, "Refuser" opens the confirmation via
  `GET /?decline={id}`; `public/assets/js/home.js` does it in place and counts the deadline down. The mandate labels
  are shared with the find-property review in `ui/mandate/MandateLabels`.
- A VERIFIED agent's Espace agent also lists its mandates in progress (*Mes mandats*): `HomeController` reads
  `GET /v1/mandates?agent-id=&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED` (search order, capped at 20),
  one call for every card: each summary carries its contract, for the next step's date (a card shows none without
  it); the location names
  of offers and mandates come in one lookup. Cards are `MandateCards` → `MandateCardView`; statuses, tags, party
  wording ("le client" / "le bailleur", after `mandateType`), dates and next
  steps are `ui/mandate/MandateRules`. Each card opens the mandate page, `MandateController` (`ui/mandate/`,
  `templates/mandate.html`): `GET /mandates/{id}` (`MandateViews` → `MandateView`: steps, panel of the status,
  contact from `GET /v1/customers/{id}`, criteria, "accepté le" from the ACCEPTED assignment); only the mandate
  itself is required (contract, customer, offer or names failing just leave their lines out), and a status outside
  `MandateRules.SHOWN` or a 404 is the locked view. Actions are post-redirect-get back to the page with an `outcome`
  flash attribute (`sent`, `resent`, `closed`, `cancelled`, `saved`, `changed` for a 404/409 or a mandate that moved
  on, `email-conflict` for request-approval's 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT`: the customer's e-mail is
  another account's, `error`), worded by the controller: `POST /mandates/{id}/request-approval|close|cancel`. The close / cancel
  form opens without JS via `GET /mandates/{id}?end=close|cancel` (ignored when the button wouldn't show) and comes
  back with its errors. "Envoyé le" is the contract's `signatureRequestedAt` (`MandateRules.requestedAt`), and the
  days given to sign are its deadline less that date (`MandateRules.signatureTtlDays`): partner-central has no TTL
  setting of its own, so it can't drift from ndopify-server's `ndopify.contract.signature.ttl-days`. The edit page (`GET|POST /mandates/{id}/edit`, `templates/mandate/edit.html`,
  `MandateEditForm` / `MandateEditRules`) is only for `MandateRules.EDITABLE` statuses (ASSIGNED, PENDING_APPROVAL),
  re-checked on POST (ndopify-server's update also refuses a signed or closed mandate: 409 `MANDATE_ALREADY_APPROVED` /
  `MANDATE_ALREADY_CLOSED`, shown as `changed`). Saving a PENDING_APPROVAL mandate cancels its contract and puts it back
  to ASSIGNED (server side): the outcome is `saved-unsent`, the agent sends it again. It posts the mandate's `version` it was
  loaded with (hidden field), and a 409 `MANDATE_VERSION_CONFLICT` shows the form again with the agent's answers, now
  based on the new version (the profile page does the same with `AGENT_VERSION_CONFLICT`); the city can't change, the
  neighbourhoods are checked by `SectorValidator`, and the rest by `FindPropertyRules` (the only validation a mandate
  gets). `public/assets/js/mandate.js` only enhances both pages (`data-page` `mandate` / `mandate-edit`). ndopify-server
  checks ownership (see the mandate domain in `modules/ndopify-server/CLAUDE.md`): another agent's mandate is a 403, which the page shows as the locked view
  (and an action as `changed`, like a 404/409). A 403 / 404 from ndopify-server that no page catches goes to
  `HttpClientErrorHandler` (`ui/layout/`), which answers the same status so that Spring Boot renders
  `templates/error/403.html` / `404.html` (also used for unknown URLs). These pages have no account menu: the signed-in
  agent isn't known in Spring Boot's error controller.
- A landlord's signed mandate (APPROVED, LISTING) has a listing: the mandate page's *Actif* panel shows its line
  (`ListingViews.forMandate` → `MandateListingView`, the listing loaded by `MandateController`, a failure leaving only
  the link): "Créer l'annonce" posts `POST /mandates/{id}/listing` (`ListingController.create` → `NdListingClient` →
  `POST /v1/listings`, a DRAFT pre-filled from the landlord's answers; one that exists, or 409
  `LISTING_ALREADY_EXISTS`, is opened instead), then where it stands and "Continuer / Voir l'annonce". The listing
  page, `GET /listings/{id}` (`templates/listing.html`, `ListingViews` → `ListingView`), is the checklist of the visit:
  one row per `ListingSection` (`ListingRules.SECTIONS`, linking to its page),
  each section done once ndopify-server's checklist says so (`Listing.sections`, `ListingRules.done`), which also
  says which sections are required (`ListingSectionSummary.required`, `ListingRules.required`), the photos' counts
  from `GET /v1/listings/{id}/images`. "Envoyer pour vérification" (`POST /listings/{id}/verify`) is inactive
  (`aria-disabled`) until every required section is done, and re-checked on POST (`incomplete` outcome, also for
  ndopify-server's 409 `LISTING_INCOMPLETE`) since the button still posts without JS; sent, the listing is read-only (PENDING_VERIFICATION, UNDER_VERIFICATION,
  VERIFIED, PUBLISHING: "En vérification", "Envoyée le" its `modifiedAt`, the server having no sent date; VERIFIED
  included since ndopify-server publishes it, the agent doesn't), then PUBLISHED with its title and summary (or a
  line saying it has none, when the server published it without generated content) and "Voir l'annonce en ligne",
  `{publicPortalUrl}{Listing.url}` (none without either). ndopify-server has no rejected status: a failed
  verification sends the listing back to DRAFT with its `errorCodes`, which this page shows "À corriger" (tag, panel
  `TO_FIX` listing the reasons, `ListingRules.rejections`, an unknown code or `INTERNAL_ERROR` as a generic one, and
  their sections marked "À corriger"), editable and sent again like any DRAFT; the mandate's line says "Corriger
  l'annonce". ndopify-server doesn't check who reads or writes a listing: the page requires `Listing.agentId` to be the
  signed-in agent's (else the locked view, or `changed` for an action), and create requires the mandate's agent.
  A listing ndopify-server can't return (5xx, network) is the `unavailable` view, not the locked one. Outcomes (flash
  `outcome`, shown on the locked and unavailable views too): `sent`, `incomplete`, `changed` (403/404/409 or moved
  on), `error`, `saved-<section>` ("Emplacement enregistré."…); a hand-edited id on a POST goes back to `/`. The
  ownership check is `ListingAccess` (found / locked / unavailable), shared by the three listing controllers.
  Each section is `ListingSectionController` (`GET|POST /listings/{id}/{slug}`, slug = the `ListingSection` in lower
  case: `location`, `gps`, `property`, `amenities`, `terms`, `remarks`; not `{section}`, which Spring would expose to
  the view over the `section` model attribute), one template for the six, `templates/listing/section.html`: the form
  (`ListingSectionForm`, all Strings so a hand-edited value is a field error) is pre-filled from the listing and posts
  its `version`; `ListingRules.errors` / `request` are the rules (the prototype's `LISTING_RULES`: only the section's
  fields are sent, a question of *Le bien* that doesn't apply to the type, `ListingRules.applies`, shown anyway
  without JS, as null), and the neighbourhood is checked against the listing's city (`SectorValidator`). A 409
  `LISTING_VERSION_CONFLICT` shows the form again with the agent's answers, now on the new version;
  `LISTING_NOT_DRAFT`, another agent's listing or one sent meanwhile go back to the checklist (`changed`). Under each
  field the agent changed, the landlord's answer from the mandate (`ListingViews.ownerHints`, server-side only). Once
  sent, a section is read-only: its answers only, disabled. GPS without JS is typed (latitude / longitude, "4,0335"
  accepted); `listing.js` takes the phone's position instead. Photos are `ListingPhotoController`
  (`GET|POST /listings/{id}/photos`, `POST /listings/{id}/photos/{imageId}/delete`, `templates/listing/photos.html`):
  sent straight to ndopify-server (`POST /v1/listings/{id}/images/upload`), sniffed with Tika, 5 Mo and 20 photos at
  most, the first problem as the `photoError` flash attribute; only one of the listing's own images is deleted
  (`DELETE /v1/listings/images/{id}` doesn't check). `KycUploadErrorHandler` sends an upload over the multipart limit
  back with `?error=too-large`. `public/assets/js/listing.js` only enhances the three pages (`data-page` `listing`,
  `listing-section`, `listing-photos`): the types and questions of the type, geolocation, photos shrunk and sent as
  soon as chosen, no double post. Prototype: `src/main/html/listing.html`, `listing/*.html`; its "Simuler" buttons
  have no server support, and its `REJECTED` panel is the `TO_FIX` one here.
- `src/main/html/` holds static mockups generated by the `/init-partner-central` command; the served pages are the
  Thymeleaf templates. `application-test.yml` / `application-prod.yml` still contain config copied from another
  project (`koki`) — don't treat it as meaningful.
