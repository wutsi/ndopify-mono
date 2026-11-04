# ndopify-web-core

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-web-core-pr.yml)
[![JaCoCo](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-core-jacoco.svg)](https://github.com/wutsi/ndopify-mono/blob/main/.github/badges/ndopify-web-core-jacoco.svg)

A library (jar, no `Application`, not deployed) holding the infrastructure shared by the three web apps:
[ndopify-partner-central](../ndopify-partner-central), [ndopify-esignature](../ndopify-esignature) and
[ndopify-web](../ndopify-web). It depends only on `ndopify-dto`, never on `ndopify-server` nor on a web app.

## Contents

Package `com.wutsi.ndopify.web.core`:

| Class / package | Purpose |
|-----------------|---------|
| `service/TenantContext`, `service/TenantPortal` | Finds the tenant whose URL for a portal (`partnerCentralUrl`, `eSignaturePortalUrl`, `publicPortalUrl`) prefixes the request URL. Not a `@Service`: each app declares it with its own portal. |
| `service/TenantFormatter` | Dates, times and amounts in the tenant's formats, locale and time zone. Every date or amount shown by a web app goes through it. |
| `client/ndopify/NdTenantClient` | `GET /v1/tenants`, on the app's plain `RestTemplate` bean (tenants are global reference data). |
| `client/LoggingClientHttpRequestInterceptor`, `client/TenantIdClientHttpRequestInterceptor`, `client/AuthorizationClientHttpRequestInterceptor` | Added by each app's `RestConfiguration` to its `RestClient.Builder`. |
| `service/security/AccessTokenService` | The access-token cookie (`HttpOnly`, `SameSite=Lax`, `Secure` over HTTPS), kept until the token's `exp`. Each app passes its own cookie name. |
| `service/security/JwtDecoder`, `JwtAuthenticationFilter`, `JwtAuthentication` | Reads the token's claims without checking the signature; each app accepts only its own `ApplicationCode`. ndopify-server checks the signature on every call. |

What stays in each web app: `ClockConfiguration`, `RestConfiguration`, `SecurityConfiguration`, its domain clients,
its own `CurrentParty` / `CurrentAgent` and its UI.

## Build & Test

```bash
cd modules/ndopify-web-core
mvn verify
```

Tests are plain unit tests (no Spring context, no MySQL): `MockRestServiceServer` for the clients and interceptors,
`MockHttpServletRequest` for `TenantContext`, and `TenantFixtures.TENANT` for `TenantFormatter`.

## Versioning & Publishing

Released like `ndopify-dto` (see [ndopify-dto](../ndopify-dto/README.md#versioning--publishing)): the web apps fetch
a released version from GitHub Packages, never a `-SNAPSHOT`. A push to `main` under this module runs
`ndopify-web-core-main.yml`, which builds, generates the coverage badge and releases it with `maven-release-plugin`
(tag `ndopify-web-core-<version>`). Bump `ndopify-web-core.version` in the root `pom.xml` to pick up the new release.
