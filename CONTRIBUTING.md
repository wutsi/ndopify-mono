# Contributing to ndopify-mono

This guide gets a local development environment running: the toolchain, the MySQL database, and the three
external services `ndopify-server` talks to — an SMTP server for email (Mailpit), the MTN Mobile Money API, and
the Kimi (Moonshot AI) LLM API.

## Prerequisites

| Tool    | Version | Notes                                                              |
|---------|---------|--------------------------------------------------------------------|
| JDK     | 17      |                                                                    |
| Maven   | 3.9+    |                                                                    |
| MySQL   | 8+      | `root` user with **no password** (matches `application.yml`)       |
| Mailpit | latest  | Local SMTP server that catches outgoing email (see [Email](#email-mailpit)) |

On macOS:

```bash
brew install openjdk@17 maven mysql mailpit
brew services start mysql
```

Create the database (Flyway creates the tables on first start):

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS ndopify"
```

## Environment variables

`ndopify-server` reads two secrets from the environment. Both placeholders have no default, so **the server
won't start if either is unset** — set them even if you're not working on MTN or AI features (any non-empty value
lets the app boot; the related features then fail when called).

| Variable                          | Used for                                     | How to get it                    |
|-----------------------------------|----------------------------------------------|----------------------------------|
| `MTN_COLLECTION_SUBSCRIPTION_KEY` | MTN Mobile Money KYC checks on agents        | [MTN MoMo API](#mtn-mobile-money-api) |
| `KIMI_API_KEY`                    | Reading identity documents (AI extraction)   | [Kimi API](#kimi-api-moonshot-ai) |

Put them in your shell profile (e.g. `~/.zshrc`) so every terminal and your IDE's run configurations see them:

```bash
export MTN_COLLECTION_SUBSCRIPTION_KEY=<your-mtn-primary-key>
export KIMI_API_KEY=<your-kimi-api-key>
```

Never commit real keys.

## External services

### Email (Mailpit)

`ndopify-server` sends email (e.g. one-time sign-in codes) over SMTP to `localhost:1025`. Locally, Mailpit plays
that server: it accepts every message and shows it in a web inbox instead of delivering it.

The default `spring.mail` config in `modules/ndopify-server/src/main/resources/application.yml` uses SMTP auth
(`user@example.com` / `secret`) and **requires** STARTTLS. Mailpit has no TLS certificate out of the box, so
start Mailpit accepting any credentials, and turn STARTTLS off for the local server run:

```bash
mailpit --smtp-auth-accept-any --smtp-auth-allow-insecure
```

```bash
cd modules/ndopify-server
mvn spring-boot:run -Dspring-boot.run.arguments="\
--spring.mail.properties.mail.smtp.starttls.enable=false \
--spring.mail.properties.mail.smtp.starttls.required=false"
```

- SMTP: `localhost:1025`
- Inbox: http://localhost:8025 — every email the server sends shows up here

Tests don't need Mailpit: they use an embedded GreenMail server on port 3025 (`src/test/resources/application-qa.yml`).

### MTN Mobile Money API

The `agent` domain uses MTN's **Collection** product to check that an agent's mobile-money number belongs to the
name they declared (KYC). Locally the server runs against MTN's **sandbox**
(`ndopify.mobile-money.mtn.environment: sandbox`), which uses test data and moves no real money.

1. Create an account on the [MTN MoMo Developer Portal](https://momodeveloper.mtn.com).
2. Under **Products**, subscribe to **Collections**.
3. In your profile, copy the subscription's **Primary key** into `MTN_COLLECTION_SUBSCRIPTION_KEY`.

That's all the sandbox needs: on first use, the server creates its own sandbox API user and API key with that
subscription key (`MtnUserProviderSandbox`), so leave `ndopify.mobile-money.mtn.collection.user-id` and
`api-key` empty. Those two are only for the production environment, where MTN issues the API user and key.

### Kimi API (Moonshot AI)

`ndopify-server` uses an LLM to extract information from photos of identity documents
(`IdentificationInfoExtractorAi`). It goes through Spring AI's OpenAI client pointed at Moonshot AI's
OpenAI-compatible endpoint (`spring.ai.openai` in `application.yml`):

- base URL: `https://api.moonshot.ai/v1`
- model: `kimi-k2.6`

1. Create an account on the [Moonshot AI platform](https://platform.moonshot.ai).
2. Add credit to the account (API calls are billed per token).
3. Create an API key and put it in `KIMI_API_KEY`.

## Running locally

Start the services first (MySQL, Mailpit), then the apps:

| App                       | Command                                                  | URL                                    |
|---------------------------|----------------------------------------------------------|----------------------------------------|
| `ndopify-server`          | `cd modules/ndopify-server && mvn spring-boot:run` (with the Mailpit arguments above) | http://localhost:8080 — Swagger UI at `/api.html` |
| `ndopify-partner-central` | `cd modules/ndopify-partner-central && mvn spring-boot:run` | http://localhost:8081                  |

`ndopify-partner-central` calls `ndopify-server` at `http://localhost:8080`
(`ndopify.server.api.base-url`), so start the server first.

First-time setup: import the reference data for at least one country (ISO country code, e.g. `CM`):

```bash
curl "http://localhost:8080/v1/locations/import?country=CM"
```

## Build, test and code quality

```bash
mvn install -Dheadless=true          # whole monorepo
cd modules/<module> && mvn test      # one module (endpoint tests need MySQL running)
mvn test -Dtest=ClassName#method     # one test
mvn antrun:run@ktlint-format         # auto-format Kotlin
mvn verify                           # tests + JaCoCo coverage gate
```

The coverage thresholds are enforced by the build: see `jacoco.threshold.*` in the root `pom.xml` and any
per-module override.

## Conventions

See [`CLAUDE.md`](CLAUDE.md) for architecture, multi-tenancy, testing and migration conventions. In short:

- Assertions use `kotlin.test` (`assertEquals`, `assertFailsWith`…), not JUnit's `Assertions`.
- Never edit an existing Flyway migration; add a new `V{version}__{description}.sql`.
- Keep the CI badges at the top of each module's `README.md` when editing it.
