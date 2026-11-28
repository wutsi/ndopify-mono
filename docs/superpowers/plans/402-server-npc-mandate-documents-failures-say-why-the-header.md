# Mandate document failures say why, and the header fits a phone — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a `FAILED` mandate document a deterministic code and a stored reason for support, log the resizer's refusal, and port the approved Partner Central copy (document page, ownership alert, Documents header) to production.

**Architecture:** `ndopify-server` splits the model's `valid = false` (`DOCUMENT_INVALID`) from the resizer's `DOCUMENT_UNREADABLE`, stores one new nullable `failure_reason` column written by every `fail()` path, and clears it on every reset. `ndopify-partner-central` renders the approved prototype's wording: a neutral document heading, the new instruction, one-sentence ownership reasons with the intro linking to Documents, and the Documents count moved into the intro.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Flyway (MySQL), Thymeleaf, JUnit 5 + kotlin.test, mockito-kotlin, Logback (test appender), esbuild for the web app's CSS bundle.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/402 (the enriched story body; the approved prototype is under `modules/ndopify-partner-central/src/main/html/v2/`)

## Global Constraints

- `failure_reason` is `VARCHAR(1000)` on `T_MANDATE_DOCUMENT`; the value is `trim()`-ed, truncated to 1000 characters, and a blank value is stored as `NULL`. Existing rows stay `NULL` — no backfill.
- The stored reason is support-only: never a `MandateDocument` DTO field, endpoint, page, e-mail, KV-log key or exception message. The only reason that is logged is the resizer's `ImageResizeException` message (one WARN).
- `DOCUMENT_UNREADABLE` is produced only by the resizer path; `DOCUMENT_INVALID` only by `DocumentChecks` on `valid = false`; a missing required side stays `DOCUMENT_PAGE_MISSING` alone.
- No `ndopify-dto` change and no `ndopify-dto` / `ndopify-web-core` release; `problemCodes` stays a list of plain strings.
- UI text is French; code, comments, logs and docs are English. Copy is exactly the approved prototype's words.
- Partner Central deploys before the server (an old client falls back to `document.problem.unknown`).
- Tests use `kotlin.test` assertions and `mockito-kotlin`; no new production dependency.
- Run ktlint through Maven; a formatting violation fails the build.
- No endpoint, page, DTO or wizard is added.

## Review Focus

- **A model reason over 1000 characters, blank, or null:** the failure save must still succeed, storing exactly the trimmed prefix or `NULL` — never `""` or the text `null`. Covered in Task 3 and Task 5.
- **A technical exception whose message carries model output:** it reaches `failure_reason` only, never a log line or a KV key. Covered in Task 5 and Task 6.
- **A page change or requeue during/after a failure:** no stale `failure_reason` may survive a claim, requeue, recovery, page change or a success. Covered in Task 1 and Task 6.
- **A FAILED ownership verdict with an unknown code or the city mismatch:** one sentence with the piece in bold, the mismatch's two places inline, no per-reason action, list or button. Covered in Task 8.
- **The Documents intro at 0, 1, 2, 5 and unreadable, at 360 px:** no count pill, no screen-reader duplicate, head and action on one line. Covered in Task 9.

---

## Task 1: Persist the failure reason

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateDocumentEntity.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V25_0__mandate-document-failure-reason.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt` (`resetExtraction`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt`

**Interfaces:**
- Consumes: none.
- Produces: `MandateDocumentEntity.failureReason: String?`; every later server task writes it; `resetExtraction` clears it.

- [ ] **Step 1: Write the failing test**

In `MandateDocumentServiceTest`, in `uploadReplacementPage - a one-page pdf replaces the page and resets the extraction`, seed the document with a reason and assert the reset clears it:

```kotlin
val document = MandateDocumentEntity(
    id = 2000L,
    mandateId = 100L,
    status = DocumentStatus.EXTRACTED,
    dataVersion = 1,
    failureReason = "unreadable page",
)
// ...
verify(documentDao).save(argThat<MandateDocumentEntity> {
    status == DocumentStatus.UPLOADED && dataVersion == null && failureReason == null
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: compilation failure — `failureReason` does not exist on `MandateDocumentEntity`.

- [ ] **Step 3: Implement the column and the reset**

Add to `MandateDocumentEntity` after `problemCodes`:

```kotlin
/**
 * Why the last extraction failed, kept for support only: the model's reason, the resizer's message or the technical
 * exception. Never exposed in a DTO, a page nor a log, and cleared with the extraction.
 */
val failureReason: String? = null,
```

Create the migration:

```sql
-- #402: why the last extraction failed, kept for support only. Never exposed in the DTO nor a page; cleared with the
-- extraction (claim, requeue, page change) and on success. Existing rows stay NULL: no backfill.
ALTER TABLE T_MANDATE_DOCUMENT ADD COLUMN failure_reason VARCHAR(1000) DEFAULT NULL;
```

In `MandateDocumentService.resetExtraction`, add `failureReason = null` to the `copy`.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateDocumentEntity.kt \
  modules/ndopify-server/src/main/resources/db/migration/common/V25_0__mandate-document-failure-reason.sql \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt
git commit -m "feat(server): persist a mandate document's failure reason" -m "Refs #402"
```

---

## Task 2: Split `DOCUMENT_INVALID` from `DOCUMENT_UNREADABLE`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentProblemCodes.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentChecks.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentChecksTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the *Extraction* paragraph of the mandate domain)

**Interfaces:**
- Consumes: none.
- Produces: `DocumentProblemCodes.DOCUMENT_INVALID = "DOCUMENT_INVALID"`; `DocumentChecks.check` returns it where it returned `DOCUMENT_UNREADABLE` for an invalid answer.

- [ ] **Step 1: Write the failing tests**

In `DocumentChecksTest`, rename `an unreadable document is the only code` and change its expectation:

```kotlin
@Test
fun `a model-rejected document is invalid, the only code`() {
    val codes = checks.check(DocumentType.NATIONAL_ID, nationalId().copy(valid = false))

    assertEquals(listOf(DocumentProblemCodes.DOCUMENT_INVALID), codes)
}
```

Keep `an unreadable document missing a side is a missing page, not unreadable` asserting `DOCUMENT_PAGE_MISSING`.

In `MandateDocumentExtractionServiceTest`, `extract - an unreadable answer fails once, never retried` expects:

```kotlin
assertEquals(listOf(DocumentProblemCodes.DOCUMENT_INVALID), documents[DOCUMENT_ID]!!.problemCodes)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=DocumentChecksTest,MandateDocumentExtractionServiceTest`
Expected: FAIL — `DOCUMENT_INVALID` is unresolved / the codes still read `DOCUMENT_UNREADABLE`.

- [ ] **Step 3: Implement the code split**

In `DocumentProblemCodes`, add:

```kotlin
/** The model read the pages and answered `valid = false` (a specimen, a forgery, a blurry page): a new page may fix it, a retry won't. */
const val DOCUMENT_INVALID = "DOCUMENT_INVALID"
```

and rewrite `DOCUMENT_UNREADABLE`'s KDoc to say it is the resizer's refusal (a page that cannot be decoded, is not an image, or is over 40 MP).

In `DocumentChecks.check`, return `DocumentProblemCodes.DOCUMENT_INVALID` in the `!data.valid` branch when the required sides are present. Update its KDoc: "An answer the model marked invalid is `DOCUMENT_INVALID` alone…".

In `modules/ndopify-server/CLAUDE.md`, in the *Extraction* paragraph, add that the model's `valid = false` is `DOCUMENT_INVALID` (the pages were read, the model refused them) while `DOCUMENT_UNREADABLE` is now only the resizer's refusal.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=DocumentChecksTest,MandateDocumentExtractionServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentProblemCodes.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentChecks.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentChecksTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): add DOCUMENT_INVALID for a model-rejected mandate document" -m "Refs #402"
```

---

## Task 3: Store the model's own reason

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentData.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/PaySlipData.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/BankPreApprovalData.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/ProofOfFundsData.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt`

**Interfaces:**
- Consumes: `MandateDocumentEntity.failureReason` (Task 1), `DocumentProblemCodes.DOCUMENT_INVALID` (Task 2).
- Produces: `DocumentData.invalidityReason: String?` (open, default null); `MandateDocumentExtractionService.fail(document, codes, attempt, reason: String? = null)` and `failureReason(value: String?): String?` (trim, truncate 1000, blank → null).

- [ ] **Step 1: Write the failing tests**

In `MandateDocumentExtractionServiceTest`, add an `invalidityReason: String? = null` parameter to `nationalId(...)` (pass it to `NationalId`), then:

```kotlin
@Test
fun `extract - a model rejection stores its reason, trimmed`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doReturn(nationalId(valid = false, invalidityReason = "  Document is marked SPECIMEN.  "))
        .whenever(extractor).extract(eq(DocumentType.NATIONAL_ID), any())

    service.extractDue("UTC")

    assertEquals(listOf(DocumentProblemCodes.DOCUMENT_INVALID), documents[DOCUMENT_ID]!!.problemCodes)
    assertEquals("Document is marked SPECIMEN.", documents[DOCUMENT_ID]!!.failureReason)
}

@Test
fun `extract - a blank model reason stores null`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doReturn(nationalId(valid = false, invalidityReason = "   "))
        .whenever(extractor).extract(any(), any())

    service.extractDue("UTC")

    assertEquals(listOf(DocumentProblemCodes.DOCUMENT_INVALID), documents[DOCUMENT_ID]!!.problemCodes)
    assertNull(documents[DOCUMENT_ID]!!.failureReason)
}

@Test
fun `extract - a missing side on an invalid answer stores no reason`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doReturn(nationalId(valid = false, sides = listOf("FRONT"), invalidityReason = "SPECIMEN"))
        .whenever(extractor).extract(any(), any())

    service.extractDue("UTC")

    assertEquals(listOf(DocumentProblemCodes.DOCUMENT_PAGE_MISSING), documents[DOCUMENT_ID]!!.problemCodes)
    assertNull(documents[DOCUMENT_ID]!!.failureReason)
}
```

The existing `extract - an unreadable answer fails once, never retried` keeps its `DOCUMENT_INVALID` assertion and adds `assertNull(documents[DOCUMENT_ID]!!.failureReason)`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: FAIL — `failureReason` is never written / `DocumentData.invalidityReason` is not accessible from the base class.

- [ ] **Step 3: Implement it**

In `DocumentData`, add after `valid`:

```kotlin
/** Why the model judged the document invalid; null when it didn't say. Read by the extraction, never exposed as a field. */
open val invalidityReason: String? get() = null
```

Change every `val invalidityReason: String? = null` in `DocumentData.kt`, `PaySlipData.kt`, `BankPreApprovalData.kt` and `ProofOfFundsData.kt` to `override val invalidityReason: String? = null` (13 classes).

In `MandateDocumentExtractionService`, add:

```kotlin
/** The stored reason: trimmed, at most [MAX_FAILURE_REASON] characters, null when blank. */
private fun failureReason(value: String?): String? =
    value?.trim()?.take(MAX_FAILURE_REASON)?.ifEmpty { null }
```

and add to the existing `companion object` (which holds `MAX_PAGE_BYTES`):

```kotlin
/** `failure_reason`'s column length. */
private const val MAX_FAILURE_REASON = 1000
```

Then give `fail` the extra parameter:

```kotlin
private fun fail(document: MandateDocumentEntity, codes: List<String>, attempt: Int, reason: String? = null): Boolean =
    save(
        document.copy(
            status = DocumentStatus.FAILED,
            data = null,
            dataVersion = null,
            issuedAt = null,
            expiresAt = null,
            problemCodes = codes,
            failureReason = failureReason(reason),
            modifiedAt = now(),
        ),
        codes,
        attempt,
    )
```

and pass the model's reason on the checks path:

```kotlin
val codes = checks.check(claimed.type, data)
// ...
fail(claimed, codes, attempts, if (codes == listOf(DocumentProblemCodes.DOCUMENT_INVALID)) data.invalidityReason else null)
```

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest,DocumentChecksTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/DocumentData.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/PaySlipData.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/BankPreApprovalData.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/ProofOfFundsData.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt
git commit -m "feat(server): store a model-rejected document's reason" -m "Refs #402"
```

---

## Task 4: Warn on the resizer's refusal and store its message

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt`

**Interfaces:**
- Consumes: `fail(..., reason)` and `failureReason(...)` (Task 3).
- Produces: one WARN line `>>> MandateDocument#<id> - page <n> cannot be read (<Class>: <message>)`; `failure_reason` = the exception's message.

- [ ] **Step 1: Write the failing test (with a Logback appender)**

Add the imports `ch.qos.logback.classic.Level`, `ch.qos.logback.classic.Logger`, `ch.qos.logback.classic.spi.ILoggingEvent`, `ch.qos.logback.core.read.ListAppender`, and to the test class:

```kotlin
private val logAppender = ListAppender<ILoggingEvent>()
private val serviceLogger = LoggerFactory.getLogger(MandateDocumentExtractionService::class.java) as Logger

// In setUp(), after the mocks:
logAppender.start()
serviceLogger.addAppender(logAppender)

// A new @AfterEach:
@AfterEach
fun detachLogs() {
    serviceLogger.detachAppender(logAppender)
    logAppender.stop()
}
```

Replace `extract - a page the resizer rejects is unreadable, without an LLM call` with:

```kotlin
@Test
fun `extract - a page the resizer rejects warns with its reason, stores it and doesn't call the model`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doThrow(ImageResizeException("Too many pixels: 8000 x 6000")).whenever(resizer).resize(any(), any())

    service.extractDue("UTC")

    val document = documents[DOCUMENT_ID]!!
    assertEquals(DocumentStatus.FAILED, document.status)
    assertEquals(listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), document.problemCodes)
    assertEquals("Too many pixels: 8000 x 6000", document.failureReason)
    verify(extractor, never()).extract(any(), any())
    val warning = logAppender.list.single { it.level == Level.WARN && it.formattedMessage.contains("cannot be read") }
    assertEquals(
        ">>> MandateDocument#10 - page 1 cannot be read (ImageResizeException: Too many pixels: 8000 x 6000)",
        warning.formattedMessage,
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: FAIL — no WARN line, `failureReason` null.

- [ ] **Step 3: Implement it**

Change the page loop to `pages.forEachIndexed { index, page -> ... }`. In the `catch (ex: ImageResizeException)`:

```kotlin
LOGGER.warn(
    ">>> MandateDocument#${claimed.id} - page ${index + 1} cannot be read " +
        "(${ex.javaClass.simpleName}: ${ex.message})",
)
return fail(claimed, listOf(DocumentProblemCodes.DOCUMENT_UNREADABLE), 0, ex.message)
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt
git commit -m "feat(server): warn and store the resizer's refusal reason" -m "Refs #402"
```

---

## Task 5: Store the technical failure's exception

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt`

**Interfaces:**
- Consumes: `fail(..., reason)` (Task 3).
- Produces: `failure_reason` = `<ExceptionSimpleName>: <message>` for `EXTRACTION_FAILED`, both from the page-read catch and from the last extractor attempt, truncated to 1000.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `extract - a technical failure stores the last exception`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doThrow(IllegalStateException("timeout")).whenever(extractor).extract(any(), any())

    service.extractDue("UTC")

    assertEquals(listOf(DocumentProblemCodes.EXTRACTION_FAILED), documents[DOCUMENT_ID]!!.problemCodes)
    assertEquals("IllegalStateException: timeout", documents[DOCUMENT_ID]!!.failureReason)
}

@Test
fun `extract - a page read failure stores its exception and doesn't retry`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doThrow(IllegalStateException("boom")).whenever(pageDao).findByDocumentIdOrderByPageIndex(DOCUMENT_ID)

    service.extractDue("UTC")

    assertEquals(listOf(DocumentProblemCodes.EXTRACTION_FAILED), documents[DOCUMENT_ID]!!.problemCodes)
    assertEquals("IllegalStateException: boom", documents[DOCUMENT_ID]!!.failureReason)
    verify(extractor, never()).extract(any(), any())
}

@Test
fun `extract - a technical reason is truncated to the column length`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doThrow(IllegalStateException("x".repeat(1200))).whenever(extractor).extract(any(), any())

    service.extractDue("UTC")

    val reason = documents[DOCUMENT_ID]!!.failureReason!!
    assertEquals(1000, reason.length)
    assertTrue(reason.startsWith("IllegalStateException: xxx"))
}
```

Also add `assertEquals("IllegalStateException: timeout", documents[DOCUMENT_ID]!!.failureReason)` to `extract - a technical failure is retried at most three times`, and `assertNull(documents[DOCUMENT_ID]!!.failureReason)` to `extract - a technical failure that ends up succeeding is extracted`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: FAIL — `failureReason` is null on the technical paths.

- [ ] **Step 3: Implement it**

Replace the extractor retry return type with a private result:

```kotlin
private data class ExtractionOutcome(val data: DocumentData?, val attempts: Int, val failure: Exception?)

private fun reasonOf(ex: Exception): String = "${ex.javaClass.simpleName}: ${ex.message}"
```

`extract(document, images)` returns `ExtractionOutcome`: set `failure = ex` in the catch, return `ExtractionOutcome(null, attempt, failure)` after the loop, and `ExtractionOutcome(data, attempt, null)` on success. The caller:

```kotlin
val outcome = extract(claimed, files)
if (outcome.data == null) {
    return fail(claimed, listOf(DocumentProblemCodes.EXTRACTION_FAILED), maxAttempts, outcome.failure?.let(::reasonOf))
}
val data = outcome.data
// ...
fail(claimed, codes, outcome.attempts, /* the model's reason when DOCUMENT_INVALID */)
```

In the generic page-read `catch (ex: Exception)`, keep the existing WARN and return `fail(claimed, listOf(DocumentProblemCodes.EXTRACTION_FAILED), 0, reasonOf(ex))`.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt
git commit -m "feat(server): store a technical extraction failure's exception" -m "Refs #402"
```

---

## Task 6: Clear the reason on every reset, keep it out of the DTO and the logs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt` (`complete`, `reset`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the *Extraction* paragraph)

**Interfaces:**
- Consumes: everything above.
- Produces: `failureReason` null on success, on claim, on requeue and on recovery; no DTO field; no `failure_reason` KV key.

- [ ] **Step 1: Write the failing tests**

Add to `MandateDocumentExtractionServiceTest`:

```kotlin
@Test
fun `extract - a success leaves no failure reason`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))

    service.extractDue("UTC")

    assertEquals(DocumentStatus.EXTRACTED, documents[DOCUMENT_ID]!!.status)
    assertNull(documents[DOCUMENT_ID]!!.failureReason)
}

@Test
fun `requeueByType - clears the failure reason`() {
    add(document(DOCUMENT_ID, status = DocumentStatus.FAILED).copy(failureReason = "IllegalStateException: timeout"), page(100, path = "p0.png"))
    doReturn(setOf(DocumentType.NATIONAL_ID)).whenever(extractors).types()
    doReturn(true).whenever(extractors).supports(DocumentType.NATIONAL_ID)

    val count = service.requeueByType(DocumentType.NATIONAL_ID)

    assertEquals(1, count)
    assertEquals(DocumentStatus.UPLOADED, documents[DOCUMENT_ID]!!.status)
    assertNull(documents[DOCUMENT_ID]!!.failureReason)
}

@Test
fun `the dto exposes no failure reason and the kv log carries none`() {
    add(document(DOCUMENT_ID), page(100, path = "p0.png"))
    doThrow(IllegalStateException("timeout")).whenever(extractor).extract(any(), any())

    service.extractDue("UTC")

    assertTrue(MandateDocument::class.java.declaredFields.none { it.name == "failureReason" })
    assertTrue(logger.entries.none { it.first == "failure_reason" })
}
```

The existing extraction tests for `DOCUMENT_TYPE_MISMATCH`, `DOCUMENT_PAGE_MISSING`, `DOCUMENT_FIELD_LOW_CONFIDENCE` and `EXPIRY_DATE_UNREADABLE` each add `assertNull(documents[DOCUMENT_ID]!!.failureReason)`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest`
Expected: FAIL — the successful path may keep a stale reason after a claim/requeue (and the failing tests compile first).

- [ ] **Step 3: Implement it**

In `complete(...)` add `failureReason = null` to the `copy`. In `reset(...)` add `failureReason = null`. Update the service's KDoc: a failure stores its reason (support only), and the KV log never carries it. In `modules/ndopify-server/CLAUDE.md`, extend the *Extraction* paragraph with the column's contents (model reason, resizer message, technical `<SimpleName>: <message>`), its trim/1000-character rule, its reset paths, and its privacy rule.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentExtractionServiceTest,DocumentChecksTest,MandateDocumentServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/document/MandateDocumentExtractionServiceTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): clear a mandate document's failure reason on every reset" -m "Refs #402"
```

---

## Task 7: Partner Central document page — neutral heading and `DOCUMENT_INVALID`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/messages.properties`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabelsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the mandate *documents* paragraph)

**Interfaces:**
- Consumes: the server's `DOCUMENT_INVALID` code (Task 2).
- Produces: `document.problem.DOCUMENT_INVALID`; the FAILED heading *Ce document n'a pas pu être vérifié*.

- [ ] **Step 1: Write the failing tests**

In `DocumentFieldLabelsTest`, add to `each problem code has its instruction`:

```kotlin
assertEquals(
    "Le document n'a pas pu être vérifié. Vérifiez qu'il s'agit bien d'une pièce authentique, lisible et " +
        "complète : remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et " +
        "ajoutez-le de nouveau.",
    labels.instruction("DOCUMENT_INVALID"),
)
```

In `MandateDocumentTemplateTest`, change `document - a failed reading shows one instruction per code` to use `DOCUMENT_INVALID` and assert the heading:

```kotlin
assertEquals("Ce document n'a pas pu être vérifié", reading.selectFirst(".alert__title")?.text())
assertEquals(
    listOf(
        "Le document n'a pas pu être vérifié. Vérifiez qu'il s'agit bien d'une pièce authentique, lisible et " +
            "complète : remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et " +
            "ajoutez-le de nouveau.",
    ),
    reading.select(".doc-problems li").map { it.text() },
)
```

Keep a second assertion with `DOCUMENT_UNREADABLE` showing the unchanged *n'a pas pu être lu* instruction under the same heading.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=DocumentFieldLabelsTest,MandateDocumentTemplateTest`
Expected: FAIL — the key and the heading are missing.

- [ ] **Step 3: Implement it**

Add to `messages.properties` after `DOCUMENT_UNREADABLE`:

```properties
document.problem.DOCUMENT_INVALID=Le document n'a pas pu être vérifié. Vérifiez qu'il s'agit bien d'une pièce authentique, lisible et complète : remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.
```

In `templates/mandate/document.html`, change the FAILED alert title to *Ce document n'a pas pu être vérifié*. Update `modules/ndopify-partner-central/CLAUDE.md`'s *documents* paragraph: the heading is neutral for every code and `DOCUMENT_INVALID` joins the instructions.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=DocumentFieldLabelsTest,MandateDocumentTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/messages.properties \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabelsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): word DOCUMENT_INVALID and the neutral document heading" -m "Refs #402"
```

---

## Task 8: Partner Central ownership alert — one sentence per reason

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt` (the `MandateOwnershipViews.section` call)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/ownership.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the owner verification paragraph)

**Interfaces:**
- Consumes: the server's verdict DTO (unchanged).
- Produces: `OwnershipReasonView(check: String, text: String)`; `OwnershipSectionView.intro: String?` (the overridden sentence only); `MandateOwnershipViews.section(mandate, verification, listingId)` without `labels`.

- [ ] **Step 1: Write the failing tests**

In `MandateOwnershipViewsTest`:

- Change all `MandateOwnershipViews.section(mandate, verification, null, labels)` calls to drop `labels`, and delete the `DocumentFieldLabels` fixture.
- `verified has no date and links the listing`: `assertNull(view.intro)`.
- `an overridden verified verdict has its own pill and copy`: keep the party sentence.
- Replace `each failed reason names its check, its wording and its action` with:

```kotlin
@Test
fun `each failed reason is one sentence with the piece in bold`() {
    val view = MandateOwnershipViews.section(
        mandate,
        OwnershipVerification(
            FAILED,
            listOf(
                reason("OWNERSHIP_ID_EXPIRED"),
                reason("OWNERSHIP_ID_UNUSABLE"),
                reason("OWNERSHIP_ID_NAME_MISMATCH"),
                reason("OWNERSHIP_DOCUMENT_UNUSABLE"),
                reason("OWNERSHIP_DOCUMENT_NAME_MISMATCH"),
            ),
        ),
        null,
    )

    assertEquals(
        listOf(
            "La pièce d'identité" to "est expirée.",
            "La pièce d'identité" to "n'a pas pu être vérifiée.",
            "La pièce d'identité" to "n'est pas au nom du propriétaire.",
            "Le document de propriété" to "n'a pas pu être vérifié.",
            "Le document de propriété" to "ne désigne pas le propriétaire.",
        ),
        view.reasons.map { it.check to it.text },
    )
}
```

- `an unknown code reads the generic line with no kind` becomes:

```kotlin
assertEquals("Un document", view.reasons[0].check)
assertEquals("n'a pas pu être vérifié.", view.reasons[0].text)
```

- `a property mismatch shows the places in its reason` asserts `check == "Le titre"` and `text == "désigne un bien à Yaoundé, le mandat à Douala."` (with `OwnershipLocation("Douala", "Yaoundé", MISMATCH)`).
- `the intro names the owner` becomes: non-overridden `section.intro` is null for both listing kinds; the overridden one keeps *Ndopify vérifie que le vendeur/le bailleur est bien le propriétaire du bien.*

In `MandateTemplateTest`:

- `the owner verification renders its states`, FAILED part: assert the section text contains *La pièce d'identité n'a pas pu être vérifiée.*, does **not** contain the old instruction *Le document n'a pas pu être lu.*, and has no `[data-ownership-failed] .ownership__actions`.
- `a property mismatch renders its places, not a hint`: assert no `[data-reason-places]`, no `[data-ownership-location]`, and the section text contains *Le titre désigne un bien à Yaoundé, le mandat à Douala.*
- A new assertion in `a verified owner...` (or `the owner verification renders its states`) that the non-overridden intro is *À partir des pièces de la section Documents.* with `a[href='#documents-title']` whose text is *section Documents*.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest,MandateTemplateTest`
Expected: FAIL (compilation: `section` still takes `labels`, `OwnershipReasonView` still has `action`/`problems`).

- [ ] **Step 3: Implement it**

`MandateOwnershipView.kt`:

```kotlin
data class OwnershipReasonView(
    val check: String,
    val text: String,
)
```

and make `OwnershipSectionView.intro: String?`, KDoc: "the overridden verdict's sentence; null otherwise — the fragment owns the fixed *À partir des pièces de la section Documents.* with its link."

`MandateOwnershipViews.kt`:

- `section(mandate, verification, listingId)` (drop `labels`), `intro = if (overridden) "Ndopify vérifie que $owner est bien le propriétaire du bien." else null`.
- Replace `REASON_WORDING`/`REASON_OTHER` with the pieces and texts listed in the story, `PROPERTY_MISMATCH` built inline:

```kotlin
private const val TITLE_CHECK = "Le titre"

private val REASON_WORDING = mapOf(
    "OWNERSHIP_ID_EXPIRED" to Wording(ID_CHECK, "est expirée."),
    "OWNERSHIP_ID_UNUSABLE" to Wording(ID_CHECK, "n'a pas pu être vérifiée."),
    "OWNERSHIP_ID_NAME_MISMATCH" to Wording(ID_CHECK, "n'est pas au nom du propriétaire."),
    "OWNERSHIP_DOCUMENT_UNUSABLE" to Wording(DOCUMENT_CHECK, "n'a pas pu être vérifié."),
    "OWNERSHIP_DOCUMENT_NAME_MISMATCH" to Wording(DOCUMENT_CHECK, "ne désigne pas le propriétaire."),
)
private val REASON_OTHER = Wording("Un document", "n'a pas pu être vérifié.")

private data class Wording(val check: String, val text: String)

private fun reason(reason: OwnershipVerificationReason, mismatchPlaces: OwnershipPlacesView?): OwnershipReasonView =
    if (reason.code == PROPERTY_MISMATCH) {
        OwnershipReasonView(
            check = TITLE_CHECK,
            text = mismatchPlaces
                ?.let { "désigne un bien à ${it.documentLocality}, le mandat à ${it.mandateCity}." }
                ?: "désigne un bien dans une autre ville que celle du mandat.",
        )
    } else {
        val wording = REASON_WORDING[reason.code] ?: REASON_OTHER
        OwnershipReasonView(wording.check, wording.text)
    }
```

Delete the now-unused `DocumentFieldLabels` import/parameter; `MandateController` drops `documentLabels` from its call.

`fragments/mandate-ownership.html`:

- Intro:

```html
<p class="ownership__intro" th:if="${section.overridden}" th:text="${section.intro}">Ndopify vérifie que le vendeur
    est bien le propriétaire du bien.</p>
<p class="ownership__intro" th:unless="${section.overridden}">À partir des pièces de la <a
        href="#documents-title">section Documents</a>.</p>
```

- Each reason:

```html
<li class="ownership__reason" th:each="reason : ${section.reasons}">
    <p><strong class="ownership__check" th:text="${reason.check}">La pièce d'identité</strong> <span
            th:text="${reason.text}">est expirée.</span></p>
</li>
```

- Remove the `<dl class="ownership__places">`, the `<ul class="ownership__problems">`, the `<p class="ownership__action">` and the FAILED block's `<p class="ownership__actions">` with its button.

`src/main/assets/css/ownership.css`: delete `.ownership__problems`, `.ownership__problems > li + li` and `.ownership__action`; change `.ownership__reason + .ownership__reason` to `margin-top: var(--space-2); padding-top: var(--space-2);`.

Update `modules/ndopify-partner-central/CLAUDE.md`'s owner verification paragraph: the FAILED verdict is one sentence per reason (piece in bold, mismatch places inline), no action/list/button, and the intro is the link sentence.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest,MandateTemplateTest,MandateControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
  modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html \
  modules/ndopify-partner-central/src/main/assets/css/ownership.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): cut the owner verification's reasons to the cause" -m "Refs #402"
```

---

## Task 9: Partner Central Documents header — the count moves into the intro

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-documents.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/document.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt` (the documents tests)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the *documents* paragraph)

**Interfaces:**
- Consumes: `MandateDocumentRules.MAX_DOCUMENTS` / `atLimit` (unchanged).
- Produces: `DocumentSectionView` without `count`; the intro `"<n> document(s) sur 5. Identité et <propriété|financement> <whose>, jamais publiés."`, or the sentence alone when unreadable or at 5.

- [ ] **Step 1: Write the failing tests**

In `MandateDocumentViewsTest`:

- `section - the count against the cap` becomes:

```kotlin
@Test
fun `section - the intro carries the count`() {
    val two = MandateDocumentViews.section(mandate, List(2) { doc(id = it.toLong()) }, FORMATTER, TODAY)
    assertEquals("2 documents sur 5. Identité et propriété du vendeur, jamais publiés.", two.intro)

    val one = MandateDocumentViews.section(mandate, listOf(doc()), FORMATTER, TODAY)
    assertEquals("1 document sur 5. Identité et propriété du vendeur, jamais publiés.", one.intro)

    val empty = MandateDocumentViews.section(mandate, emptyList(), FORMATTER, TODAY)
    assertEquals("0 document sur 5. Identité et propriété du vendeur, jamais publiés.", empty.intro)

    val five = MandateDocumentViews.section(mandate, List(5) { doc(id = it.toLong()) }, FORMATTER, TODAY)
    assertEquals("Identité et propriété du vendeur, jamais publiés.", five.intro)
    assertTrue(five.atLimit)

    val unavailable = MandateDocumentViews.section(mandate, null, FORMATTER, TODAY)
    assertEquals("Identité et propriété du vendeur, jamais publiés.", unavailable.intro)
    assertFalse(unavailable.atLimit)
}
```

- `section - a closed mandate at the cap shows no cap notice`: drop the `section.count` assertion.
- `section - the hint and the intro follow the kind of mandate`: assert *de financement de l'acheteur* and *du bailleur* still appear.
- The main `section` test's intro assertion becomes:

```kotlin
assertEquals("2 documents sur 5. Identité et propriété du vendeur, jamais publiés.", section.intro)
```

In `MandateTemplateTest`:

- `documents - the count against the cap` becomes:

```kotlin
assertEquals("Un mandat peut avoir 5 documents au plus.", section.selectFirst(".documents__limit")?.text())
assertNull(section.selectFirst("[data-documents-add]"))
assertNull(section.selectFirst(".documents__count"))
assertEquals("Identité et financement du client, jamais publiés.",
    section.selectFirst(".documents__intro")?.text()?.replace("\u00a0", " "))
```

- `documents - below the cap shows the count and the action` becomes: no `.documents__count`; intro *2 documents sur 5. Identité et financement du client, jamais publiés.*; the add button is present and `.documents__limit` is null.
- `documents - none` adds the intro *0 document sur 5. Identité et financement du client, jamais publiés.*
- `documents - unavailable, the rest of the page still renders` asserts the intro sentence alone (no count lead) and no `.documents__count`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentViewsTest,MandateTemplateTest`
Expected: FAIL — the intro still says *Les pièces d'identité…* and `count` still exists.

- [ ] **Step 3: Implement it**

`MandateDocumentView.kt`: delete `val count: Int` and its KDoc from `DocumentSectionView`.

`MandateDocumentViews.section`:

```kotlin
val count = documents?.size ?: 0
val atLimit = documents != null && writable && MandateDocumentRules.atLimit(count)
val lead = if (documents != null && count < MandateDocumentRules.MAX_DOCUMENTS) {
    "$count document${if (count > 1) "s" else ""} sur ${MandateDocumentRules.MAX_DOCUMENTS}. "
} else {
    ""
}
val intro = lead + "Identité et " + (if (listing) "propriété " else "financement ") + "${words.of}, jamais publiés."
```

Use `atLimit` in the constructor; remove `count = count`.

`fragments/mandate-documents.html`: delete the `<p class="documents__count">` block, keep `<div class="documents__title"><h2 …>Documents</h2></div>`.

`src/main/assets/css/document.css`:

- Delete `.documents__count`.
- `.documents__title`: keep `display:flex; align-items:baseline; flex:1 1 0; min-width:min-content;` and drop `flex-wrap` / `gap`.
- Add, after `.documents__limit`:

```css
/* At the narrowest phones the gap tightens so that the title and the action keep one line (#402, 360 px). */
@media (max-width: 399.98px) {
    .documents__head {
        gap: var(--space-1);
    }
}
```

Update `modules/ndopify-partner-central/CLAUDE.md`'s *documents* paragraph: no compact count, the number opens the intro, no lead at the cap or on an unavailable list, the empty state reads *0 document sur 5.*

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentViewsTest,MandateTemplateTest,MandateDocumentControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt \
  modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-documents.html \
  modules/ndopify-partner-central/src/main/assets/css/document.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): move the Documents count into the intro" -m "Refs #402"
```

---

## Task 10: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the server's tests and quality gate**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS (requires local MySQL 8+, root without password; the migration `V25_0` applies on the test database; ktlint and the coverage gate pass).

- [ ] **Step 2: Run Partner Central's tests and quality gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (the frontend build needs network for `npm ci`; ktlint and the coverage gate pass).

- [ ] **Step 3: Confirm no DTO change and a clean scope**

Run: `git diff --name-only origin/main -- modules/ndopify-dto` → no output.
Run: `git status --porcelain` → clean.

- [ ] **Step 4: Report**

Report the test counts, the two module gates, and confirm no `ndopify-dto` / `ndopify-web-core` release is needed.
