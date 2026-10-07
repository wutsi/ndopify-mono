# Plan: User ↔ Tenant ↔ Party linkage

## Context / decisions made so far

- A `User` belongs to **exactly one tenant** (no multi-tenant access for regular users).
- `UserEntity.tenantId: Long?` has already been added (`V4_1__security-user-tenant.sql`) — a **plain**
  column, not `@TenantId`, because login resolves `User` by email *before* any tenant context exists.
  `null` means the user isn't scoped to a single tenant.
- **Super-admins** access a cross-tenant `admin-console` application. Their `User.tenantId` is `null`;
  authorization for cross-tenant access comes from `UserApplicationEntity` membership in `admin-console`
  (existing mechanism — no new boolean flag needed). For these sessions the `X-Tenant-ID` header is
  honored per-request (already supported by `TenantContextFilter`'s fallback); for tenant-scoped users the
  header must be ignored and the tenant must come from `user.tenantId` instead (**not implemented yet** —
  see Out of scope).
- **Option A chosen** for the `User` ↔ `Party` relationship: `User.email` stays the global login key,
  unchanged. `Party` becomes the source of truth for the person's business-identity email, linked via a
  nullable FK. Login mechanics (`AbstractAuthenticator.findUser`, `UserRepository.findByEmailIgnoreCase`)
  are **not touched** by this plan — rejected alternative ("tenant-first login", `User.email` removed
  entirely) because `Party.email` is only unique *per tenant* (`UNIQUE(tenant_id, email)` in
  `V3_0__party.sql`), which breaks the global, pre-tenant login lookup every session depends on.
- Cascade direction: `Party.email` is authoritative → changes propagate to the linked `User.email`.
  Centralized in `PartyService` (not left to callers) so the invariant can't silently rot — accepted
  tradeoff: `party` domain now depends on `security` domain for this one case, inverting the existing
  `security → party` dependency (`UserEntity.party` FK). This is a deliberate, narrow exception, not a
  general rule change.

## Schema changes

### `V4_2__security-user-party.sql` (new migration — never edit existing ones)

```sql
ALTER TABLE T_USER ADD COLUMN party_id BIGINT REFERENCES T_PARTY(id);
ALTER TABLE T_USER ADD UNIQUE(party_id);
```

- Nullable: super-admins (and any other party-less account) have no linked `Party`.
- `UNIQUE(party_id)`: a `Party` can back at most one `User` (1:1).

## Entity changes

### `UserEntity.kt`

Add:

```kotlin
@OneToOne
@JoinColumn(name = "party_id")
val party: PartyEntity? = null,
```

Same pattern as `AgentEntity.party`.

### Simplification unlocked

`AgentEntity.party` already exists. With `User.party` added, `user ↔ party ↔ agent` is derivable without
adding a separate `AgentEntity.user` FK (an option discussed earlier and now superseded): "is this user an
agent" becomes "does `user.party` have an `AgentEntity` row" — one join, not two FKs to keep in sync. Do
**not** add `AgentEntity.user` as part of this work.

## Repository changes

### `UserRepository.kt`

Add:

```kotlin
fun findByPartyId(partyId: Long): UserEntity?
```

Used by the cascade (below) — cheap lookup since `party_id` is unique.

## Service changes — email cascade

### `PartyService.update()`

Current behavior (`party/server/service/PartyService.kt`):

```kotlin
@Transactional
fun update(party: PartyEntity, request: UpdatePartyRequest): PartyEntity {
    val email = request.email?.lowercase()
    if (email != null) {
        ensureEmailUnique(email, party.id)   // tenant-scoped uniqueness only
    }
    ...
    return dao.save(party.copy(..., email = request.email?.lowercase() ?: party.email, ...))
}
```

**New behavior**, still inside the same `@Transactional` boundary:

1. Keep the existing `ensureEmailUnique(email, party.id)` check (tenant-scoped — unchanged).
2. **New check**: if the linked `User` exists (`userRepository.findByPartyId(party.id!!)`) and the new
   email differs from `user.email`, verify no *other* `User` already owns that email
   (`userRepository.findByEmailIgnoreCase(email)`/equivalent global check). If it's taken by a different
   user, throw `ConflictException` (reuse or add a `USER_EMAIL_ALREADY_EXISTS`-style error code) **before**
   saving the party — don't let a partial update happen.
3. Save `Party` as today.
4. If a linked `User` was found and the email changed, save
   `user.copy(email = email)`.
5. All four steps share the one `@Transactional` method, so any failure rolls back both saves together.

### Why centralized here, not in callers

`AgentService.kt:157` is the only current caller of `PartyService.update()`. Putting the sync there would
work today but is fragile — any *future* caller of `PartyService.update()` would need to remember to
replicate step 2/4, or the `User`/`Party` emails silently drift. Centralizing in `PartyService` makes the
invariant impossible to bypass, at the cost of `party` depending on `security`'s `UserRepository` for this
one method.

## Files touched (summary)

| File | Change |
|---|---|
| `db/migration/common/V4_2__security-user-party.sql` | new — `party_id` column + unique constraint on `T_USER` |
| `security/server/domain/UserEntity.kt` | add nullable `party: PartyEntity?` |
| `security/server/dao/UserRepository.kt` | add `findByPartyId(partyId: Long): UserEntity?` |
| `party/server/service/PartyService.kt` | `update()` gains cross-entity uniqueness check + cascade save |
| `security/server/error` (maybe) | new conflict error code if none fits |

## Explicitly out of scope for this change

- Deriving the JWT tenant claim from `user.tenantId` instead of trusting the `X-Tenant-ID` header/claim in
  `AbstractAuthenticator`/`AccessTokenService` — agreed direction, not yet implemented.
- Branching login on `admin-console` to decide whether a session is tenant-locked or tenant-selectable —
  agreed direction, not yet implemented.
- Any flow that actually **creates** a `User` + links a `Party` together (agent onboarding) — no such
  endpoint exists in the codebase today; this plan only covers keeping an *already-linked* pair in sync.
- Removing/renaming `Application`/`UserApplication` — separate, unresolved thread; `admin-console`
  membership via the existing mechanism is still relied on for super-admin gating.

## Risks / open questions

1. **Cross-domain dependency direction**: `party` → `security` for this one method inverts the existing
   `security` → `party` relationship. Acceptable as a narrow, explicit exception, but flag it in code
   review rather than letting it set a precedent for other cross-domain calls.
2. **No linking flow exists yet** — until agent onboarding (or some admin tool) actually sets
   `User.party`, this cascade has no rows to act on. Low risk to ship ahead of that, since it's a no-op
   until `party_id` is populated, but worth sequencing the onboarding flow next.
3. **Error code choice** for the new global-email-conflict case — needs a specific `WutsiException` subtype
   decision (reuse `ConflictException` with a new error code, following the `PARTY_EMAIL_ALREADY_EXISTS`
   pattern).
