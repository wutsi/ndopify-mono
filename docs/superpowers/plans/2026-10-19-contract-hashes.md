# Contract Hashes & Signature Request Date Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store a SHA-256 `contentHash` on each contract and a SHA-256 `signatureHash` on each contract party when it
signs. Both are stored only, and never returned by the API. Also store and return the contract's
`signatureRequestedAt`, so that Partner Central stops deriving it from the deadline (Task 4).

**Architecture:** A pure `ContractHasher` (`contract/server/service/`) computes both hashes. `MandateService`
hashes the generated HTML bytes it uploads and hands the hash to `ContractService.setContent` through a new
parameter, not through the DTO. `ContractService.sign` computes the party's `signatureHash` from the contract's
`contentHash` and the signature's details, in the same save as `signed = true`.

**Tech Stack:** Kotlin, Spring Boot, JPA/Hibernate, Flyway (MySQL), commons-codec `DigestUtils` (already used by
`PasswordEncryptor`).

**Spec:** No separate spec. The decisions below come from the conversation of 2026-10-19 and are the spec.

## Decisions (spec)

- **Store only.** No field is added to any DTO in `ndopify-dto`, not even `SetContractRequest`. `ContractMapper` is
  left unchanged. The hashes must never appear in an API response.
- **`contentHash`**: SHA-256, lowercase hex (64 chars), of the exact bytes uploaded as the contract's content.
  Computed where those bytes exist: `MandateService.requestApproval` (step 2, outside any transaction).
  `POST /v1/contracts/{id}/content` (admin, URL only) sets `contentHash = null`. The server never downloads a URL to
  hash it. *(Option 1 of the conversation, the recommended one.)*
- **New content replaces the hash.** `setContent` always writes the given hash, null included, so a stale hash never
  outlives its content.
- **`signatureHash`**: SHA-256, lowercase hex, of the canonical string below, computed in `ContractService.sign`.
  Nothing verifies either hash, at signing or later.
- **Columns:** `T_CONTRACT.content_hash CHAR(64) DEFAULT NULL` and
  `T_CONTRACT_PARTY.signature_hash CHAR(64) DEFAULT NULL`, added by editing `V4_0__contract.sql` in place. This is
  allowed before launch: no database is deployed.

- **`signatureRequestedAt`**: set by each `requestSignature`, from the same instant as `signatureDeadline`
  (`deadline = requestedAt + ttl-days`), and returned in `Contract` and `ContractSummary`. Partner Central's
  `MandateRules.requestedAt` reads it instead of subtracting `ttl-days`. Partner Central's
  `ndopify.contract.signature.ttl-days` **stays**, but only for the wording of the ASSIGNED panel ("qui le signe en
  ligne sous 7 jours"), which is shown before any contract exists. A drift would show a wrong number of days in the
  text, never a wrong date.

How it fits together:

```
requestApproval ─ generate HTML ─▶ bytes (UTF-8) ─┬─▶ upload ─▶ url ──────────┐
                                                  └─▶ sha256Hex ─▶ contentHash ┴─▶ setContent ─▶ T_CONTRACT.content_hash

sign ─ party.copy(signed, ip, UA, signedAt) ─▶ sha256Hex(canonical string, incl. contract.contentHash)
                                             ─▶ T_CONTRACT_PARTY.signature_hash
```

Canonical signature string (*my call*): these fields joined with `"\n"`, a null written as `""`, in this order:

```
contract.id
contract.contentHash
party.id               (the contract party's id)
party.partyId
party.role.name
party.legalName
party.legalEmail
party.signedAt.time    (epoch millis)
party.ipAddress
party.userAgent
```

`"\n"` is safe as a separator because none of these fields can contain a newline: HTTP headers can't, and the others
are ids, names and e-mails.

## Global Constraints

- Assertions use `kotlin.test` only (`assertEquals`, `assertNull`, …), never JUnit's `Assertions`.
- Mock Spring beans with `@MockitoBean`, never `@MockBean`.
- Update entities with `copy()` of a loaded entity, never by building a new one with an existing id (`@Version`).
- Run `mvn validate` (ktlint) before each commit; `mvn antrun:run@ktlint-format` fixes the formatting.
- Endpoint tests need the local MySQL (root, no password) and Mailpit on `localhost:1025`.

## Review Focus

1. **Hash leaking into a response.** `GET /v1/contracts/{id}` and the search must not contain `contentHash` or
   `signatureHash`. Pinned in Task 1.
2. **Non-ASCII content.** French HTML with accents must be hashed from the same UTF-8 bytes that are uploaded, not
   from a second `toByteArray()` with another charset. Pinned in Tasks 1 and 3.
3. **Signing a contract whose `contentHash` is null** (content set by the admin endpoint). The signature must still
   succeed, with `""` in the canonical string. Pinned in Tasks 1 and 2.
4. **Content replaced after a first signature.** A PENDING_SIGNATURE contract can get new content. The earlier
   party's `signatureHash` keeps the old `contentHash` (it's a snapshot), while the contract's hash changes. Pinned in
   Task 3.
5. **Double sign** (`CONTRACT_PARTY_ALREADY_SIGNED`) must leave the first `signatureHash` untouched. The existing
   check runs before the save. Pinned in Task 2.

---

### Task 1: Columns, entity fields and `ContractHasher`

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/db/migration/common/V4_0__contract.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/domain/ContractEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/domain/ContractPartyEntity.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractHasher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/service/ContractHasherTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/GetContractEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/contract/GetContractEndpoint.sql`

**Interfaces:**
- Produces:
  - `ContractEntity.contentHash: String? = null`, placed right after `contentUrl`
  - `ContractPartyEntity.signatureHash: String? = null`, placed right after `signedAt`
  - `object ContractHasher` (a Kotlin `object`, since it's pure and needs no injection), with
    `fun contentHash(content: ByteArray): String` and
    `fun signatureHash(contract: ContractEntity, party: ContractPartyEntity): String`

- [ ] **Step 1: Write the failing tests** in `ContractHasherTest`:
  - `content hash` → `assertEquals(DigestUtils.sha256Hex("<p>Bail signé à Yaoundé</p>".toByteArray(Charsets.UTF_8)), ContractHasher.contentHash("<p>Bail signé à Yaoundé</p>".toByteArray(Charsets.UTF_8)))`, plus `assertEquals(64, hash.length)`.
  - `signature hash` → build `contract = ContractEntity(id = "c-1", contentHash = "abc")` and
    `party = ContractPartyEntity(id = "cp-11", partyId = 100, role = MANDATOR, legalName = "Ray Sponsible", legalEmail = "ray.sponsible@gmail.com", signedAt = Date(1718000000000), ipAddress = "192.168.1.10", userAgent = "Mozilla/5.0 (iPhone)")`.
    Assert it equals `DigestUtils.sha256Hex("c-1\nabc\ncp-11\n100\nMANDATOR\nRay Sponsible\nray.sponsible@gmail.com\n1718000000000\n192.168.1.10\nMozilla/5.0 (iPhone)")`.
  - `signature hash without content hash` → same party, `contract.contentHash = null`. Expected: the same string
    with `""` in the second line (`"c-1\n\ncp-11\n…"`).
  - `signature hash changes with the content` → `contentHash = "abc"` vs `"abd"` give different hashes.

  In `GetContractEndpointTest`, add `hashes are never returned`: set `content_hash` / `signature_hash` to 64-char
  values in the fixture's rows for the fetched contract, GET it as `String::class.java`, and
  `assertFalse(body.contains("contentHash"))` / `assertFalse(body.contains("signatureHash"))` / `assertFalse(body.contains(<the fixture hash>))`.
  Add the two columns to the fixture's `INSERT` column lists.

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd modules/ndopify-server && mvn test -Dtest=ContractHasherTest+GetContractEndpointTest`
  Expected: compilation failure (`ContractHasher`, `contentHash` unresolved).

- [ ] **Step 3: Implement**
  - In `V4_0__contract.sql`, add `content_hash CHAR(64) DEFAULT NULL` after `content_url`, and
    `signature_hash CHAR(64) DEFAULT NULL` after `T_CONTRACT_PARTY.signed_at`.
  - Add the two entity fields:

    ```kotlin
    // ContractEntity, right after contentUrl
    val contentHash: String? = null, // SHA-256 of the content's bytes (ContractHasher), null when unknown

    // ContractPartyEntity, right after signedAt
    val signatureHash: String? = null, // SHA-256 of the signature (ContractHasher), set by the signature
    ```

  - `ContractHasher.kt`:

    ```kotlin
    package com.wutsi.ndopify.contract.server.service

    import com.wutsi.ndopify.contract.server.domain.ContractEntity
    import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
    import org.apache.commons.codec.digest.DigestUtils

    /**
     * SHA-256 fingerprints of contracts, as lowercase hex (64 characters). Stored only: never in a DTO.
     *
     * A signature's hash covers, in this order, one per line (a null as an empty line): the contract's id and
     * content hash, the contract party's id, its party id, role, legal name and e-mail, when it signed (epoch
     * millis), from which IP address and user agent. Changing the order or the fields changes every hash computed
     * from then on: the stored ones can't be recomputed.
     */
    object ContractHasher {
        fun contentHash(content: ByteArray): String = DigestUtils.sha256Hex(content)

        fun signatureHash(contract: ContractEntity, party: ContractPartyEntity): String =
            DigestUtils.sha256Hex(
                listOf(
                    contract.id,
                    contract.contentHash,
                    party.id,
                    party.partyId.toString(),
                    party.role.name,
                    party.legalName,
                    party.legalEmail,
                    party.signedAt?.time?.toString(),
                    party.ipAddress,
                    party.userAgent,
                ).joinToString("\n") { it ?: "" }
            )
    }
    ```

    `DigestUtils.sha256Hex(String)` encodes the string as UTF-8, so accented names hash the same on every JVM,
    whatever its default charset.

- [ ] **Step 4: Run the tests to verify they pass**

  Run: `cd modules/ndopify-server && mvn test -Dtest=ContractHasherTest+GetContractEndpointTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "contract: content_hash and signature_hash columns, ContractHasher"
```

### Task 2: `sign` stores the party's `signatureHash`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt` (`sign`, ~line 259)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/SignContractEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/contract/SignContractEndpoint.sql`

**Interfaces:**
- Consumes: `ContractHasher.signatureHash(contract, party)`, `ContractPartyEntity.signatureHash`, `ContractEntity.contentHash` (Task 1)

- [ ] **Step 1: Write the failing tests**
  - Fixture: give `c-1` a `content_hash` (64 hex chars, for example `sha256Hex("c-1")`), and add the column to the
    `T_CONTRACT` / `T_CONTRACT_PARTY` column lists. Leave the other contracts' hashes null.
  - In `first signature`, after the existing asserts:
    `assertEquals(ContractHasher.signatureHash(contract, party), party.signatureHash)` and
    `assertNull(partyDao.findById("cp-12").get().signatureHash)`.
  - In `last signature signs the contract` (its contract has a null `content_hash`), assert every party has a
    non-null 64-char `signatureHash`, and the signer's equals `ContractHasher.signatureHash(contract, party)`.
  - In `party already signed` (`c-2` / `cp-21`, 409 `CONTRACT_PARTY_ALREADY_SIGNED`), give `cp-21` a
    `signature_hash` in the fixture and assert it's unchanged after the 409.

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd modules/ndopify-server && mvn test -Dtest=SignContractEndpointTest`
  Expected: FAIL. `signatureHash` is null.

- [ ] **Step 3: Implement.** In `sign`, build the signed party first, then hash that party, so the hash is computed
  from the very values that get stored. It's still a single save:

  ```kotlin
  // ContractService.sign, replacing the current partyDao.save(party.copy(...))
  val signed = party.copy(
      signed = true,
      ipAddress = request.ipAddress,
      userAgent = request.userAgent,
      signedAt = now,
  )
  partyDao.save(signed.copy(signatureHash = ContractHasher.signatureHash(contract, signed)))
  ```

  `contract` is the one loaded with `findByIdForUpdate` at the start of `sign`, so its `contentHash` can't change
  while the party signs: `setContent` takes the same lock.

- [ ] **Step 4: Run the tests to verify they pass**

  Run: `cd modules/ndopify-server && mvn test -Dtest=SignContractEndpointTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "contract: store each party's signature hash on signing"
```

### Task 3: `contentHash` set with the content

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt` (`setContent`, ~line 174)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt` (`requestApproval` ~line 251, `storeContent` ~425, `completeApproval` ~433)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/SetContractContentEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/contract/SetContractContentEndpoint.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/RequestMandateApprovalEndpointTest.kt` (verifies at ~147, ~250, ~293)
- Modify: `CLAUDE.md` (contract domain paragraph)

**Interfaces:**
- Consumes: `ContractHasher.contentHash(ByteArray)`, `ContractEntity.contentHash` (Task 1)
- Produces: `ContractService.setContent(id: String, request: SetContractRequest, contentHash: String? = null): ContractEntity`.
  `ContractEndpoints.setContent` keeps calling `setContent(id, request)` unchanged, so the hash is cleared.

- [ ] **Step 1: Write the failing tests**
  - `SetContractContentEndpointTest`: give the fixture's DRAFT and PENDING_SIGNATURE contracts a non-null
    `content_hash`, and give a party of the PENDING_SIGNATURE one, already signed, a `signature_hash`. In
    `draft contract` and `contract pending signature`, add `assertNull(contract.contentHash)`: a URL-only content
    clears it. In `contract pending signature`, also assert the signed party's `signatureHash` is unchanged
    (Review Focus 4).
  - `RequestMandateApprovalEndpointTest`: change both `verify(contractService).setContent("c-1"/"c-4", SetContractRequest(url = …))`
    to also pass `DigestUtils.sha256Hex(HTML.toByteArray(Charsets.UTF_8))` as the third argument. Change the
    `never()` verify (~293) to `setContent(any<String>(), any(), anyOrNull())`. Set `HTML` to a value with an accent
    (for example `"<html>Mandat de recherche à Yaoundé</html>"`) so that a charset mismatch fails the test. If the
    test reads back the stored bytes (`assertStored`), also assert `sha256Hex(storedBytes)` equals the hash passed.

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd modules/ndopify-server && mvn test -Dtest=SetContractContentEndpointTest+RequestMandateApprovalEndpointTest`
  Expected: FAIL. The old hash is kept, and `setContent` is called without a hash.

- [ ] **Step 3: Implement**
  - `ContractService.setContent`:

    ```kotlin
    /**
     * Sets the URL of the contract's document, while it can still be signed: DRAFT or PENDING_SIGNATURE. Locked like a
     * signature, so that the last party can't sign it while its document changes. [contentHash] is the caller's, of
     * the bytes it stored (ContractHasher.contentHash): null when unknown (ex: POST /v1/contracts/{id}/content), which
     * clears the previous content's.
     */
    @Transactional
    fun setContent(id: String, request: SetContractRequest, contentHash: String? = null): ContractEntity {
        val contract = findByIdForUpdate(id)
        ensureNotClosed(contract)
        return dao.save(contract.copy(contentUrl = request.url.trim(), contentHash = contentHash))
    }
    ```

  - `MandateService`: the HTML becomes bytes once, and those same bytes are both uploaded and hashed. The hash is
    computed in step 2, outside any transaction, like the upload:

    ```kotlin
    fun requestApproval(id: Long): MandateEntity {
        val contractId = tx.execute { prepareApproval(id) }
        val (version, html) = tx.execute {
            val mandate = findById(id)
            mandate.version to contractGenerator.generate(mandate, contractService.findById(contractId))
        }
        val content = html.toByteArray(Charsets.UTF_8)
        val url = storeContent(id, contractId, content)
        val contentHash = ContractHasher.contentHash(content)
        return tx.execute { completeApproval(id, contractId, version, url, contentHash) }
    }

    private fun storeContent(id: Long, contractId: String, content: ByteArray): String {
        val path = "mandates/$id/contracts/$contractId/${UUID.randomUUID()}.html"
        val storage = storageProvider.get()
        storage.store(path, ByteArrayInputStream(content), "text/html")
        return storage.publicUrl(path).toString()
    }

    // completeApproval(id, contractId, version, url, contentHash: String): unchanged, except for
    contractService.setContent(contractId, SetContractRequest(url = url), contentHash)
    ```

    Today `storeContent` calls `html.toByteArray()`, which uses the JVM's default charset. With explicit UTF-8 bytes
    shared by the upload and the hash, the hash is of exactly what's stored (Review Focus 2).
  - `CLAUDE.md`, contract domain: one sentence after the `content` description, roughly: "`contentHash` (SHA-256 of
    the uploaded bytes, set by `MandateService`, null via the endpoint) and each party's `signatureHash` (set by
    `sign`, `ContractHasher`) are stored only, never in a DTO."

- [ ] **Step 4: Run the tests to verify they pass, then the whole module**

  Run: `cd modules/ndopify-server && mvn test -Dtest=SetContractContentEndpointTest+RequestMandateApprovalEndpointTest`
  Expected: PASS.
  Run: `cd modules/ndopify-server && mvn verify`
  Expected: `BUILD SUCCESS` (tests, ktlint and the JaCoCo gate).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server CLAUDE.md
git commit -m "contract: store the content hash of mandate contracts"
```

### Task 4: `signatureRequestedAt`, stored and returned

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/db/migration/common/V4_0__contract.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/domain/ContractEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/service/ContractService.kt` (`requestSignature`, ~line 187)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/contract/server/mapper/ContractMapper.kt` (both mappings, ~lines 22 and 37)
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/contract/dto/Contract.kt`, `ContractSummary.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/contract/server/endpoint/RequestContractSignatureEndpointTest.kt`, `GetContractEndpointTest.kt`, `SearchContractEndpointTest.kt`, plus their fixtures under `src/test/resources/db/test/contract/`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/RequestMandateApprovalEndpointTest.kt` (only if it stops mocking `requestSignature`; otherwise unchanged)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt` (~line 69), `MandateViews.kt` (~line 50)
- Modify: `modules/ndopify-partner-central/src/main/resources/application.yml` (comment on `ttl-days`, ~line 63)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRulesTest.kt` (~line 68), `MandateViewsTest.kt` (contract fixtures)
- Modify: `CLAUDE.md`

**Interfaces:**
- Produces:
  - `ContractEntity.signatureRequestedAt: Date? = null`, right before `signatureDeadline`, plus column
    `signature_requested_at DATETIME DEFAULT NULL` before `signature_deadline`
  - `Contract.signatureRequestedAt: Date? = null` and `ContractSummary.signatureRequestedAt: Date? = null`, each
    right before `signatureDeadline`
  - `MandateRules.requestedAt(contract: Contract?): Date?`. The `ttlDays` parameter is removed.
- Unchanged: `MandateViews.build(…, ttlDays: Int)` and `MandateController`'s `@Value("\${ndopify.contract.signature.ttl-days}")`.
  They still feed `MandateView.signatureTtlDays`, used for the panels' wording.

- [ ] **Step 1: Write the failing tests (ndopify-server)**
  - `request signature`: `assertNotNull(contract.signatureRequestedAt)` and
    `assertEquals(contract.signatureRequestedAt!!.time + Duration.ofDays(7).toMillis(), contract.signatureDeadline!!.time)`.
  - `request signature again resets the deadline`: the fixture's earlier `signature_requested_at` is replaced by one
    `>` it, still exactly 7 days before the new deadline.
  - `GetContractEndpointTest` / `SearchContractEndpointTest`: put a `signature_requested_at` in the fixtures of the
    contracts that have a deadline. Assert the returned value equals it (`Contract` and `ContractSummary`), and that
    it's null where the deadline is null (`contracts[0]` in search, line ~36).

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd modules/ndopify-dto && mvn install -q && cd ../ndopify-server && mvn test -Dtest='RequestContractSignatureEndpointTest+GetContractEndpointTest+SearchContractEndpointTest'`
  Expected: compilation failure (`signatureRequestedAt` unresolved).

- [ ] **Step 3: Implement (ndopify-server + dto)**
  - Add the column, entity field and DTO fields, and map them in both `ContractMapper` functions.
  - `requestSignature`: `val now = Date(clock.millis())`, then
    `copy(status = PENDING_SIGNATURE, signatureRequestedAt = now, signatureDeadline = Date(now.time + Duration.ofDays(signatureTtlDays).toMillis()))`.
    Read `now` inside `requestSignature`, after the caller's save (Review Focus 6).
  - `mvn install` `ndopify-dto` so that Partner Central compiles against the new field.

- [ ] **Step 4: Run the ndopify-server tests to verify they pass**

  Run: same command as Step 2.
  Expected: PASS.

- [ ] **Step 5: Write the failing tests (Partner Central)**
  - `MandateRulesTest.requestedAt`:
    `assertEquals(date("2026-10-15T10:00:00Z"), MandateRules.requestedAt(Contract(signatureRequestedAt = date("2026-10-15T10:00:00Z"), signatureDeadline = date("2026-10-30T10:00:00Z"))))`.
    The deadline is deliberately not 7 days later, which proves the TTL isn't used. Also
    `assertNull(MandateRules.requestedAt(Contract(signatureDeadline = date("2026-10-22T10:00:00Z"))))`: no request
    date means no date, never a computed one. And `assertNull(MandateRules.requestedAt(null))`.
  - `MandateViewsTest`: give the PENDING / APPROVED contract fixtures
    `signatureRequestedAt = date("2026-10-15T…")`, so that `pending approval` still expects `"15 oct. 2026"`.
    Add `editedSince` false when `mandate.modifiedAt == signatureRequestedAt` (already covered in
    `MandateRulesTest` lines ~78-82, keep it).

- [ ] **Step 6: Run them to verify they fail**

  Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateRulesTest+MandateViewsTest'`
  Expected: compilation failure (`requestedAt` arity).

- [ ] **Step 7: Implement (Partner Central)**
  - `MandateRules.requestedAt(contract: Contract?): Date? = contract?.signatureRequestedAt`. Rewrite its KDoc: the
    contract says when it was last sent. Drop the "must match ndopify-server's" wording. If `DAY` is no longer used,
    remove it.
  - `MandateViews.build`: `MandateRules.requestedAt(contract)`. `ttlDays` stays for `signatureTtlDays`.
  - `application.yml`: replace the comment above `ttl-days` with: only the panels' wording («sous 7 jours»), shown
    before any contract exists; the dates come from the contract.

- [ ] **Step 8: Run the tests to verify they pass, then both modules**

  Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateRulesTest+MandateViewsTest'`
  Expected: PASS.
  Run: `cd <repo root> && mvn install -Dheadless=true`
  Expected: `BUILD SUCCESS` (all modules, ktlint, JaCoCo).

- [ ] **Step 9: Update `CLAUDE.md`**
  - Contract domain, `request-signature` sentence: "…to `PENDING_SIGNATURE` with `signatureRequestedAt` = now and
    `signatureDeadline` = now + `ndopify.contract.signature.ttl-days` (7)…".
  - Partner Central section: replace "«Envoyé le» is derived (`MandateRules.requestedAt`): the contract's
    `signatureDeadline` less `ndopify.contract.signature.ttl-days` (partner-central's `application.yml`), which must
    match ndopify-server's setting of the same name." with: "«Envoyé le» is the contract's `signatureRequestedAt`
    (`MandateRules.requestedAt`); partner-central's `ndopify.contract.signature.ttl-days` only words the panels
    («sous 7 jours») and should match ndopify-server's."

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-dto modules/ndopify-server modules/ndopify-partner-central CLAUDE.md
git commit -m "contract: store and return signatureRequestedAt; Partner Central reads it"
```
