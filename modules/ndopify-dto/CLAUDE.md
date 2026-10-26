# ndopify-dto

The request/response contracts shared by `ndopify-server` and its clients (`ndopify-partner-central`,
`ndopify-esignature`, `ndopify-web`). No business logic, and no dependency on any other module of the repo. Its only
dependencies are `jakarta.validation-api` (the `@NotEmpty` / `@Size`... the server validates), `spring-web`
(`@BindParam` on search requests) and `java-jwt`.

## Code Organization

One package per server domain, `com.wutsi.ndopify.<domain>.dto` (`agent`, `contract`, `customer`, `listing`,
`mandate`, `party`, `refdata`, `security`), plus `common.dto` (`HttpHeader`: the custom header names, ex:
`X-Tenant-ID`; import messages) and `error.dto` (`ErrorResponse`, `Error`, `Parameter`, `ErrorCode`).

Naming, per resource `<Entity>`:

- `<Entity>` — what get returns (in `Get<Entity>Response`); `<Entity>Summary` — what a search returns (in
  `Search<Entity>Response`), a subset of the fields: never add a field there that a search mustn't hand out (ex:
  `ContractSummary` has no `signedContentUrl`).
- `Create<Entity>Request` / `Create<Entity>Response` (the new id), `Update<Entity>Request` (with `version` when the
  entity is `@Version`-ed), `<Action><Entity>Request` for a transition (ex: `CloseMandateRequest`).
- `Search<Entity>Request` — bound from query parameters: kebab-case names with `@param:BindParam("agent-id")`, a
  list for a multi-valued filter (`statuses`, bound as repeated `status=`), `limit` (default 20) and `offset`.
- Enums are one file each, and start with `UNKNOWN` (the server's converters read an unknown or NULL value as it).
  Values are stored by name: adding or reordering is safe; renaming one needs a migration in ndopify-server.

All DTOs are Kotlin `data class`es whose every field has a default, so that Jackson can build them and a client can
build a partial one. A JSON name Jackson would get wrong needs both `@get:JsonProperty` and `@field:JsonProperty`
(ex: `Tenant.eSignaturePortalUrl`).

`ErrorCode` is an `object` of URNs, `urn:wutsi:ndopify:error:<entity>:<kebab-case-reason>`, grouped by entity in
alphabetical order. The clients compare `ErrorResponse.error.code` against these constants, never against a string.

## Versioning and Release

The other modules depend on `ndopify-dto` through `<ndopify-dto.version>` in the root `pom.xml`, and their CI builds
each module on its own, fetching `ndopify-dto` from GitHub Packages (`settings.xml`). So:

1. While developing, set `<ndopify-dto.version>` to the DTO's current `-SNAPSHOT` (its `pom.xml` version) and
   `mvn install` the DTO module locally (the full reactor build does it).
2. Pushing a DTO change to `main` runs `ndopify-dto-main.yml`, which releases it with `maven-release-plugin`
   (`release:prepare release:perform`: a `[maven-release-plugin]` commit pair, tag `ndopify-dto-<version>`, and the
   next `-SNAPSHOT`).
3. Then set `<ndopify-dto.version>` to the released version (a "fix dependencies" commit): the other modules'
   CI can't resolve a `-SNAPSHOT`.

The DTO has no tests: it's covered by the server's endpoint tests and the clients' tests.
