# Multi-Tenancy Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close tenant-isolation gaps in the `party` domain's child entities, then in `AgentEntity`, by giving each the same `@TenantId` treatment `PartyEntity` already has — so party, KYC, and agent data are all actually isolated per tenant at the domain/persistence layer.

**Architecture:** `ndopify-server` already has a working Hibernate multi-tenancy mechanism (`TenantContext` + `TenantIdentifierResolver` + `TenantContextFilter`, wired in `platform/config/TenantConfiguration.kt`) that auto-filters any entity annotated `@TenantId` by the tenant resolved for the current request. Today only `PartyEntity` carries that annotation. That annotation only protects queries rooted at `PartyEntity` itself — it does **not** propagate to child entities joined via a FK, even though they're always loaded through a `party_id` relationship. A codebase audit found this is a real, currently-unguarded gap: `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, and `KycCaseEntity` are all fetched directly by their own id from REST endpoints (`GET /v1/payment-methods/{id}`, `GET /v1/identifications/{id}`, `GET /v1/identifications/images/{id}/url`, `GET /v1/kyc/cases/{id}`, `POST /v1/kyc/cases/{id}/verify`) with no check that the loaded row belongs to the caller's tenant — none of it is covered by any existing cross-tenant test. `AgentEntity` has the same shape of gap (it joins to `PartyEntity` via `party_id` but isn't itself tenant-scoped) and is addressed second, per request, since the party-domain gap is more directly exploitable (string UUID ids returned straight from create responses) and should be closed first. Both fixes extend `@TenantId` to the affected entities rather than hand-rolling predicates, mirroring `PartyEntity`'s existing pattern exactly — no manual tenant predicate exists anywhere for `PartyEntity` today, and none should be added for these either.

**Tech Stack:** Kotlin, Spring Boot, Hibernate (`@TenantId` / `CurrentTenantIdentifierResolver`), Flyway, `kotlin.test` assertions, `TestRestTemplate`-based integration tests.

**Spec:** No separate spec document — this plan is scoped directly from a codebase audit (see conversation) against `CLAUDE.md`'s "Multi-Tenancy — current state" section, which is itself partially stale (it describes an explicit-predicate pattern for the `agent` domain and references `MobileChangeEntity`/`IdentityChangeEntity`, neither of which exist in the code). This plan corrects course based on what's actually there.

**Explicitly deferred to a later plan:** JWT signing (`AccessTokenService` currently uses `Algorithm.none()`) and enforcing authentication at the Spring Security filter chain (currently `permitAll()`). Tenant *resolution* today still relies on an unsigned token or an unauthenticated header — that trust gap is unaffected by this plan and will be addressed separately. This plan only makes the domain/persistence layer correctly tenant-scoped once a tenant is resolved, by whatever means.

## Global Constraints

- Never modify an existing Flyway migration — always add a new one (`CLAUDE.md`).
- Migration files: `V{version}__{description}.sql`, split into `db/migration/common/` (always run) vs `db/migration/local/` (local-dev seed data only).
- Assertions in tests use `kotlin.test` (`assertEquals`, `assertTrue`, `assertFailsWith`, …), never `org.junit.jupiter.api.Assertions.*`.
- `ktlint` runs at the `validate` phase — run `mvn antrun:run@ktlint-format` before committing if formatting drifts.
- JaCoCo coverage is a build-breaking gate (`mvn verify`) — new code needs covering tests, not just passing ones.
- Follow the `agent`/`party` domain's existing tenant pattern: tenant scoping comes from `@TenantId` + the existing Hibernate resolver, not a hand-rolled predicate — do not add a manual `tenantId` filter to any service; that would duplicate enforcement and risk drifting out of sync with the real mechanism.
- `KycVerificationEntity` is deliberately **not** touched by this plan — it has no repository method that loads it by its own id anywhere in the codebase (confirmed by audit); it's only ever reached via the already-tenant-checked `KycCaseEntity.verifications` association, so adding `@TenantId` there would be scope creep with no isolation benefit.
- Do not touch `SecurityConfiguration`, `AccessTokenService`'s signing algorithm, or any JWT verification logic — that's explicitly out of scope for this plan.

## Review Focus

- **A tenant's caller fetches another tenant's payment method/identification/identification image/KYC case by guessing or replaying an id:** `GET /v1/payment-methods/{id}`, `GET /v1/identifications/{id}`, `GET /v1/identifications/images/{id}/url`, and `GET /v1/kyc/cases/{id}` must all 404 instead of 200 once `@TenantId` is added — this is the actual, currently-unguarded leak this plan closes first.
- **`POST /v1/kyc/cases/{id}/verify` on another tenant's case:** must also 404 rather than mutating a case (and its party's KYC status) belonging to a different tenant.
- **Two agents in different tenants with the same neighborhood/city/id search criteria:** `AgentService.search()` (and `findById`) must never return the other tenant's row once `@TenantId` is added to `AgentEntity`.
- **Entity creation under `TenantContext` unset or `NO_TENANT (-1L)`:** should behave the same way `PartyEntity` creation already behaves in that case (fails closed per `TenantIdentifierResolver`), not silently default to tenant 1.
- **Existing fixtures/tests that don't set a tenant explicitly:** the `DEFAULT 1` on every new column must keep all pre-existing single-tenant tests (fixtures inserted without a `tenant_id`) working unchanged, since they all run under `TenantAwareEndpointIntegrationTest`'s default `TENANT_ID = 1`.
- **`T_AGENT_NEIGHBORHOOD` join table:** this `@ElementCollection` table has no `tenant_id` of its own and is joined via `agent_id` — confirm the existing join-based query (`neighborhoodIds` search predicate in `AgentService.search()`) still only returns rows for the caller's tenant's agents once `@TenantId` filters the `T_AGENT` side of that join.

---

### Task 1: Add `tenantId` to the `party` domain's child entities via `@TenantId`

> **Status: done** (commits `d518954`, `d61ed6e`, and the fold-into-existing-tests follow-up). Two deviations from the plan as originally written, both per explicit direction during review:
> 1. The `tenant_id` columns were squashed directly into `V3_0__party.sql` instead of a separate `V5_0__party-children-tenant.sql` migration — the feature hadn't shipped yet, so there was no deployed schema to leave alone.
> 2. The cross-tenant isolation assertions were folded into the existing `GetPaymentMethodEndpointTest`, `GetIdentificationEndpointTest`, `GetIdentificationImageUrlEndpointTest`, and `GetKycCaseEndpointTest` (each gained a tenant-2 fixture row and a `... from another tenant is not found` test) instead of a standalone `PartyChildTenantIsolationEndpointTest`. Final count: 54/54 party tests green.

**Files (as actually changed):**
- Modified: `modules/ndopify-server/src/main/resources/db/migration/common/V3_0__party.sql` (tenant_id columns + indexes added directly)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/PaymentMethodEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/IdentificationEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/IdentificationImageEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/KycCaseEntity.kt`
- Modified: `GetPaymentMethodEndpointTest.kt` / `.sql`, `GetIdentificationEndpointTest.kt` / `.sql`, `GetIdentificationImageUrlEndpointTest.kt` / `.sql`, `GetKycCaseEndpointTest.kt` / `.sql` (all under `modules/ndopify-server/src/test/.../party/endpoint/` and `.../db/test/party/`)

**Interfaces:**
- Produces: `tenantId: Long?` on all four entities, nullable and left `null` on construction exactly like `PartyEntity.tenantId` — Hibernate's `TenantIdGeneration` populates it from `TenantContext` on insert.
- No change to any `*Service.kt`, `*Repository.kt`, or `*Endpoints.kt` in the `party` domain — per the Global Constraints, isolation comes from the annotation + the existing `TenantIdentifierResolver`, not a code-level check added to `PaymentMethodService.findById()` / `IdentificationService.findById()` / `IdentificationService.findImageById()` / `KycService.findById()`.

- [ ] **Step 1: Add the migration**

```sql
ALTER TABLE T_PAYMENT_METHOD ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE T_IDENTIFICATION ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE T_IDENTIFICATION_IMAGE ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;
ALTER TABLE T_KYC_CASE ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;

CREATE INDEX I_PAYMENT_METHOD_tenant ON T_PAYMENT_METHOD(tenant_id);
CREATE INDEX I_IDENTIFICATION_tenant ON T_IDENTIFICATION(tenant_id);
CREATE INDEX I_IDENTIFICATION_IMAGE_tenant ON T_IDENTIFICATION_IMAGE(tenant_id);
CREATE INDEX I_KYC_CASE_tenant ON T_KYC_CASE(tenant_id);
```

- [ ] **Step 2: Write the failing cross-tenant isolation test**

```sql
-- modules/ndopify-server/src/test/resources/db/test/party/PartyChildTenantIsolationEndpoint.sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (100, 1, 'tenant1@example.com', 'Tenant', 'One', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (200, 2, 'tenant2@example.com', 'Tenant', 'Two', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_PAYMENT_METHOD(id, tenant_id, party_id, number, type, status, created_at, modified_at) VALUES
    ('pm-100', 1, 100, '+237671111111', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('pm-200', 2, 200, '+237672222222', 1, 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION(id, tenant_id, party_id, type, issuing_country_code, status, created_at, modified_at) VALUES
    ('id-100', 1, 100, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('id-200', 2, 200, 1, 'CM', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_IDENTIFICATION_IMAGE(id, tenant_id, identification_id, image_type, storage_type, path, mime_type, created_at, uploaded_at) VALUES
    ('img-100', 1, 'id-100', 1, 1, 'identifications/id-100/images/img-100.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01'),
    ('img-200', 2, 'id-200', 1, 1, 'identifications/id-200/images/img-200.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:01');

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, identification_id, payment_method_id, status, created_at, modified_at) VALUES
    ('case-100', 1, 100, 'id-100', 'pm-100', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('case-200', 2, 200, 'id-200', 'pm-200', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

```kotlin
package com.wutsi.ndopify.party.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetIdentificationResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.GetPaymentMethodResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/PartyChildTenantIsolationEndpoint.sql"])
class PartyChildTenantIsolationEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun `payment method from another tenant is not found`() {
        val response = rest.getForEntity("/v1/payment-methods/pm-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PAYMENT_METHOD_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `payment method from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/payment-methods/pm-100", GetPaymentMethodResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("pm-100", response.body!!.paymentMethod.id)
    }

    @Test
    fun `identification from another tenant is not found`() {
        val response = rest.getForEntity("/v1/identifications/id-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `identification from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/identifications/id-100", GetIdentificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("id-100", response.body!!.identification.id)
    }

    @Test
    fun `identification image from another tenant is not found`() {
        val response = rest.getForEntity("/v1/identifications/images/img-200/url", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `kyc case from another tenant is not found`() {
        val response = rest.getForEntity("/v1/kyc/cases/case-200", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `kyc case from the caller's own tenant is found`() {
        val response = rest.getForEntity("/v1/kyc/cases/case-100", GetKycCaseResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("case-100", response.body!!.case.id)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=PartyChildTenantIsolationEndpointTest`
Expected: FAIL on all three "from another tenant is not found" cases — today `pm-200`, `id-200`, `img-200`, and `case-200` are all reachable from tenant 1's context, since none of these four entities carry `@TenantId`.

- [ ] **Step 4: Add `@TenantId` to the four entities**

`PaymentMethodEntity.kt` — add alongside the existing `@Id`:

```kotlin
    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @org.hibernate.annotations.TenantId
    val tenantId: Long? = null,
```

Apply the identical field (same import, same comment, same position right after `@Id`) to `IdentificationEntity.kt`, `IdentificationImageEntity.kt`, and `KycCaseEntity.kt`. Use a proper top-level import (`import org.hibernate.annotations.TenantId`) in each file rather than the fully-qualified form shown above once editing for real.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=PartyChildTenantIsolationEndpointTest`
Expected: PASS, all six tests green.

- [ ] **Step 6: Run the full existing party test suite to catch regressions**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetPaymentMethodEndpointTest,SearchPaymentMethodEndpointTest,GetIdentificationEndpointTest,SearchIdentificationEndpointTest,CreateIdentificationEndpointTest,GetIdentificationImageUrlEndpointTest,UploadIdentificationImageEndpointTest,GetKycCaseEndpointTest,SearchKycCaseEndpointTest,CreateKycCaseEndpointTest,VerifyKycCaseEndpointTest`
Expected: PASS — these tests all run under `TENANT_ID = 1` by default and none mix tenants in their fixtures, so adding the columns/annotations should be transparent to them.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V5_0__party-children-tenant.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/PaymentMethodEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/IdentificationEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/IdentificationImageEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/domain/KycCaseEntity.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/PartyChildTenantIsolationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/PartyChildTenantIsolationEndpoint.sql
git commit -m "scope payment method, identification, and KYC case entities to tenant via @TenantId"
```

---

### Task 2: Add `tenantId` to `AgentEntity` via `@TenantId`

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V6_0__agent-tenant.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/domain/AgentEntity.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTenantIsolationEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/agent/AgentTenantIsolationEndpoint.sql`

**Interfaces:**
- Produces: `AgentEntity.tenantId: Long?` (nullable, left `null` on construction exactly like `PartyEntity.tenantId` — Hibernate's `TenantIdGeneration` populates it from `TenantContext` on insert; passing a non-null value makes Hibernate throw if it doesn't match the resolved tenant).
- No change to `AgentService`, `AgentRepository`, or `AgentEndpoints` signatures — per the Global Constraints, isolation comes from the annotation + the existing `TenantIdentifierResolver`, the same way it already does for every `PartyEntity` query in the codebase today.

- [ ] **Step 1: Add the migration**

```sql
ALTER TABLE T_AGENT ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 AFTER id;

CREATE INDEX I_AGENT_tenant ON T_AGENT(tenant_id);
```

- [ ] **Step 2: Write the failing cross-tenant isolation test**

```sql
-- modules/ndopify-server/src/test/resources/db/test/agent/AgentTenantIsolationEndpoint.sql
INSERT INTO T_PARTY(id, tenant_id, first_name, last_name, email) VALUES
  (201, 1, 'Ada', 'T1', 'ada.t1@example.com'),
  (202, 2, 'Bob', 'T2', 'bob.t2@example.com');

INSERT INTO T_AGENT(id, tenant_id, party_id) VALUES
  (301, 1, 201),
  (302, 2, 202);
```

```kotlin
package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/AgentTenantIsolationEndpoint.sql"])
class AgentTenantIsolationEndpointTest : AbstractAgentEndpointTest() {
    @Test
    fun `search only returns agents belonging to the caller's tenant`() {
        val response = rest.getForEntity("/v1/agents", SearchAgentResponse::class.java)

        val agentIds = response.body!!.agents.map { it.id }
        assertEquals(listOf(301L), agentIds)
    }

    @Test
    fun `get by id for another tenant's agent is not found`() {
        overrideTenantId = 1L

        val response = rest.getForEntity("/v1/agents/302", GetAgentResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `get by id for the caller's own tenant succeeds`() {
        overrideTenantId = 1L

        val response = rest.getForEntity("/v1/agents/301", GetAgentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(301L, response.body!!.agent.id)
    }

    @Test
    fun `switching tenant context reveals the other tenant's agent instead`() {
        overrideTenantId = 2L

        val response = rest.getForEntity("/v1/agents", SearchAgentResponse::class.java)

        val agentIds = response.body!!.agents.map { it.id }
        assertEquals(listOf(302L), agentIds)
    }
}
```

Note: `overrideTenantId` and `TENANT_ID` already exist on `TenantAwareEndpointIntegrationTest` today (see `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/TenantAwareEndpointIntegrationTest.kt`) — no base test class changes are needed for this plan, since tenant resolution via the `X-Tenant-ID` header already works independently of the JWT/auth work that's deferred.

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTenantIsolationEndpointTest`
Expected: FAIL on `search only returns agents belonging to the caller's tenant` and `get by id for another tenant's agent is not found` — today both tenants' agents are visible/reachable from either tenant context, since `AgentEntity` has no `@TenantId`.

- [ ] **Step 4: Add `@TenantId` to `AgentEntity`**

```kotlin
package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import java.util.Date

@Entity
@Table(name = "T_AGENT")
data class AgentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Left unset (null) on construction so Hibernate's TenantIdGeneration can populate it from the current
    // tenant on insert — mirrors PartyEntity.tenantId exactly.
    @TenantId
    val tenantId: Long? = null,

    @OneToOne
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    @ElementCollection
    @CollectionTable(
        name = "T_AGENT_NEIGHBORHOOD",
        joinColumns = [JoinColumn(name = "agent_id")]
    )
    @Column(name = "neighborhood_id")
    val neighborhoodIds: List<Long> = emptyList(),

    val cityId: Long? = null,
    val whatsappNumber: String? = null,
    val agentType: AgentType = AgentType.UNKNOWN,
    val experienceLevel: ExperienceLevel = ExperienceLevel.UNKNOWN,
    val biography: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTenantIsolationEndpointTest`
Expected: PASS, all four tests green.

- [ ] **Step 6: Run the full existing agent test suite to catch regressions**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateAgentEndpointTest,GetAgentEndpointTest,SearchAgentEndpointTest,UpdateAgentEndpointTest`
Expected: PASS — these tests run under `TENANT_ID = 1` by default (from `TenantAwareEndpointIntegrationTest`), and their fixtures don't mix tenants, so adding the column/annotation should be transparent to them.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V6_0__agent-tenant.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/domain/AgentEntity.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTenantIsolationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/agent/AgentTenantIsolationEndpoint.sql
git commit -m "scope AgentEntity to tenant via @TenantId, matching PartyEntity"
```

---

### Task 3: Update `CLAUDE.md`'s Multi-Tenancy section to match reality

**Files:**
- Modify: `CLAUDE.md` (the "Multi-Tenancy — current state" section)

**Interfaces:** None — documentation only.

- [ ] **Step 1: Replace the stale description**

Rewrite the "Multi-Tenancy — current state" section to describe:
- The real mechanism: `TenantContext` (ThreadLocal) + `TenantIdentifierResolver` (Hibernate `CurrentTenantIdentifierResolver`) + `TenantContextFilter`, wired in `platform/config/TenantConfiguration.kt`.
- `@TenantId` is the actual annotation used for tenant-scoped entities (`PartyEntity`, `PaymentMethodEntity`, `IdentificationEntity`, `IdentificationImageEntity`, `KycCaseEntity`, now also `AgentEntity`) — no manual predicates anywhere. `KycVerificationEntity` is deliberately excluded (only ever reached via an already-tenant-checked `KycCaseEntity` association).
- Tenant resolution still trusts an unsigned JWT or an unauthenticated `X-Tenant-ID` header, and `SecurityConfiguration` still `permitAll()`s everything — call this out explicitly as a known, not-yet-closed gap rather than implying it's handled.
- Remove the reference to `MobileChangeEntity`/`IdentityChangeEntity` tenant-scoping unless/until those entities actually exist.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: correct multi-tenancy section in CLAUDE.md to match implementation"
```

---

## Non-Goals (explicitly out of scope for this plan)

- JWT signing (`AccessTokenService` still uses `Algorithm.none()`) and filter-chain authentication enforcement (`SecurityConfiguration` still `permitAll()`s) — deferred to a later plan.
- Per-endpoint role authorization (e.g. `@PreAuthorize("hasRole('ADMIN')")`) — depends on the authentication work above.
- Extending `@TenantId` to refdata/security entities (`TenantEntity`, `LocationEntity`, `UserEntity`, etc.) — not requested in this round.
- `MobileChangeEntity`/`IdentityChangeEntity` KYC workflow entities referenced in the pre-existing `CLAUDE.md` text — they don't exist yet; out of scope until someone actually specs that work.
