# ndopify-esignature

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-esignature-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-esignature-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-esignature-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-esignature-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-esignature-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-esignature-jacoco.svg)

E-signature portal — the page where a contract's parties (customers, landlords…) read and sign the contract they
were asked to sign. Server-side rendered with Kotlin + Spring Boot + Thymeleaf, UI in French.

It has no database: every read/write goes to [ndopify-server](../ndopify-server) over REST, using the shared
contracts from [ndopify-dto](../ndopify-dto). It never depends on [ndopify-partner-central](../ndopify-partner-central),
whose infrastructure it copies (same class names), minus the agent and login parts.

## Features

- **Sign page** (`ui/sign/SignController`) — one page, `templates/sign.html`, all under `/sign/{contractPartyId}`: the
  link e-mailed by ndopify-server when a contract's signature is requested (`{eSignaturePortalUrl}/sign/{contractPartyId}`).
    1. **E-mail** — signed out, nothing about the contract is shown. `POST /sign/{id}/otp` has ndopify-server e-mail a
       code to the address the link was sent to, and keeps a `PendingSignature` (this link, e-mail, expiry) in the
       HTTP session; `resend=true` sends a new one.
    2. **Code** — `?step=code`, shown only while a sign-in is pending for **this** link. `POST /sign/{id}/verify`
       exchanges the code for an access token (`AuthType.OTP`, application `e-signature`), reading the e-mail from
       the session, never from the page.
    3. **Sign** — the contract's content (HTML, sanitized with Jsoup) with a consent checkbox. `POST /sign/{id}` signs
       as the link's contract party, with the signer's IP and user agent, then shows the confirmation (`?step=done`).
       It refuses to sign a version other than the one shown ("Le contrat a été modifié depuis son affichage…").
- **Blocked views** — instead of the sign step: already signed, signature not required, cancelled, expired,
  content unavailable, and `not-yours` (the signed-in person isn't this link's party, shown without any contract data).
- **Sign-out** — `POST /sign/{id}/logout` ("Utiliser une autre adresse") drops the access-token cookie and the session.
- **Verify page** (`ui/verify/VerifyController`, `templates/verify.html`) — public, no session, at
  `/verify/{contractId}/{signatureHash}`: the URL the QR code printed next to a signature in the signed PDF opens. It
  shows the contract number, the signer's name, the date and time of the signature, and the hash (no download of the signed PDF: the page is public), from
  ndopify-server's `GET /v1/contracts/{id}/signatures/{hash}`. A malformed hash is reported invalid without calling
  ndopify-server; ndopify-server's 404 `CONTRACT_SIGNATURE_NOT_FOUND` is invalid too, and both are HTTP 200. Any other error, incl. any other 404 (unknown route, proxy), shows an error page, never "invalid".

Every step posts without JavaScript; `public/assets/js/esignature.js` only enhances (code digits, consent, busy
state against double submit, focus).

## How it works

- **Authentication** — the access token is stored in the `__nes_access_token` HTTP-only cookie (`AccessTokenService`,
  1 hour) and decoded by `JwtAuthenticationFilter`; only `e-signature` tokens are accepted. `SecurityConfiguration`
  permits `/assets/**`, `/sign/**`, `/verify/**` and `/error`, and denies everything else.
  Note: tokens are decoded with `Algorithm.none()` — the same unsigned-JWT gap as ndopify-server.
- **Membership** — a party can sign in only once ndopify-server has given it a membership with `e-signature` access,
  which a request for signature does. An e-mail with no contract is told "Aucun contrat n'a été envoyé à cette adresse".
- **Access** — the contract is found with `GET /v1/contracts?contract-party-id=…`, which ndopify-server restricts to
  the caller's own party. Only the party itself can sign: there's no admin bypass.
- **Tenant resolution** — `TenantContext` picks the active tenant whose `eSignaturePortalUrl` is a prefix of the
  request URL. `ui/layout/TenantAdvice` puts it in every page's model, for the header's logo (falling back to
  Ndopify's). Dates go through `TenantFormatter` (the tenant's formats, locale and time zone).
- **REST clients** (`client/ndopify/`: `NdAuthClient`, `NdContractClient`, `NdOtpClient`, `NdTenantClient`) — like
  Partner Central's: every Spring-built `RestClient` gets logging, `Authorization` and `X-Tenant-ID` interceptors.
  `client/ContractContentClient` downloads the contract's `contentUrl` through a plain `RestClient`, so that those
  headers never reach the storage.
- **Errors** — a 403 / 404 from ndopify-server that the page doesn't catch is answered with the same status
  (`ui/layout/HttpClientErrorHandler`), rendered by `templates/error/403.html` / `404.html`.

## Directory structure

```
src/main/kotlin/com/wutsi/ndopify/esignature/
    client/      REST clients for ndopify-server, the contract-content client, request interceptors
    config/      Spring configuration (security, REST, clock)
    service/     TenantContext, TenantFormatter, security/ (access-token cookie, JWT decoding, auth filter)
    ui/          MVC: sign (the sign page), verify (the signature verification page), layout (tenant advice, error handler)
src/main/resources/
    templates/   Thymeleaf pages served by the app (sign.html, verify.html, error/)
    public/      Static assets (CSS, JS, images) served under /assets
src/main/html/   The prototype (sign.html + assets) generated by /init-esignature, which the template is derived
                 from. Not served — and kept as is: never delete or "clean up" anything in it
ux/journeys/     User-journey maps for the sign page
```

## Environment Setup

- JDK 17, Maven 3.9+
- A running [ndopify-server](../ndopify-server) — by default on `http://localhost:8080`
  (`ndopify.server.api.base-url` in `application.yml`)
- A tenant whose `eSignaturePortalUrl` matches the URL you browse to (e.g. `http://localhost:8083`), otherwise tenant
  resolution fails
- To receive OTP codes and signature requests locally, an SMTP catcher (e.g. Mailpit) for ndopify-server — see its
  README

## Running

```bash
mvn spring-boot:run          # starts on port 8083
```

In deployment, the `Procfile` runs the jar with `--spring.profiles.active=$APP_PROFILE`.

## Build & Test

```bash
mvn package
mvn test
mvn test -Dtest=ClassName#methodName
```

Tests are unit / standalone-MockMvc level and need neither ndopify-server nor a database. `SignTemplateTest` and
`ErrorTemplateTest` render the templates with a real Thymeleaf engine (checked with jsoup), so template mistakes fail
the build.

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
mvn verify                            # JaCoCo coverage gate (build-breaking)
```

Coverage thresholds are the parent POM's defaults (`jacoco.threshold.line` / `jacoco.threshold.class` in the root
`pom.xml`).
