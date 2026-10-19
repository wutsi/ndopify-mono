# ndopify-dto

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml)

Shared request/response DTOs and contracts for Ndopify. This module has no business logic — it exists purely to be
a dependency of `ndopify-server` and of its clients, `ndopify-partner-central` and `ndopify-esignature` (and, in the
future, other consumers such as SDKs).

## Packages

All packages live under `com.wutsi.ndopify.<package>.dto`.

| Package    | Contents                                                                                                  |
|------------|-----------------------------------------------------------------------------------------------------------|
| `agent`    | Agent DTOs: `Agent`, `AgentSummary`, create/update/search requests and responses, `AgentType`, `ExperienceLevel`, `AgentDocumentType` |
| `common`   | Cross-cutting DTOs: `HttpHeader` constants, `ImportResponse`, `ImportMessage`                              |
| `contract` | `Contract`, `ContractSummary`, `ContractParty`, create/search/content (`SetContractRequest`)/sign requests, `ContractStatus`, `ContractType`, `ContractPartyRole` |
| `customer` | `Customer`, `CustomerSummary`, create/search requests and responses                                       |
| `error`    | The shared error contract: `ErrorCode`, `Error`, `ErrorResponse`, `Parameter`, `ParameterType`            |
| `listing`  | Property vocabulary shared by mandates: `PropertyCategory`, `PropertyType`, `ListingType`, `FurnitureType`, `MovingPeriod`, `ContactPeriodType` |
| `mandate`  | `Mandate`, `MandateSummary`, create/update/search/close/cancel requests, `MandateStatus`, `MandateType`, `MandateCancellationCode`; the offers to agents: `Assignment`, `AssignmentSummary`, `AssignmentStatus`, search requests |
| `party`    | Party, identification (and images), KYC case/verification, and payment method DTOs and enums              |
| `refdata`  | Reference data: `Tenant`, `Location` (+ search, nearest-location search), `Application`, `ApplicationCode`, `Role`, `RoleCode`, `AuthType`, `Money`, `MoMoGatewayType`, `StorageType`, `KycErrorCode`, etc. |
| `security` | `AuthenticateRequest` / `AuthenticateResponse`, `CreateOtpRequest`, `User` / `GetUserResponse`, `GoogleOneTapPayload`, `JwtPrincipal` |

## Build

```bash
cd modules/ndopify-dto
mvn package
```

## Code Quality

```bash
mvn validate                          # ktlint check (runs automatically on build)
mvn antrun:run@ktlint-format          # auto-format
```

## Versioning & Publishing

This module is versioned and released independently of `ndopify-server` (its own `<version>` in `pom.xml`, distinct
from the parent/root version) and published to GitHub Packages (`maven.pkg.github.com/wutsi/ndopify-mono`).
`ndopify-server`, `ndopify-partner-central` and `ndopify-esignature` depend on it via the `${ndopify-dto.version}`
property in the root `pom.xml`. A push to `main` that touches this module releases it (`ndopify-dto-main` workflow:
`maven-release-plugin`, tag `ndopify-dto-<version>`); bump `ndopify-dto.version` in the root `pom.xml` to pick up the
new release in the other modules.

## Conventions

- Pure data classes only — no persistence (JPA) annotations, no business logic, and no Spring annotations except
  `@BindParam` on `Search*Request` fields whose query-parameter name differs from the field (e.g.
  `SearchLocationRequest.keyword` ← `q`). `spring-web` is an `<optional>` dependency for that purpose only, so
  consumers don't inherit it.
- Enums and constants that are part of an API contract (e.g. `IdentificationType`, `KycStatus`, `StorageType`)
  belong here, not in `ndopify-server`.
- `ndopify-dto` must never depend on `ndopify-server`, `ndopify-partner-central` or `ndopify-esignature`.
