# Plan: Global users with tenant memberships

## Context

Today `UserEntity` carries a plain (non-`@TenantId`) `tenantId` — the user's *home* tenant — where `null` means
"every tenant" (super-admins on `admin-console`), and a one-to-one `party`. Problems:

- **`null` as a wildcard fails open**: a lost/forgotten tenant id grants access to all tenants.
- **No "2 of N tenants"**: a user is in one tenant or all of them.
- **`User.party` is 1:1 but `Party` is per tenant** (`UNIQUE(tenant_id, email)`), so a user in two tenants
  can't have two parties.
- Tenant isolation for users is hand-rolled (`UserRepository.isVisible`, `AbstractAuthenticator.ensureTenantValid`,
  `OtpMailet.resolveTenant` fallback) instead of relying on Hibernate `@TenantId`.

Rejected alternative: make `UserEntity` itself `@TenantId` (one user per tenant). Cheaper now, but a person in two
tenants becomes two accounts with two credential sets, and moving to multi-tenant later means merging users by
e-mail.

## Decision

Split **identity** (global) from **membership** (tenant-scoped):

- `User` = who you are (global, e-mail unique). `AuthFactor` = how you prove it (global).
- `Membership` = which tenant you belong to, with which party and which app roles (`@TenantId`).
- Every session is scoped to exactly one tenant: the JWT carries one `tenant_id`; switching tenant = new token.
- **No null-tenant wildcard.** For now, a super-admin who needs several tenants gets one membership per tenant.

## Target model

```
T_USER              global      id, email UNIQUE, deleted, …        (tenant_id and party_id dropped)
T_AUTH_FACTOR       global      unchanged
T_MEMBERSHIP        @TenantId   id, tenant_id NOT NULL, user_id, party_id NULL, deleted, created_at
                                UNIQUE(tenant_id, user_id), UNIQUE(party_id)
T_USER_APPLICATION  via membership   membership_id replaces user_id → roles are per tenant
                                UNIQUE(membership_id, application_id)
```

`T_USER_APPLICATION` is not `@TenantId` itself: it's only reached through an already-filtered membership (same
reasoning as `KycVerificationEntity`).

## 1. Migrations (expand → contract)

Versioned `V7_x` (not `V3_x`): `V6_0` is already applied and Flyway doesn't run out-of-order migrations.

### `V7_0__security-membership.sql` — expand (additive, safe to roll back)

```sql
CREATE TABLE T_MEMBERSHIP(
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id              BIGINT NOT NULL,
  user_id                BIGINT NOT NULL,
  party_id               BIGINT,
  deleted                BOOLEAN NOT NULL DEFAULT false,
  created_at             DATETIME DEFAULT NOW(),

  UNIQUE(tenant_id, user_id),
  UNIQUE(party_id),

  CONSTRAINT FK_MEMBERSHIP_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  CONSTRAINT FK_MEMBERSHIP_user   FOREIGN KEY(user_id)   REFERENCES T_USER(id),
  CONSTRAINT FK_MEMBERSHIP_party  FOREIGN KEY(party_id)  REFERENCES T_PARTY(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

INSERT INTO T_MEMBERSHIP(tenant_id, user_id, party_id, created_at)
  SELECT tenant_id, id, party_id, created_at FROM T_USER WHERE tenant_id IS NOT NULL;

ALTER TABLE T_USER_APPLICATION ADD COLUMN membership_id BIGINT;   -- + FK, UNIQUE(membership_id, application_id)
ALTER TABLE T_USER_APPLICATION MODIFY user_id BIGINT NULL;        -- no longer written
UPDATE T_USER_APPLICATION ua JOIN T_MEMBERSHIP m ON m.user_id = ua.user_id
  SET ua.membership_id = m.id;
```

### `V7_1__security-membership-cleanup.sql` — contract (once V7_0 is live)

- Handle null-tenant users' `T_USER_APPLICATION` rows (delete, or create memberships first — see decision 3).
- `T_USER_APPLICATION`: `membership_id` → `NOT NULL`, add its FK + `UNIQUE(membership_id, application_id)`; drop
  `FK_USER_APPLICATION_user`, the `user_id` unique key and `user_id`.
- `T_USER`: drop `FK_USER_party`, `FK_USER_tenant`, `I_USER_tenant`, `party_id`, `tenant_id`.
- Unnamed unique keys get MySQL-generated index names — check them with `SHOW INDEX` before writing the `DROP`s.

## 2. Entities

- **New** `security/server/domain/MembershipEntity.kt`: `@TenantId val tenantId: Long? = null` (left null so
  Hibernate's `TenantIdGeneration` fills it in), `@ManyToOne user`, `@OneToOne party: PartyEntity?`, `deleted`,
  `createdAt`.
- `UserEntity`: remove `tenantId` and `party` — identity only.
- `UserApplicationEntity`: `user: UserEntity` → `membership: MembershipEntity`.

## 3. Repositories & services

- **New** `MembershipRepository`: `findByUser(user)`, `findByPartyId(partyId)` — both tenant-filtered by Hibernate.
- **New** `MembershipService`: `findByUserOrNull`, `findByPartyIdOrNull`, `create(user, party?)`,
  `grantAccess(membership, appCode)` (moved from `UserService`).
- `UserRepository`: delete `isVisible` and `findByPartyId`.
- `UserService`:
  - `findByIdOrNull`: returns the user only if it has a membership in the current tenant → `GET /v1/users/{id}`
    keeps returning 404 `USER_NOT_FOUND` across tenants.
  - `create(party)` → `findByEmailOrCreate(email)`: reuse a user who already exists in another tenant.
- `UserApplicationRepository` / `UserApplicationService`: `findByUserAndApplication` →
  `findByMembershipAndApplication`.

## 4. Authentication — `AbstractAuthenticator`

```kotlin
requireTenant()                                             // 400 HTTP_MISSING_PARAMETER X-Tenant-ID
val user = findUser(request.email)                          // global
val membership = membershipService.findByUserOrNull(user)   // tenant-filtered
    ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
val userApplication = checkApplicationAccess(membership, application)
…
tokenService.create(…, tenantId = membership.tenantId, …)
```

- Delete `ensureTenantValid` — the membership lookup replaces it.
- `GoogleOneTapAuthenticator` creates the user + app access on the fly today; see decision 1 for the membership.

## 5. OTP

- `OtpService.create`: require a tenant and a membership in it (else 404 `USER_NOT_FOUND`), so a tenant-A user
  can't trigger tenant-B-branded codes.
- `OtpMailet`: drop the `?: user.tenantId` fallback in `resolveTenant`; the recipient's name comes from
  `membershipService.findByUserOrNull(user)?.party` (the listener already sets `TenantContext`).

## 6. Other domains

- `AgentService.create`: `userService.findByEmailOrCreate(party.email)` → `membershipService.create(user, party)` →
  `grantAccess(membership, PARTNER_CENTRAL)`.
- `PartyService.update`: the linked-user lookup (`userDao.findByPartyId`) goes through the membership; the
  e-mail sync is revisited (decision 2).
- `UserMapper`: `party` comes from the current tenant's membership. **The `User` DTO is unchanged**, so
  partner-central (`CurrentParty` → `GET /v1/users/{id}`) needs no change.

## 7. Tests & docs

- Security fixtures (`src/test/resources/db/test/security/*.sql`): add `T_MEMBERSHIP` rows, move
  `T_USER_APPLICATION` to `membership_id`, drop `T_USER.tenant_id` / `party_id`. Check that the default user of
  `AuthorizationAwareEndpointIntegrationTest` (user `11`, tenant `1`) has a membership; add `T_MEMBERSHIP` to
  `clean.sql` before `T_USER`.
- New cases:
  - the same e-mail logs into tenants 1 and 2 and gets a token for each, with different roles;
  - a user with a membership in tenant 2 only is denied in tenant 1;
  - a missing tenant → 400;
  - an OTP request from a non-member → 404;
  - `GET /v1/users/{id}` returns the current tenant's party;
  - registering an agent whose e-mail exists in another tenant reuses that user.
- Remove the `all-tenants@gmail.com` cases and the OTP home-tenant fallback test.
- Update `OtpMailetTest`, `KycMailetTest`, `AccessTokenServiceTest`.
- `CLAUDE.md`: rewrite the security / multi-tenancy paragraphs (`MembershipEntity` in the `@TenantId` list; remove
  the "home tenant" and `isVisible` text).

## Decisions (taken: the proposed defaults below)

1. **Google One Tap auto-join** — should signing in with Google create a membership in any tenant?
   *Proposed:* no; deny unless a membership exists. Sign-up (join wizard / invitation) is the only way to create
   one.
2. **Login e-mail vs party e-mail** — *proposed:* decouple. Party e-mail is per-tenant contact data; user e-mail is
   the global login. Changing the login e-mail becomes a separate, later user-level operation. Keeping the sync
   would let an edit in one tenant change how the person logs into every tenant.
3. **Existing null-tenant users (super-admins)** — *proposed:* delete their app rows in V7_1 (or give them one
   membership per tenant first if they're still needed).

## PR slices

1. **PR 1** — V7_0 migration, entities, membership repository/service, authentication, OTP, agent creation,
   mapper and tests, all reading the new tables; old columns left in place.
2. **PR 2** — V7_1 migration (drop old columns) + `CLAUDE.md` update.

## Out of scope

- Signing JWTs (still `Algorithm.none()`) and enforcing authentication in `SecurityConfiguration`.
- Cross-tenant platform roles for `admin-console` (explicit platform scope, not a null tenant).
- Changing a user's login e-mail.
