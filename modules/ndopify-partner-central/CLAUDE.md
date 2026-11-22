# ndopify-partner-central

The agent-facing web app (port 8081, French UI): a Spring Boot + Thymeleaf BFF with no database, which talks to
`ndopify-server` over REST (`ndopify.server.api.base-url`). It depends on `ndopify-dto` and `ndopify-web-core` only.
Its infrastructure (`TenantContext`, `TenantFormatter`, the REST interceptors, `NdTenantClient`, the access-token and
JWT classes) comes from `ndopify-web-core` (package `com.wutsi.ndopify.web.core`).

## Code Organization

Package `com.wutsi.ndopify.npc`:

- `ui/<feature>/` — one package per page or wizard (`login`, `join`, `kyc`, `home`, `profile`, `mandate`,
  `assignment`, `listing`, `match`, `visit`, `findproperty`, `offerproperty`, `location`, `logout`), shared page infrastructure in `ui/layout/`
  (`SignedInAdvice`, `HttpClientErrorHandler`). A feature package holds:
  - `<Feature>Controller` — the MVC controller. It renders one template, reads and writes the session, calls the
    clients, and redirects (post-redirect-get, with a flash attribute for the outcome).
  - `<Feature>Rules` — an `object` with the page's validation and display rules, pure functions, unit tested.
  - `<Feature>Form(s)` — the `data class`es a step's `POST` binds to.
  - `Pending<Thing>` — a wizard's answers, kept in the HTTP session under a `SESSION_ATTRIBUTE` constant
    (`npc.<thing>`), with `isComplete(step)` / `firstIncompleteStep()`; `Submitted<Thing>` for its confirmation.
  - `<Feature>Views` → `<Feature>View` — build the template's view model from the DTOs (labels, formatted dates).
- `client/ndopify/Nd<Entity>Client` — one `@Service` per ndopify-server resource, built on the Spring
  `RestClient.Builder` (so web-core's logging, `Authorization` and `X-Tenant-ID` interceptors, added by
  `config/RestConfiguration`, apply); web-core's `NdTenantClient` is the exception (plain `RestTemplate`, tenants
  are global).
- `service/security/` — the signed-in user (`CurrentParty`, `CurrentAgent`) and `SignInRequiredException`.
- `config/` — security (which paths are public), REST clients, `Clock`, and `WebCoreConfiguration`: the web-core
  beans (`TenantContext` on `TenantPortal.PARTNER_CENTRAL`, `AccessTokenService` on `__npc_access_token` as a session
  cookie, `JwtDecoder` accepting only `ApplicationCode.PARTNER_CENTRAL`) and `@Import(NdTenantClient)`.

Resources: one Thymeleaf template per page in `src/main/resources/templates/` (sub-pages in a folder named after
the feature, ex: `profile/edit.html`; `error/403.html` / `404.html`; fragments shared by several pages in
`fragments/`, ex: `fragments/mandate-kind.html`, the icon before a mandate's title — magnifying glass for a search,
key for a landlord's listing, from the views' `owner`; `fragments/agent-card.html`, the one standard card that names
another agent — photo or initials, name linked to `/agent?id=&back=…`, WhatsApp text link, no phone number — built by
`ui/agent/AgentCardViews.of`, used as a card footer on the visit, proposal and match cards and boxed (`standalone`) on
the visit and the read-only listing pages; a null `AgentCardView` renders "un autre agent"), and page scripts in
`src/main/assets/js/`, one per page (`<page>.js`, picked by the body's `data-page`) plus `partner-central.js` /
`util.js`. `direct-upload.js` is the
shared browser half of a direct upload: `NdDirectUpload.upload(action, file, options)` runs presign → PUT → attach
against `<action>/presign` and `<action>/attach` and resolves false on any failure, for the page to fall back to its
form post (the KYC and profile photo stories reuse it). Only a caller whose attach replaces an object (KYC, profile
photo) passes `{attachTimeout: true}`: the listing photos are added, so an attach that times out but lands must not
be followed by the multipart fallback, which would add a second photo. Every page works without
JS: a script only enhances it, and the rules it mirrors are always re-checked server-side by `<Feature>Rules`.
**CSS and JS ship as one bundle.** The sources are in `src/main/assets/{css,js}`, not served; `build-assets.mjs`
concatenates and minifies them with esbuild into `public/assets/dist/app.min.css` and `app.min.js` (build output
under `target/classes`, never committed), and `templates/fragments/head.html` is the only place that links them.
`frontend-maven-plugin` runs that build in `generate-resources` (Node is installed by Maven; a clean run needs network
for `npm ci`), so the bundles are ready before the tests read them. The order is fixed and deliberate — CSS
`partner-central`, `join`, `find-property`, `offer-property`, `home`, `listing`, `visit`, `profile`, `agent`, `telegram`;
JS `util`, `direct-upload`, `partner-central`, `home`, `join`, `kyc`, `listing`, `mandate`, `visit`, `find-property`,
`offer-property` — and it preserves every "loaded after" pair the per-page head used to give (a rule later in the CSS
wins). A page script is still an IIFE gated on `data-page`; it only ships in the shared bundle now. Images stay in
`public/assets/images`.
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
  `/v1/otp` then `/v1/auth` with `AuthType.OTP` and `ApplicationCode.PARTNER_CENTRAL`. A wrong code shows the attempts
  the account lock leaves (`error.data.remainingAttempts`, `OtpLockMessage.invalid`), and a locked account the wait
  (`error.data.lockedUntil`, `OtpLockMessage.locked`) on both steps: `/v1/auth` and `/v1/otp` answer 409
  `AUTH_ACCOUNT_LOCKED`, so the e-mail step says it too. The JWT is kept in the
  HTTP-only `__npc_access_token` cookie (`AccessTokenService`) and decoded by `JwtAuthenticationFilter`, which clears
  the cookie of a token that doesn't decode (expired) and marks the request `ACCESS_TOKEN_REJECTED`, so that
  `AuthorizationClientHttpRequestInterceptor` doesn't forward it (ndopify-server would answer 401); everything
  except `/assets/**`, `/sw.js`, `/offline.html`, `/manifest.json`, `/login/**`, `/error`, `/locations/**`, `/join/**`, `/find-property/**`, `/offer-property/**` requires authentication. `POST /logout` (a form in the account menu, never a link: a GET could be forced by another site) clears the cookie
  and session, and so does a 401 from ndopify-server or a `SignInRequiredException` (`HttpClientErrorHandler`), which
  a page throws when the caller isn't an agent (`CurrentAgent`) or has no party (`CurrentParty`). Sign-in changes the
  session id.
  The header's account menu is the one fragment `fragments/user-menu.html` on every signed-in page
  (`<nav th:replace="~{fragments/user-menu :: menu}"></nav>`): the signed-in agent's identity block — photo or
  initials, full name, e-mail, opening `/agent?id=<its id>` — then a divider and *Modifier mon profil* (`/profile`)
  and *Déconnexion* (`POST /logout`). `ProfileRules.header` fills its model attributes; the profile edit page also
  sets `accountCurrent = "profile"` for its `aria-current="page"`. Its `th:if="${signedIn}"` guard (a model attribute
  `SignedInAdvice` in `ui/layout/` sets on every page) lives in the fragment: include it, don't copy it.
- Tenant is resolved by `TenantContext` (web-core) matching the request URL against each active tenant's
  `partnerCentralUrl` (from `GET /v1/tenants`). Dates and amounts shown to users go through `TenantFormatter` (web-core, built from the
  `Tenant`, like ndopify-server's): the tenant's `dateFormat` / `monetaryFormat` / `numberFormat`, first locale and
  `timeZone`, with no-break spaces; a field's unit or a message names the currency with `currency()` (the
  `currencySymbol`), never a hard-coded "FCFA". `mandate.js` reads it from the body's `data-currency`.
- `RestConfiguration` adds logging, `Authorization` and `X-Tenant-ID` interceptors to every Spring-built
  `RestClient`. `NdTenantClient` uses a plain `RestTemplate` instead, since tenants are global.
- **PWA**: the app is installable from the browser menu (Android) or Share → *Sur l'écran d'accueil* (iOS).
  `public/manifest.json` (named `.json`, not `.webmanifest`: embedded Tomcat 11 has no MIME mapping for the latter) is
  linked from `fragments/head.html`, with the interim `/assets/images/icon.png` (500×500); the four polished icons are a
  later drop-in. `sw.js` is generated by `build-assets.mjs` from `src/main/assets/sw.js`: its `VERSION` is the first 12
  hex chars of `sha256(offline.html + logo.png)`, so an edited offline page or logo ships a new cache. The worker
  handles navigations (network first, `/offline.html` when offline) and the two precached URLs (`/offline.html`, the
  logo) only. `/offline.html` is a self-contained static page (inline CSS/JS/SVG, no webfont, no bundle), served for a
  failed navigation, with a retry link and one auto-reload once back online. **HTML and API responses are never
  cached**: authenticated, per-tenant, shared phones. `AssetCacheFilter` sets the headers — `/assets/dist/**` is
  `public, max-age=31536000, immutable`, the other `/assets/**` a day, everything else (pages, `/sw.js`,
  `/offline.html`, `/manifest.json`) `no-cache, must-revalidate`; Spring's content-version strategy appends `-<hash>`
  to the `@{…}` asset URLs, so a deploy reaches every browser instead of pinning a stale bundle.
- Sign-up is the *Rejoindre Ndopify* wizard (`JoinController`, `ui/join/`), built like the login: one page
  (`join`), five steps (`identity` → `experience` → `area` → `payment` → `review`), `GET /join?step=…`
  shows a step and `POST /join/{step}` validates it (`JoinRules`) into the session's `PendingJoin`. A step only
  opens once all earlier ones are complete; the review submits the session (`POST /v1/agents`), never the page. The
  `templates/join.html` renders only the current step (each its own `<form>`), pre-filled from `answers`, with
  `errors` per field; `JoinTemplateTest` renders it with a real Thymeleaf engine. `src/main/assets/js/join.js`
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
  (`FindPropertyRules`) into the session's `PendingMandate`, and the review submits the session as a SEARCH/RENTAL
  mandate (`NdMandateClient` → `POST /v1/mandates`, price in the tenant's currency), then shows the confirmation while
  a `SubmittedMandate` is in the session. Since ndopify-server doesn't validate mandates yet, these rules and the
  city/neighbourhood checks are the only validation a mandate gets. "Indifférent" (`ANY`) is a UI-only answer, sent
  as `null`. Without JS the property step shows every follow-up question of the category and the server ignores
  those that don't apply to the type; `src/main/assets/js/find-property.js` hides them. Prototype:
  `src/main/html/find-property.html`.
- *Proposer un bien* is `OfferPropertyController` (`ui/offerproperty/`), a **public** wizard for owners offering a
  property for rent, built like *Trouver un bien*: one page (`templates/offer-property.html`), seven steps
  (`property` → `address` → `terms` → `features` → `amenities` → `contact` → `review`), `POST /offer-property/{step}`
  validates (`OfferPropertyRules`, the only validation a listing gets) into the session's `PendingListing`, and the
  review submits it as a LISTING / RENTAL mandate with a MONTHLY price in the tenant's currency (`NdMandateClient` →
  `POST /v1/mandates`), then shows the confirmation while a `SubmittedMandate` is in the session. `availableFrom` must
  be between today and 365 days later, in the tenant's time zone (`Clock`, so a saved date can expire). The header's
  *Quitter* and the confirmation's CTA go to `{publicPortalUrl}/owner` (none without a `publicPortalUrl`). Without JS
  every step posts; `src/main/assets/js/offer-property.js` only enhances (city / neighbourhood slim-select over
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
  limit). `src/main/assets/js/kyc.js` only enhances (shrinks photos, in-page selfie camera); every step posts without it.
  **Direct upload** (#205): `kyc.html` loads `direct-upload.js` before `kyc.js`; on *Suivant*, `kyc.js` sends the shrunk
  photo (or the selfie) through `POST /kyc/{step}/presign` → PUT → `POST /kyc/{step}/attach` (JSON proxies to
  ndopify-server, 502 when it fails; like the form post, they open a case when the agent has none or a cancelled
  one, and answer 409 once it is submitted or when the step is ahead of the first missing photo; *Suivant* stays
  inactive meanwhile and the photo can't be changed; `direct-upload.js` aborts a stalled call, which falls back), clears the input and
  submits the form, which moves on as for a photo the case already has. Any failure (local storage: no url, a failed
  PUT or attach) posts the photo with the form: the multipart upload above stays the fallback, and the path without JS.
- Login redirects to `/`, the *Espace agent* (`HomeController`, `ui/home/`, `templates/index.html`), whatever the
  KYC status: an agent who isn't `VERIFIED` gets a banner linking to `/kyc`, worded after its newest case (documents
  to send, under review, rejected), since the party's status only changes on verification. Its profile section only
  invites the agent to add its photo or biography while one is missing (`photoMissing` / `biographyMissing`), or
  confirms a save: the agent doesn't need to see its own profile on each visit. The signed-in agent is `CurrentAgent`
  (`GET /v1/agents/me`: the server resolves the agent from the token): agent and party ids always come from the token,
  never from a form, since ndopify-server doesn't check who updates an agent. The profile pages (`ProfileController`,
  `ui/profile/`) are open whatever the KYC
  status: `GET|POST /profile` (`templates/profile/edit.html`, `POST /v1/agents/{id}`; the name isn't editable, the party's e-mail is shown read-only until changing it checks ownership of both addresses) and
  `GET|POST /profile/photo` (`templates/profile/photo.html`, sniffed with Tika, 5 Mo, sent to `POST
  /v1/parties/{id}/photo`). Both go back to `/` with a `saved` flash attribute. With JS, the photo goes straight to
  the storage: `POST /profile/photo/presign` → PUT → `POST /profile/photo/attach` (JSON proxies, the party taken from
  `CurrentAgent`, ndopify-server's status and, for attach, its error body; a 401 — never the sign-out redirect, which
  `fetch` would follow to a 200 — without a signed-in agent; `direct-upload.js` also takes a redirected answer, ex: the
  login redirect of an expired session, for a failure), then `GET /profile/photo/saved` sets the
  same flash and redirects to `/`; the multipart post stays the no-JS / local storage / failure path.
  `join.js` and `kyc.js` enhance them (`data-page` `profile-edit` / `profile-photo`); on `profile-photo` only,
  `kyc.js` first centre-crops the photo and shrinks it to a 400 px square (`PROFILE_SIDE`, white-filled, never
  enlarged), then its `initDirectUpload` submit handler sends it through `direct-upload.js` (loaded by
  `profile/photo.html`) and posts the form when it resolves false; the KYC wizard uploads directly too, through its own
  `initKycDirectUpload`, and its steps keep their shape capped at 2000 px (`MAX_SIDE`). `SectorValidator`
  (`ui/location/`) is the city/neighbourhood check shared with the join wizard.
  A VERIFIED agent's Espace agent also lists the mandates offered to it (*Mandats proposés*): `HomeController` reads
  its PENDING assignments (`NdAssignmentClient` → `GET /v1/assignments?agent-id=&status=PENDING`, leaving out those
  past `expiresAt`), their mandates in one search (`GET /v1/mandates?offered-to=`, skipped without offer; an offer
  whose mandate isn't returned is left out, a failure is the section's "unavailable"), turned into `Mandate`s by
  `ui/mandate/MandateSummaries` for the labels, and the location names in one lookup, into `AssignmentView`s (`AssignmentViews`): no customer
  data (no id, no comments) before acceptance. `AssignmentController` posts `POST /assignments/{id}/accept|decline`
  and goes back to `/#assignments-title` with an `assignment` flash attribute (`accepted`, `declined`, `closed` for a 403/404/409,
  `error`); ndopify-server checks the caller is the offer's agent. Without any offer (nor an outcome or "unavailable"
  to show) the section isn't rendered at all. Without JS, "Refuser" opens the confirmation via
  `GET /?decline={id}`; `src/main/assets/js/home.js` does it in place and counts the deadline down. The mandate labels
  are shared with the find-property review in `ui/mandate/MandateLabels`. The page opens with the triage header
  (`todayLabel`, `offerCount`, `urgentOfferCount`, `requestedCount`, `todayCount`, `todayHref`): the visible *Espace
  agent* title, the agent's day and links to the sections; `todayCount` includes the hosted visits, and `todayHref`
  points at their block when none of the agent's own visits is today. Offers are sorted by `expiresAt`;
  `AssignmentView.urgent` marks the under-15-minute ones, and only the first Accept is a primary button.
- A VERIFIED agent's Espace agent also lists its mandates in progress (*Mes mandats*): `HomeController` reads
  `GET /v1/mandates?agent-id=&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED` (search order, capped at 20),
  one call for every card, then the approved searches' activity in one
  `GET /v1/mandates/stats?mandate-id=&mandate-id=` (`NdMandateClient.stats`, skipped without such a card; a failure
  leaves the line out); the location names of offers and mandates come in one lookup. A landlord's signed mandate
  (`MandateRules.listed`) also gets its listing's line (`ListingViews.line` → `ListingLineView`: "Annonce" linking to
  the listing and its tag ("Publiée le …" in the tenant's `dateFormat`), or "À créer" linking to the mandate; no
  number, so that it fits one line on a phone): their listings come in one
  `GET /v1/listings?mandate-id=&mandate-id=&agent-id=` (`NdListingClient.searchByMandates`, skipped without such a
  mandate; a failure leaves the line out). A published listing's card also shows its activity ("2 personnes
  intéressées · 3 visites", `ListingStatsView`): how many clients accepted it and its visits, in one
  `GET /v1/listings/stats?listing-id=&listing-id=` (`NdListingClient.stats`, skipped without a published listing; a
  failure leaves the line out; zeros included). The
  summaries have no `errorCodes`, so a listing sent back to fix is "Brouillon" on the
  card, "À corriger" on the mandate page. Cards are `MandateCards` → `MandateCardView`; statuses, tags, party
  wording ("le client" / "le bailleur", after `mandateType`) and dates are `ui/mandate/MandateRules`; the card has no
  next step. Each card opens the mandate page, `MandateController` (`ui/mandate/`,
  `templates/mandate.html`): `GET /mandates/{id}` (`MandateViews` → `MandateView`: steps, panel of the status,
  contact from `GET /v1/customers/{id}`, criteria, "accepté le" from the ACCEPTED assignment); only the mandate
  itself is required (contract, customer, offer or names failing just leave their lines out), and a status outside
  `MandateRules.SHOWN` or a 404 is the locked view. The cancel action is post-redirect-get back to the page with an
  `outcome` flash attribute (`cancelled`, `changed` for a 404/409 or a mandate that moved on, `error`), worded by the
  controller: `POST /mandates/{id}/cancel`. The close action has its own page (see below); the cancel form opens
  without JS via `GET /mandates/{id}?end=cancel` (ignored when the button wouldn't show) and comes back with its
  errors. Sending the contract has its own page too (`GET|POST /mandates/{id}/contract`,
  `MandateContractController`, `templates/mandate/contract.html`, `MandateContractForm` / `MandateContractRules`),
  reached from *Envoyer au client* (ASSIGNED) / *Renvoyer* (PENDING_APPROVAL): the agent confirms *Durée* (1–12,
  default 6; a contract set through the API outside that range keeps its own value as an extra option) and *Frais de
  visite* (0 = none, in the tenant's currency), pre-filled from the current contract — never the defaults when the
  contract exists but can't be read — then submits `RequestMandateApprovalRequest`; its outcomes are `sent` /
  `resent`, `changed` (403/404/409), `email-conflict` for the 409 `CONTRACT_PARTY_MEMBERSHIP_CONFLICT` (the customer's
  e-mail is another account's) and `error`, and a mandate that isn't ASSIGNED / PENDING_APPROVAL redirects to its page.
  It works without JS. The mandate page's *Termes du contrat* section (once a contract exists) shows the duration and
  the fee (`Aucun frais de visite` when 0), then *Signé le* / *Valable jusqu'au* once signed. "Envoyé le" is the
  contract's `signatureRequestedAt` (`MandateRules.requestedAt`), and the
  days given to sign are its deadline less that date (`MandateRules.signatureTtlDays`): partner-central has no TTL
  setting of its own, so it can't drift from ndopify-server's `ndopify.contract.signature.ttl-days`. The edit page (`GET|POST /mandates/{id}/edit`, `templates/mandate/edit.html`,
  `MandateEditForm` / `MandateEditRules`) is only for `MandateRules.EDITABLE` statuses (ASSIGNED, PENDING_APPROVAL),
  re-checked on POST (ndopify-server's update also refuses a signed or closed mandate: 409 `MANDATE_ALREADY_APPROVED` /
  `MANDATE_ALREADY_CLOSED`, shown as `changed`). Saving a PENDING_APPROVAL mandate cancels its contract and puts it back
  to ASSIGNED (server side): the outcome is `saved-unsent`, the agent sends it again. It posts the mandate's `version` it was
  loaded with (hidden field), and a 409 `MANDATE_VERSION_CONFLICT` shows the form again with the agent's answers, now
  based on the new version (the profile page does the same with `AGENT_VERSION_CONFLICT`); the city can't change, the
  neighbourhoods are checked by `SectorValidator`, and the rest by `FindPropertyRules` (the only validation a mandate
  gets). The customer's contact block (`.contact-card`) is unboxed: role ("Client" / "Bailleur"), full name, "à appeler"
  when the mandate has a call window, the number as a `tel:` link with a compact WhatsApp button beside it, and the
  e-mail as a `mailto:` link. Its "Modifier" (only while `MandateRules.CUSTOMER_EDITABLE`, so not on a closed or
  cancelled mandate) opens the customer edit page (`GET|POST /mandates/{id}/customer/edit`,
  `templates/mandate/customer-edit.html`, `CustomerEditController`, `CustomerEditForm` / `CustomerEditRules`): first
  name, last name, e-mail and WhatsApp number, the phone through intl-tel-input / `util.js` and posted E.164, saved
  through `NdCustomerClient.update` (`POST /v1/mandates/{id}/customer`) so the correction shows on all of the
  customer's mandates. Its outcomes are `customer-saved` and, when the e-mail of a PENDING_APPROVAL mandate changed,
  `customer-saved-unsent` (ndopify-server cancelled the contract and put the mandate back to ASSIGNED); an e-mail
  another party owns is the e-mail field's error, a 403/404/409 `changed`, a 5xx the form again with a generic error.
  `src/main/assets/js/mandate.js` only enhances both pages (`data-page` `mandate` / `mandate-edit`), and the customer
  edit one (`data-page` `customer-edit`). When a signed search has an ACCEPTED match without an open visit
  (`MatchView.schedulable`, `MandateController`'s `canScheduleVisit`), the contact block also shows *Planifier une
  visite*, opening its own page (`GET /mandates/{id}/visits`, `MandateVisitController`, `mandate/visit.html`): the
  accepted property (a picker when several), the day and the time (the same `VisitSlots` as the visit page) and the type
  (`Sur place` / `Visite vidéo`). `POST /mandates/{id}/visits` validates (`MandateVisitRules`) and books through
  `NdVisitClient.createForMatch`, then returns to `/mandates/{id}#contact-title` with the `visitOutcome` flash:
  `scheduled`, `invalid-slot` (400 `VISIT_INVALID_SLOT`), `whatsapp-missing` (409 `VISIT_WHATSAPP_MISSING`), `changed`
  (403/404/409) or `error`. The page has no script and works without JS; the match's card then only carries the
  resulting visit, and the contact block's WhatsApp button is unchanged. ndopify-server
  checks ownership (see the mandate domain in `modules/ndopify-server/CLAUDE.md`): another agent's mandate is a 403, which the page shows as the locked view
  (and an action as `changed`, like a 404/409). A 403 / 404 from ndopify-server that no page catches goes to
  `HttpClientErrorHandler` (`ui/layout/`), which answers the same status so that Spring Boot renders
  `templates/error/403.html` / `404.html` (also used for unknown URLs). These pages have no account menu: the signed-in
  agent isn't known in Spring Boot's error controller.
- A landlord's signed mandate (APPROVED, LISTING) has a listing: the mandate page's *Actif* panel shows its line
  (`ListingViews.forMandate` → `MandateListingView`, the listing found by `MandateController` with
  `NdListingClient.findIdByMandate`, `GET /v1/listings?mandate-id=&agent-id=`, the agent's own id or its DRAFT isn't
  returned; a failure to load it leaves only the link), starting with the same `ListingLineView` as the card (its
  number a link to the listing; once PUBLISHED, that line only: no text, no button): "Créer l'annonce" posts `POST /mandates/{id}/listing`
  (`ListingController.create` → `NdListingClient` → `POST /v1/listings`, a DRAFT pre-filled from the landlord's
  answers; on 409 `LISTING_ALREADY_EXISTS`, ex: from another tab, the existing one is opened instead), then where it stands and "Continuer / Voir l'annonce"; once published, its activity line, like the card
  (`ListingStatsView`, from its ACCEPTED matches and its one `GET /v1/visits?listing-id=&agent-id=&status=…`; a failure leaves the line out), and its *Propositions*
  (`MandateController.listingClients`, `ListingViews.clients` → `ListingClientsView`, models `listingVisits` and
  `listingProposals`), after the progress, where a search has its *Biens correspondants*: the listing's matches sent to a client
  (`GET /v1/matches?listing-id=&status=PROPOSED|ACCEPTED|REJECTED`, `MatchRules.SENT`), the answers' line
  (`MatchRules.proposals`), and one row per client who didn't refuse it (interested first), linking to the search's
  `/mandates/{id}#matches-title`. A client with an open visit is in the listing's *Visites* section above (the same
  `VisitCards` as the Espace agent: the client, the visit's type, tag and date, linking to `/visits/{id}`), and leaves
  *Propositions* while it is open; the summary leads with "1 visite". The visits are read in one
  `GET /v1/visits?listing-id=&agent-id=&status=REQUESTED|CONFIRMED|CHECKED_IN|COMPLETED` by the listing's id; that read
  failing shows the section's "unavailable" and leaves every proposal, and a stale visit or one of another agent's is
  ignored. The same page renders *Visites d'autres agents* for a `PUBLISHED`/`RENTED`/`SOLD` listing
  (`MandateController.coBrokerageVisits`, the shared `fragments/co-brokerage-visit.html`):
  `GET /v1/visits/co-brokerage?listing-id=`, its organizer in the standard `agent-card` footer and never A's customer; a
  failed read or a DRAFT listing leaves it out. A `CLOSED` listing mandate also resolves its listing (RENTED / SOLD), so the block
  still shows there; its *Actif* panel stays absent. The activity line replaces the WIP `visitStats` line when it is shown. **Another agent's mandate is protected**: only the agent's own mandates among the
  matches' are looked up (`NdMandateClient.searchByIds`, `id=&agent-id=`, checked again on `agentId`), and only their
  customers (`NdCustomerClient.search`); any other match is "Client d'un autre agent", never named nor linked, its
  customer never asked of ndopify-server; only that agent (`Match.agentId`, the search's agent) is named, in the standard
  `agent-card` at the bottom of the row (`ProposalRow.otherAgent` / `agent`, linked to
  `/agent?id=&back=/mandates/{id}#proposals-title`), read in parallel; an agent that can't be read renders the card's
  "un autre agent" wording. The matches or mandates failing leave the section out (rather than mislabel
  a client); the customers failing show the mandate's number instead of the name. The listing
  page, `GET /listings/{id}` (`templates/listing.html`, `ListingViews` → `ListingView`), is the checklist of the visit:
  one row per `ListingSection` (`ListingRules.SECTIONS`, linking to its page),
  each section done once ndopify-server's checklist says so (`Listing.sections`, `ListingRules.done`), which also
  says which sections are required (`ListingSectionSummary.required`, `ListingRules.required`), the photos' counts
  from `GET /v1/listings/{id}/images`. "Envoyer pour vérification" (`POST /listings/{id}/verify`) is inactive
  (`aria-disabled`) until every required section is done, and re-checked on POST (`incomplete` outcome, also for
  ndopify-server's 409 `LISTING_INCOMPLETE`) since the button still posts without JS; sent, the listing is read-only (PENDING_VERIFICATION, UNDER_VERIFICATION,
  VERIFIED, PUBLISHING: "En vérification", "Envoyée le" its `modifiedAt`, the server having no sent date; VERIFIED
  included since ndopify-server publishes it, the agent doesn't), then PUBLISHED, with no panel: its date in the
  tag ("Publiée le …"), its hero image (`Listing.heroImageId`, when its photo can be loaded) and "Voir l'annonce en
  ligne" under the title, `{publicPortalUrl}{Listing.url}` for PUBLISHED, RENTED and SOLD (none without either a URL or the
  tenant's portal). Its
  *Propositions* are on its mandate's page, not here. ndopify-server has no rejected status: a failed
  verification sends the listing back to DRAFT with its `errorCodes`, which this page shows "À corriger" (tag, panel
  `TO_FIX` listing the reasons, `ListingRules.rejections`, an unknown code or `INTERNAL_ERROR` as a generic one, and
  their sections marked "À corriger"), editable and sent again like any DRAFT; the mandate's line says "Corriger
  l'annonce". ndopify-server checks listing access itself (PUBLISHED, SOLD and RENTED are public reads; only an admin
  or the listing's agent may read or change the others). The BFF's `PublishedListingAccess` admits the listing's own
  agent (the owner lens) or **any** agent on a `PUBLISHED`, `RENTED` or `SOLD` listing (the read-only lens), and locks
  the other statuses; create requires the mandate's agent.
  A listing ndopify-server can't return (5xx, network) is the `unavailable` view, not the locked one. Outcomes (flash
  `outcome`, shown on the locked and unavailable views too): `sent`, `incomplete`, `changed` (403/404/409 or moved
  on), `error`, `saved-<section>` ("Emplacement enregistré."…); a hand-edited id on a POST goes back to `/`. The
  ownership check of the write pages is `ListingAccess` (found / locked / unavailable), shared by the three listing
  controllers; `PublishedListingAccess` is the read gate of the listing page itself.
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
  (`GET|POST /listings/{id}/photos`, `POST /listings/{id}/photos/presign`, `POST /listings/{id}/photos/attach`,
  `POST /listings/{id}/photos/{imageId}/delete`, `templates/listing/photos.html`):
  the multipart post sends them straight to ndopify-server (`POST /v1/listings/{id}/images/upload`), sniffed with
  Tika, 5 Mo and 20 photos at
  most, the first problem as the `photoError` flash attribute; only one of the listing's own images is deleted
  (`DELETE /v1/listings/images/{id}`, which ndopify-server checks against the listing's agent and DRAFT status).
  With JS, `listing.js` first sends each shrunk photo straight to the storage via `direct-upload.js`
  (`POST /listings/{id}/photos/presign` → PUT → `POST /listings/{id}/photos/attach`), and falls back to the
  multipart post for that photo and the rest when presign has no URL (local storage) or any step fails; the
  multipart path is the no-JS path. The two proxies answer JSON: 404 for an unknown or another agent's listing, 409
  once it isn't a DRAFT, else ndopify-server's answer (its error body for attach).
  `KycUploadErrorHandler` sends an upload over the multipart limit
  back with `?error=too-large`. `src/main/assets/js/listing.js` only enhances the three pages (`data-page` `listing`,
  `listing-section`, `listing-photos`): the types and questions of the type, geolocation, photos shrunk and sent as
  soon as chosen, no double post. Prototype: `src/main/html/listing.html`, `listing/*.html`; its "Simuler" buttons
  have no server support, and its `REJECTED` panel is the `TO_FIX` one here.
  Another agent's `PUBLISHED`, `RENTED` or `SOLD` listing opens read-only (`PublishedListingAccess.ReadOnly`, the
  `readOnly` lens): no mandate number, no customer, no public link or share, no stats, no exact position or access
  directions, no verification trail or section links and no send/upload/delete action. It keeps the public place
  (neighbourhood, city, landmark), the note that the agent shares the exact address before the visit, and the
  publishing agent's boxed standard `agent-card` (`ListingView.agent`, no "Publié par", no phone number; unreadable:
  no card), whose profile link returns to the same listing URL. A `?match=` is only a context hint: a visible
  co-brokerage match of one of the agent's searches sets the return link to `/mandates/{id}#matches-title`; a stale or
  bogus one (another listing's, dismissed, another agent's, a non-search mandate, or a failed read) is ignored. Else a
  valid same-origin `?back=` (the agent profile's card) returns there with "← Retour", else the Espace agent with
  "← Retour à l'espace agent"; own listings still return to their mandate. The listing page has no
  *Visites d'autres agents* block any more (neither does it read `GET /v1/visits/co-brokerage?listing-id=`): that block
  stays on the Espace agent and the mandate page. A visit page's listing link still carries `?match={matchId}` so a
  co-brokerage listing opens with that context.
- A signed search (APPROVED, SEARCH: `MatchRules.matched`) has *Biens correspondants* on its mandate page: the
  PUBLISHED listings of the agent's inventory ndopify-server matched to it (`ui/match/`, see
  `docs/superpowers/plans/2026-10-31-partner-central-matches.md`). `MandateController` reads its matches in one
  `GET /v1/matches?mandate-id=&status=…` (`NdMatchClient`, ACCEPTED / ACTIVE / PROPOSED / EXPIRED, and REJECTED / DISMISSED, only
  counted: a refused one isn't shown, the section's one-liner "1 intéressé · 1 refusé · 1 en attente",
  `MatchRules.proposals` like the cards', counts it), then their listings in one `GET /v1/listings?id=&id=` (`NdListingClient.searchByIds`; a match whose listing
  isn't returned, unpublished meanwhile, is left out), and the location names with the mandate's. Either search failing
  is the section's "unavailable": the mandate is still shown. The cards are compact for phones: the title links to the
  listing, then one line of facts (score, neighbourhood, the rent in bold and in short, `compact = true`: "245K FCFA /
  mois", availability) and, once sent, "Envoyé le …" (tag "En attente" / "Intéressé"; "Expirée" and "Envoyé le …, sans réponse" for a
  proposal ndopify-server expired unanswered, counted "1 sans réponse"). `MatchViews` → `MatchView` (cards in `MatchRules.ORDER`,
  best score first within each status) and `MatchRules`: tags, the client's answer ("Envoyé le …" or, interested,
  "Intéressé le …": the latest step only), and "Pourquoi ce bien ?", the partial scores of ndopify-server's `Matcher` in words (neighbourhood, price,
  rooms, amenities when some were asked, availability, furniture when asked). An ACTIVE or EXPIRED card (`MatchRules.ACTIONABLE`) is sent to the client
  (again, for an expired one) or set aside by `MatchController`, `POST /mandates/{mandateId}/matches/{matchId}/propose|dismiss`, which reads the match
  first: one of another mandate than the URL's or no longer ACTIVE / EXPIRED is `changed` without any call (no second e-mail on
  a double post), so is a 403 / 404 / 409; then back to `/mandates/{id}#matches-title` with the `match` flash attribute
  (`proposed`, `dismissed`, `changed`, `error`) and `matchListing`, worded by `MandateController` (it has the contact's
  e-mail). A PROPOSED or ACCEPTED card without an open visit (`MatchRules.RESENDABLE`, `MatchView.resendable`) also has
  "Renvoyer le lien" (`POST …/resend`, `NdMatchClient.resend`) inline on that sent-status line, in a
  `.match__status` row with a `.link-button--inline` submit button (no button row, no 44px target: the card keeps its
  height): ndopify-server e-mails the customer a fresh link without changing the status (`resent`), and its 409
  `MATCH_RATE_LIMITED` is read from the body as `rate-limited` ("Un lien
  vient d'être envoyé au client. Réessayez dans quelques minutes.", warning), never as the stale page's `changed`. Without JS, "Écarter" opens the confirmation with `GET /mandates/{id}?dismiss={matchId}` (only an ACTIVE or EXPIRED match
  shown on the page); `mandate.js` does it in place. *Mes mandats* cards of signed searches get the one-line activity
  (`MandateCards`, `SearchStatsView`, from one `GET /v1/mandates/stats` for all of them, skipped without such a
  card, a failure leaves the line out): "12 biens · 1 intéressé · 3 visites", zero segments left out, linking to the
  section; the mandate page derives the same line from its loaded matches and visits (`SearchStatsView.of`), with no
  extra call. Not built, though the
  prototype and ndopify-server have them: *Retenir* (SHORTLISTED, never shown here) and sending several at once, the
  coded rejection reasons, the distance and the missing amenities' names. The mandate page's *Visites* section lists
  the client's open visits above *Biens correspondants*, from one `GET /v1/visits?mandate-id=&agent-id=&status=…`
  (`NdVisitClient.search`, the same `VisitCards` as the Espace agent; past visits aren't shown). A listing whose
  client has an open visit leaves *Biens correspondants*, so it isn't shown twice, and comes back once the visit
  closes (cancelled, missed, expired or completed); if only such visits remain, the section says where they went.
  A co-brokerage match's listing agent is named once, in the standard `agent-card` at the bottom of the card
  (`MatchView.agent`, back to `/mandates/{id}#matches-title`); its visit block never repeats it
  (`MatchViews` clears the visit's card), and an unreadable agent renders "un autre agent". Its title link carries
  `?back=/mandates/{searchMandateId}#matches-title`; an agent-profile listing card carries `?back=` to its profile page.
- The Espace agent's *Visites* section (`HomeController`, `ui/visit/`) lists the agent's open visits:
  `GET /v1/visits?agent-id=&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN` (`NdVisitClient.search`,
  `VisitRules.OPEN`), their listings in one `GET /v1/listings?id=` and their customers in one `GET /v1/customers?id=`,
  into `VisitCards` → `VisitCardsView`: requested by expiry (soonest first, a request past
  `max(createdAt + 48 h, requestedAt)` dropped) and
  upcoming by scheduled time (a confirmed visit past its `+ 4 h` window dropped). The search failing is the section's
  "unavailable"; a missing listing or customer only leaves its line out. The home page confirms a requested visit
  inline: `VisitCardView` carries the slot lists and the requested slot, `POST /visits/{id}/schedule` with
  `VisitScheduleForm.from=home` comes back to `/#visits-title` with the `visitOutcome` flash, and
  `templates/fragments/visit-card.html` renders the one-tap form (or the slot picker when the requested slot is
  outside the list). The upcoming visits are grouped by day and capped at four
  (`VisitCardsView.ongoing/upcomingGroups/upcomingPreviewGroups/upcomingMoreGroups`); the rest is folded. A
  co-brokerage visit's card ends with the standard `agent-card` footer (`VisitCardView.coBrokerage` / `agent`, back to
  `/#visits-title`); an own-inventory visit has none. The page also lists the visits other agents organize on the agent's live
  listings (*Visites d'autres agents* from `GET /v1/visits/co-brokerage`, no `listing-id`, the shared `co-visit-card`):
  they count in `todayCount` (which links to their block when no own visit is today), not `requestedCount`, never
  show A's customer, and the block is left out when the read fails. `GET /visits/{id}` (`VisitController`,
  `templates/visit.html`, `VisitViews` → `VisitPage`, `VisitRules`, `VisitSlots`) is the prototype's page: one panel per
  status (REQUESTED, CONFIRMED, WINDOW in the check-in window, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED, NO_SHOW) and
  its forms: schedule (`POST /visits/{id}/schedule`), refuse / cancel (`/cancel`), check-in with the customer's code and
  an optional position (`/check-in`), no-show (`/no-show`), check-out with the private report (`/check-out`). Its header
  states the fee to collect (`VisitPage.visitFee`, from `Visit.visitFee`, formatted by `TenantFormatter`;
  *Aucun frais de visite.* when 0) while REQUESTED, CONFIRMED (the check-in window included) or CHECKED_IN, and hides it
  once done or called off. The visit
  is required (another agent's visit, a 403, or a 404 is the locked view); the listing and customer are secondary. The
  listing's agent, when the visit is on another agent's listing, is a third secondary read (`NdAgentClient`): a boxed
  standard `agent-card` (`agent-card--standalone`, no "Visite organisée avec", no phone number) from `VisitPage.agent`;
  an unreadable agent leaves it out. The
  address comes from the listing (`landmark` + `accessDirections`), never `Visit.address`; the agent never receives the
  code, and the API exposes neither the code attempts nor the no-show position, so `VISIT_CODE_LOCKED` is kept in the
  session (`npc.visit.code-locked.<id>`) to hide the check-in form for this agent. Slots are the 7 days starting
  tomorrow, 08:00–20:00 every 30 min in the tenant's time zone (`VisitSlots`), and the server's windows are mirrored
  (check-in `scheduledAt − 1 h` → `+ 4 h`, no-show from `+ 15 min`, check-out from `+ 10 min`, expiry at
  `max(createdAt + 48 h, requestedAt)`). Actions
  are post-redirect-get back to `/visits/{id}` with the `visitOutcome` flash, worded by the controller; 403/404/409 and
  an action whose visit moved on are `changed` without a call. The page is behind the default authentication rule (no
  security matcher). `src/main/assets/js/visit.js` only enhances (flash focus, digits-only code, type hint, comment
  counter, position, the closure box); `GET /visits/{id}?close=refuse|cancel|no-show` opens the chosen closure
  form without JS (ignored when the panel doesn't offer it); `home.js` is unchanged (the cards are server-rendered,
  and a reload shows the next time-bound panel). `HomeController` loads the
  sections' independent reads together on virtual threads (`config/Parallel`), so the page waits on four round trips
  instead of up to eleven; each section still degrades on its own, every `Model` write stays on the request thread,
  and each task re-binds the request attributes the `Authorization` / `X-Tenant-ID` interceptors read. The same open visits,
  filtered by mandate, are on a signed search's mandate page (see above); it reuses `VisitCards` and hides no match
  when the visits can't be read.
- `src/main/html/` holds static mockups generated by the `/init-partner-central` command; the served pages are the
  Thymeleaf templates. `application-test.yml` / `application-prod.yml` hold the deployed settings
  (`NDOPIFY_SERVER_URL`, secure session cookies, reduced actuator exposure), not copied config.
