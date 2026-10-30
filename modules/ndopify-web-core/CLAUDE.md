# ndopify-web-core

A library (jar, no `Application`, not deployed) holding the infrastructure the web apps share: `ndopify-partner-central`,
`ndopify-esignature` and `ndopify-web`. It depends only on `ndopify-dto`, never on `ndopify-server` nor on a web app.
The web apps don't use it yet: each still has its own copy of these classes.

## Code Organization

Package `com.wutsi.ndopify.web.core`:

- `service/TenantContext` — the tenant whose URL for a `TenantPortal` (`PARTNER_CENTRAL` → `partnerCentralUrl`,
  `E_SIGNATURE` → `eSignaturePortalUrl`, `PUBLIC_PORTAL` → `publicPortalUrl`) prefixes the request URL. Not a
  `@Service`: each web app declares it as a bean with its own portal.
- `service/TenantFormatter` — dates, times and amounts in the tenant's formats, locale and time zone: every date or
  amount a web app shows goes through it.
- `client/ndopify/NdTenantClient` — `GET /v1/tenants`, a `@Service` on the app's plain `RestTemplate` bean.
- `client/LoggingClientHttpRequestInterceptor`, `client/TenantIdClientHttpRequestInterceptor` — added by each web
  app's `RestConfiguration` to its `RestClient.Builder`.

What stays in each web app: `ClockConfiguration` (each app declares its own `Clock`), `RestConfiguration`,
`SecurityConfiguration`, the domain clients and the UI.

## Testing Patterns

Plain unit tests: `MockRestServiceServer` for the client and interceptors, `MockHttpServletRequest` for
`TenantContext`, `TenantFixtures.TENANT` (the local seed's tenant) for `TenantFormatter`. No Spring context, no MySQL.

## Versioning and Release

Released like `ndopify-dto` (see *Versioning and Release* in `modules/ndopify-dto/CLAUDE.md`): the web apps' CI builds
each module on its own, so they can only use a released version from GitHub Packages, never a `-SNAPSHOT`. A push to
`main` under `modules/ndopify-web-core/` runs `ndopify-web-core-main.yml`: build, coverage badge, then
`release:prepare release:perform` (tag `ndopify-web-core-<version>`, next `-SNAPSHOT`). `ndopify-web-core-pr.yml`
builds and tests it on a pull request.
