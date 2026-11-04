# Contributing to ndopify-mono

This guide gets a local development environment running: the toolchain, the MySQL database, and the external
services `ndopify-server` talks to — an SMTP server for email (Mailpit), the DeepSeek LLM API (used by both AI
clients today), and, only when exercising real calls, the MTN Mobile Money API.

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

`ndopify-server` reads one secret from the environment. It has no default, so **the server won't start without
it** — set it even if you're not working on AI features (any non-empty value lets the app boot; the AI features
then fail when called).

| Variable           | Used for                                               | How to get it                        |
|--------------------|--------------------------------------------------------|--------------------------------------|
| `DEEPSEEK_API_KEY` | Both AI clients: document extraction and generated text | [DeepSeek API](#deepseek-api)         |

Put it in your shell profile (e.g. `~/.zshrc`) so every terminal and your IDE's run configurations see it:

```bash
export DEEPSEEK_API_KEY=<your-deepseek-api-key>
```

Never commit real keys.

## External services

### Email (Mailpit)

`ndopify-server` sends email (e.g. one-time sign-in codes) over SMTP to `localhost:1025`. Locally, Mailpit plays
that server: it accepts every message and shows it in a web inbox instead of delivering it.

The default `spring.mail` config in `modules/ndopify-server/src/main/resources/application.yml` sends to
`localhost:1025` with SMTP auth and STARTTLS both off, which is exactly what Mailpit accepts by default:

```bash
mailpit
```

```bash
cd modules/ndopify-server
mvn spring-boot:run
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
subscription key (`MtnUserProviderSandbox`). `ndopify.mobile-money.mtn.collection.user-id` and `api-key` default to
dummy placeholders; leave them as they are locally. They are only set in production, where MTN issues the API user
and key. The server boots without any MTN setting, so this section is only needed for real MTN calls.

### DeepSeek API

`ndopify-server` uses two Spring AI `ChatClient`s (`ChatClients.VISION` and `ChatClients.TEXT`): the vision client
extracts information from photos of identity documents and listing pictures, the text client generates listing
titles, summaries and descriptions. Both currently point at DeepSeek:

- `spring.ai.openai` (vision, OpenAI-compatible): `https://api.deepseek.com`, model `deepseek-flash`
- `spring.ai.deepseek` (text): `https://api.deepseek.com`, model `deepseek-flash`

1. Create an account on the [DeepSeek platform](https://platform.deepseek.com).
2. Add credit to the account (API calls are billed per token).
3. Create an API key and put it in `DEEPSEEK_API_KEY`.

## Running locally

Start the services first (MySQL, Mailpit), then the apps:

| App                       | Command                                                  | URL                                    |
|---------------------------|----------------------------------------------------------|----------------------------------------|
| `ndopify-server`          | `cd modules/ndopify-server && mvn spring-boot:run`       | http://localhost:8080 — Swagger UI at `/api.html` |
| `ndopify-partner-central` | `cd modules/ndopify-partner-central && mvn spring-boot:run` | http://localhost:8081                  |
| `ndopify-web`             | `cd modules/ndopify-web && mvn spring-boot:run`            | http://localhost:8082                  |
| `ndopify-esignature`      | `cd modules/ndopify-esignature && mvn spring-boot:run`     | http://localhost:8083                  |

All three web apps call `ndopify-server` at `http://localhost:8080` (`ndopify.server.api.base-url`), so start the
server first. Each also needs a tenant whose `partnerCentralUrl` / `publicPortalUrl` / `eSignaturePortalUrl` matches
the URL you browse to (the local seed data uses the ports above).

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
