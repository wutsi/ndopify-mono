# ndopify-dto

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml)

Shared request/response DTOs and contracts for Ndopify. This module has no business logic — it exists purely to be
a dependency of `ndopify-server` (and, in the future, other consumers such as SDKs or a portal).

## Packages

All packages live under `com.wutsi.ndopify.<package>.dto`.

| Package    | Contents                                                                                                  |
|------------|-----------------------------------------------------------------------------------------------------------|
| `agent`    | Agent DTOs: `Agent`, `AgentSummary`, create/update/search requests and responses, `AgentType`, `ExperienceLevel`, `AgentDocumentType` |
| `common`   | Cross-cutting DTOs: `HttpHeader` constants, `ImportResponse`, `ImportMessage`                              |
| `error`    | The shared error contract: `ErrorCode`, `Error`, `ErrorResponse`, `Parameter`, `ParameterType`            |
| `party`    | Party, identification (and images), KYC case/verification, and payment method DTOs and enums              |
| `refdata`  | Reference data: `Tenant`, `Location`, `Application`, `Role`, `AuthType`, `MoMoGatewayType`, `PropertyCategory`, `StorageType`, `KycErrorCode`, etc. |
| `security` | `AuthenticateRequest` / `AuthenticateResponse`                                                            |

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
`ndopify-server` depends on it via the `${ndopify-dto.version}` property in the root `pom.xml`.

## Conventions

- Pure data classes only — no Spring annotations, no persistence (JPA) annotations, no business logic.
- Enums and constants that are part of an API contract (e.g. `IdentificationType`, `KycStatus`, `StorageType`)
  belong here, not in `ndopify-server`.
- `ndopify-dto` must never depend on `ndopify-server`.
