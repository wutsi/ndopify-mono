# ndopify-esignature

Package `com.wutsi.ndopify.esignature` (port 8083, French UI): the contract sign page, a Spring Boot + Thymeleaf BFF with
no database that talks to `ndopify-server` over REST. Its infrastructure is copied from Partner Central (same class
names and signatures; depends on `ndopify-dto` only, never on partner-central), minus the agent / login parts.

## Code Organization

Same layout as Partner Central (see `modules/ndopify-partner-central/CLAUDE.md`), with two pages:

- `ui/sign/` — `SignController`, its forms (`OtpForm`, `VerifyForm`, `SignForm`), the session's `PendingSignature` (`nes.pending-signature`),
  and `SignViews` → `SignView` (picks the step and formats the dates). `ui/verify/` — `VerifyController`,
  `VerifyView`. `ui/layout/` — `TenantAdvice`, `HttpClientErrorHandler`.
- `client/ndopify/` — `NdOtpClient`, `NdAuthClient`, `NdContractClient`, `NdTenantClient`; `client/` — the
  interceptors and `ContractContentClient` (+ `ContractContent`), which downloads a contract's content.
- `service/` — `TenantContext`, `TenantFormatter`; `service/security/` — the JWT cookie, decoder and filter.
- `config/` — security (the only public paths), REST clients, `Clock`.

Templates: `templates/sign.html`, `templates/verify.html`, `templates/error/`; one script,
`public/assets/js/esignature.js`, which only enhances.

## Testing Patterns

As Partner Central's: plain unit tests (no MySQL, no server), `MockMvc` standalone controller tests
(`SignControllerTest`, `VerifyControllerTest`), real-Thymeleaf template tests (`SignTemplateTest`,
`VerifyTemplateTest`, `ErrorTemplateTest`), view-model tests (`SignViewsTest`), and `MockRestServiceServer` client
tests (`Nd*ClientTest`, `ContractContentClientTest`). Fixtures: `TenantFixtures`, `SignFixtures`. A change to what
the sign or verify page may show (see the rules below) needs a template test asserting it isn't rendered.

## Pages

- Tenant is resolved by `service/TenantContext` matching the request URL against each active tenant's
  `eSignaturePortalUrl` (not `partnerCentralUrl`). `ui/layout/TenantAdvice` puts it in every page's model as `tenant`
  (error pages included), for the header's logo (`logoUrl`, falling back to Ndopify's `logo.png`); null when it can't
  be resolved, so that an error page still renders.
- The JWT is kept in the HTTP-only `__nes_access_token` cookie (`AccessTokenService`, `Path=/`, 1 hour like the token's
  TTL) and decoded by `JwtAuthenticationFilter`, like Partner Central's.
- `SecurityConfiguration` permits only `/assets/**`, `/sign/**`, `/verify/**` and `/error`; every other path is denied (nothing else
  is served). The 403 / 404 pages (`templates/error/`) show the logo only, no account menu.
- `client/ndopify/` holds the ndopify-server clients: `NdOtpClient` / `NdAuthClient` (as Partner Central's),
  `NdContractClient` (get, search incl. `contract-party-id`, `sign`, `getSignature`) and the global `NdTenantClient`.
  `client/ContractContentClient` downloads the contract's `contentUrl`: HTML only, sanitized with Jsoup
  `Safelist.relaxed()` (the page renders it with `th:utext`), null on any failure (the page then can't sign), through
  the plain `contentRestClient` bean (`RestClient.create()`, not the Spring builder) so that the `Authorization` /
  `X-Tenant-ID` interceptors never reach a storage URL.
- The sign page is `SignController` (`ui/sign/`), one page (`templates/sign.html`, view model `SignView` in `view`,
  plus `contractPartyId`), all under `/sign/{contractPartyId}`, built like Partner Central's login. Signed out,
  nothing about the contract is shown: the intro asks for the e-mail the link was sent to, `POST /otp` (trimmed,
  lowercased; `resend=true` reuses the pending one) sends a code and records a `PendingSignature` (this link's
  `contractPartyId`, e-mail, expiry) in the session, and `?step=code` only shows while it's there for **this** link;
  `POST /verify` reads the e-mail from it (never the form) and authenticates with `ApplicationCode.E_SIGNATURE`.
  A 404 `USER_NOT_FOUND` (OTP) or 409 `AUTH_ACCESS_DENIED` (auth) is "Aucun contrat n'a été envoyé à cette adresse".
  Signed in (a `JwtAuthentication`), the contract is found by `search(contract-party-id)`, which ndopify-server
  restricts to the caller's party: none is the `not-yours` blocked view, with no contract data. `SignViews.build` then
  picks the step: done (signed, `?step=done`), blocked (`signed`, `not-required`, `cancelled`, `expired` incl. a
  deadline already past, `unavailable`), else `sign`, the only one that fetches the content (`ContractContentClient`),
  and signable (`canSign`) only with it. `POST /` rebuilds the sign view (fetching the content again) and signs only
  if it's still the `sign` step (else `?step=done`, without signing), `canSign`, and the form's hidden `contentUrl`
  (`SignView.contentUrl`, the version shown; only compared, never used to find anything) is still the contract's: else
  the sign step again, with the new content, and "Le contrat a été modifié depuis son affichage…". Then it needs
  `consent=yes`, signs as the URL's contract party with the
  `remoteAddr` (the client's behind a proxy: `server.forward-headers-strategy: native`, never the raw `X-Forwarded-For`), the `User-Agent` and the `contentHash` of the content fetched
  (`ContractContentClient.fetch` returns a `ContractContent`: the sanitized HTML plus the SHA-256 of the raw bytes
  downloaded, never of the sanitized HTML), and goes to `?step=done`, also on a 403 / 404 / 409 (but a 409
  `CONTRACT_CONTENT_MISMATCH`, shown as "Le contrat a été modifié…" on the sign step; a double tap's `CONTRACT_PARTY_ALREADY_SIGNED`: the done or blocked view says which). `POST /logout` ("Utiliser une
  autre adresse") clears the cookie and the session. Its warn logs carry ndopify-server's status and error code only,
  never the response body (which can name the e-mail).
- `templates/sign.html` renders only the current step (`th:if` on `view.step`), "Étape N sur 3" included, one
  `<form method="post">` per action (intro `/otp`, code `/verify`, resend `/otp` with `resend=true`, sign `/`, logout
  `/logout`; the resend button sits in the code form with `form="resend-form"`, since forms can't nest), so every step
  posts without JS. `view` dates are already formatted by `TenantFormatter`: print them as-is. Nothing of the contract
  in the intro / code steps; the contract's HTML goes through `th:utext` from `view.contentHtml` only (sanitized by
  `ContractContentClient`). No WhatsApp number, no signed-copy promise, no "Précédent" on the intro.
  `public/assets/js/esignature.js` only enhances (digit sync + "Suivant", consent enabling, busy state against double
  submit, focus on the code field / confirmation heading); it keeps no state. `SignTemplateTest` renders the template
  with a real Thymeleaf engine.
- The verify page is `VerifyController` (`ui/verify/`, `templates/verify.html`, view model `VerifyView` in `view`),
  `GET /verify/{contractId}/{signatureHash}`: the page the signed PDF's QR code opens. **Public**: no session, sets no cookie of its own
  (`JwtAuthenticationFilter` still clears an expired token's; a signed-in visitor's valid token is forwarded on the
  `getSignature` call, harmless: the tenant comes from the host), no JS (no `<script>`), `noindex` and `no-referrer`, HTTP 200 for both states. The hash is normalized
  (`trim().lowercase()`); one that isn't 64 hex characters is invalid without calling ndopify-server. Otherwise
  `NdContractClient.getSignature` (`GET /v1/contracts/{id}/signatures/{hash}`): only ndopify-server's 404 `CONTRACT_SIGNATURE_NOT_FOUND` (null) is the invalid view, any
  other error (5xx, any other 404 such as an unknown route or a proxy's, other 4xx, I/O) is thrown to Spring Boot's error page, **never** shown as "not valid". Valid
  whatever the contract's status (the server decides), and the hash printed is the URL's, only once the server matched
  it. It shows only the contract number, the signer's legal name, the date and time (`TenantFormatter`) and the hash:
  never add more, and no link to the signed PDF (whoever has a QR code, ex: a photo of one page, would get the whole
  contract).
- `src/main/html/` (`sign.html`, `verify.html` + assets) is the prototype: never serve it, and never delete or "clean up" anything in
  it; the served pages are `templates/sign.html` and `templates/verify.html`.
