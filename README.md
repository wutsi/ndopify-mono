# ndopify-mono

Maven monorepo for Ndopify — a multi-tenant backend for managing mobile-money agents, their KYC verification, and
mobile-money payment integrations across Africa.

[![main](https://github.com/wutsi/ndopify-mono/actions/workflows/_main.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/_main.yml)
[![pr](https://github.com/wutsi/ndopify-mono/actions/workflows/_pr.yml/badge.svg)](https://github.com/wutsi/ndopify-mono/actions/workflows/_pr.yml)

## Project Status

Early-stage. Only four modules exist today — there is no SDK or tracking-server module (yet).

## Modules

| Module                                                     | Description                                                                              |
|------------------------------------------------------------|------------------------------------------------------------------------------------------|
| [ndopify-dto](modules/ndopify-dto)                         | Shared request/response DTOs and contracts — no business logic                           |
| [ndopify-server](modules/ndopify-server)                   | REST API backend (Spring Boot)                                                           |
| [ndopify-partner-central](modules/ndopify-partner-central) | Agent-facing web app (Spring Boot + Thymeleaf, French): sign-up wizard, e-mail OTP sign-in, KYC wizard |
| [ndopify-website](modules/ndopify-website)                 | Static marketing + lead-capture website (plain HTML/CSS/JS, French) — not a Maven module |

### Module Dependency

```
ndopify-dto  →  ndopify-server
ndopify-dto  →  ndopify-partner-central  ──(REST, runtime)──▶  ndopify-server
```

`ndopify-partner-central` depends on `ndopify-dto` at build time and calls `ndopify-server` over HTTP at runtime
(`ndopify.server.api.base-url`); it has no database of its own.

`ndopify-website` is standalone: it is not part of the Maven reactor and has no build-time dependency on the other
modules.

## Technologies

- **Language**: Kotlin, JDK 17
- **Framework**: Spring Boot (Web, Data JPA, Security, Cache, Validation, Actuator), Thymeleaf (partner central)
- **AI**: Spring AI (OpenAI-compatible client, Kimi by default) for identity-document data extraction
- **Database**: MySQL, with Flyway for versioned schema migrations
- **API docs**: SpringDoc OpenAPI / Swagger UI
- **Build**: Maven
- **Code quality**: ktlint (formatting), JaCoCo (coverage gate)

## Architecture Overview

`ndopify-server` is organized by business domain under `com.wutsi.ndopify.<domain>.server`, each following the same
layering (`endpoint` → `service` → `dao` → `domain`, plus `mapper` for entity/DTO conversion):

- **refdata** — shared reference data: `Tenant`, `Location` (imported from GeoNames), `Application`, `Role`.
- **security** — `User` accounts, JWT issuance, and pluggable authentication (`PasswordAuthenticator`,
  `GoogleOneTapAuthenticator`, `OtpAuthenticator` for e-mailed one-time codes).
- **party** — parties, identifications (with uploaded images), KYC cases/verifications, and payment methods. KYC
  cases are verified through pluggable `KycVerifier` strategies (identification, mobile-money, manual); data is
  extracted from identity-document images by an LLM (`IdentificationInfoExtractorAi`).
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

Requirements: JDK 17, Maven 3.9+, MySQL 8+ (local `root` user with no password). For OTP sign-in e-mails, a local
SMTP catcher such as [Mailpit](https://mailpit.axllent.org/) on `localhost:1025`. For identity-document extraction,
a `KIMI_API_KEY`.

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

Run the partner central (starts on port 8081, expects `ndopify-server` on `http://localhost:8080`):

```bash
cd modules/ndopify-partner-central
mvn spring-boot:run
```

To preview the website (no build step):

```bash
cd modules/ndopify-website/src/main/html
python3 -m http.server 8080   # then open http://localhost:8080/index.html
```

## AI Commands & Skills

AI tooling for [Claude Code](https://claude.com/claude-code) lives in [`.claude/`](.claude): slash commands under
`.claude/commands/` and skills under `.claude/skills/`.

### Slash commands

| Command                                                            | Purpose                                                                                                   |
|--------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| [`/init-website-design`](.claude/commands/init-website-design.md)   | Generates `modules/ndopify-website/DESIGN.md` (colors, typography, spacing, components) from the canonical design doc. Run it before `/init-website`, which reads that file. |
| [`/init-website`](.claude/commands/init-website.md)                 | Builds the French website in `modules/ndopify-website/src/main/html/` in three stages: journey planning (`neo-user-journey`), generation (`power-design`), critique and fixes (`impeccable`). |
| [`/init-partner-central`](.claude/commands/init-partner-central.md) | Builds the French partner central pages in `modules/ndopify-partner-central/src/main/html/` using the same three-stage pipeline (`neo-user-journey` → `power-design` → `impeccable`). |

### Vendored third-party skills

Used by `/init-website` and `/init-partner-central`; not specific to Ndopify.

| Skill                                                          | Purpose                                                                                           |
|----------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| [`neo-user-journey`](.claude/skills/neo-user-journey/SKILL.md) | UX research: user journeys, personas, synthetic-user walkthroughs, accessibility audits           |
| [`power-design`](.claude/skills/power-design/SKILL.md)         | On-brand HTML decks and websites, using 20 codified web design rules                              |
| [`impeccable`](.claude/skills/impeccable/SKILL.md)             | Design critique, audit, polish and an anti-pattern detector for frontend interfaces               |

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
