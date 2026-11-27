# Mandate e-mail validation (SES Email Validation) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a mandate is submitted, refuse a contact e-mail that Amazon SES Email Validation rates undeliverable, with `400 EMAIL_ADDRESS_INVALID`, a cached verdict, a bounded SES bill, and the approved French message on the three public forms.

**Architecture:** A new `email` domain in ndopify-server keeps a global, hashed verdict cache (`T_EMAIL_ADDRESS`) and the policy above a `EmailVerifier` port: normalization, a local syntax check, the cache, the TTLs, the fail-open threshold and an in-memory cost guard. The port's only real adapter is `EmailVerifierSES` (AWS SDK v2 `sesv2`, in `platform/email`) behind `ndopify.email.verification.provider`; `none` is the default (local, QA, the server's own tests) and `ses` is set in the TEST and PROD profiles. `MandateService.create` calls `EmailVerificationService.ensureDeliverable` first. The three public forms read `ErrorResponse.error.code` and show « Cette adresse email n'est pas valide. » on the e-mail field, as the approved prototype does.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA + Flyway (MySQL), AWS SDK v2 `sesv2` (`2.57.3`), Thymeleaf, JUnit 5 / `kotlin.test` / mockito-kotlin, `TestRestTemplate` + MySQL, `MockMvc` + a real Thymeleaf engine, Maven with ktlint and JaCoCo.

**Spec:** story #380 — https://github.com/wutsi/ndopify-mono/issues/380 — with its approved prototype (2026-11-27) under
`modules/ndopify-partner-central/src/main/html/v2` (`find-property.html`, `offer-property.html`, their scripts and
README) and `modules/ndopify-web/src/main/html/v2` (`annonce.html`, `assets/js/annonce.js`, `assets/css/styles.css`,
README).

## Global Constraints

- **`main` starts the work with `V20_0__mandate-document-source.sql` as its last common migration.** The new one is
  `V21_0__email-address.sql`; there is no V21_* yet, and if there is, renumber before writing it.
- **`Shared code: ndopify-dto`** — the one new `ErrorCode` is released from its own commit before the branch is
  rebased (the developer's release step), then `<ndopify-dto.version>` in the root `pom.xml` is bumped to the released
  version. Never commit a `-SNAPSHOT` in the root POM.
- **The provider is the only switch: no shadow mode, no `mode` setting.** `ndopify.email.verification.provider`
  is `none` in `application.yml` and in the `qa` profile (the server's tests), `ses` in `application-test.yml` (the
  deployed TEST environment) and `application-prod.yml`. With `ses`, a deliverability LOW is refused immediately.
- **The score follows deliverability only.** `HasValidSyntax`, `HasValidDnsRecords`, `MailboxExists` decide the score
  (HIGH 1.0, MEDIUM 0.5, LOW 0.0); `IsRoleAddress`, `IsDisposable`, `IsRandomInput` never change it and only name the
  reason. Only `score == 0.0` refuses.
- **Only an SES answer is stored.** A locally malformed address is refused without a call and without a row; a
  timeout, a 429, a 5xx, a 400 or any unexpected error is fail-open (accepted, nothing stored).
- **The verdict is written in its own transaction** (`REQUIRES_NEW`): `MandateService.create` rolls back when it
  refuses, and the rejection must survive it.
- **Privacy:** no plaintext address in `T_EMAIL_ADDRESS` (SHA-256 only), none in a log line (the domain may be), none
  in the 400 body (code + `{name: "email", type: PAYLOAD}`, no message, no data), and the verdict never in a body.
- **UI: yes.** The approved prototype is the baseline; port it, never redesign and never edit `src/main/html`. The one
  French sentence, exactly as approved: « Cette adresse email n'est pas valide. »
- **The feature is off everywhere the tests run:** `application-test.yml` is the deployed TEST environment, not the
  test profile — the suite runs `qa`, so the 500+ existing endpoint tests make no AWS call and write no cache row.
- **AWS:** `software.amazon.awssdk:sesv2` version `2.57.3` (already in the local Maven cache), next to the v1 S3
  client (`aws.version` 1.12.797, untouched); default credentials chain like S3; region `us-east-1`; IAM needs
  `ses:GetEmailAddressInsights` and `ses:GetAccount`.
- Tests: `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`), mockito-kotlin
  (`com.nhaarman.mockitokotlin2`); `TestRestTemplate` + MySQL in the server, `MockMvc` standalone + real Thymeleaf in
  the web apps. Partner Central keeps one private `errorCode(ex)` helper per controller (12 today) — follow it, don't
  refactor the others.
- `mvn verify` green in `modules/ndopify-dto`, `modules/ndopify-server` (local MySQL), `modules/ndopify-partner-central`
  and `modules/ndopify-web`.

## Files

- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V21_0__email-address.sql`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/EmailAddressEntity.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/VerificationReason.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/Verdict.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerification.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifier.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifierNone.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailSyntax.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationThrottle.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSES.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthIndicator.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/ClientIp.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt` (constructor, `create`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt` (`create`)
- Modify: `modules/ndopify-server/pom.xml` (the `sesv2` dependency), `modules/ndopify-dto/.../error/dto/ErrorCode.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml`, `application-test.yml`, `application-prod.yml`
- Modify: `modules/ndopify-server/src/test/resources/db/test/clean.sql`
- Modify: `modules/ndopify-partner-central/.../ui/findproperty/FindPropertyController.kt`, `.../offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-web/.../ui/listing/ListingController.kt`, `src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-server/CLAUDE.md`, `modules/ndopify-partner-central/CLAUDE.md`, `modules/ndopify-web/CLAUDE.md`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepositoryTest.kt`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationServiceTest.kt`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationThrottleTest.kt`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSESTest.kt`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthEndpointTest.kt`
- Test: `.../ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt`
- Test: `.../ndopify-partner-central/src/test/kotlin/.../findproperty/FindPropertyControllerTest.kt`, `.../FindPropertyTemplateTest.kt`, `.../offerproperty/OfferPropertyControllerTest.kt`, `.../OfferPropertyTemplateTest.kt`
- Test: `.../ndopify-web/src/test/kotlin/.../listing/ListingControllerTest.kt`, `.../ListingTemplateTest.kt`

## Review Focus

1. **A refused mandate still caches its verdict** — the mandate's transaction rolls back, so the upsert must be
   `REQUIRES_NEW`; a second submission of the same address within 7 days is refused with no SES call (`CreateMandateEmailEndpointTest`).
2. **Provider `none` does nothing at all** — no SES call, no cache row, no throttle, no health component; the whole
   existing suite runs on it (`EmailVerificationServiceTest`, `SesEmailVerifierHealthEndpointTest`).
3. **Fail-open never refuses** — a timeout, a 429, a 5xx, a 400 and an unexpected error all create the mandate, cache
   nothing, and log neither the address nor the exception's message (`EmailVerificationServiceTest`, endpoint test).
4. **The budget never turns into a refusal** — the per-IP window and the daily cap accept the submission without a
   call; a spoofed `X-Forwarded-For` only moves the per-IP bucket, the daily cap still holds
   (`EmailVerificationThrottleTest`).
5. **Nothing leaks** — the table holds only hashes; the 400 has the code and `{name: "email", type: PAYLOAD}` and no
   message/data; the health details carry no credential, account id or e-mail (`CreateMandateEmailEndpointTest`,
   `SesEmailVerifierHealthEndpointTest`).
6. **The sentence appears once, without losing answers** — no `Envoi impossible pour le moment.` for this code, the
   wizards come back to the contact step with `returning = true` and the popup reopens with the typed values
   (both web apps' controller and template tests).

---

### Task 1: The refusal code (`ndopify-dto`)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Test: none (the DTO has no tests; the server's endpoint test covers it)

**Interfaces:**
- Produces: `ErrorCode.EMAIL_ADDRESS_INVALID = "urn:wutsi:ndopify:error:email-address:invalid"`.

- [ ] **Step 1: Add the error code**

A new `EMAIL_ADDRESS` group between `CUSTOMER_*` and `HTTP_*` (the groups are alphabetical):
`val EMAIL_ADDRESS_INVALID = "$PREFIX:email-address:invalid"`, with a KDoc line: SES Email Validation rated the
mandate's contact address undeliverable; the parameter is the `email` field, and neither the address nor the verdict
is carried.

- [ ] **Step 2: Install the DTO locally**

Run: `cd modules/ndopify-dto && mvn install`
Expected: BUILD SUCCESS (ktlint clean). The rest of the branch builds against this install until the release.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add the invalid e-mail address error code" -m "Refs #380"
```

The developer's release step releases `ndopify-dto` from this commit and bumps `<ndopify-dto.version>`; later tasks
run against the released version.

---

### Task 2: The global verdict cache

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V21_0__email-address.sql`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/EmailAddressEntity.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/VerificationReason.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/Verdict.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepository.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class VerificationReason { NONE, SYNTAX_INVALID, DOMAIN_INVALID, MAILBOX_UNREACHABLE, DISPOSABLE, ROLE_ADDRESS, RANDOM_INPUT, UNAVAILABLE }`
  - `enum class Verdict { HIGH, MEDIUM, LOW }`
  - `data class EmailAddressEntity(id: Long? = null, emailHash: String = "", score: Double? = null, reason: VerificationReason = NONE, hasValidSyntax: Verdict = HIGH, hasValidDnsRecords: Verdict = HIGH, mailboxExists: Verdict = HIGH, isRoleAddress: Verdict = HIGH, isDisposable: Verdict = HIGH, isRandomInput: Verdict = HIGH, checkedAt: Date = Date(), expiresAt: Date = Date())`
  - `EmailAddressRepository.findByEmailHash(emailHash: String): EmailAddressEntity?` and
    `EmailAddressRepository.upsert(emailHash: String, score: Double, reason: String, hasValidSyntax: String, hasValidDnsRecords: String, mailboxExists: String, isRoleAddress: String, isDisposable: String, isRandomInput: String, checkedAt: Date, expiresAt: Date)` — no return value.

- [ ] **Step 1: Write the failing test**

`EmailAddressRepositoryTest : BaseEndpointIntegrationTest()`, `@Sql("/db/test/clean.sql")`, autowiring the repository
and a `DataSource`:

```kotlin
@Test
fun `the upsert inserts once and updates the row of the same hash`() {
    val now = Date()
    dao.upsert(HASH, 1.0, "NONE", "HIGH", "HIGH", "HIGH", "HIGH", "HIGH", "HIGH", now, Date(now.time + DAY))
    dao.upsert(HASH, 0.0, "MAILBOX_UNREACHABLE", "HIGH", "HIGH", "LOW", "HIGH", "HIGH", "HIGH", now, now)

    val row = dao.findByEmailHash(HASH)!!
    assertEquals(0.0, row.score)
    assertEquals(VerificationReason.MAILBOX_UNREACHABLE, row.reason)
    assertEquals(Verdict.LOW, row.mailboxExists)
    assertEquals(1L, count()) // COUNT(*) through the DataSource
}
```

and a second test reading back the six verdict columns and `expiresAt` after one upsert (`assertEquals(Verdict.HIGH, row.isRoleAddress)`…).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=EmailAddressRepositoryTest`
Expected: FAIL — no bean of type `EmailAddressRepository`, no table `T_EMAIL_ADDRESS` (MySQL must be running).

- [ ] **Step 3: Write the migration**

`V21_0__email-address.sql`, in the style of `V19_0__mandate-document.sql` but with **no `tenant_id`** (the table is
global, like `T_USER`) and no foreign keys:

```sql
CREATE TABLE T_EMAIL_ADDRESS(
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  email_hash            CHAR(64) NOT NULL,
  score                 DOUBLE DEFAULT NULL,
  reason                VARCHAR(30) NOT NULL,
  has_valid_syntax      VARCHAR(10) NOT NULL,
  has_valid_dns_records VARCHAR(10) NOT NULL,
  mailbox_exists        VARCHAR(10) NOT NULL,
  is_role_address       VARCHAR(10) NOT NULL,
  is_disposable         VARCHAR(10) NOT NULL,
  is_random_input       VARCHAR(10) NOT NULL,
  checked_at            DATETIME NOT NULL,
  expires_at            DATETIME NOT NULL,
  PRIMARY KEY(id),
  CONSTRAINT UK_EMAIL_ADDRESS_hash UNIQUE(email_hash)
) ENGINE = InnoDB;

CREATE INDEX I_EMAIL_ADDRESS_expires_at ON T_EMAIL_ADDRESS(expires_at);
```

A comment above it: the SHA-256 of the normalized address only, so the table holds no plaintext address; the six
verdicts are kept because a policy change must cost no new call; `expires_at` is indexed for a future prune job.

- [ ] **Step 4: Write the entity and the two enums**

`EmailAddressEntity` mirrors the columns (Spring's default strategy turns `emailHash` into `email_hash`), with
`@Enumerated(EnumType.STRING)` on `reason` and the six verdicts. KDoc: one row per distinct address, global, the
verdict SES last gave; `score` null never happens (only a definitive answer is stored).

`VerificationReason` KDoc: the provider-neutral reason, **stored by name, never renamed**; the precedence
`SYNTAX_INVALID` > `DOMAIN_INVALID` > `MAILBOX_UNREACHABLE` > `DISPOSABLE` > `ROLE_ADDRESS` > `RANDOM_INPUT` >
`NONE`, and `UNAVAILABLE` when SES could not answer. `Verdict` KDoc: SES's HIGH / MEDIUM / LOW.

- [ ] **Step 5: Write the repository**

`CrudRepository<EmailAddressEntity, Long>` plus `findByEmailHash`. The upsert is a native `@Query` +
`@Modifying`:

```
INSERT INTO T_EMAIL_ADDRESS (email_hash, score, reason, has_valid_syntax, ...)
VALUES (:emailHash, :score, :reason, ...)
ON DUPLICATE KEY UPDATE score = VALUES(score), reason = VALUES(reason), ... (every column but email_hash)
```

annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`, so concurrent submissions of one address update
one row instead of failing on the unique key, **and** the row survives the caller's rollback. KDoc says both.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=EmailAddressRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V21_0__email-address.sql \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email
git commit -m "feat(server): cache e-mail verdicts in T_EMAIL_ADDRESS" -m "Refs #380"
```

---

### Task 3: The verifier port and its two adapters

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerification.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifier.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifierNone.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSES.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt`
- Modify: `modules/ndopify-server/pom.xml`, `modules/ndopify-server/src/main/resources/application.yml`
- Modify: `pom.xml` (the `aws-sdk-v2.version` property)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSESTest.kt`

**Interfaces:**
- Consumes: `Verdict`, `VerificationReason`.
- Produces:
  - `data class EmailVerification(val score: Double?, val reason: VerificationReason)`
  - `fun interface EmailVerifier { fun verify(email: String): EmailVerification }`
  - `class EmailVerifierNone : EmailVerifier` (always `EmailVerification(1.0, NONE)`)
  - `class EmailVerifierSES(private val client: SesV2Client) : EmailVerifier`
  - beans: `EmailVerifier` (one of the two, by `ndopify.email.verification.provider`), `SesV2Client` (only with `ses`).

- [ ] **Step 1: Write the failing tests**

`EmailVerifierSESTest`, mocking the SDK client only (`mock<SesV2Client>()`, `doReturn(response).whenever(client).getEmailAddressInsights(any())`):

```kotlin
@Test
fun `maps a HIGH address to a valid verdict`() {
    val verdict = verifier.verify("ray@gmail.com")
    assertEquals(1.0, verdict.score)
    assertEquals(VerificationReason.NONE, verdict.reason)
}

@Test
fun `a disposable address keeps its deliverability score`() {
    val verdict = verifier.verify("ray@mailinator.com") // hasValid/mailbox HIGH, isDisposable LOW
    assertEquals(1.0, verdict.score)
    assertEquals(VerificationReason.DISPOSABLE, verdict.reason)
}

@Test
fun `a missing mailbox is undeliverable`() {
    assertEquals(0.0, verifier.verify("nobody@example.com").score)
    assertEquals(VerificationReason.MAILBOX_UNREACHABLE, verifier.verify("nobody@example.com").reason)
}
```

plus one test per mapping (syntax → `SYNTAX_INVALID`, DNS → `DOMAIN_INVALID`, role → `ROLE_ADDRESS`, random input →
`RANDOM_INPUT`, a MEDIUM deliverability → `0.5` / `NONE`), a precedence test (syntax + DNS + mailbox all LOW →
`SYNTAX_INVALID`), a null-evaluation test (a response with an evaluation missing counts as HIGH, never refuses), and
an argument-captor test asserting one `GetEmailAddressInsights` call with `emailAddress == "ray@gmail.com"`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=EmailVerifierSESTest`
Expected: FAIL — `EmailVerifierSES` does not exist.

- [ ] **Step 3: Add the SDK dependency**

In the root `pom.xml` properties: `<aws-sdk-v2.version>2.57.3</aws-sdk-v2.version>`, next to `aws.version` (leave it at
1.12.797). In `modules/ndopify-server/pom.xml`, next to the v1 S3 artifact:
`software.amazon.awssdk:sesv2:${aws-sdk-v2.version}`.

- [ ] **Step 4: Write the port and the two adapters**

`EmailVerification` KDoc: `score` 1.0 for HIGH, 0.5 for MEDIUM, 0.0 for LOW, `null` when SES could not answer.

`EmailVerifierSES.verify` reads `GetEmailAddressInsightsResponse.mailboxValidation()` and maps each evaluation's
`confidenceVerdict()` to a `Verdict`. The score is the **minimum of the three deliverability** evaluations
(`hasValidSyntax`, `hasValidDnsRecords`, `mailboxExists`); the reason is the first LOW in the precedence order across
all six (deliverability first, then `isDisposable`, `isRoleAddress`, `isRandomInput`), else `NONE`. A null evaluation
is HIGH. It does not catch anything: a failed call is the policy's problem.

`EmailVerifierNone.verify` returns `EmailVerification(1.0, NONE)` and its KDoc says it is the default when
`ndopify.email.verification.provider=none`: it never reaches AWS.

- [ ] **Step 5: Write the configuration and the defaults**

`EmailConfiguration` (`@Configuration`, `config/platform/`): `sesV2Client()` =
`SesV2Client.builder().region(Region.of(region)).overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(timeoutSeconds)) }.build()`
(`@Value("\${ndopify.email.verification.ses.region}")` and
`@Value("\${ndopify.email.verification.timeout-seconds}") private val timeoutSeconds: Long`, so the profile's 2 s bound
becomes the SDK's own `apiCallTimeout(Duration.ofSeconds(timeoutSeconds))`: a slow SES never holds a submission),
`emailVerifierNone()` with
`@ConditionalOnProperty(name = ["ndopify.email.verification.provider"], havingValue = "none", matchIfMissing = true)`,
and `emailVerifierSes(client)` with `havingValue = "ses"`. The class takes `clock: Clock` (the `ClockConfiguration` bean) and the
health TTLs from `ndopify.email.verification.health.up-ttl-minutes` / `.down-ttl-seconds`.

In `application.yml`, after `ndopify.ai`:

```yaml
    email:
        verification:
            provider: none                    # none | ses; ses in TEST and PROD
            ses:
                region: us-east-1
            ttl:
                valid-days: 30                # HIGH / MEDIUM
                invalid-days: 7               # LOW
            timeout-seconds: 2
            throttle:
                per-ip-per-hour: 20
                daily-cap: 500
            health:
                up-ttl-minutes: 1
                down-ttl-seconds: 15
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=EmailVerifierSESTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add pom.xml modules/ndopify-server/pom.xml \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerification.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifier.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifierNone.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSES.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt \
  modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email
git commit -m "feat(server): add the SES e-mail verifier port" -m "Refs #380"
```

---

### Task 4: The policy, the local syntax check and the cost guard

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailSyntax.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationThrottle.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/ClientIp.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationThrottleTest.kt`

**Interfaces:**
- Consumes: `EmailVerifier`, `EmailVerification`, `EmailAddressRepository`, `VerificationReason`, `Verdict`.
- Produces:
  - `class EmailVerificationService(verifier: EmailVerifier, dao: EmailAddressRepository, throttle: EmailVerificationThrottle, clock: Clock, provider: String, validTtlDays: Long, invalidTtlDays: Long)`
    — `@Service`, the last three from `@param:Value` on `ndopify.email.verification.provider`, `.ttl.valid-days` and
    `.ttl.invalid-days`; `fun ensureDeliverable(email: String, clientIp: String?)`, throws
    `BadRequestException(error = Error(code = ErrorCode.EMAIL_ADDRESS_INVALID, parameter = Parameter(name = "email", type = ParameterType.PARAMETER_TYPE_PAYLOAD)))`
    and returns otherwise.
  - `class EmailVerificationThrottle(clock: Clock, perIpPerHour: Int, dailyCap: Int) { fun tryAcquire(ip: String?): Boolean }`
    — `@Component`.
  - `object EmailSyntax { fun isValid(email: String): Boolean }` and `object ClientIp { fun of(request: HttpServletRequest): String? }`.

- [ ] **Step 1: Write the failing service tests**

`EmailVerificationServiceTest` with `mock()` collaborators, a fixed `Clock`, `provider = "ses"`, and
`doReturn(true).whenever(throttle).tryAcquire(any())` in a `@BeforeEach`:

```kotlin
@Test
fun `a definitive valid verdict is cached for 30 days and accepted`() {
    doReturn(null).whenever(dao).findByEmailHash(any())
    doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify("ray@gmail.com")

    service.ensureDeliverable("  Ray@Gmail.com ", "1.2.3.4")

    verify(verifier).verify("ray@gmail.com")
    val hash = argumentCaptor<String>()
    val expiresAt = argumentCaptor<Date>()
    verify(dao).upsert(eq(hash.capture()), eq(1.0), eq("NONE"), any(), any(), any(), any(), any(), any(), any(), eq(expiresAt.capture()))
    assertEquals(EXPECTED_SHA256, hash.firstValue)
    assertEquals(Date(NOW + 30L * DAY), expiresAt.firstValue)
}

@Test
fun `a LOW verdict is refused and cached for 7 days`() {
    doReturn(EmailVerification(0.0, VerificationReason.MAILBOX_UNREACHABLE)).whenever(verifier).verify(any())

    val ex = assertFailsWith<BadRequestException> { service.ensureDeliverable("ray@gmail.com", null) }

    assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, ex.error.code)
    assertEquals("email", ex.error.parameter!!.name)
    assertEquals(ParameterType.PARAMETER_TYPE_PAYLOAD, ex.error.parameter!!.type)
    assertNull(ex.error.message)
    assertNull(ex.error.data)
    val expiresAt = argumentCaptor<Date>()
    verify(dao).upsert(any(), eq(0.0), eq("MAILBOX_UNREACHABLE"), any(), any(), any(), any(), any(), any(), any(), eq(expiresAt.capture()))
    assertEquals(Date(NOW + 7L * DAY), expiresAt.firstValue)
}
```

plus: the cached valid row inside its TTL → no verifier call, no upsert; the cached `0.0` row inside its TTL → refused
with no verifier call; an expired row → verified again and upserted; `provider = "none"` → returns without touching the
repository, the verifier or the throttle; a malformed address (`"ray@"`) → refused, `verify(verifier, never())`,
`verify(dao, never()).upsert(...)`; the verifier throwing (`RuntimeException("boom ray@gmail.com")`) → accepted, nothing
upserted; `EmailVerification(null, UNAVAILABLE)` → accepted, nothing upserted; a MEDIUM (0.5) verdict → accepted and
cached for 30 days; `tryAcquire` false → accepted, `verify(verifier, never())`, nothing upserted.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=EmailVerificationServiceTest`
Expected: FAIL — `EmailVerificationService` does not exist.

- [ ] **Step 3: Write the local syntax check**

`object EmailSyntax { fun isValid(email: String): Boolean }`, backed by `commons-validator`'s
`EmailValidator.getInstance()` (already a dependency): no DNS, no network, and a KDoc line saying it only saves a paid
call — SES still decides.

- [ ] **Step 4: Write the throttle and the caller's IP**

`EmailVerificationThrottle` `@Component`, `@Synchronized fun tryAcquire(ip: String?): Boolean`: one `ArrayDeque` of
instants per IP (pruned to the last hour) and a daily counter keyed on the UTC day (`LocalDate.now(clock)`). A call is a
**paid call** — the caller asks only on a cache miss. KDoc: a budget guard, not a security control: it fails open (when
spent, the caller accepts the submission without verifying) and a spoofed `X-Forwarded-For` only moves the per-IP
bucket, the daily cap holds. `EmailVerificationThrottleTest` (its own file, `MutableClock` defined privately in it):

```kotlin
@Test
fun `the per-IP window refuses the 21st call and another address still passes`() {
    repeat(20) { assertTrue(throttle.tryAcquire("1.2.3.4")) }
    assertFalse(throttle.tryAcquire("1.2.3.4"))
    assertTrue(throttle.tryAcquire("5.6.7.8"))
}

@Test
fun `the window forgets calls older than an hour`() {
    repeat(20) { throttle.tryAcquire("1.2.3.4") }
    clock.instant = clock.instant.plus(Duration.ofMinutes(61))
    assertTrue(throttle.tryAcquire("1.2.3.4"))
}
```

plus: `dailyCap = 2` spent by two different IPs → a third is refused; a null IP counts against the daily cap only; the
daily counter resets on the next UTC day.

`object ClientIp { fun of(request: HttpServletRequest): String? }`: the first entry of `X-Forwarded-For`, else
`request.remoteAddr`, blank as null. KDoc: Heroku terminates TLS at its router, so `remoteAddr` alone is the router's
address; the header is only trusted as a bucket key for the cost guard.

- [ ] **Step 5: Write the service**

`ensureDeliverable` in this order, each step short and commented where the reason is non-obvious:
1. `if (provider != "ses") return` — KDoc: with `none` there is nothing to verify, cache or throttle (this is why the
   default and the `qa` profile never touch AWS).
2. `normalized = email.trim().lowercase()`; `if (!EmailSyntax.isValid(normalized)) throw invalid()` — no row, no call.
3. `dao.findByEmailHash(sha256(normalized))` with `expiresAt` after the clock → refuse if `score == 0.0`, return
   otherwise; both without any call.
4. `if (!throttle.tryAcquire(clientIp)) return` — fail open, nothing cached.
5. `try { verifier.verify(normalized) } catch (ex: Exception) { LOGGER.warn("E-mail verification failed: {} ({})", ex.javaClass.simpleName, domain(normalized)); null }`;
   a null or `score == null` → return, nothing cached.
6. `dao.upsert(hash, score, reason.name, six verdict names…, now, now + ttl)`; TTL 30 days when `score >= 0.5`, 7 days
   when `0.0`; then refuse when `score == 0.0`.

The SHA-256 is `MessageDigest.getInstance("SHA-256")` over the normalized address in UTF-8, hex, lowercase (64 chars
for the `CHAR(64)` column). Never log the address: `domain()` is `normalized.substringAfterLast('@')`. The `upsert`
call happens through the repository's `REQUIRES_NEW` transaction, so the verdict survives the caller's rollback.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service   modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/ClientIp.kt   modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service
git commit -m "feat(server): verify the mandate e-mail with a cached SES verdict and a bounded budget" -m "Refs #380"
```

---

### Task 5: The mandate hook

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt:64-96,168-202`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt:82-88`
- Modify: `modules/ndopify-server/src/test/resources/db/test/clean.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt`

**Interfaces:**
- Consumes: `EmailVerificationService.ensureDeliverable(email, clientIp)`, `ClientIp.of(request)`.
- Produces: `MandateService.create(request: CreateMandateRequest, clientIp: String? = null): MandateEntity` (the
  default keeps every existing Kotlin caller compiling).

- [ ] **Step 1: Add the table to the test cleanup**

`DELETE FROM T_EMAIL_ADDRESS;` at the top of `clean.sql` (the table is global and outlives a tenant-scoped test), with
a one-line comment.

- [ ] **Step 2: Write the failing endpoint test**

`CreateMandateEmailEndpointTest : TenantAwareEndpointIntegrationTest()`, with
`@TestPropertySource(properties = ["ndopify.email.verification.provider=ses", "ndopify.email.verification.throttle.per-ip-per-hour=1000", "ndopify.email.verification.throttle.daily-cap=1000"])`,
`@MockitoBean private lateinit var verifier: EmailVerifier`, `@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateEndpoint.sql"])`,
reusing `CreateMandateEndpointTest`'s `request` fixture, and autowiring `MandateRepository`, `CustomerRepository`,
`EmailAddressRepository`, `DataSource`:

```kotlin
@Test
fun `a LOW verdict is refused, writes nothing and is cached`() {
    doReturn(EmailVerification(0.0, VerificationReason.SYNTAX_INVALID)).whenever(verifier).verify(any())

    val response = rest.postForEntity("/v1/mandates", request, ErrorResponse::class.java)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, response.body!!.error.code)
    assertEquals("email", response.body!!.error.parameter?.name)
    assertEquals(ParameterType.PARAMETER_TYPE_PAYLOAD, response.body!!.error.parameter?.type)
    assertNull(response.body!!.error.message)
    assertNull(response.body!!.error.data)
    assertEquals(0L, mandateDao.count())
    assertEquals(0L, customerDao.count())
    assertEquals(1L, emailDao.count())
    verify(verifier, times(1)).verify(any())

    rest.postForEntity("/v1/mandates", request, ErrorResponse::class.java)
    verify(verifier, times(1)).verify(any()) // the second refusal comes from the cache
}
```

plus:
- a valid verdict → 200, one mandate, one row;
- the same address twice inside the TTL → 200 twice, `verify(verifier, times(1))`, one row;
- a row expired by a `DataSource` `UPDATE ... SET expires_at = <past>` → verified again and the row updated
  (`assertEquals(1L, emailDao.count())`);
- the verifier throwing → 200, no row;
- `email = "ray@"` → 400 `EMAIL_ADDRESS_INVALID`, `verify(verifier, never())`, no row;
- `request.copy(originListingId = 999_999)` with a refused verdict → 400 `EMAIL_ADDRESS_INVALID`, not
  `LISTING_NOT_FOUND` (the check runs first);
- a second parameterless `CreateMandateEndpointTest` guard: the same fixture creates fine with `provider=none`'s
  default (that test is unchanged and must stay green).

- [ ] **Step 3: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateMandateEmailEndpointTest`
Expected: FAIL — the mandate is created (200) for a refused address.

- [ ] **Step 4: Call the check first**

In `MandateService`: add `private val emailVerification: EmailVerificationService` to the constructor;
`fun create(request: CreateMandateRequest, clientIp: String? = null)` starts with
`emailVerification.ensureDeliverable(request.email, clientIp)`, before `validateRentalTerms` and before the origin
listing is resolved. Its KDoc gains the order: `EMAIL_ADDRESS_INVALID` first, then `LISTING_NOT_FOUND`,
`MANDATE_RATE_LIMITED`.

- [ ] **Step 5: Pass the caller's IP**

`MandateEndpoints.create` takes `http: HttpServletRequest` and calls `service.create(request, ClientIp.of(http))`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='CreateMandateEmailEndpointTest,CreateMandateEndpointTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate \
  modules/ndopify-server/src/test/resources/db/test/clean.sql \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt
git commit -m "feat(server): refuse an undeliverable mandate e-mail" -m "Refs #380"
```

---

### Task 6: The SES health check, and the provider in TEST and PROD

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthIndicator.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt`
- Modify: `modules/ndopify-server/src/main/resources/application-test.yml`, `application-prod.yml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/SesEmailVerifierHealthEndpointTest.kt`

**Interfaces:**
- Consumes: `SesV2Client`, `Clock`.
- Produces: `class SesEmailVerifierHealthIndicator(client: SesV2Client, clock: Clock, upTtl: Duration, downTtl: Duration) : HealthIndicator`;
  bean `sesEmailVerifierHealthIndicator` (component id `sesEmailVerifier`), only with `provider=ses`.

- [ ] **Step 1: Write the failing tests**

`SesEmailVerifierHealthEndpointTest : BaseEndpointIntegrationTest()` with
`@TestPropertySource(properties = ["ndopify.email.verification.provider=ses", "ndopify.email.verification.health.up-ttl-minutes=0", "ndopify.email.verification.health.down-ttl-seconds=0"])`
and `@MockitoBean private lateinit var client: SesV2Client` (as `TelegramHealthEndpointTest` does):

```kotlin
@Test
fun `SES is UP with its account flags`() {
    doReturn(GetAccountResponse.builder().sendingEnabled(true).productionAccessEnabled(false).build())
        .whenever(client).getAccount(any(GetAccountRequest::class.java))

    val component = component(rest.getForEntity("/actuator/health", Map::class.java).body!!)

    assertEquals("UP", component["status"])
    assertEquals(true, (component["details"] as Map<*, *>)["sendingEnabled"])
    assertEquals(false, (component["details"] as Map<*, *>)["productionAccessEnabled"])
    assertTrue((component["details"] as Map<*, *>)["latency"] is Number)
}

@Test
fun `a failing SES is DOWN and leaks nothing`() {
    doThrow(RuntimeException("AKIA-secret ray@gmail.com")).whenever(client).getAccount(any(GetAccountRequest::class.java))

    val body = rest.getForEntity("/actuator/health", Map::class.java).body!!
    assertEquals("DOWN", component(body)["status"])
    assertFalse(body.toString().contains("AKIA-secret"))
    assertFalse(body.toString().contains("ray@gmail.com"))
    assertEquals("RuntimeException", (component(body)["details"] as Map<*, *>)["error"])
}
```

plus: the cached result means two probes make one call (with `up-ttl-minutes=1` and the mock's `times(1)`), and a
separate `SesEmailVerifierHealthDisabledTest : BaseEndpointIntegrationTest()` (default provider) asserting
`component(...)` is null / the JSON has no `sesEmailVerifier` key.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='SesEmailVerifierHealth*'`
Expected: FAIL — no `sesEmailVerifier` component.

- [ ] **Step 3: Write the indicator**

Clone `TelegramHealthIndicator`'s shape (private `cached`/`expiresAt`, `@Synchronized health()`, UP TTL then DOWN TTL):
`client.getAccount(GetAccountRequest.builder().build())` → UP with `sendingEnabled`, `productionAccessEnabled` and
`latency`; on any exception DOWN with `error = ex.javaClass.simpleName` only. KDoc: `GetAccount` is free and validates
credentials, region and reachability (not the `GetEmailAddressInsights` permission); the result is cached because
`/actuator/health` is public and SES throttles around one call per second.

- [ ] **Step 4: Register it conditionally**

In `EmailConfiguration`, next to the verifier beans:

```kotlin
@Bean
@ConditionalOnProperty(name = ["ndopify.email.verification.provider"], havingValue = "ses")
fun sesEmailVerifierHealthIndicator(client: SesV2Client): HealthIndicator =
    SesEmailVerifierHealthIndicator(client, clock, Duration.ofMinutes(upTtlMinutes), Duration.ofSeconds(downTtlSeconds))
```

with a KDoc line saying why it is gated here and not by `management.health.<id>.enabled` (Spring Boot registers every
`HealthIndicator` bean regardless — see `TelegramConfiguration`).

- [ ] **Step 5: Turn the provider on in the deployed environments**

`application-test.yml` and `application-prod.yml`, in their `ndopify:` block:

```yaml
    email:
        verification:
            provider: ses
```

(region and TTLs keep `application.yml`'s values; the credentials are the instance's, like the S3 client's). Leave
`application.yml` and the `qa` profile at `none`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='SesEmailVerifierHealth*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt \
  modules/ndopify-server/src/main/resources/application-test.yml \
  modules/ndopify-server/src/main/resources/application-prod.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email
git commit -m "feat(server): report the SES verifier in health and enable it in TEST and PROD" -m "Refs #380"
```

---

### Task 7: The two public wizards (Partner Central)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt:314-339`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt:344-369`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`, `FindPropertyTemplateTest.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`, `OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.EMAIL_ADDRESS_INVALID`, `ErrorResponse`, the existing `step(step, answers, returnTo, errors, model)`.
- Produces: nothing for other code; both controllers gain a private
  `const val INVALID_EMAIL = "Cette adresse email n'est pas valide."` and a private `errorCode(ex: HttpClientErrorException): String?`
  (the shape `JoinController` uses).

- [ ] **Step 1: Port check against the approved prototype**

Open the approved contact-step state (`src/main/html/v2/find-property.html?sample=email-invalid`, same for
`offer-property.html`) next to `templates/find-property.html`'s contact step: the template already renders
`errors['email']` with `aria-invalid` and the `:: error('email')` fragment, so nothing is ported. Record that in the
commit body; do not add markup.

- [ ] **Step 2: Write the failing controller test**

In `FindPropertyControllerTest`, with a complete `PendingMandate` in the session (the existing `submit()` fixture):

```kotlin
@Test
fun `a refused e-mail comes back to the contact step`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
    doThrow(badRequest(ErrorCode.EMAIL_ADDRESS_INVALID)).whenever(mandateClient).create(any())

    val result = mvc.perform(post("/find-property/review").session(session)).andReturn()

    assertEquals("find-property", result.modelAndView?.viewName)
    assertEquals("contact", model(result)["step"])
    assertEquals(true, model(result)["returning"])
    assertEquals("review", model(result)["previous"])
    assertEquals(mapOf("email" to "Cette adresse email n'est pas valide."), model(result)["errors"])
    assertNull(model(result)["error"])
    assertEquals(complete, pending())
    assertNull(session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE))
}

@Test
fun `another refusal keeps the review error`() {
    // HTTP_INVALID_PARAMETER: step "review", errors empty, error "Envoi impossible pour le moment. Réessayez dans quelques instants."
}
```

with `private fun badRequest(code: String): HttpClientErrorException` built as in `JoinControllerTest`
(`setBodyConvertFunction { ErrorResponse(Error(code = code)) }`). The existing `RestClientException` tests stay
unchanged. Same two tests in `OfferPropertyControllerTest` (`/offer-property/review`, its `complete: PendingListing`,
and `session.getAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE)` — the class's own constant, `npc.submitted-listing`).

- [ ] **Step 3: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyControllerTest,OfferPropertyControllerTest'`
Expected: FAIL — the review step is rendered with the generic error.

- [ ] **Step 4: Handle the code in both controllers**

In the single `catch (ex: RestClientException)` block, before the generic lines:

```kotlin
val refused = ex is HttpClientErrorException && errorCode(ex) == ErrorCode.EMAIL_ADDRESS_INVALID
if (refused) return step(STEP_CONTACT, pending, RETURN_TO_REVIEW, mapOf("email" to INVALID_EMAIL), model)
```

Keep the existing log + `model.addAttribute("error", …)` + `step(STEP_REVIEW, …)` for everything else. Add the private
`errorCode(ex)` helper (read `ErrorResponse::class.java` off the response body, `null` on any failure) and the constant,
as `JoinController` does. A KDoc line on `submit`: a refused contact e-mail comes back to the contact step (the review
would show a dead end).

- [ ] **Step 5: Write the failing template test**

In `FindPropertyTemplateTest`, a test that seeds a complete session, throws the coded 400 and asserts the rendered
HTML: the e-mail error paragraph is not `hidden` and reads « Cette adresse email n'est pas valide. », the `#email`
input carries `aria-invalid="true"`, and the page contains neither *Envoi impossible pour le moment* nor a `Suivant`
submit on the contact step (its button reads *Revenir à la revue*). Mirror it in `OfferPropertyTemplateTest`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='FindProperty*Test,OfferProperty*Test'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): send the wizard back to the e-mail on a refused address" -m "Refs #380"
```

---

### Task 8: The listing interest popup (ndopify-web)

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt:75-110,112-129`
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html:115-118`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingControllerTest.kt`, `ListingTemplateTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.EMAIL_ADDRESS_INVALID`, `ErrorResponse`, the existing `page(id, model, form, errors, done)`.
- Produces: nothing for other code; a private `errorCode(ex)` and
  `const val INVALID_EMAIL = "Cette adresse email n'est pas valide."` in `ListingController`.

- [ ] **Step 1: Port the approved prototype's markup**

The approved `src/main/html/v2/annonce.html` gives the popup's e-mail input an `id`-linked error
(`aria-describedby="email-error"`, `id="email-error"` on the span, `aria-invalid` when flagged). Port exactly that to
`templates/listing.html`: on the input add `th:attr="aria-invalid=${errors?.get('email') != null ? 'true' : null}"`
and `aria-describedby="email-error"`, and give the existing `<span class="field-error" th:text="${errors?.get('email')}">`
`id="email-error"`. Nothing else: the global paragraph already renders only when `errors['global']` exists, and the
`#visit` modal already opens when `errors` is non-empty.

- [ ] **Step 2: Write the failing controller test**

In `ListingControllerTest` (a new test next to `a rejected request re-renders the form with a global error`, using the
class's existing `interest(vararg params)` and `result.modelAndView?.model` helpers):

```kotlin
@Test
fun `a refused e-mail re-renders the popup with its field error`() {
    doThrow(badRequest(ErrorCode.EMAIL_ADDRESS_INVALID)).whenever(mandateClient).create(any())

    val result = interest(
        "firstName" to "Awa",
        "lastName" to "Ndiaye",
        "email" to "awa.ndiaye@gmail.com",
        "whatsappNumber" to "+237650000001",
        "movingPeriod" to "WITHIN_1_MONTH",
    )

    val errors = result.modelAndView?.model?.get("errors") as Map<*, *>
    assertEquals("Cette adresse email n'est pas valide.", errors["email"])
    assertNull(errors["global"])
    assertNotNull(result.modelAndView?.model?.get("interestForm"))   // the typed values are kept
    assertEquals(false, result.modelAndView?.model?.get("done"))
}
```

and keep `a rejected request re-renders the form with a global error` green (its bare `BadRequest` has no body, so its
code is null and it keeps the global line); add an explicit second test with `HTTP_INVALID_PARAMETER` asserting that
global message and no `errors["email"]`. `badRequest(code)` mirrors `JoinControllerTest`'s helper.

- [ ] **Step 3: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingControllerTest`
Expected: FAIL — `errors["email"]` is null, `errors["global"]` is set.

- [ ] **Step 4: Handle the code**

In `ListingController.interest`'s `catch (ex: HttpClientErrorException.BadRequest)`, branch on
`errorCode(ex) == ErrorCode.EMAIL_ADDRESS_INVALID` → `page(id, model, form, mapOf("email" to INVALID_EMAIL), done = false)`;
otherwise the existing global message. Add the private `errorCode(ex)` helper and the constant. KDoc of `interest`: a
refused address reopens the popup on its e-mail field; any other 400 keeps the global line.

- [ ] **Step 5: Write the failing template test**

In `ListingTemplateTest`, render the page with `errors = mapOf("email" to "Cette adresse email n'est pas valide.")`:
the `#email` input has `aria-invalid="true"` and `aria-describedby="email-error"`, the `#email-error` span reads the
sentence, `#visit` has `is-open`, and no global error paragraph is rendered.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest='ListingControllerTest,ListingTemplateTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingController.kt \
  modules/ndopify-web/src/main/resources/templates/listing.html \
  modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing
git commit -m "feat(web): flag the refused e-mail in the listing interest popup" -m "Refs #380"
```

---

### Task 9: Document the behaviour and verify the four modules

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`, `modules/ndopify-partner-central/CLAUDE.md`, `modules/ndopify-web/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: nothing.

- [ ] **Step 1: Document the server side**

In `modules/ndopify-server/CLAUDE.md`: a new `email` domain entry in *Domains* (the global `T_EMAIL_ADDRESS` cache, the
`EmailVerifier` port, the policy and its order, the cost guard, `EMAIL_ADDRESS_INVALID`), a sentence in the `mandate`
entry (`MandateService.create` checks the contact e-mail first, before `LISTING_NOT_FOUND` and
`MANDATE_RATE_LIMITED`), and the `platform/email` paragraph (SES adapter, health component, the provider setting, and
that `none` is the default so QA never calls AWS).

- [ ] **Step 2: Document the two web apps**

In `modules/ndopify-partner-central/CLAUDE.md`, one line on each wizard's error handling: a 400
`EMAIL_ADDRESS_INVALID` at the review comes back to the contact step with « Cette adresse email n'est pas valide. »;
anything else keeps *Envoi impossible pour le moment.* In `modules/ndopify-web/CLAUDE.md`, the same for the interest
popup (the e-mail field's error reopens `#visit`; any other 400 keeps the global line).

- [ ] **Step 3: Verify every module**

Run, in order:

```bash
cd modules/ndopify-dto && mvn install
cd ../ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
cd ../ndopify-web && mvn verify
```

Expected: BUILD SUCCESS in each — ktlint, tests, JaCoCo gates (the web apps' `page-scripts` run after surefire), and
the existing suite untouched.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md modules/ndopify-web/CLAUDE.md
git commit -m "docs: describe the mandate e-mail validation" -m "Refs #380"
```
