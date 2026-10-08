# ndopify-mono

Maven monorepo for Ndopify — a multi-tenant backend for managing mobile-money agents, their KYC verification, and
mobile-money payment integrations across Africa.

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/_main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/_main.yml)
[![pr](https://github.com/wutsi/ndopify-mono/actions/workflows/_pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/_pr.yml)

## Project Status

Early-stage. Only three modules exist today — there is no portal, SDK, or tracking-server module (yet).

## Modules

| Module                                     | Description                                                                              |
|--------------------------------------------|------------------------------------------------------------------------------------------|
| [ndopify-dto](modules/ndopify-dto)         | Shared request/response DTOs and contracts — no business logic                           |
| [ndopify-server](modules/ndopify-server)   | REST API backend (Spring Boot)                                                           |
| [ndopify-website](modules/ndopify-website) | Static marketing + lead-capture website (plain HTML/CSS/JS, French) — not a Maven module |

### Module Dependency

```
ndopify-dto  →  ndopify-server
```

`ndopify-website` is standalone: it is not part of the Maven reactor and has no build-time dependency on the other
modules.

## Technologies

- **Language**: Kotlin, JDK 17
- **Framework**: Spring Boot (Web, Data JPA, Security, Cache, Validation, Actuator)
- **Database**: MySQL, with Flyway for versioned schema migrations
- **API docs**: SpringDoc OpenAPI / Swagger UI
- **Build**: Maven
- **Code quality**: ktlint (formatting), JaCoCo (coverage gate)

## Architecture Overview

`ndopify-server` is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same
layering (`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

- **refdata** — shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`.
- **security** — `User` accounts, JWT issuance, and pluggable authentication (`PasswordAuthenticator`,
  `GoogleOneTapAuthenticator`).
- **party** — parties, identifications (with uploaded images), KYC cases/verifications, and payment methods. KYC
  cases are verified through pluggable `KycVerifier` strategies (identification, mobile-money, manual).
- **agent** — mobile-money agents: create, update, get and search, plus a welcome e-mail on registration.
- **error** — shared exception types and a global error handler.

A non-domain **`platform`** package holds shared infrastructure: the mobile-money gateway integration
(`platform.momo`, MTN only today), file storage (`platform.storage`, local or S3), mail (`platform.mail`),
request logging (`platform.logger`) and tenant resolution (`platform.tenant`).

Tenancy is request/token-scoped via the `X-Tenant-ID` header and a `tenantId` JWT claim, and enforced by Hibernate's
`@TenantId` on the `party` entities and `AgentEntity` — every query against them is filtered automatically.
Reference data (`refdata`) and `security` entities are global.

See [CLAUDE.md](CLAUDE.md) for detailed build/test commands and architecture notes for AI-assisted development.

## Getting Started

Requirements: JDK 17, Maven 3.9+, MySQL 8+ (local `root` user with no password).

```bash
# Build the whole monorepo
mvn install -Dheadless=true

# Run the server (starts on port 8080, Swagger UI at /api.html)
cd modules/ndopify-server
mvn spring-boot:run
```

First-time local setup requires importing reference data:

```bash
curl "http://localhost:8080/v1/locations/import?country=<ISO-country-code>"
```

To preview the website (no build step):

```bash
cd modules/ndopify-website/src/main/html
python3 -m http.server 8080   # then open http://localhost:8080/index.html
```

## AI Commands & Skills

AI tooling for [Claude Code](https://claude.com/claude-code) lives in [`.claude/`](.claude). There are no custom
slash commands; everything is a skill under `.claude/skills/`.

### Project skills

These drive the generation of the website. Run `init-website-design` first, since `init-website` reads the
`DESIGN.md` it produces.

| Skill                                                              | Purpose                                                                                                                                                   |
|--------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`init-website-design`](.claude/skills/init-website-design/SKILL.md) | Generates `modules/ndopify-website/DESIGN.md` (colors, typography, spacing, components) from the canonical design doc, then validates it with `npx @google/design.md lint`. |
| [`init-website`](.claude/skills/init-website/SKILL.md)             | Builds the French website in `modules/ndopify-website/src/main/html/` in three stages: journey planning (`neo-user-journey`), generation (`power-design`), critique and fixes (`impeccable`). |

### Vendored third-party skills

Used by `init-website`; not specific to Ndopify.

| Skill                                                          | Purpose                                                                                           |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [`neo-user-journey`](.claude/skills/neo-user-journey/SKILL.md) | UX research: user journeys, personas, synthetic-user walkthroughs, accessibility audits           |
| [`power-design`](.claude/skills/power-design/SKILL.md)         | On-brand HTML decks and websites, using 20 codified web design rules                              |
| [`impeccable`](.claude/skills/impeccable/SKILL.md)             | Design critique, audit, polish and an anti-pattern detector for frontend interfaces               |

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
