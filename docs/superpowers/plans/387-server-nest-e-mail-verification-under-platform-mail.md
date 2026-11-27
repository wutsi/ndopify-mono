# One mail umbrella for e-mail: package, configuration and settings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate e-mail under one umbrella in `modules/ndopify-server`: move the nine verification classes from `com.wutsi.ndopify.platform.email` to `com.wutsi.ndopify.platform.mail.verification`, fold `config/platform/EmailConfiguration` into `config/platform/MailConfiguration`, rename the verification settings from `ndopify.email.verification.*` to `ndopify.mail.verification.*`, and give the SES client the deployment's credentials (`AWS_ACCESS_KEY` / `AWS_SECRET_KEY`) — with no behavior, database, DTO or value change beyond authenticating those calls.

**Architecture:** Four edits that each keep the module compiling and its tests green, in this order: rename the settings (YAML, `@Value`/`@ConditionalOnProperty` strings, test overrides); merge the configuration (four `@Bean` methods and their constructor parameters move into `MailConfiguration`, `EmailConfiguration` is deleted); move the package (nine classes and their five tests change their `package` line, four main-tree callers and three test files change imports); then wire the credentials (two new settings under `ses`, a `StaticCredentialsProvider` on the client when both are set, one new unit test). The sending classes stay flat in `platform.mail`, the `email` domain keeps its policy and its `T_EMAIL_ADDRESS` cache, and every class keeps its fully qualified name but for the package segment. Both packages stay under the scanned `com.wutsi.ndopify` root, so Spring and JPA wiring is unchanged.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA, Maven (ktlint, JaCoCo), JUnit 5 / `kotlin.test` / mockito-kotlin.

**Spec:** story #387 — https://github.com/wutsi/ndopify-mono/issues/387

## Global Constraints

- **No change beyond the four outcomes.** No threshold, TTL, budget or timeout value changes; the one behavioral change is that SES calls now authenticate with the deployment's credentials. The only edits inside a class are a moved class's `package` line, the `@Value` / `@ConditionalOnProperty` key strings, the two KDoc sentences naming a key, the configuration merge and the conditional credentials provider. No Flyway migration, no `ndopify-dto` change, no `pom.xml` change.
- **Target package:** `com.wutsi.ndopify.platform.mail.verification`, flat — no `model/`, `ses/` or `none/` subpackage. `com.wutsi.ndopify.platform.email` must not remain on disk.
- **One configuration class.** `config/platform/MailConfiguration` is the only e-mail configuration; `config/platform/EmailConfiguration.kt` is deleted. The four verification bean names stay — `emailVerifierNone`, `sesV2Client`, `emailVerifierSes`, `sesEmailVerifierHealthIndicator` — because the `sesEmailVerifier` actuator health component's id derives from the last one. Every `@ConditionalOnProperty` stays, including `matchIfMissing = true` on the `none` bean.
- **One settings namespace.** `ndopify.mail.verification.*` — the nine renamed keys `provider`, `ses.region`, `timeout-seconds`, `ttl.valid-days`, `ttl.invalid-days`, `throttle.per-ip-per-hour`, `throttle.daily-cap`, `health.up-ttl-minutes`, `health.down-ttl-seconds`, plus the two new credential keys `ses.access-key` and `ses.secret-key`. Same leaf names, same values, same comments for the nine.
- **`spring.mail.*` is untouched.** It is Spring Boot's SMTP configuration, read by `JavaMailSender` and by `mailService`'s `from`; it is not the verification namespace.
- **The nine renamed keys gain no default.** Each stays a required `@Value` placeholder: a one-sided rename must fail the context, never fall back silently. The two credential keys are the deliberate exception, empty by design.
- **The credentials are the deployment's variables, not the SDK's.** Heroku holds `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` (the v1 legacy names, read by the S3 client's chain); v2's environment provider reads `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`. The client gets the former explicitly. When either is blank the SDK's default chain stays in charge — the app must boot without credentials. Neither value is ever logged, echoed in a message or carried by the health payload.
- **`none` builds no client.** `sesV2Client` stays `@ConditionalOnProperty(havingValue = "ses")`, so the `none` path constructs no client and cannot reach AWS.
- **Sending stays flat.** `platform/mail` keeps `Email`, `Receipient`, `MailService`, `MailBodyResolver`, `MailFilter`, `MailImages`, `ServerMessageIdParser` and `mail/filter/` unchanged.
- **The `email` domain keeps its FQNs:** `com.wutsi.ndopify.email.server.service.EmailVerificationService`, `com.wutsi.ndopify.email.server.domain.EmailAddressEntity`, `com.wutsi.ndopify.email.server.dao.EmailAddressRepository`, and its own behaviour.
- **Tests keep their assertions.** In the tests that move or are edited, only `package`, `import` and `@TestPropertySource` lines change. One unit-test class is added, `MailConfigurationTest`.
- **Wiring stays as is.** `Application.kt`'s `@EntityScan` / `@EnableJpaRepositories` entries are not edited.
- **Style:** ktlint runs in `mvn validate`; imports are one alphabetically sorted block, and no same-package import may remain in `platform.mail.verification`.
- `cd modules/ndopify-server && mvn verify` is the gate (local MySQL required for the endpoint and integration tests).
- **Historical plans stay untouched:** `docs/superpowers/plans/380-*.md` and `docs/superpowers/plans/384-*.md` keep their `platform/email` and `ndopify.email.verification` references — they are the design record of #380 / #384 (story #387, AC 5).

## Review Focus

1. **The SES client actually carries the deployment's credentials.** The v2 default chain looks for `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`, and TEST/PROD set neither: without the static provider the client cannot sign, the call throws before reaching SES, the policy fails open and `sesEmailVerifier` goes DOWN. The client must use `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` through the two settings when both are non-blank, and keep the default chain when either is blank — a half-configured pair must not build a static provider. `MailConfigurationTest` asserts the resolved access key id, no AWS call involved.
2. **A one-sided rename cannot pass.** Each of the nine renamed keys is a `@Value` placeholder without a default, so the YAML key and the code key must agree or the context fails to start — in every integration test, `mvn verify` included. The likeliest slip is renaming a `@TestPropertySource` on one prefix and the code on the other; the assertions then fail on the other side.
3. **All four verification beans survived the merge, with their guards, and `none` builds no client.** `emailVerifierNone` (`matchIfMissing = true`), `sesV2Client`, `emailVerifierSes` and `sesEmailVerifierHealthIndicator` (all `ses` only). Dropping one or its `@ConditionalOnProperty` leaves a context without a verifier, or with SES beans under `none`. `/actuator/health` must still report `sesEmailVerifier` — the id derives from `sesEmailVerifierHealthIndicator`'s bean name, which is why that name stays. `SesEmailVerifierHealthDisabledTest`, `SesEmailVerifierHealthEndpointTest` and `CreateMandateEmailEndpointTest` boot the context for real.
4. **The cached verdict still round-trips its enums.** `EmailAddressEntity` and the `@Converter(autoApply = true)` classes in `util/jpa/EnumConverters` must name the same `Verdict` / `VerificationReason` types (now `platform.mail.verification`); a converter left on another type makes a stored row read back as the converter's default. `EmailAddressRepositoryTest` round-trips both enums.
5. **The policy's behavior is unchanged.** `EmailVerificationServiceTest` pins cache, TTL, fail-open and budget; `CreateMandateEmailEndpointTest` pins `400 EMAIL_ADDRESS_INVALID` on a LOW verdict and acceptance with `provider=none`.

---

### Task 1: Rename the verification settings to `ndopify.mail.verification.*`

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (the `ndopify:` → `email:` block becomes `mail:`)
- Modify: `modules/ndopify-server/src/main/resources/application-test.yml` (same block, `provider: ses` only)
- Modify: `modules/ndopify-server/src/main/resources/application-prod.yml` (same block, `provider: ses` only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt` (4 `@Value`, 4 `@ConditionalOnProperty`, KDoc)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationService.kt` (3 `@Value`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerificationThrottle.kt` (2 `@Value`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierNone.kt` (KDoc)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt` (3 overrides)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthEndpointTest.kt` (6 overrides over its two classes)

**Interfaces:**
- Produces, the settings every later task and the running application read:

  | key | read by |
  |---|---|
  | `ndopify.mail.verification.provider` | `EmailVerificationService` (`@Value`), `EmailConfiguration` (4 `@ConditionalOnProperty`) |
  | `ndopify.mail.verification.ses.region` | `EmailConfiguration` |
  | `ndopify.mail.verification.timeout-seconds` | `EmailConfiguration` |
  | `ndopify.mail.verification.ttl.valid-days` | `EmailVerificationService` |
  | `ndopify.mail.verification.ttl.invalid-days` | `EmailVerificationService` |
  | `ndopify.mail.verification.throttle.per-ip-per-hour` | `EmailVerificationThrottle` |
  | `ndopify.mail.verification.throttle.daily-cap` | `EmailVerificationThrottle` |
  | `ndopify.mail.verification.health.up-ttl-minutes` | `EmailConfiguration` |
  | `ndopify.mail.verification.health.down-ttl-seconds` | `EmailConfiguration` |

- Consumes: nothing new.

- [ ] **Step 1: Rename the block in `application.yml`**

Replace the `email:` key under `ndopify:` with `mail:`, keeping the whole `verification:` subtree, its values, its
inline comments and its position between `ai:` and `agent:`. The result:

```yaml
    mail:
        verification:
            provider: none                    # none | ses; ses in TEST and PROD
            ses:
                region: us-east-1             # The storage region
            ttl:
                valid-days: 30                # HIGH / MEDIUM
                invalid-days: 7               # LOW
            timeout-seconds: 2                # A slow SES never holds a mandate submission
            throttle:                         # A budget guard: past either, accept without verifying
                per-ip-per-hour: 20
                daily-cap: 500
            health: # /actuator/health is public and SES throttles around one call per second: the result is cached
                up-ttl-minutes: 1
                down-ttl-seconds: 15
```

Do not touch the `spring:` → `mail:` block earlier in the same file.

- [ ] **Step 2: Rename it in the two profiles**

`application-test.yml` and `application-prod.yml` each carry the same three-line block. In both, replace `email:` with
`mail:`, so each reads:

```yaml
    mail:
        verification:
            provider: ses
```

- [ ] **Step 3: Rename the keys in the code**

Every string below changes its prefix only, from `ndopify.email.verification` to `ndopify.mail.verification`; the
suffixes and the surrounding code stay identical.

- `config/platform/EmailConfiguration.kt`: the KDoc's two key names, the `@param:Value` of `ses.region`,
  `timeout-seconds`, `health.up-ttl-minutes` and `health.down-ttl-seconds`, and the four `@ConditionalOnProperty`
  `name = ["ndopify.email.verification.provider"]`.
- `email/server/service/EmailVerificationService.kt`: the `@param:Value` of `provider`, `ttl.valid-days` and
  `ttl.invalid-days`.
- `platform/email/EmailVerificationThrottle.kt`: the `@param:Value` of `throttle.per-ip-per-hour` and
  `throttle.daily-cap`.
- `platform/email/EmailVerifierNone.kt`: the KDoc sentence naming `…provider=none`.

- [ ] **Step 4: Rename the overrides in the tests**

- `CreateMandateEmailEndpointTest.kt`, its one `@TestPropertySource`: `provider`, `throttle.per-ip-per-hour`,
  `throttle.daily-cap`.
- `SesEmailVerifierHealthEndpointTest.kt`, both `@TestPropertySource` blocks (one per class, `…EndpointTest` and
  `…CacheTest`): `provider`, `health.up-ttl-minutes`, `health.down-ttl-seconds`.

`SesEmailVerifierHealthDisabledTest` has no override and is not edited: it is the test that reads the profile's
`none` through the renamed key.

- [ ] **Step 5: Assert nothing of the old key remains in the code**

```bash
cd modules/ndopify-server
grep -rn "ndopify\.email\.verification" src
```

Expected: no output. (The domain's packages are `com.wutsi.ndopify.email.server.*`; the search names the key prefix,
which those packages do not contain.) `CLAUDE.md` still names the old keys until Task 5; Task 6 covers it.

- [ ] **Step 6: Compile, then run the unit tests that need no MySQL**

Run: `cd modules/ndopify-server && mvn test-compile`
Expected: BUILD SUCCESS.

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest,EmailVerifierSESTest,SesEmailVerifierHealthIndicatorTest'`
Expected: BUILD SUCCESS, every test passes. Those four construct their objects directly, so they prove the rename did
not disturb the classes; the `@Value` binding itself is proven by the integration tests in Task 6.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/main/resources/application-test.yml \
  modules/ndopify-server/src/main/resources/application-prod.yml \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify
git commit -m "refactor(server): move the e-mail verification settings to ndopify.mail.verification" -m "Refs #387"
```

---

### Task 2: Merge `EmailConfiguration` into `MailConfiguration`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt`
- Delete: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt`

**Interfaces:**
- Consumes: Task 1's `ndopify.mail.verification.*` keys.
- Produces, in `com.wutsi.ndopify.config.platform.MailConfiguration`, unchanged bean names and types:
  - `@Bean fun mailService(from: String): MailService`
  - `@Bean fun mjmlRenderer(): MjmlRenderer`
  - `@Bean fun mustacheFactory(): MustacheFactory`
  - `@Bean fun mailBodyResolver(): MailBodyResolver`
  - `@Bean @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "none", matchIfMissing = true) fun emailVerifierNone(): EmailVerifier`
  - `@Bean @ConditionalOnProperty(…, havingValue = "ses") fun sesV2Client(): SesV2Client`
  - `@Bean @ConditionalOnProperty(…, havingValue = "ses") fun emailVerifierSes(client: SesV2Client): EmailVerifier`
  - `@Bean @ConditionalOnProperty(…, havingValue = "ses") fun sesEmailVerifierHealthIndicator(client: SesV2Client): HealthIndicator`
- The class is `@Configuration open class MailConfiguration` with the constructor below; the `open` on the class and on
  its bean methods follows the file's existing style.

- [ ] **Step 1: Extend `MailConfiguration`'s constructor**

Keep `private val mail: JavaMailSender` first, then add `EmailConfiguration`'s four `@Value` parameters with their
existing names, and its `Clock`:

```kotlin
@Configuration
open class MailConfiguration(
    private val mail: JavaMailSender,
    @param:Value("\${ndopify.mail.verification.ses.region}") private val region: String,
    @param:Value("\${ndopify.mail.verification.timeout-seconds}") private val timeoutSeconds: Long,
    @param:Value("\${ndopify.mail.verification.health.up-ttl-minutes}") private val upTtlMinutes: Long,
    @param:Value("\${ndopify.mail.verification.health.down-ttl-seconds}") private val downTtlSeconds: Long,
    private val clock: Clock,
)
```

- [ ] **Step 2: Move the four verification beans in, verbatim**

Append the four `@Bean` methods of `EmailConfiguration` after `mailBodyResolver()`, with their bodies, their
`@ConditionalOnProperty` annotations and `sesEmailVerifierHealthIndicator`'s KDoc unchanged. Give each method the
`open` modifier the file's other bean methods use. Add the imports they need — `ConditionalOnProperty`,
`HealthIndicator`, `Region`, `SesV2Client`, `Clock`, `Duration` — and bring `EmailConfiguration`'s four
`com.wutsi.ndopify.platform.email.*` imports over unchanged; Task 3 rewrites their package.

- [ ] **Step 3: Move the verification KDoc to the class**

`EmailConfiguration`'s class KDoc describes the verification wiring; `MailConfiguration` has none. Write one class KDoc
covering both halves: that it wires e-mail sending (`MailService`, `MjmlRenderer`, `MailBodyResolver`) and, behind
`ndopify.mail.verification.provider`, the e-mail verification — the `none` verifier by default, the SES client, its
verifier and its health indicator when `ses` — and keep the sentence that the SDK's own call timeout bounds a slow SES,
so it never holds a submission past `ndopify.mail.verification.timeout-seconds`.

- [ ] **Step 4: Delete `EmailConfiguration`**

```bash
cd modules/ndopify-server
git rm src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt
grep -rn "EmailConfiguration" src/ || echo "gone"
```

Expected: `gone`.

- [ ] **Step 5: Compile and run the unit tests**

Run: `cd modules/ndopify-server && mvn test-compile`
Expected: BUILD SUCCESS.

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest'`
Expected: PASS. The bean wiring itself is proven in Task 6, which boots the context with `none` and with `ses`.

- [ ] **Step 6: Commit**

```bash
git add -u modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform
git commit -m "refactor(server): merge the e-mail verification beans into MailConfiguration" -m "Refs #387"
```

---

### Task 3: Move the nine classes and their five tests

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
- Modify: `.../config/platform/MailConfiguration.kt` (imports only)
- Modify: `.../email/server/domain/EmailAddressEntity.kt` (imports only)
- Modify: `.../email/server/service/EmailVerificationService.kt` (imports only)
- Modify: `.../util/jpa/EnumConverters.kt` (imports only)
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
- Consumes: Task 2's `MailConfiguration`, which imports four of these types.

- [ ] **Step 1: Move the nine main files**

```bash
cd modules/ndopify-server
mkdir -p src/main/kotlin/com/wutsi/ndopify/platform/mail/verification
git mv src/main/kotlin/com/wutsi/ndopify/platform/email/*.kt \
  src/main/kotlin/com/wutsi/ndopify/platform/mail/verification/
rmdir src/main/kotlin/com/wutsi/ndopify/platform/email
```

Change the first line of each of the nine files to `package com.wutsi.ndopify.platform.mail.verification`. Change
nothing else: no intra-package import exists today (the nine reference each other by simple name), and none may be
added. `EmailVerifierSES.kt` drops no import — it has only its four `software.amazon.awssdk` ones.

```bash
ls src/main/kotlin/com/wutsi/ndopify/platform
```

Expected: no `email` entry; `ai`, `endpoint`, `image`, `logger`, `mail`, `momo`, `storage`, `telegram`, `tenant`, `util`
remain.

- [ ] **Step 2: Fix the four main-code imports**

Each replaces the prefix `com.wutsi.ndopify.platform.email` with `com.wutsi.ndopify.platform.mail.verification`; the
simple names do not change and the block stays alphabetically sorted.

- `config/platform/MailConfiguration.kt`: `EmailVerifier`, `EmailVerifierNone`, `EmailVerifierSES`,
  `SesEmailVerifierHealthIndicator`.
- `email/server/domain/EmailAddressEntity.kt`: `Verdict`, `VerificationReason`.
- `email/server/service/EmailVerificationService.kt`: `EmailSyntax`, `EmailVerification`, `EmailVerificationThrottle`,
  `EmailVerifier`.
- `util/jpa/EnumConverters.kt`: `Verdict`, `VerificationReason`.

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
imports a class of its own package, so no import is added or removed here.

- [ ] **Step 4: Fix the three test imports**

Same prefix replacement:

- `email/server/dao/EmailAddressRepositoryTest.kt`: `Verdict`, `VerificationReason`.
- `email/server/service/EmailVerificationServiceTest.kt`: `EmailVerification`, `EmailVerificationThrottle`,
  `EmailVerifier`, `VerificationReason`.
- `mandate/server/endpoint/CreateMandateEmailEndpointTest.kt`: `EmailVerification`, `EmailVerifier`,
  `VerificationReason`.

Keep the `com.wutsi.ndopify.email.server.dao.EmailAddressRepository` import in the last two: the domain did not move.

- [ ] **Step 5: Compile and run the unit tests**

Run: `cd modules/ndopify-server && mvn test-compile`
Expected: BUILD SUCCESS — no unresolved reference to a moved class.

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest,EmailVerifierSESTest,SesEmailVerifierHealthIndicatorTest'`
Expected: PASS. The two `SesEmailVerifierHealth*` endpoint tests need MySQL and run in Task 6.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify
git commit -m "refactor(server): nest e-mail verification under platform/mail/verification" -m "Refs #387"
```

---

### Task 4: Give the SES client the deployment's credentials

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (two keys under `ndopify.mail.verification.ses`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/platform/MailConfigurationTest.kt` (create)

**Interfaces:**
- Consumes: Task 2's `MailConfiguration` (with Task 3's `platform.mail.verification` imports).
- Produces:
  - settings `ndopify.mail.verification.ses.access-key` and `ndopify.mail.verification.ses.secret-key`, each a `String`,
    empty by default;
  - `MailConfiguration`'s constructor gains them right after `region`, before `timeoutSeconds`:
    `@param:Value("\${ndopify.mail.verification.ses.access-key}") private val accessKey: String` and the same for
    `secretKey`;
  - `sesV2Client(): SesV2Client` unchanged in name, type and guard, now credential-aware.

- [ ] **Step 1: Write the failing test**

Create `MailConfigurationTest` in `com.wutsi.ndopify.config.platform`, following `ImageConfigurationTest`: it builds the
configuration directly, so it needs no Spring context, no MySQL and no network.

```kotlin
package com.wutsi.ndopify.config.platform

import com.nhaarman.mockitokotlin2.mock
import org.springframework.mail.javamail.JavaMailSender
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.sesv2.SesV2Client
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MailConfigurationTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    @Test
    fun `the SES client uses the configured static credentials`() {
        val client = configuration("AKIATEST", "secret").sesV2Client()

        assertEquals("AKIATEST", client.credentials().resolveCredentials().accessKeyId())
    }

    @Test
    fun `without both values the SES client keeps the SDK default chain`() {
        assertFalse(configuration("", "").sesV2Client().credentials() is StaticCredentialsProvider)
    }

    @Test
    fun `a half-configured pair keeps the SDK default chain`() {
        assertFalse(configuration("AKIATEST", "").sesV2Client().credentials() is StaticCredentialsProvider)
        assertFalse(configuration("", "secret").sesV2Client().credentials() is StaticCredentialsProvider)
    }

    private fun configuration(accessKey: String, secretKey: String) =
        MailConfiguration(mock<JavaMailSender>(), "us-east-1", accessKey, secretKey, 2, 1, 15, clock)

    @Suppress("UNCHECKED_CAST")
    private fun SesV2Client.credentials() =
        serviceClientConfiguration().credentialsProvider() as AwsCredentialsProvider
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailConfigurationTest`
Expected: test-compile FAILS — no `MailConfiguration` constructor takes two `String` credentials between `region` and
`timeoutSeconds`. That is the only unresolved reference: the helper and the assertions are valid Kotlin already.

- [ ] **Step 3: Add the two settings**

In `application.yml`, under the `ses:` block Task 1 created, beside `region`:

```yaml
            ses:
                region: us-east-1                        # The storage region
                access-key: "${AWS_ACCESS_KEY:}"         # Heroku config var (v1's name); empty = SDK default chain
                secret-key: "${AWS_SECRET_KEY:}"
```

The quotes keep YAML from reading `${AWS_ACCESS_KEY:}` as a mapping; the empty default is what lets local dev and CI
boot without credentials. Do not add them to `application-test.yml` or `application-prod.yml`: those read the Heroku
variables through `application.yml`.

- [ ] **Step 4: Add the constructor parameters and the conditional provider**

Add the two `@param:Value` parameters after `region`. In `sesV2Client()`, build the client through a local builder and
add the provider only when both values are non-blank:

```kotlin
    @Bean
    @ConditionalOnProperty(name = ["ndopify.mail.verification.provider"], havingValue = "ses")
    fun sesV2Client(): SesV2Client {
        val builder = SesV2Client.builder()
            .region(Region.of(region))
            .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(timeoutSeconds)) }
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
            )
        }
        return builder.build()
    }
```

Imports: `software.amazon.awssdk.auth.credentials.AwsBasicCredentials` and
`software.amazon.awssdk.auth.credentials.StaticCredentialsProvider`. Nothing else in the class changes, and no log line,
exception message or KDoc names either value.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MailConfigurationTest`
Expected: PASS — three tests, no network. If the machine has `AWS_ACCESS_KEY_ID` set, the default-chain assertions still
hold: they assert the provider is not a `StaticCredentialsProvider`, not that it is empty.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/platform/MailConfigurationTest.kt
git commit -m "feat(server): authenticate SES with the deployment's AWS_ACCESS_KEY and AWS_SECRET_KEY" -m "Refs #387"
```

---

### Task 5: Update the module documentation

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the `platform/mail` bullet, the `platform/email` bullet, the `email`
  domain bullet)

**Interfaces:**
- Consumes: Tasks 1–4's final tree, packages, key names and credential settings.
- Produces: nothing (documentation).

- [ ] **Step 1: Fold the verification capability into the `platform/mail` bullet**

That bullet currently ends at `…a missing cover still sends no image.` Append, in the same bullet, that the e-mail
verification capability sits under `mail/verification`: the `EmailVerifier` port, the provider-neutral
`EmailVerification` model with its `Verdict` / `VerificationReason` enums, the `EmailVerifierNone` default adapter, the
`EmailVerificationThrottle` paid-call budget guard and the local `EmailSyntax` check; and the SES adapter
(`EmailVerifierSES`, one `GetEmailAddressInsights` call through the AWS SDK v2 `sesv2`, beside the v1 S3 client) with
its cached health indicator (`SesEmailVerifierHealthIndicator`, `GetAccount`, `sesEmailVerifier`). Keep the sentence
that `ndopify.mail.verification.provider` is the only switch — `none` by default (local, QA, the server's tests), `ses`
in TEST and PROD — and that `none` wires `EmailVerifierNone` and no health component, so nothing calls AWS. Add one
sentence on the credentials: the SES client takes `ndopify.mail.verification.ses.access-key` / `secret-key`, which the
deployment fills from its Heroku `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` — the names the v1 S3 client reads too — and an
empty value leaves the SDK's default chain in charge, so a local run and an IAM role both work. Keep the existing
sending text ahead of it.

- [ ] **Step 2: Delete the `platform/email` bullet**

Remove it entirely; no bullet may open with `` - `platform/email` `` afterwards.

- [ ] **Step 3: Update the `email` domain bullet**

Point it at `platform.mail.verification` instead of `platform.email`, and rename its key references:
`ndopify.mail.verification.ttl.valid-days` / `.invalid-days` and `ndopify.mail.verification.throttle.*`. Change nothing
else — the policy's behavior, the TTL values, fail-open, the budget and `400 EMAIL_ADDRESS_INVALID` stay as written.

- [ ] **Step 4: Name the merged configuration, not the deleted class**

`MailConfiguration` is now where both halves are wired. That file names configuration classes nowhere today; keep it
that way (no `EmailConfiguration` reference may survive, and none is added). The `spring.mail.*` / SMTP description, if
any, stays untouched.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): describe the e-mail configuration, settings and credentials under one mail umbrella" -m "Refs #387"
```

---

### Task 6: Verify the tree, the settings, the credentials, the wiring and the build

**Files:** none (verification only).

**Interfaces:**
- Consumes: Tasks 1–5.

- [ ] **Step 1: Assert the old package and the old key are gone**

```bash
grep -rn --include='*.kt' --include='*.yml' --include='*.yaml' --include='*.properties' --include='*.md' \
  -e 'com\.wutsi\.ndopify\.platform\.email' -e 'platform/email' -e 'ndopify\.email\.verification' \
  modules/ndopify-server docs/superpowers/specs
```

Expected: no output. The two completed plan records `docs/superpowers/plans/380-*.md` and `384-*.md` still match and
are intentionally out of the search root. (`ndopify.email.verification` is the key prefix: it never matches the
domain's own `com.wutsi.ndopify.email.server.*` packages.)

- [ ] **Step 2: Assert one configuration class**

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform`
Expected: `AIConfiguration.kt`, `EndpointConfiguration.kt`, `ImageConfiguration.kt`, `MailConfiguration.kt`,
`TelegramConfiguration.kt`, … — no `EmailConfiguration.kt`.

Run: `grep -n "@Bean" -A1 modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt`
Expected: eight bean methods, the four sending ones then `emailVerifierNone`, `sesV2Client`, `emailVerifierSes`,
`sesEmailVerifierHealthIndicator`.

- [ ] **Step 3: Assert the settings, key by key**

Run: `sed -n '/^    mail:/,/^    agent:/p' modules/ndopify-server/src/main/resources/application.yml` (or read the
file) and check the eleven keys are present: the nine renamed ones with their old values, and under `ses:` the two
credential keys reading `"${AWS_ACCESS_KEY:}"` and `"${AWS_SECRET_KEY:}"` — with no secret in the file, only the
variable names.

Run: `grep -n -A2 "mail:" modules/ndopify-server/src/main/resources/application-test.yml modules/ndopify-server/src/main/resources/application-prod.yml`
Expected: in both, `mail:` → `verification:` → `provider: ses`.

Run: `grep -n "spring:" -A6 modules/ndopify-server/src/main/resources/application.yml` and confirm the SMTP block is
unchanged (same `host`, `port`, `properties.mail.smtp.from`).

- [ ] **Step 4: Assert each package holds the expected classes**

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/verification`
Expected: exactly the nine files named in Task 3, Step 1.

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail`
Expected: `Email.kt`, `MailBodyResolver.kt`, `MailFilter.kt`, `MailImages.kt`, `MailService.kt`, `Receipient.kt`,
`ServerMessageIdParser.kt`, `filter`, `verification` — no verification class flat in `mail` itself.

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/*/`
Expected: `dao/EmailAddressRepository.kt`, `domain/EmailAddressEntity.kt`, `service/EmailVerificationService.kt`, and
nothing else.

- [ ] **Step 5: Assert the credentials are wired and never echoed**

Run: `grep -n "accessKey\|secretKey" modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt`
Expected: the two constructor parameters, the `isNotBlank()` checks and the `create(accessKey, secretKey)` call — and
no other use.

Run: `grep -n "LOGGER\|logger\|println" modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/MailConfiguration.kt`
Expected: no output. The class logs nothing, so neither value can reach a log through it.

- [ ] **Step 6: Run the full module gate**

Run: `cd modules/ndopify-server && mvn verify` (local MySQL must be running)
Expected: BUILD SUCCESS — `validate` (ktlint), every test and the JaCoCo coverage gate pass. This is where the
rename is proven at boot: `SesEmailVerifierHealthDisabledTest` (`none`, no `sesEmailVerifier` component),
`SesEmailVerifierHealthEndpointTest` and `SesEmailVerifierHealthCacheTest` (`ses`, component present, cached),
`CreateMandateEmailEndpointTest` (`ses`, `400 EMAIL_ADDRESS_INVALID` on a LOW) and `EmailAddressRepositoryTest` (the
enum round-trip) each boot a context that resolves all eleven `ndopify.mail.verification.*` keys, and
`MailConfigurationTest` proves the client carries the static credentials when both are set and the default chain when
one is not.

- [ ] **Step 7: Review the diff against `main`**

Run: `git diff --stat origin/main` and `git diff origin/main -- modules/ndopify-server/src`
Expected: renames under `platform/email/` → `platform/mail/verification/` (main and test); import-only edits in the
three import-only tests; the merged `MailConfiguration` with the conditional credentials provider and the deleted
`EmailConfiguration`; the key rename in the three YAML files, three code files, two KDoc mentions and two test files;
the two new credential keys in `application.yml`; the new `config/platform/MailConfigurationTest.kt`; and
`modules/ndopify-server/CLAUDE.md`. No migration, no DTO, no `pom.xml`, no `Application.kt`, no `spring.mail.*` change,
no workflow change, and nothing under `docs/superpowers/plans/380-*.md` or `384-*.md`. Inside a moved class body, the
only changes are its `package` line, the `ndopify.mail.verification.*` key strings it holds and its KDoc sentence
naming a key.
