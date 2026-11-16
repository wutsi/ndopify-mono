# Match Link TTL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the customer links of the match proposal and visit e-mails valid for 365 days instead of 7, so a customer can come back to a match months after marking it as interesting.

**Architecture:** One config value in `ndopify-server`: `ndopify.security.access-token.ttl.match-link` goes from `7d` to `365d`. `MatchLinkTokenService` passes that TTL to every `public-portal` token it mints, and the six visit mailets use the same service and key, so all customer links change together. No code path changes; the docs that state the old value are updated.

**Tech Stack:** Kotlin, Spring Boot, `application.yml`, `ndopify-server` endpoint integration tests (MySQL), `kotlin.test`, auth0 `JWT`.

**Spec:** GitHub issue #176 — https://github.com/wutsi/ndopify-mono/issues/176

## Global Constraints

- `ndopify-server` only. No `ndopify-dto`, `ndopify-web-core`, Flyway, UI or root `pom.xml` change; no library release.
- One config value: `ndopify.security.access-token.ttl.match-link: 365d`. No new config key, no `application-prod.yml` override.
- `ndopify.match.expire.days` stays `7`: this story doesn't change how long an unanswered proposal stays answerable.
- The token stays a full, unscoped, unrevocable `public-portal` token; scoping and revocation are out of scope.
- French e-mail copy is unchanged; no template change.
- `kotlin.test` assertions; conventional commit with `Refs #176` in the body.
- `mvn verify` green in `modules/ndopify-server` before the commit — it needs a local MySQL.

## Review Focus

1. **Every customer link, not just the proposal** — the six visit mailets call `MatchLinkTokenService`, so the shared key covers them; a split or a second key would leave visit links at 7d (Task 1, step 6).
2. **No environment silently keeps 7d** — `application-prod.yml`, `application-test.yml` and `application-qa.yml` must not override `match-link` (Task 1, step 6).
3. **The cookie follows the token** — web-core's `AccessTokenService` reads the token's `exp`, so the browser cookie now lives 365 days; no web-core change, and this is intended (Task 1, step 6).
4. **The proposal still expires at 7 days** — `ndopify.match.expire.days` is untouched; `ExpireMatchesJobTest` stays green (Task 1, step 7).
5. **A token past a year is still rejected** — the JWT verifier already rejects it, and the `match-link` "Ce lien a expiré…" page is unchanged (Task 1, step 7).

---

### Task 1: Extend the `match-link` TTL to 365 days

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application.yml:268`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ProposeMatchEndpointTest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchLinkTokenService.kt:13-18`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenProperties.kt:6-10`
- Modify: `modules/ndopify-server/CLAUDE.md:405-407`

**Interfaces:**
- Consumes: `POST /v1/matches/{id}/propose` (existing), `MatchLinkTokenService` (existing).
- Produces: every customer token minted by `MatchLinkTokenService.create` has `exp - iat = 365 days`.

- [ ] **Step 1: Write the failing test**

Add to `ProposeMatchEndpointTest`, after `the link signs the customer in to the public portal` (imports: `com.auth0.jwt.JWT`, `java.time.Duration`):

```kotlin
@Test
fun `the link lives a year`() {
    rest.postForEntity("/v1/matches/1/propose", null, Any::class.java)

    assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    val body = smtp.receivedMessages.single().content.toString()
    val token = Regex("/match/1\\?token=([A-Za-z0-9._-]+)").find(body)!!.groupValues[1]
    val jwt = JWT.decode(token)
    assertEquals(Duration.ofDays(365), Duration.between(jwt.issuedAtAsInstant, jwt.expiresAtAsInstant))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ProposeMatchEndpointTest`

Expected: FAIL in `the link lives a year` — `expected: PT8760H (365 days)` but was `PT168H (7 days)`.

- [ ] **Step 3: Change the TTL**

In `modules/ndopify-server/src/main/resources/application.yml`, line 268:

```yaml
                match-link: 365d           # The token of the customer links of the proposal and visit e-mails (MatchLinkTokenService)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=ProposeMatchEndpointTest`

Expected: PASS, `Tests run: … Failures: 0`.

- [ ] **Step 5: Update the docs that state the old behaviour**

- `MatchLinkTokenService` KDoc (lines 14-18): say it issues the token of the customer links of the **match proposal and the visit e-mails**, and that it lives 365 days.
- `AccessTokenProperties` KDoc (lines 6-10): the map is keyed by `ApplicationCode` **or a purpose key** (`match-link`), not only by application.
- `modules/ndopify-server/CLAUDE.md` lines 405-407: replace "(7d)" with "(365d)" and add that the visit e-mails' customer links use the same token and TTL. Keep the warning that the token isn't scoped to the match and signs in whoever has the e-mail.

- [ ] **Step 6: Check no override and every consumer**

Run:

```bash
grep -rn "match-link" modules/ndopify-server/src/main/resources modules/ndopify-server/src/test/resources
grep -rn "linkTokenService.create" modules/ndopify-server/src/main/kotlin
```

Expected: `match-link` only in `application.yml` (no `application-prod.yml` / `-test` / `-qa` override); `create` called by `MatchProposedMailet` and the six visit mailets, all through `MatchLinkTokenService`.

- [ ] **Step 7: Run the module verification**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass. `ExpireMatchesJobTest` and `AccessTokenServiceTest` stay green.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/ProposeMatchEndpointTest.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchLinkTokenService.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/AccessTokenProperties.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(match): keep the customer links valid for a year" -m "Refs #176"
```
