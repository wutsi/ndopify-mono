# AI extraction of identity and ownership documents — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An uploaded identity or ownership document of a mandate is read in the background by the vision LLM, stored
as typed, versioned JSON, and exposed as generic `fields` plus actionable problem codes, or ends `FAILED` with what the
agent must fix.

**Architecture:** A new extraction sub-package of the mandate domain in `ndopify-server`
(`mandate/server/service/document/`): a versioned sealed `DocumentData` per handled `DocumentType`, one extractor per
document family on `ChatClients.VISION` (identity reuses the KYC identification prompt/schema), a pure checks object
for the problem codes, and `MandateDocumentExtractionService` (claim under a lock, storage I/O and LLM call outside any
transaction, save under `@Version`), driven by `MandateDocumentJobs.extract` and two `@OpsEndpoint`s. No migration and no
DTO change: #328's `T_MANDATE_DOCUMENT` columns and `MandateDocument` / `DocumentField` carry everything.

**Tech Stack:** Kotlin, Spring Boot, Spring AI (`ChatClients.VISION`), Spring Data JPA / Hibernate (`@TenantId`,
`@Version`, pessimistic lock), Jackson, MySQL 8, JUnit + `kotlin.test` + mockito-kotlin, `TestRestTemplate`.

**Spec:** GitHub issue #330 (enriched body: *Acceptance criteria*, *Decisions and assumptions*), epic #327. #328's plan
(`docs/superpowers/plans/328-server-mandate-documents-record-private-storage-upload-read.md`) defines the document
record, endpoints and columns this plan builds on.

**#333's plan, already on `main`,** was written against an `ExtractedField<T>` shape. Its own *Global Constraints* say
the merged #330 code wins; this plan's flat nullable properties + `fieldConfidences` (and the `issuedAt()` /
`expiresAt()` hooks it shares) are that code, and #333's implementer adapts its three subclasses to it.

## Global Constraints

- **Dependency:** #328 is merged first. Use its `MandateDocumentEntity`, `MandateDocumentPageEntity`,
  `MandateDocumentService`, `MandateDocumentRepository`, `MandateDocumentPageRepository`, `MandateDocumentMapper`,
  `MandateDocumentEndpoints`, `V19_0__mandate-document.sql`, `T_MANDATE_DOCUMENT.data / data_version / problem_codes /
  issued_at / expires_at / status`, and the released `ndopify-dto` (`DocumentType`, `DocumentStatus`, `DocumentField`,
  `MandateDocument`). No migration, no DTO edit, no new error code.
- `UI: no`. `Shared code: none`.
- **Handled types:** `NATIONAL_ID`, `PASSPORT`, `LAND_TITLE`, `CERTIFICATE_OF_OWNERSHIP`, `SURVEY_PLAN`,
  `POWER_OF_ATTORNEY`, `LEASE_CONTRACT`, `UTILITY_BILL`, `TAX_RECEIPT`, `OTHER`. `UNKNOWN`, `PAY_SLIP`,
  `BANK_PRE_APPROVAL`, `PROOF_OF_FUNDS` have no extractor: the job skips them and they stay `UPLOADED` (#333).
- **Statuses:** `UPLOADED → EXTRACTING → EXTRACTED | FAILED`. `EXTRACTED` carries only the warning
  `DOCUMENT_EXPIRED`; any other code makes the document `FAILED`. No `EXPIRED` status (#332 is closed).
- **Problem codes** (plain uppercase strings, stored in `problem_codes`, this order): `DOCUMENT_TYPE_MISMATCH`,
  `DOCUMENT_UNREADABLE`, `DOCUMENT_PAGE_MISSING`, `DOCUMENT_FIELD_LOW_CONFIDENCE`, `EXPIRY_DATE_UNREADABLE`,
  `EXTRACTION_FAILED`, warning `DOCUMENT_EXPIRED` last.
- **Required fields** (a missing value or a confidence `< min-confidence` is `DOCUMENT_FIELD_LOW_CONFIDENCE`):
  - `NATIONAL_ID`, `PASSPORT`: `number`, `firstName`, `lastName`; `requiresExpiry` (else `EXPIRY_DATE_UNREADABLE`);
    sides `FRONT` + `BACK` for `NATIONAL_ID`, `FRONT` for `PASSPORT` (else `DOCUMENT_PAGE_MISSING`).
  - `LAND_TITLE`, `CERTIFICATE_OF_OWNERSHIP`: `titleNumber`, `owners`, `locality`.
  - `SURVEY_PLAN`: `parcelReference`, `locality`.
  - `POWER_OF_ATTORNEY`: `grantor`, `attorney`, `purpose`.
  - `LEASE_CONTRACT`, `UTILITY_BILL`, `TAX_RECEIPT`: `holderName`, `issueDate`, at least one of `address` / `locality`.
  - `OTHER`: `summary`.
  Everything else optional (see the field keys below).
- **Field keys** (the contract with #331): identity `number`, `firstName`, `lastName`, `dateOfBirth`, `issueDate`,
  `expiryDate`, `countryCode`; title `titleNumber`, `owners`, `department`, `arrondissement`, `locality`, `surface`,
  `issueDate`; survey plan `parcelReference`, `locality`, `surface`, `coordinates`; procuration `grantor`, `attorney`,
  `purpose`, `notary`, `issueDate`, `expiryDate`; occupancy `holderName`, `address`, `locality`, `provider`,
  `issueDate`; other `title`, `summary`. Values are display-ready strings: ISO dates, a surface in m² as a number,
  `owners` joined with ", ". `detectedType`, `valid`, `invalidityReason`, `sidesPresent`, the overall confidence and the
  confidence map are internal and never exposed as fields.
- **Dates:** the AI returns `yyyy-MM-dd`; `issued_at` is the issue date (titles, occupancy) or the procuration's date;
  `expires_at` is the ID/passport expiry or the procuration's end date. An unparseable date is null; an unparseable
  required expiry is `EXPIRY_DATE_UNREADABLE`. An expiry strictly before today on the tenant's clock and time zone is
  `DOCUMENT_EXPIRED`, with the document `EXTRACTED`.
- **Config:** `ndopify.mandate.document.extract.cron` (`0 */15 * * * ?`), `batch-size` 100, `max-attempts` 3,
  `min-confidence` 0.7, `in-progress-timeout-minutes` 15. In `application-qa.yml`, the cron is `-` (tests call the job).
- **Retries:** at most `max-attempts` attempts per document in one run, technical failures only; an unreadable answer
  (valid JSON, `valid=false`) is never retried. An `EXTRACTING` document older than the timeout is reset to `UPLOADED`.
- **Transactions / locking:** the claim and every save lock the document (`@Lock(PESSIMISTIC_WRITE)` JPQL, so Hibernate's
  tenant filter still applies). The storage download and the LLM call run outside any transaction. A page change during
  the extraction bumps the version: the extraction's save fails and is skipped, the document stays `UPLOADED`.
- **Clearing:** entering `EXTRACTING` clears `data`, `data_version`, `issued_at`, `expires_at`, `problem_codes`;
  `FAILED` keeps only its codes; `EXTRACTED` stores the JSON (`data_version` 1), the dates and the warning.
- **Privacy:** never log an extracted value. Log only ids, type, status, problem codes and counts — never an exception
  message (it may carry the model's output).
- **Testing:** `kotlin.test` assertions, mockito-kotlin, one unit test per class; endpoint tests against MySQL with
  `@Sql(clean.sql + fixture)`, mocking `StorageServiceProvider` / `DocumentDataExtractorProvider`; the full gate is
  `cd modules/ndopify-server && mvn verify`.
- Code, comments, KDoc, codes and keys in English. No user-facing text in this story.

## Review Focus

1. **A page replaced while the extractor runs** — the stale extraction must not overwrite the new page: the save is
   skipped on the `@Version` conflict and the document stays `UPLOADED` for the next run.
2. **Required vs optional confidence** — a required field at 0.6 fails the document (`DOCUMENT_FIELD_LOW_CONFIDENCE`);
   an optional field at 0.4 is still `EXTRACTED` and keeps its confidence in `fields`.
3. **Expiry edge cases** — an expired-but-readable ID is `EXTRACTED` + `DOCUMENT_EXPIRED` (not `FAILED`); an unreadable
   expiry is `FAILED` + `EXPIRY_DATE_UNREADABLE` (and not the generic field code twice).
4. **A financial document** — `PAY_SLIP`, `BANK_PRE_APPROVAL`, `PROOF_OF_FUNDS` are skipped and stay `UPLOADED`; the
   type-wide ops endpoint answers 400 for them, and never touches an `EXTRACTING` document of a valid type.
5. **A malformed or older stored payload** — `GET` still returns 200 with empty `fields`, never a 500; an older
   `data_version` or an unknown extra field reads without error.

---

### Task 1: `DocumentData`, the codec and the JPA JSON helper

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentData.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentDataCodec.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/JsonHelper.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentDataCodecTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/util/jpa/JsonHelperTest.kt`

**Interfaces — Produces:**
- `sealed class DocumentData` with `abstract val detectedType: String?`, `abstract val valid: Boolean`,
  `abstract val confidence: Double?`, `abstract val fieldConfidences: Map<String, Double>`,
  `abstract fun toFields(): Map<String, String?>`, `abstract fun issuedAt(): LocalDate?`,
  `abstract fun expiresAt(): LocalDate?`, and `companion object { const val VERSION = 1 }`, annotated
  `@JsonIgnoreProperties(ignoreUnknown = true)`.
  `issuedAt()` / `expiresAt()` parse the subclass's own `issueDate` / `expiryDate` string (`yyyy-MM-dd`, null when
  blank or unparseable), so a later type with a derived date (ex: #333's pay month) overrides them instead of changing
  the service.
  Subclasses, all `data class`, every property defaulted; the `List` / `Map` properties carry
  `@param:JsonSetter(nulls = Nulls.AS_EMPTY)` like `KycInfo`, so a null answer reads as empty:
  - `NationalId` / `Passport`: `detectedType`, `number`, `firstName`, `lastName`, `dateOfBirth`, `issueDate`,
    `expiryDate`, `countryCode`, `sidesPresent: List<String>`, `valid`, `invalidityReason`, `confidence`,
    `fieldConfidences`.
  - `LandTitle` / `CertificateOfOwnership`: `detectedType`, `titleNumber`, `owners: List<String>`, `department`,
    `arrondissement`, `locality`, `surface`, `issueDate`, `valid`, `invalidityReason`, `confidence`, `fieldConfidences`.
  - `SurveyPlan`: `detectedType`, `parcelReference`, `locality`, `surface`, `coordinates`, the common tail.
  - `PowerOfAttorney`: `detectedType`, `grantor`, `attorney`, `purpose`, `notary`, `issueDate`, `expiryDate`, the
    common tail.
  - `LeaseContract` / `UtilityBill` / `TaxReceipt`: `detectedType`, `holderName`, `address`, `locality`, `provider`,
    `issueDate`, the common tail.
  - `Other`: `detectedType`, `title`, `summary`, `valid`, `invalidityReason`, `confidence`, `fieldConfidences`.
  `toFields(): LinkedHashMap<String, String?>` returns the display keys in the order above, excluding the common tail
  and `sidesPresent`; `owners` is `joinToString(", ")`, nulls stay null.
- `@Component class JsonHelper(private val mapper: JsonMapper)` (`tools.jackson.databind.json.JsonMapper`, the
  Spring Boot 4 mapper the other JSON code uses) with `fun <T> read(json: String?, clazz: Class<T>): T?` (null for
  blank) and `fun write(value: Any?): String?` (null for null). KDoc: the helper of a
  `@JdbcTypeCode(SqlTypes.JSON)` column, which stays a `String`; the domain reads it with its own class.
- `@Service class DocumentDataCodec(private val json: JsonHelper)` with
  `fun write(data: DocumentData): String`, `fun read(json: String?, type: DocumentType, dataVersion: Int?): DocumentData?`
  (null for a type without a data class, a blank payload or malformed JSON; `dataVersion` is accepted for future
  readers and does not change the current, additive reading), and `fun version(): Int = DocumentData.VERSION`.

- [ ] **Step 1: Write the failing tests**
  - `JsonHelperTest`: `write`/`read` a small data class round-trips; `read(null)` and `read("  ")` are null.
  - `DocumentDataCodecTest`:
    - `write` then `read` each of the 10 subclasses with every field set; `assertEquals` the data class.
    - `read` a `NationalId` JSON missing `dateOfBirth` → `null` there, the others kept.
    - `read` a `NationalId` JSON with `"unknown": 1` → no exception.
    - `read(..., dataVersion = 0)` of a payload with only `number`/`valid` → the other fields null.
    - `read` for `UNKNOWN`, `PAY_SLIP` and a malformed payload → null.
    - `NationalId(...).toFields()` keys are exactly `number, firstName, lastName, dateOfBirth, issueDate, expiryDate,
      countryCode` in that order, no `detectedType`/`valid`/`confidence`/`fieldConfidences`/`sidesPresent`.
    - `NationalId(...).issuedAt()` / `expiresAt()` parse the date strings; an unparseable one is null; a `LandTitle`
      has `issuedAt` and no `expiresAt`; a `LandTitle(owners = listOf("A", "B")).toFields()["owners"] == "A, B"`;
      dates stay ISO strings.
- [ ] **Step 2:** Run `cd modules/ndopify-server && mvn test -Dtest='DocumentDataCodecTest,JsonHelperTest'` — Expected:
  FAIL (classes missing).
- [ ] **Step 3:** Add `DocumentData.kt`, `JsonHelper.kt`, `DocumentDataCodec.kt` as specified.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): add the mandate document extraction data and JSON helper` with `Refs #330`.

### Task 2: Share the identity schema with KYC and map it

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentDataExtractor.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentExtractionException.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/IdentityDocumentDataExtractor.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/kyc/KycInfo.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/kyc/KycInfoExtractorIdentification.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/IdentityDocumentDataExtractorTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/kyc/KycInfoExtractorIdentificationTest.kt`
  (extend)

**Interfaces — Produces:**
- `interface DocumentDataExtractor { val types: Set<DocumentType>; fun extract(type: DocumentType, images: List<File>): DocumentData }`
- `class DocumentExtractionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)`
- `@Service class IdentityDocumentDataExtractor(private val kyc: KycInfoExtractorIdentification) : DocumentDataExtractor`
  with `types = setOf(NATIONAL_ID, PASSPORT)`; `extract` calls `kyc.extract(images)`, throws
  `DocumentExtractionException("empty identity extraction")` when the answer is the empty default
  (`type == UNKNOWN`, blank `number`/`firstName`/`lastName`, no `invalidityReason`), else maps to `NationalId` or
  `Passport`: `detectedType = info.type.name`, `valid`, `confidence`, `fieldConfidences = info.fieldConfidences`,
  `sidesPresent = info.sidesPresent`, the strings, and `expiryDate` as `info.expiryDate` formatted `yyyy-MM-dd` in UTC.
- `KycInfo` gains `dateOfBirth: String? = null`, `issueDate: String? = null`, `sidesPresent: List<String> = emptyList()`,
  `fieldConfidences: Map<String, Double> = emptyMap()` (setter `Nulls.AS_EMPTY` for the list/map), and
  `@JsonIgnoreProperties(ignoreUnknown = true)`.
- `KycInfoExtractorIdentification.PROMPT` asks for `dateOfBirth`, `issueDate`, `sidesPresent` (the sides visible among
  the provided images: `FRONT`, `BACK`) and `fieldConfidences` (one 0..1 confidence per extracted field), and its JSON
  example shows them.

**Consumes:** `KycInfoExtractorIdentification` (existing), Task 1's `DocumentData`.

- [ ] **Step 1: Write the failing tests**
  - Extend the `national_id - CM` test's JSON with `"dateOfBirth": "1990-05-01"`, `"issueDate": "2020-01-12"`,
    `"sidesPresent": ["FRONT", "BACK"]`, `"fieldConfidences": {"number": 0.9, "firstName": 0.8}` and assert them; keep
    the existing assertions (an answer without the new keys still works).
  - `IdentityDocumentDataExtractorTest` (mock `KycInfoExtractorIdentification`):
    - `NATIONAL_ID` with all fields → a `NationalId` with the number/names, `dateOfBirth`, `issueDate`,
      `expiryDate` `"2032-01-12"`, `countryCode`, `sidesPresent`, `detectedType == "NATIONAL_ID"`, `valid`, the map.
    - `PASSPORT` → a `Passport`.
    - `KycInfo()` (empty, no reason) → `assertFailsWith<DocumentExtractionException>`.
    - `KycInfo(type = DRIVER_LICENSE, valid = true, ...)` → `detectedType == "DRIVER_LICENSE"`.
- [ ] **Step 2:** Run `mvn test -Dtest='IdentityDocumentDataExtractorTest,KycInfoExtractorIdentificationTest'` —
  Expected: FAIL.
- [ ] **Step 3:** Add/extend the files as specified.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): share the KYC identity schema with mandate documents` with `Refs #330`.

### Task 3: The ownership, locality and other extractors

**Files:**
- Create: `.../document/DocumentDataExtractorProvider.kt`
- Create: `.../document/LandTitleDocumentDataExtractor.kt`
- Create: `.../document/SurveyPlanDocumentDataExtractor.kt`
- Create: `.../document/PowerOfAttorneyDocumentDataExtractor.kt`
- Create: `.../document/OccupancyDocumentDataExtractor.kt`
- Create: `.../document/OtherDocumentDataExtractor.kt`
- Test: `.../document/LandTitleDocumentDataExtractorTest.kt`, `SurveyPlanDocumentDataExtractorTest.kt`,
  `PowerOfAttorneyDocumentDataExtractorTest.kt`, `OccupancyDocumentDataExtractorTest.kt`,
  `OtherDocumentDataExtractorTest.kt`, `DocumentDataExtractorProviderTest.kt`
  (all under `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/`)

**Interfaces — Produces:**
- `@Service class DocumentDataExtractorProvider(extractors: List<DocumentDataExtractor>)` with
  `fun get(type: DocumentType): DocumentDataExtractor?` (by `types`) and `fun supports(type: DocumentType): Boolean`.
- Each extractor injects `@Qualifier(ChatClients.VISION) client: ChatClient` and `clock: Clock`, has a `PROMPT`
  companion listing every `DocumentType` name for `detectedType`, today's date, the type's fields, `valid`,
  `invalidityReason`, `confidence`, `fieldConfidences` and `yyyy-MM-dd` dates, and builds the request as
  `KycInfoExtractorIdentification.extract` does (text + each image as `MediaType`/`FileSystemResource`,
  `.call().entity(<data class>::class.java)`); a null answer throws `DocumentExtractionException`.
  - `LandTitleDocumentDataExtractor`: `types = setOf(LAND_TITLE, CERTIFICATE_OF_OWNERSHIP)`, returns
    `LandTitle` / `CertificateOfOwnership` by type.
  - `SurveyPlanDocumentDataExtractor`: `SURVEY_PLAN` → `SurveyPlan`.
  - `PowerOfAttorneyDocumentDataExtractor`: `POWER_OF_ATTORNEY` → `PowerOfAttorney`.
  - `OccupancyDocumentDataExtractor`: `LEASE_CONTRACT`, `UTILITY_BILL`, `TAX_RECEIPT` → the matching data class.
  - `OtherDocumentDataExtractor`: `OTHER` → `Other`.

**Consumes:** Task 1, Task 2's interface/exception.

- [ ] **Step 1: Write the failing tests** (each extractor test mocks a `ChatModel` with a JSON answer, as
  `KycInfoExtractorIdentificationTest` does, and asserts the returned data):
  - Land title: all fields, `owners = listOf("A", "B")`, `surface = "500"`, `issueDate = "2020-01-01"`, `detectedType`;
    `CERTIFICATE_OF_OWNERSHIP` returns that class. A null model answer throws.
  - Survey plan: `parcelReference`, `locality`, `surface`, `coordinates`.
  - Procuration: `grantor`, `attorney`, `purpose`, `notary`, `issueDate`, `expiryDate`.
  - Occupancy: each of the three types returns its class with `holderName`, `address`, `locality`, `provider`,
    `issueDate`.
  - Other: `title`, `summary`.
  - `DocumentDataExtractorProviderTest`: each handled type resolves to its extractor; `UNKNOWN`, `PAY_SLIP`,
    `BANK_PRE_APPROVAL`, `PROOF_OF_FUNDS` → null.
- [ ] **Step 2:** Run `mvn test -Dtest='*DocumentDataExtractorTest'` — Expected: FAIL.
- [ ] **Step 3:** Implement the extractors and the provider as specified.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): add the ownership and locality document extractors` with `Refs #330`.

### Task 4: Requirements and the generic checks

**Files:**
- Create: `.../document/DocumentProblemCodes.kt`
- Create: `.../document/DocumentRequirements.kt`
- Create: `.../document/DocumentChecks.kt`
- Test: `.../document/DocumentRequirementsTest.kt`, `.../document/DocumentChecksTest.kt`

**Interfaces — Produces:**
- `object DocumentProblemCodes` with the six constants plus `DOCUMENT_EXPIRED`.
- `object DocumentRequirements` with
  `data class Requirements(val required: Set<String> = emptySet(), val oneOf: List<Set<String>> = emptyList(),
  val requiresExpiry: Boolean = false, val sides: Set<String> = emptySet())` and
  `fun of(type: DocumentType): Requirements?` (null for `UNKNOWN`, `PAY_SLIP`, `BANK_PRE_APPROVAL`, `PROOF_OF_FUNDS`),
  populated exactly as *Global Constraints* says.
- `@Service class DocumentChecks(@param:Value("\${ndopify.mandate.document.extract.min-confidence}") private val minConfidence: Double)`
  with `fun check(type: DocumentType, data: DocumentData): List<String>`:
  `!data.valid` → `[DOCUMENT_UNREADABLE]` alone; otherwise, in order, `detectedType` (uppercased) different from the
  declared type → `DOCUMENT_TYPE_MISMATCH`; the required sides not all present → `DOCUMENT_PAGE_MISSING`;
  `requiresExpiry` and `data.expiresAt() == null` → `EXPIRY_DATE_UNREADABLE`; a required field blank or with
  `fieldConfidences[key] ?: 0.0 < minConfidence`, or no `oneOf` key present with a sufficient confidence →
  `DOCUMENT_FIELD_LOW_CONFIDENCE`. `expiryDate` is checked by the expiry rule, never by the generic one. The warning is
  not added here.

**Consumes:** Task 1's `DocumentData`.

- [ ] **Step 1: Write the failing tests**
  - `DocumentRequirementsTest`: each type's `required` / `oneOf` / `requiresExpiry` / `sides` exactly; null for the four
    unhandled types.
  - `DocumentChecksTest` (a real `DocumentChecks(0.7)`):
    - `valid = false` → only `DOCUMENT_UNREADABLE`.
    - `detectedType = "UTILITY_BILL"` on a `LAND_TITLE` → `DOCUMENT_TYPE_MISMATCH`; `detectedType = null` → none.
    - `NATIONAL_ID` without `BACK` → `DOCUMENT_PAGE_MISSING`; with both → none; `PASSPORT` without `FRONT` → the code.
    - `NATIONAL_ID` with `expiryDate = null` or `"next year"` → `EXPIRY_DATE_UNREADABLE`; `"2030-01-01"` → none; a
      `LAND_TITLE` without an expiry → none.
    - A required field blank → `DOCUMENT_FIELD_LOW_CONFIDENCE`; confidence 0.69 → the code; 0.7 → none; an optional
      field at 0.3 → none.
    - Occupancy with `address = "..."` at 0.4 and no `locality` → the code; `locality` at 0.9 → none.
    - `OTHER` with a blank `summary` → the code.
    - A document failing several checks → the codes in the fixed order.
- [ ] **Step 2:** Run `mvn test -Dtest='DocumentRequirementsTest,DocumentChecksTest'` — Expected: FAIL.
- [ ] **Step 3:** Implement the three files.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): check the extracted mandate documents` with `Refs #330`.

### Task 5: The extraction service

**Files:**
- Create: `.../document/MandateDocumentExtractionService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateDocumentRepository.kt`
- Test: `.../document/MandateDocumentExtractionServiceTest.kt`

**Interfaces — Produces:**
- `MandateDocumentRepository` gains:
  - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT d FROM MandateDocumentEntity d WHERE d.id = :id") fun findByIdForUpdate(id: Long): MandateDocumentEntity?`
  - `fun findByStatusOrderByIdAsc(status: DocumentStatus, pageable: Pageable): List<MandateDocumentEntity>`
  - `fun findByStatusAndModifiedAtBefore(status: DocumentStatus, cutoff: Date): List<MandateDocumentEntity>`
  - `fun findByTypeAndStatusNot(type: DocumentType, status: DocumentStatus): List<MandateDocumentEntity>`
- `@Service class MandateDocumentExtractionService(...)` injecting `MandateService`, `MandateDocumentService`
  (`findDocument`), `MandateDocumentRepository`, `MandateDocumentPageRepository`, `StorageServiceProvider`,
  `DocumentDataExtractorProvider`, `DocumentChecks`, `DocumentDataCodec`, `TenantService`, `Clock`,
  `TransactionTemplate`, `KVLogger`, and the three config values `batch-size`, `max-attempts`,
  `in-progress-timeout-minutes` (`min-confidence` lives in `DocumentChecks`). Produces:
  - `fun extractDue(timeZone: String): Int` — recover the interrupted, then extract the tenant's `UPLOADED`
    documents through `findByStatusOrderByIdAsc(UPLOADED, OffsetLimitPageRequest(0, batchSize))`, each in its own
    try/catch; returns the count extracted.
  - `fun extract(mandateId: Long, documentId: Long)` — the ops path: `mandateService.findById` (404) →
    `documentService.findDocument` (404) → 400 `HTTP_INVALID_PARAMETER` (`type`) when the type has no extractor →
    extract with `force = true`, using the current tenant's `timeZone`.
  - `fun requeueByType(type: DocumentType): Int` — 400 `HTTP_INVALID_PARAMETER` (`type`) when
    `DocumentRequirements.of(type)` is null; otherwise reset that type's documents, any status but `EXTRACTING`, to
    `UPLOADED` with data/dates/codes cleared (each locked; a version conflict skips that one); returns the count.
  - private `extract(document, timeZone, force)`: a type without an extractor (`extractors.supports(type)` false) is
    left untouched and returns; otherwise claim under the lock (`EXTRACTING`, cleared) — skip a document already
    `EXTRACTING`, or not `UPLOADED` without `force`; download the pages to temp files (each page's own storage, the
    default one for `UNKNOWN`) and delete them in `finally`; call the extractor up to `maxAttempts` times (no
    exception message logged); if every attempt throws → `FAILED` `EXTRACTION_FAILED`; otherwise
    `DocumentChecks.check`; dates from `data.issuedAt()` / `data.expiresAt()`; no failure code →
    `EXTRACTED`, `data` = `codec.write`, `data_version` = 1, the dates, `DOCUMENT_EXPIRED` when the expiry is before
    today in `timeZone`; any failure → `FAILED`, the codes, data/dates null; save under `@Version` and skip on
    `ObjectOptimisticLockingFailureException`. The `KVLogger` receives only `document_id`, `type`, `status`, `attempt`,
    `problem_codes`.
  - private `recoverInterrupted()`: `EXTRACTING` older than `in-progress-timeout-minutes` → `UPLOADED` (re-read under
    the lock, conflict skipped).

**Consumes:** Tasks 1–4; #328's entities/services.

- [ ] **Step 1: Write the failing tests** (`MandateDocumentExtractionServiceTest`, a real `DocumentChecks` /
  `DocumentDataCodec`, mocked DAOs/storage/extractor provider, a fixed `Clock`, a real `TransactionTemplate` over a
  mocked transaction manager, and a mocked `KVLogger`):
  - `extractDue` recovers an `EXTRACTING` document older than 15 min to `UPLOADED`, then extracts the `UPLOADED`
    documents (up to the batch) and returns the count; a document whose extraction throws doesn't stop the others.
  - A successful `NATIONAL_ID` → `EXTRACTED`, `data_version = 1`, `data` non-null, `issued_at`/`expires_at` set,
    `problem_codes` empty; the extractor called once.
  - A document reset to `UPLOADED` by a page change (data, dates and codes null) is extracted again, from its new
    pages.
  - `valid = false` → `FAILED` `DOCUMENT_UNREADABLE`, extractor called once (never retried), data/dates null.
  - Type mismatch, missing `BACK`, unreadable expiry, a required field at 0.6, an optional at 0.3 (still
    `EXTRACTED`) — one test per code/outcome.
  - An expiry yesterday (tenant zone) → `EXTRACTED` + `DOCUMENT_EXPIRED`; today's date → no warning.
  - The extractor throws twice then returns → 3 calls, `EXTRACTED`; throws 3 times → 3 calls, `FAILED`
    `EXTRACTION_FAILED`.
  - A `PAY_SLIP` document stays `UPLOADED` and no extractor is called for it.
  - A repository save throwing `ObjectOptimisticLockingFailureException` → no exception, the document unchanged.
  - The `KVLogger` mock never receives a field value (only ids/type/status/codes).
  - `requeueByType(LAND_TITLE)` resets the non-`EXTRACTING` titles, leaves an `EXTRACTING` one alone and returns the
    count; `requeueByType(PAY_SLIP)` throws 400 `HTTP_INVALID_PARAMETER`.
  - `extract(mandateId, documentId)` on an `EXTRACTED` document re-extracts it (force) and a document of another
    mandate is 404 `MANDATE_DOCUMENT_NOT_FOUND`.
- [ ] **Step 2:** Run `mvn test -Dtest=MandateDocumentExtractionServiceTest` — Expected: FAIL.
- [ ] **Step 3:** Implement the repository additions and the service.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): extract the mandate documents in the background` with `Refs #330`.

### Task 6: The job and its configuration

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/job/MandateDocumentJobs.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (the `ndopify.mandate` block)
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml` (the `mandate` block)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/job/MandateDocumentJobsTest.kt`

**Interfaces — Produces:**
- `@Service class MandateDocumentJobs(private val service: MandateDocumentExtractionService, private val tenantService: TenantService)`
  with `@Scheduled(cron = "\${ndopify.mandate.document.extract.cron}") fun extract()`, looping `tenantService.search(true)`
  as `MandateJobs.forEachTenant` does (a `DefaultKVLogger` in `KVLoggerThreadLocal`, `TenantContext`, the tenant id in
  the log, a failing tenant logged and skipped, both thread-locals removed in `finally`), calling
  `service.extractDue(tenant.timeZone)` and logging its count.
- `application.yml`, under `mandate` (after #328's `document.url-ttl-seconds`):
  ```yaml
  document:
      url-ttl-seconds: 3600
      extract:
          cron: "0 */15 * * * ?"     # Every 15 minutes: documents not read yet, or left EXTRACTING
          batch-size: 100            # Documents examined per tenant per run
          max-attempts: 3            # Technical failures retried in the run, then FAILED
          min-confidence: 0.7        # A required field below this confidence fails the document
          in-progress-timeout-minutes: 15  # An EXTRACTING document older is interrupted: it's retried
  ```
- `application-qa.yml`, under `mandate`:
  ```yaml
  document:
      extract:
          cron: "-" # Never on its own: tests call MandateDocumentJobs
  ```

- [ ] **Step 1: Write the failing test** (`MandateDocumentJobsTest`, as `MandateJobsTest`): two active tenants →
  `extractDue(tenant.timeZone)` called once per tenant with `TenantContext` set and a `KVLogger` installed; a tenant
  throwing doesn't stop the second; both thread-locals are removed.
- [ ] **Step 2:** Run `mvn test -Dtest=MandateDocumentJobsTest` — Expected: FAIL.
- [ ] **Step 3:** Add the job and the configuration.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): schedule the mandate document extraction` with `Refs #330`.

### Task 7: The ops endpoints

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateDocumentEndpoints.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateDocumentOpsEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/ExtractMandateDocumentEndpointTest.kt`
- Test: `.../endpoint/ExtractMandateDocumentsByTypeEndpointTest.kt`
- Test: `.../endpoint/ExtractMandateDocumentOpsEndpointDisabledTest.kt`
- Fixtures: `modules/ndopify-server/src/test/resources/db/test/mandate/ExtractMandateDocumentEndpoint.sql`,
  `ExtractMandateDocumentsByTypeEndpoint.sql`

**Interfaces — Produces:**
- `MandateDocumentEndpoints` gains
  `@OpsEndpoint @PostMapping("/{documentId}/extract") fun extract(@PathVariable id: Long, @PathVariable documentId: Long)`
  → 204, `extractionService.extract(id, documentId)`; 404 `MANDATE_NOT_FOUND` / `MANDATE_DOCUMENT_NOT_FOUND` as the
  service checks them; no access check (ops, off in prod).
- `@RestController @RequestMapping("/v1/mandate-documents") class MandateDocumentOpsEndpoints(private val service: MandateDocumentExtractionService)`
  with `@OpsEndpoint @PostMapping("/extract") fun extract(@RequestParam type: DocumentType)` → 204,
  `service.requeueByType(type)`; 400 `HTTP_INVALID_PARAMETER` (`type`) for a type without an extractor.

- [ ] **Step 1: Write the failing tests** (two fixtures. `ExtractMandateDocumentEndpoint.sql`: tenant 1, user 11's
  agent assigned to mandate `100`; document `1000` of type `NATIONAL_ID` `UPLOADED` with two S3 pages; document `1003`
  of type `PAY_SLIP`; document `1100` on mandate `101`. `ExtractMandateDocumentsByTypeEndpoint.sql`: document `1001`
  of type `LAND_TITLE` `EXTRACTED` with `data`/dates/codes, document `1002` of type `LAND_TITLE` `EXTRACTING`,
  document `1003` of type `PAY_SLIP`; `@MockitoBean StorageServiceProvider` returning bytes, `@MockitoBean
  DocumentDataExtractorProvider` returning a mocked `IdentityDocumentDataExtractor`/`LandTitleDocumentDataExtractor`):
  - `ExtractMandateDocumentEndpointTest`: POST `/v1/mandates/100/documents/1000/extract` → 204; reloaded document is
    `EXTRACTED`, `data_version = 1`, `data` non-null, `issued_at`/`expires_at` set (the identity answer has both
    dates), one storage download per page; POST `/v1/mandates/100/documents/1100/extract` → 404
    `MANDATE_DOCUMENT_NOT_FOUND`; unknown mandate → 404 `MANDATE_NOT_FOUND`; POST on `1003` (a `PAY_SLIP`) → 400
    `HTTP_INVALID_PARAMETER` (`type`); anonymous → 401.
  - `ExtractMandateDocumentsByTypeEndpointTest`: POST `/v1/mandate-documents/extract?type=LAND_TITLE` → 204; `1001`
    becomes `UPLOADED` with data/codes/dates cleared; `1002` (`EXTRACTING`) untouched; `1003` untouched; POST
    `type=PAY_SLIP` and `type=UNKNOWN` → 400 `HTTP_INVALID_PARAMETER`.
  - `ExtractMandateDocumentOpsEndpointDisabledTest` (`@TestPropertySource(properties =
    ["ndopify.endpoints.ops=false"])`): both endpoints → 404 `HTTP_NOT_FOUND`; a normal document GET is still served.
- [ ] **Step 2:** Run `mvn test -Dtest='ExtractMandateDocument*Test'` — Expected: FAIL.
- [ ] **Step 3:** Implement the endpoints.
- [ ] **Step 4:** Run the same command — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): add the mandate document extraction ops endpoints` with `Refs #330`.

### Task 8: Expose the fields, document, verify

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateDocumentMapper.kt`
- Modify: `modules/ndopify-server/CLAUDE.md`
- Test: `.../mandate/server/endpoint/GetMandateDocumentEndpointTest.kt` (extend)
- Test: `.../mandate/server/endpoint/SearchMandateDocumentEndpointTest.kt` (extend)
- Fixtures: `GetMandateDocumentEndpoint.sql`, `SearchMandateDocumentEndpoint.sql` (add extracted documents)

**Interfaces:**
- `MandateDocumentMapper.toMandateDocument(...)` injects `DocumentDataCodec`; for an entity with `data`, it reads the
  typed data by `type` / `data_version`, takes `toFields()` (non-null, non-blank values, in order) and builds
  `List<DocumentField>` with `confidence = data.fieldConfidences[key]`; a blank/malformed payload or an unknown type
  gives `emptyList()` and is logged by class only, never a 500. `problemCodes`, `issuedAt`, `expiresAt` and `status`
  keep #328's direct mapping.
- `CLAUDE.md`: a **mandate documents** paragraph in the mandate domain section (the extraction job and config, the
  handled types and the financial ones left to #333, `UPLOADED → EXTRACTING → EXTRACTED | FAILED`, the problem codes
  and their required fields, the `fields` keys and value formats, the KYC schema shared with
  `KycInfoExtractorIdentification`, the ops endpoints, the retry/timeout rule, privacy); the `util/jpa` bullet for
  `JsonHelper` and the `@JdbcTypeCode(SqlTypes.JSON)` column; `MandateDocumentJobs` in the jobs list.

**Consumes:** Tasks 1–7.

- [ ] **Step 1: Write the failing tests**
  - Extend `GetMandateDocumentEndpointTest`'s fixture with a `LAND_TITLE` document whose `data` is
    `{"detectedType":"LAND_TITLE","titleNumber":"TF-1","owners":["A","B"],"locality":"Yaoundé","surface":"500","issueDate":"2020-01-01","valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.8,"locality":0.85}}`,
    `data_version=1`, no problem code, `issued_at='2020-01-01'`; assert `status`, `issuedAt`, `fields` exactly
    (`titleNumber`/`owners`/`locality`/`surface`/`issueDate`, their values and confidences) and that the body has no
    `"data"`, `"path"` nor a public URL. Add a `NATIONAL_ID` with `problem_codes='DOCUMENT_EXPIRED'` and an
    `expires_at` → the warning and the date are mapped. Add a document with `data = NULL` → `fields` empty, and one
    with `data = '[]'` (valid JSON, not a payload) → 200, `fields` empty.
  - Extend `SearchMandateDocumentEndpointTest` similarly for one extracted document.
- [ ] **Step 2:** Run `mvn test -Dtest='GetMandateDocumentEndpointTest,SearchMandateDocumentEndpointTest'` — Expected:
  FAIL.
- [ ] **Step 3:** Update the mapper and the fixtures; document in `CLAUDE.md`.
- [ ] **Step 4:** Run the module's full gate:
  `cd /Users/herve/Projects/ndopify-mono && mvn install -Dheadless=true -pl modules/ndopify-dto` (if #328's DTO
  release is not yet installed) then `cd modules/ndopify-server && mvn verify` — Expected: BUILD SUCCESS (ktlint,
  all tests, JaCoCo). MySQL must be running for the endpoint tests.
- [ ] **Step 5:** Commit `feat(server): map the extracted fields into the document DTO`, then
  `docs(server): document the mandate document extraction` — each with `Refs #330`.

## Release

No release of its own: deploy `ndopify-server` on top of #328's released `ndopify-dto`. `PAY_SLIP`,
`BANK_PRE_APPROVAL` and `PROOF_OF_FUNDS` stay unextracted until #333; #331 consumes `fields` and `problemCodes` as
specified here.
