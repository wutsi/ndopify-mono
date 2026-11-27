# Move e-mail verification into platform/email — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the e-mail verification port, provider-neutral model, default adapter, cost guard, syntax check and the two model enums from the `email` domain into `platform/email`, so `platform/` no longer depends on a domain, with no behavior, database, DTO or config change.

**Architecture:** Pure package move in `modules/ndopify-server`: seven classes change package to `com.wutsi.ndopify.platform.email`; the policy (`EmailVerificationService`, `email.server.service`) and the cache (`EmailAddressEntity`, `email.server.domain`; `EmailAddressRepository`, `email.server.dao`) stay where they are and import the port and model from `platform.email`. Callers and tests change imports only; `EmailVerificationThrottleTest` moves beside its class. Both packages stay under the scanned `com.wutsi.ndopify` root, so Spring and JPA wiring is unchanged.

**Tech Stack:** Kotlin, Spring Boot 4, Spring Data JPA, Maven (ktlint, JaCoCo), JUnit 5 / `kotlin.test` / mockito-kotlin.

**Spec:** story #384 — https://github.com/wutsi/ndopify-mono/issues/384

## Global Constraints

- **Pure package move.** No class, enum constant, method, constructor, bean name, config key, DB column or stored enum name changes. No Flyway migration, no `ndopify-dto` change, no `ndopify-server` `pom.xml` change.
- **Target package:** `com.wutsi.ndopify.platform.email`. The policy and the cache keep their fully qualified names:
  `com.wutsi.ndopify.email.server.service.EmailVerificationService`,
  `com.wutsi.ndopify.email.server.domain.EmailAddressEntity`,
  `com.wutsi.ndopify.email.server.dao.EmailAddressRepository`.
- **One module:** `modules/ndopify-server`. The web apps never reference these classes.
- **Tests keep their assertions.** Only imports change, except `EmailVerificationThrottleTest`, which keeps its test bodies and moves package.
- **Wiring stays as is.** `Application.kt`'s `@EntityScan` still lists `com.wutsi.ndopify.email.server.domain` (for `EmailAddressEntity`) and `@EnableJpaRepositories` still lists `com.wutsi.ndopify.email.server.dao`. `EmailVerificationThrottle` stays a `@Component`; `EmailVerifierNone` stays `new`-ed by `EmailConfiguration`.
- **Style:** ktlint runs in `mvn validate`; imports are one alphabetically sorted block. No same-package import may remain in `platform.email`.
- `cd modules/ndopify-server && mvn verify` is the gate (local MySQL required for the endpoint tests).

## Review Focus

1. **The throttle bean still exists.** `EmailVerificationThrottle` is a `@Component`; a wrong target package silently drops it and `EmailVerificationService` fails to construct. The Spring context boots in `CreateMandateEmailEndpointTest`.
2. **The cache still reads its enums.** `EmailAddressEntity` and the `@Converter(autoApply = true)` classes in `util/jpa/EnumConverters` must reference the same `Verdict` / `VerificationReason` types (now `platform.email`); otherwise a cached row reads the converter's default instead of its stored verdict. `EmailAddressRepositoryTest` round-trips both enums.
3. **`platform/` no longer imports a domain.** A leftover import in `EmailVerifierSES` was the bug; the grep in Task 3 must return nothing.
4. **No redundant same-package import.** After the move, `EmailVerifierSES` and `EmailVerifierSESTest` must drop their old imports; ktlint in `mvn verify` catches one left behind.
5. **Behavior is unchanged.** `EmailVerificationServiceTest` pins cache/TTL/fail-open/budget; `CreateMandateEmailEndpointTest` pins `400 EMAIL_ADDRESS_INVALID` on a LOW and acceptance with `provider=none`.
6. **The policy and cache did not move.** `MandateService` is not edited; its import and `EmailVerificationService`'s FQN stay valid.

---

### Task 1: Move the seven classes and update every import

**Files:**
- Move: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerifier.kt` → `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifier.kt`
- Move: `.../email/server/service/EmailVerification.kt` → `.../platform/email/EmailVerification.kt`
- Move: `.../email/server/service/EmailVerifierNone.kt` → `.../platform/email/EmailVerifierNone.kt`
- Move: `.../email/server/service/EmailVerificationThrottle.kt` → `.../platform/email/EmailVerificationThrottle.kt`
- Move: `.../email/server/service/EmailSyntax.kt` → `.../platform/email/EmailSyntax.kt`
- Move: `.../email/server/domain/Verdict.kt` → `.../platform/email/Verdict.kt`
- Move: `.../email/server/domain/VerificationReason.kt` → `.../platform/email/VerificationReason.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/domain/EmailAddressEntity.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationService.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/EmailConfiguration.kt` (imports only)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSES.kt` (drop imports)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt` (imports only)
- Move: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationThrottleTest.kt` → `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerificationThrottleTest.kt`
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/email/server/dao/EmailAddressRepositoryTest.kt` (imports only)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/email/server/service/EmailVerificationServiceTest.kt` (imports only)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEmailEndpointTest.kt` (imports only)
- Modify test: `.../src/test/kotlin/com/wutsi/ndopify/platform/email/EmailVerifierSESTest.kt` (drop imports)

**Interfaces:**
- Produces, all in `com.wutsi.ndopify.platform.email`:
  - `fun interface EmailVerifier { fun verify(email: String): EmailVerification }`
  - `data class EmailVerification(score: Double?, reason: VerificationReason, hasValidSyntax: Verdict = Verdict.HIGH, hasValidDnsRecords: Verdict = Verdict.HIGH, mailboxExists: Verdict = Verdict.HIGH, isRoleAddress: Verdict = Verdict.HIGH, isDisposable: Verdict = Verdict.HIGH, isRandomInput: Verdict = Verdict.HIGH)`
  - `class EmailVerifierNone : EmailVerifier`
  - `@Component class EmailVerificationThrottle(clock: Clock, perIpPerHour: Int, dailyCap: Int) { fun tryAcquire(ip: String?): Boolean }`
  - `object EmailSyntax { fun isValid(email: String): Boolean }`
  - `enum class Verdict { HIGH, MEDIUM, LOW }`
  - `enum class VerificationReason { NONE, SYNTAX_INVALID, DOMAIN_INVALID, MAILBOX_UNREACHABLE, DISPOSABLE, ROLE_ADDRESS, RANDOM_INPUT, UNAVAILABLE }`
- Consumes: nothing new.

- [ ] **Step 1: Move the seven main files and change their package declarations**

Use `git mv` for each of the seven files listed above, then change the first line of each to
`package com.wutsi.ndopify.platform.email`. Leave every class, KDoc and body untouched. Delete the now-redundant
`import com.wutsi.ndopify.email.server.domain.Verdict` / `...VerificationReason` from `EmailVerification.kt` and
`EmailVerifierNone.kt` (same package after the move), and keep `EmailVerification`'s/`EmailVerifierNone`'s references
unqualified.

Verify the target package holds the five moved classes plus the two enums beside the existing
`EmailVerifierSES.kt` and `SesEmailVerifierHealthIndicator.kt`:

```bash
ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email
```

Expected: `EmailSyntax.kt`, `EmailVerification.kt`, `EmailVerificationThrottle.kt`, `EmailVerifier.kt`,
`EmailVerifierNone.kt`, `EmailVerifierSES.kt`, `SesEmailVerifierHealthIndicator.kt`, `Verdict.kt`,
`VerificationReason.kt`.

- [ ] **Step 2: Fix the main-code imports**

- `email/server/domain/EmailAddressEntity.kt`: add `import com.wutsi.ndopify.platform.email.Verdict` and
  `import com.wutsi.ndopify.platform.email.VerificationReason`.
- `email/server/service/EmailVerificationService.kt`: add `import com.wutsi.ndopify.platform.email.EmailVerification`,
  `...EmailVerificationThrottle`, `...EmailSyntax` and `...EmailVerifier`.
- `config/platform/EmailConfiguration.kt`: change the `EmailVerifier` and `EmailVerifierNone` imports to
  `com.wutsi.ndopify.platform.email.*`.
- `platform/email/EmailVerifierSES.kt`: delete its four imports
  (`com.wutsi.ndopify.email.server.domain.Verdict`, `...VerificationReason`,
  `com.wutsi.ndopify.email.server.service.EmailVerification`, `...EmailVerifier`); they are same-package now.
- `util/jpa/EnumConverters.kt`: change the `Verdict` and `VerificationReason` imports to
  `com.wutsi.ndopify.platform.email.*`.

`MandateService` and `Application.kt` are not edited: their `EmailVerificationService` FQN and the `email` scan entries
are still correct.

- [ ] **Step 3: Move the throttle test and fix the test imports**

Use `git mv` to move `EmailVerificationThrottleTest.kt` to
`src/test/kotlin/com/wutsi/ndopify/platform/email/`, change its package to `com.wutsi.ndopify.platform.email`, and keep
all five test bodies and the private `MutableClock` unchanged. Then:

- `email/server/dao/EmailAddressRepositoryTest.kt`: change the `Verdict` / `VerificationReason` imports to
  `com.wutsi.ndopify.platform.email.*`.
- `email/server/service/EmailVerificationServiceTest.kt`: keep the `EmailAddressEntity` import; add imports for
  `com.wutsi.ndopify.platform.email.EmailVerification`, `...EmailVerifier`, `...EmailVerificationThrottle`; change the
  `VerificationReason` import to `platform.email`.
- `mandate/server/endpoint/CreateMandateEmailEndpointTest.kt`: change the `EmailVerification`, `EmailVerifier` and
  `VerificationReason` imports to `com.wutsi.ndopify.platform.email.*`; keep the `EmailAddressRepository` import.
- `platform/email/EmailVerifierSESTest.kt`: delete its `Verdict` and `VerificationReason` imports (same package).

- [ ] **Step 4: Compile, then run the affected unit tests**

Run: `cd modules/ndopify-server && mvn test-compile`
Expected: BUILD SUCCESS — no unresolved reference to a moved class.

Run: `cd modules/ndopify-server && mvn test -Dtest='EmailVerificationServiceTest,EmailVerificationThrottleTest,EmailVerifierSESTest'`
Expected: BUILD SUCCESS, every test passes (these three need no MySQL).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify
git commit -m "refactor(server): move e-mail verification into platform/email" -m "Refs #384"
```

---

### Task 2: Update the module documentation

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the `platform/email` bullet, and the `email` domain bullet's opening
  sentence)

**Interfaces:**
- Consumes: Task 1's final packages.
- Produces: nothing (documentation).

- [ ] **Step 1: Describe the new `platform/email` content**

In the **`platform/email`** bullet (currently the SES adapter and its health indicator), add, in one or two sentences,
that it also holds the e-mail verification capability: the `EmailVerifier` port, the provider-neutral
`EmailVerification` model with its `Verdict` / `VerificationReason` enums, the `EmailVerifierNone` default adapter, the
`EmailVerificationThrottle` paid-call budget guard and the local `EmailSyntax` check. Keep the existing text about
`EmailVerifierSES`, `SesEmailVerifierHealthIndicator`, the `ndopify.email.verification.provider` switch and the AWS
calls.

- [ ] **Step 2: State what the `email` domain keeps**

At the start of the **email** domain bullet (`— the e-mail verification policy of the public forms, on a global hashed
cache`), add a sentence saying the port, the model, the default adapter, the throttle and the syntax check live in
`platform.email`, and that this domain keeps only the policy (`EmailVerificationService`) and the `T_EMAIL_ADDRESS`
cache (`EmailAddressEntity`, `EmailAddressRepository`). Do not change the description of the policy's behavior, the
TTLs, fail-open, budget or error code.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): describe the e-mail verification split between platform and the domain" -m "Refs #384"
```

---

### Task 3: Verify the layering and the build

**Files:** none (verification only).

**Interfaces:**
- Consumes: Tasks 1 and 2.

- [ ] **Step 1: Assert `platform/` imports no domain (main code)**

Run:

```bash
cd modules/ndopify-server
grep -rn --include='*.kt' -E "import com\.wutsi\.ndopify\.(agent|contract|customer|email|listing|mandate|match|party|refdata|security|visit)\.server" \
  src/main/kotlin/com/wutsi/ndopify/platform
```

Expected: no output. (A hit in `src/test/kotlin/.../platform/telegram/` is out of scope: those are endpoint tests that
legitimately use domain DAOs.)

- [ ] **Step 2: Assert each package holds the expected classes**

Run: `find modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/email -type f | sort`
Expected: exactly `dao/EmailAddressRepository.kt`, `domain/EmailAddressEntity.kt`,
`service/EmailVerificationService.kt`.

Run: `ls modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/email`
Expected: the nine files listed in Task 1, Step 1.

- [ ] **Step 3: Run the full module gate**

Run: `cd modules/ndopify-server && mvn verify` (local MySQL must be running)
Expected: BUILD SUCCESS — `validate` (ktlint), all tests (including `EmailAddressRepositoryTest`,
`CreateMandateEmailEndpointTest`, `SesEmailVerifierHealth*Test`) and the JaCoCo coverage gate pass.

- [ ] **Step 4: Review the diff against `main`**

Run: `git diff --stat origin/main`
Expected: only renames under `email/server/*` → `platform/email/*`, import-only edits in the five caller files and four
test files, the moved throttle test, and `modules/ndopify-server/CLAUDE.md`. No migration, DTO, `pom.xml`, config file
or `MandateService` change.
