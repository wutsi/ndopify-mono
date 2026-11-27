# Nest e-mail verification under platform/mail/verification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the nine e-mail verification classes from `com.wutsi.ndopify.platform.email` to `com.wutsi.ndopify.platform.mail.verification`, so `platform/` holds one e-mail umbrella instead of two indistinguishable siblings, with no behavior, database, DTO or config change.

**Architecture:** Pure package move in `modules/ndopify-server`. The nine classes change their `package` line and nothing else; their five tests move beside them; four main-tree callers and three test callers change imports only. The sending classes stay flat in `platform.mail`, the `email` domain keeps its policy and its `T_EMAIL_ADDRESS` cache, and every class keeps its fully qualified name but for the package segment. Both packages stay under the scanned `com.wutsi.ndopify` root, so Spring and JPA wiring is unchanged.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA, Maven (ktlint, JaCoCo), JUnit 5 / `kotlin.test` / mockito-kotlin.

**Spec:** story #387 — https://github.com/wutsi/ndopify-mono/issues/387

## Global Constraints

- **Pure package move.** No class, enum constant, method, constructor, bean name, config key, DB column or stored enum name changes. No Flyway migration, no `ndopify-dto` change, no `pom.xml` change.
- **Target package:** `com.wutsi.ndopify.platform.mail.verification`, flat — no `model/`, `ses/` or `none/` subpackage.
- **Source package gone:** `com.wutsi.ndopify.platform.email` must not remain on disk.
- **Sending stays flat.** `platform/mail` keeps `Email`, `Receipient`, `MailService`, `MailBodyResolver`, `MailFilter`, `MailImages`, `ServerMessageIdParser` and `mail/filter/` (`HtmlEscapeFilter`, `MailFilterNull`) unchanged.
- **The `email` domain keeps its FQNs:** `com.wutsi.ndopify.email.server.service.EmailVerificationService`, `com.wutsi.ndopify.email.server.domain.EmailAddressEntity`, `com.wutsi.ndopify.email.server.dao.EmailAddressRepository`.
- **Tests keep their assertions.** Only `package` and `import` lines change in the five moved tests and the three import-only tests.
- **Wiring stays as is.** `EmailConfiguration` keeps its name, its `config/platform` place, its bean method names (`emailVerifierNone`, `emailVerifierSes`, `sesEmailVerifierHealthIndicator`) and its `@ConditionalOnProperty` keys; only its imports change. `Application.kt`'s `@EntityScan` / `@EnableJpaRepositories` entries are not edited.
- **Config keys keep the word `email`:** `ndopify.email.verification.*` is unchanged.
- **Style:** ktlint runs in `mvn validate`; imports are one alphabetically sorted block, and no same-package import may remain in `platform.mail.verification`.
- `cd modules/ndopify-server && mvn verify` is the gate (local MySQL required for the endpoint and integration tests).
- **Historical plans stay untouched:** `docs/superpowers/plans/380-*.md` and `docs/superpowers/plans/384-*.md` keep their `platform/email` references — they are the design record of #380 / #384 (story #387, AC 5).

## Review Focus

1. **The cached verdict still round-trips its enums.** `EmailAddressEntity` and the `@Converter(autoApply = true)` classes in `util/jpa/EnumConverters` must name the same `Verdict` / `VerificationReason` types (now `platform.mail.verification`); a converter left on another type makes a stored row read back as the converter's default. `EmailAddressRepositoryTest` round-trips both enums.
2. **The throttle bean still exists.** `EmailVerificationThrottle` is a `@Component`, found by component scan under `com.wutsi.ndopify`; a target package outside that root silently drops it and `EmailVerificationService` fails to construct. The context boots in `CreateMandateEmailEndpointTest` and the two `SesEmailVerifierHealth*Endpoint*/Disabled` tests.
3. **A stale import fails the build twice.** An import of a moved class under its old package is a compile error; an import of a class now in the same package is redundant and fails ktlint. A move that leaves `import com.wutsi.ndopify.platform.email.…` anywhere in the tree is caught by the grep in Task 3, Step 1.
4. **The SES adapter is still wired only under `provider=ses`.** `EmailConfiguration` must still wire `EmailVerifierNone` with `matchIfMissing = true`, and the SES client, verifier and health indicator only with `ses`; `SesEmailVerifierHealthDisabledTest` proves `none` makes no AWS call.
5. **The policy's behavior is unchanged.** `EmailVerificationServiceTest` pins cache, TTL, fail-open and budget; `CreateMandateEmailEndpointTest` pins `400 EMAIL_ADDRESS_INVALID` on a LOW verdict and acceptance with `provider=none`.

---

### Task 1: Move the nine classes, their five tests and every import

**Files:**
- Move: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifier.kt` → `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/verification/EmailVerifier.kt`
- Move: `.../platform/email/EmailVerification.kt` → `.../platform/mail/verification/EmailVerification.kt`
- Move: `.../platform/email/EmailVerifierNone.kt` → `.../platform/mail/verification/EmailVerifierNone.kt`
- Move: `.../platform/email/EmailVerifierSES.kt` → `.../platform/mail/verification/EmailVerifierSES.kt`
- Move: `.../platform/email/SesEmailVerifierHealthIndicator.kt` → `.../platform/mail/verification/SesEmailVerifierHealthIndicator.kt`
- Move: `.../platform/email/EmailVerificationThrottle.kt` → `.../platform/mail/verification/EmailVerificationThrottle.kt`
- Move: `.../platform/email/EmailSyntax.kt` → `.../platform/mail/verification/EmailSyntax.kt`
- Move: `.../platform/email/Verdict.kt` → `.../platform/mail/verification/Verdict.kt`
- Move: `.../platform/email/VerificationReason.kt` → `.../platform/mail/verification/VerificationReason.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/EmailAddressEntity.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationService.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt` (imports only)
- Move: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerificationThrottleTest.kt` → `.../platform/mail/verification/EmailVerificationThrottleTest.kt`
- Move: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSESTest.kt` → `.../platform/mail/verification/EmailVerifierSESTest.kt`
- Move: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthDisabledTest.kt` → `.../platform/mail/verification/SesEmailVerifierHealthDisabledTest.kt`
- Move: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthEndpointTest.kt` → `.../platform/mail/verification/SesEmailVerifierHealthEndpointTest.kt`
- Move: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthIndicatorTest.kt` → `.../platform/mail/verification/SesEmailVerifierHealthIndicatorTest.kt`
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepositoryTest.kt` (imports only)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationServiceTest.kt` (imports only)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt` (imports only)

**Interfaces:**
- Produces, all in `com.wutsi.ndopify.platform.mail.verification`, names and bodies unchanged:
  - `fun interface EmailVerifier { fun verify(email: String): EmailVerification }`
  - `data class EmailVerification(score: Double?, reason: VerificationReason, hasValidSyntax: Verdict = Verdict.HIGH, hasValidDnsRecords: Verdict = Verdict.HIGH, mailboxExists: Verdict = Verdict.HIGH, isRoleAddress: Verdict = Verdict.HIGH, isDisposable: Verdict = Verdict.HIGH, isRandomInput: Verdict = Verdict.HIGH)`
  - `class EmailVerifierNone : EmailVerifier`
  - `class EmailVerifierSES(private val client: SesV2Client) : EmailVerifier`
  - `@Component class EmailVerificationThrottle(clock: Clock, perIpPerHour: Int, dailyCap: Int) { fun tryAcquire(ip: String?): Boolean }`
  - `object EmailSyntax { fun isValid(email: String): Boolean }`
  - `class SesEmailVerifierHealthIndicator(client: SesV2Client, clock: Clock, upTtl: Duration, downTtl: Duration) : HealthIndicator`
  - `enum class Verdict { HIGH, MEDIUM, LOW }`
  - `enum class VerificationReason { NONE, SYNTAX_INVALID, DOMAIN_INVALID, MAILBOX_UNREACHABLE, DISPOSABLE, ROLE_ADDRESS, RANDOM_INPUT, UNAVAILABLE }`
- Consumes: nothing new.

- [ ] **Step 1: Move the nine main files**

```bash
cd modules/ndopify-server
mkdir -p src/main/kotlin/com/wutsi/ndopify/platform/mail/verification
git mv src/main/kotlin/com/wutsi/ndopify/platform/email/*.kt \
  src/main/kotlin/com/wutsi/ndopify/platform/mail/verification/
```

Change the first line of each of the nine files to `package com.wutsi.ndopify.platform.mail.verification`. Change
nothing else: no intra-package import exists today (the nine reference each other by simple name), and none may be
added. `EmailVerifierSES.kt` drops no import — it has only its four `software.amazon.awssdk` ones.

Then remove the empty source directory:

```bash
rmdir src/main/kotlin/com/wutsi/ndopify/platform/email
ls src/main/kotlin/com/wutsi/ndopify/platform
```

Expected: no `email` entry; `mail`, `ai`, `endpoint`, `image`, `logger`, `momo`, `storage`, `telegram`, `tenant`,
`util` remain.

- [ ] **Step 2: Fix the four main-code imports**

Every one of these replaces the package prefix `com.wutsi.ndopify.platform.email` with
`com.wutsi.ndopify.platform.mail.verification`; the imported simple names do not change, and the block stays
alphabetically sorted.

- `config/platform/EmailConfiguration.kt`: `EmailVerifier`, `EmailVerifierNone`, `EmailVerifierSES`,
  `SesEmailVerifierHealthIndicator` — four imports.
- `email/server/domain/EmailAddressEntity.kt`: `Verdict`, `VerificationReason` — two imports.
- `email/server/service/EmailVerificationService.kt`: `EmailSyntax`, `EmailVerification`,
  `EmailVerificationThrottle`, `EmailVerifier` — four imports.
- `util/jpa/EnumConverters.kt`: `Verdict`, `VerificationReason` — two imports.

`EmailConfiguration`'s KDoc, `@Configuration`, bean method names and `@ConditionalOnProperty` keys are not edited.

- [ ] **Step 3: Move the five tests**

```bash
cd modules/ndopify-server
mkdir -p src/test/kotlin/com/wutsi/ndopify/platform/mail/verification
git mv src/test/kotlin/com/wutsi/ndopify/platform/email/*.kt \
  src/test/kotlin/com/wutsi/ndopify/platform/mail/verification/
rmdir src/test/kotlin/com/wutsi/ndopify/platform/email
```

Change the first line of each of the five to `package com.wutsi.ndopify.platform.mail.verification`; keep every test
body, `@TestPropertySource`, `BaseEndpointIntegrationTest` superclass and private helper unchanged. None of the five
imports a class of its own package today, so no import is added or removed here.

- [ ] **Step 4: Fix the three test imports**

Same prefix replacement — `com.wutsi.ndopify.platform.email` → `com.wutsi.ndopify.platform.mail.verification`:

- `email/server/dao/EmailAddressRepositoryTest.kt`: `Verdict`, `VerificationReason`.
- `email/server/service/EmailVerificationServiceTest.kt`: `EmailVerification`, `EmailVerificationThrottle`,
  `EmailVerifier`, `VerificationReason`.
- `mandate/server/endpoint/CreateMandateEmailEndpointTest.kt`: `EmailVerification`, `EmailVerifier`,
  `VerificationReason`.

Keep the `com.wutsi.ndopify.email.server.dao.EmailAddressRepository` import in the last two: the domain did not move.

- [ ] **Step 5: Compile, then run the unit tests that need no MySQL**

Run: `cd modules/ndopify-server && mvn test-compile`
Expected: BUILD SUCCESS — no unresolved reference to a moved class.

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest,EmailVerifierSESTest,SesEmailVerifierHealthIndicatorTest'`
Expected: BUILD SUCCESS, every test passes. These four are plain unit tests; the two `SesEmailVerifierHealth*` endpoint
tests need MySQL and run in Task 3.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify
git commit -m "refactor(server): nest e-mail verification under platform/mail/verification" -m "Refs #387"
```

---

### Task 2: Merge the two platform bullets in the module docs

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the `platform/mail` bullet, the `platform/email` bullet, and the `email`
  domain bullet)

**Interfaces:**
- Consumes: Task 1's final package.
- Produces: nothing (documentation).

- [ ] **Step 1: Fold the verification capability into the `platform/mail` bullet**

The `platform/mail` bullet currently ends at `…a missing cover still sends no image.` Append, in the same bullet,
that the e-mail verification capability sits under `mail/verification`: the `EmailVerifier` port, the
provider-neutral `EmailVerification` model with its `Verdict` / `VerificationReason` enums, the `EmailVerifierNone`
default adapter, the `EmailVerificationThrottle` paid-call budget guard and the local `EmailSyntax` check; and the SES
adapter (`EmailVerifierSES`, one `GetEmailAddressInsights` call through the AWS SDK v2 `sesv2`, beside the v1 S3
client) with its cached health indicator (`SesEmailVerifierHealthIndicator`, `GetAccount`, `sesEmailVerifier`). Keep
the sentence that `ndopify.email.verification.provider` is the only switch — `none` by default (local, QA, the
server's tests), `ses` in TEST and PROD — and that `none` wires `EmailVerifierNone` and no health component, so
nothing calls AWS. Name the path as `platform/mail` with its `verification/` subpackage, and keep the existing
sending text ahead of it.

- [ ] **Step 2: Delete the `platform/email` bullet**

Remove that bullet entirely. No bullet in `modules/ndopify-server/CLAUDE.md` may open with `- \`platform/email\``
afterwards.

- [ ] **Step 3: Point the `email` domain bullet at the new package**

In the **email** domain bullet, replace `live in \`platform.email\`` with `live in \`platform.mail.verification\``.
Change nothing else there: the policy's behavior, the TTLs, fail-open, budget and `400 EMAIL_ADDRESS_INVALID` stay as
written, and `(see below)` still holds because the platform bullets follow the domain bullets.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): describe e-mail verification under platform/mail/verification" -m "Refs #387"
```

---

### Task 3: Verify the tree, the docs and the build

**Files:** none (verification only).

**Interfaces:**
- Consumes: Tasks 1 and 2.

- [ ] **Step 1: Assert the old package is gone from code, tests and the living docs**

```bash
grep -rn --include='*.kt' --include='*.yml' --include='*.yaml' --include='*.properties' --include='*.md' \
  -e 'com\.wutsi\.ndopify\.platform\.email' -e 'platform/email' \
  modules/ndopify-server docs/superpowers/specs
```

Expected: no output. The two completed plan records `docs/superpowers/plans/380-*.md` and `384-*.md` still match and
are intentionally out of the search root.

- [ ] **Step 2: Assert each package holds the expected classes**

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/verification`
Expected: exactly the nine files named in Task 1, Step 1.

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail`
Expected: `Email.kt`, `MailBodyResolver.kt`, `MailFilter.kt`, `MailImages.kt`, `MailService.kt`, `Receipient.kt`,
`ServerMessageIdParser.kt`, `filter`, `verification` — no verification class flat in `mail` itself.

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/*/`
Expected: `dao/EmailAddressRepository.kt`, `domain/EmailAddressEntity.kt`, `service/EmailVerificationService.kt`, and
nothing else.

- [ ] **Step 3: Run the full module gate**

Run: `cd modules/ndopify-server && mvn verify` (local MySQL must be running)
Expected: BUILD SUCCESS — `validate` (ktlint), every test (including `EmailAddressRepositoryTest`,
`CreateMandateEmailEndpointTest`, `SesEmailVerifierHealthDisabledTest`, `SesEmailVerifierHealthEndpointTest`) and the
JaCoCo coverage gate pass.

- [ ] **Step 4: Review the diff against `main`**

Run: `git diff --stat origin/main` and `git diff origin/main -- modules/ndopify-server/src`
Expected: only renames under `platform/email/` → `platform/mail/verification/` (main and test), import-only edits in
the four main-tree callers and three test callers, and `modules/ndopify-server/CLAUDE.md`. No change inside a moved
class body, no migration, no DTO, no `pom.xml`, no `application*.yml`, no `Application.kt`, and nothing under
`docs/superpowers/plans/380-*.md` or `384-*.md`.
