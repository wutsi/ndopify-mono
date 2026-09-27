# ndopify-dto

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-main.yml)
[![pull_request](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/ndopify-dto-pr.yml)

Shared request/response DTOs and contracts for Ndopify. No business logic — just data classes and enums shared
between API consumers and the [ndopify-dto](../ndopify-dto) module.

## Packages

| Package    | Description                                                                         |
|------------|-------------------------------------------------------------------------------------|
| `agent`    | Agent, KYC (mobile/identity change), and mobile-money DTOs                          |
| `refdata`  | Shared reference data DTOs — location, tenant, application, role, KYC/gateway enums |
| `security` | Authentication request/response DTOs                                                |
| `error`    | Error response contracts (`Error`, `ErrorResponse`, `ErrorCode`)                    |
| `common`   | Cross-cutting DTOs (e.g. HTTP header constants)                                     |

## Build & Test

```bash
cd modules/ndopify-dto
mvn install
```

This module has no runtime dependencies beyond `jakarta.validation-api` and `java-jwt`.

## Notes

- `ndopify-dto` must never depend on `ndopify-dto`.
- Changes here are contract changes — coordinate version bumps with consumers of the published artifact.
