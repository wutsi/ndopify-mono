# AI extraction of financial documents — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** #330's background extraction also reads `PAY_SLIP`, `BANK_PRE_APPROVAL` and `PROOF_OF_FUNDS` documents:
each ends `EXTRACTED` with its fields (account numbers masked to their last 4 digits) or `FAILED` with problem codes.

**Architecture:** Three new `DocumentData` subclasses (schema, prompt, required fields, dates) plugged into #330's
extraction run, generic checks, field mapping and ops re-run, plus one server-side sanitizer that strips account
numbers and IBANs before anything is stored. Server only, no DTO change, no migration.

**Tech Stack:** Kotlin, Spring Boot, Spring AI (`ChatClients.VISION`, `.entity(...)`), Jackson, JUnit + `kotlin.test` +
mockito-kotlin.

**Spec:** GitHub issue #333 (enriched body: *Acceptance criteria*, *Decisions and assumptions*); epic #327; #330.

## Global Constraints

- `UI: no`. `Shared code: none` — `DocumentType.PAY_SLIP` / `BANK_PRE_APPROVAL` / `PROOF_OF_FUNDS` and the generic
  `DocumentField` list already exist in `ndopify-dto` (#328). No DTO release, no Flyway script.
- **Blocked by #330.** This plan names #330's pieces as #330's story describes them (`DocumentData`, the extractor, the
  generic checks, the `fields` mapper, the problem codes `EXPIRY_DATE_UNREADABLE` and #330's low-confidence code, the
  warning `DOCUMENT_EXPIRED`). Before Task 1, read #330's merged code and use its real names and packages; where they
  differ from this plan, #330 wins. Change none of #330's rules (run, retries, checks, expiry).
- Field keys are the `DocumentData` property names: pay slip `employerName`, `employeeName`, `payMonth`,
  `grossSalary`, `netSalary`, `currency`; pre-approval `bankName`, `beneficiaries`, `amount`, `currency`, `issueDate`,
  `validityDate`; proof of funds `bankName`, `accountHolder`, `amount`, `amountKind`, `currency`, `statementDate`,
  `accountLast4`. Never renamed once released.
- Amounts are `BigDecimal` (stored as JSON numbers, `fields` value a plain number string), currency an ISO 4217 code;
  the prompt maps *FCFA* / *F CFA* to the tenant's `currencyCode`.
- Required fields (low confidence or missing → `FAILED` with #330's code): pay slip `employeeName`, `payMonth`,
  `netSalary`, `currency`; pre-approval `bankName`, `beneficiaries`, `amount`, `currency`, `validityDate`; proof of
  funds `accountHolder`, `amount`, `currency`, `statementDate`.
- Dates: pay slip `issuedAt` = first day of `payMonth` (`YearMonth`), no `expiresAt`; pre-approval `issuedAt` =
  `issueDate`, `expiresAt` = `validityDate`; proof of funds `issuedAt` = `statementDate`, no `expiresAt`.
- Pre-approval: `validityDate` before today (tenant clock and time zone, #330's rule) → `EXTRACTED` +
  `DOCUMENT_EXPIRED`; no readable `validityDate` → `FAILED` `EXPIRY_DATE_UNREADABLE`.
- Privacy: no log line carries an extracted value (log the document id, type, status and problem codes only); no
  stored field holds an IBAN or a run of 8+ digits.
- Tests: `kotlin.test` assertions, mockito-kotlin mocks, the vision `ChatClient` mocked as in #330's tests.

## Review Focus

1. **The AI returns a full IBAN or account number** in `accountLast4`, `accountHolder` or `bankName`: only the last 4
   digits survive in the stored JSON and in `fields` — Task 1 (`AccountNumberSanitizerTest`) and Task 3.
2. **A pre-approval valid until today** in the tenant's time zone while UTC is already tomorrow: `EXTRACTED` without
   `DOCUMENT_EXPIRED` — Task 2.
3. **An amount with no currency** (or only *FCFA*): no currency → `FAILED`; *FCFA* → the tenant's `currencyCode` —
   Task 2 (the prompt contains the tenant's code) and Task 3.
4. **An older or partial JSON** (a stored pay slip missing `grossSalary`, an unknown extra field): read without error,
   missing fields null — Task 1.
5. **A wrong-type upload** (a pay slip uploaded as `PROOF_OF_FUNDS`): `FAILED` with #330's wrong-type code, never
   `EXTRACTED` with an empty amount — Task 3.

---

### Task 1: The three schemas and the account sanitizer

**Files:**
- Create (in #330's `DocumentData` package, under `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/`):
  `PaySlipData.kt`, `BankPreApprovalData.kt`, `ProofOfFundsData.kt`, `AccountNumberSanitizer.kt`
- Modify: #330's `DocumentData` registry (the `type` → subclass `when`) for the three types
- Test: mirrored package, `PaySlipDataTest.kt`, `BankPreApprovalDataTest.kt`, `ProofOfFundsDataTest.kt`,
  `AccountNumberSanitizerTest.kt`

**Interfaces:**
- Consumes: #330's `DocumentData` (sealed, every field a value + confidence — called `ExtractedField<T>` below),
  its `issuedAt()` / `expiresAt()` / `requiredFields()` hooks and its JSON read/write helper (unknown fields ignored).
- Produces:
  - `data class PaySlipData(employerName: ExtractedField<String>?, employeeName: ExtractedField<String>?,
    payMonth: ExtractedField<YearMonth>?, grossSalary: ExtractedField<BigDecimal>?,
    netSalary: ExtractedField<BigDecimal>?, currency: ExtractedField<String>?) : DocumentData` — `issuedAt =
    payMonth.atDay(1)`, `expiresAt = null`.
  - `data class BankPreApprovalData(bankName, beneficiaries: ExtractedField<List<String>>?, amount, currency,
    issueDate: ExtractedField<LocalDate>?, validityDate: ExtractedField<LocalDate>?) : DocumentData` — `issuedAt =
    issueDate`, `expiresAt = validityDate`, and #330's "must have an expiry date" flag set.
  - `enum class AmountKind { BALANCE, CERTIFIED }`; `data class ProofOfFundsData(bankName, accountHolder, amount,
    amountKind: ExtractedField<AmountKind>?, currency, statementDate: ExtractedField<LocalDate>?,
    accountLast4: ExtractedField<String>?) : DocumentData` — `issuedAt = statementDate`, `expiresAt = null`.
  - `object AccountNumberSanitizer { fun last4(value: String?): String?; fun scrub(text: String?): String? }` —
    `last4`: the last 4 digits of the value's digits, `null` when fewer than 4; `scrub`: replaces each IBAN
    (`[A-Z]{2}\d{2}[A-Z0-9 ]{10,30}`) and each run of 8+ digits (spaces and dashes allowed inside) by `•••• ` + its
    last 4 digits.
  - Each financial subclass has `fun sanitized(): <Self>` — every `String` field through `scrub`, `accountLast4`
    through `last4`.

- [ ] **Step 1: Write the failing tests**
  - `AccountNumberSanitizerTest`: `last4("FR76 3000 6000 0112 3456 7890 189") == "0189"`; `last4("12") == null`;
    `last4(null) == null`; `scrub("Compte 10005 00001 12345678901 23")` contains no 8-digit run and ends with
    `"•••• 0123"`; `scrub("CM21 10005 00001 12345678901 23")` holds no IBAN; `scrub("Afriland First Bank") ==
    "Afriland First Bank"`.
  - `ProofOfFundsDataTest`: `sanitized()` of data with `accountLast4 = "12345678901"` and `accountHolder =
    "Jean Dupont 12345678901"` → `accountLast4.value == "8901"`, `accountHolder.value` without `"12345678"`; the
    confidences are kept; `issuedAt == statementDate`, `expiresAt == null`.
  - `PaySlipDataTest`: `payMonth = YearMonth.of(2026, 10)` → `issuedAt == LocalDate.of(2026, 10, 1)`,
    `expiresAt == null`; reading `{"employeeName":{"value":"A","confidence":0.9},"foo":1}` gives `grossSalary == null`
    and no error; a JSON round trip keeps `netSalary` as `BigDecimal("350000")`.
  - `BankPreApprovalDataTest`: `issuedAt == issueDate`, `expiresAt == validityDate`, the type requires an expiry date;
    `beneficiaries` round-trips as a list.
  - Each test also asserts `requiredFields()` equals the Global Constraints list for its type.
- [ ] **Step 2:** Run `cd modules/ndopify-server && mvn test -Dtest='PaySlipDataTest,BankPreApprovalDataTest,ProofOfFundsDataTest,AccountNumberSanitizerTest'`
  — Expected: FAIL (classes missing).
- [ ] **Step 3:** Implement the classes and register the three types in #330's registry. KDoc on `ProofOfFundsData`
  and `AccountNumberSanitizer`: why the masking is enforced server-side (never trust the model with it).
- [ ] **Step 4:** Run the same tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): add the financial document schemas` + `Refs #333`.

### Task 2: Prompts

**Files:**
- Modify: #330's extractor (or its per-type prompt registry) — one prompt per financial type
- Test: #330's extractor test, extended (`<Extractor>FinancialTest.kt` if the existing one is per family)

**Interfaces:**
- Consumes: Task 1 classes; #330's extractor signature (document pages → `DocumentData` of the document's type);
  the tenant (`currencyCode`, `timeZone`) and `Clock` as #330 already passes them.
- Produces: prompts that ask for exactly Task 1's fields with a confidence each; never a full account number or IBAN
  ("give only the last 4 digits of the account number; never write the full number or the IBAN"); amounts as plain
  numbers without separators; currency as an ISO 4217 code, *FCFA* / *F CFA* meaning `{{CURRENCY}}` (the tenant's
  `currencyCode`); dates `YYYY-MM-DD`, pay month `YYYY-MM`; `amountKind` `BALANCE` for a statement, `CERTIFIED` for
  an attestation; `{{TODAY}}` on the tenant's time zone. The extractor applies `sanitized()` to the model's result
  before returning it.

- [ ] **Step 1: Write the failing tests** (vision client mocked; capture the user text):
  - for each financial type, the prompt contains each of its field keys and the tenant's `currencyCode` (`XAF` in the
    fixture), and says *last 4 digits*;
  - a mocked `PROOF_OF_FUNDS` answer with `accountLast4 = "CM2110005000011234567890123"` → the returned data has
    `accountLast4.value == "0123"` (sanitized before it leaves the extractor);
  - `{{TODAY}}` is the tenant's date: `Clock` fixed at `2026-11-25T23:30:00Z` with tenant zone `Africa/Douala`
    (UTC+1) → the prompt says `2026-11-26`.
- [ ] **Step 2:** Run the extractor tests — Expected: the new ones FAIL.
- [ ] **Step 3:** Add the three prompts and the `sanitized()` call.
- [ ] **Step 4:** Run them — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): prompt the vision model for financial documents` + `Refs #333`.

### Task 3: End to end through #330's run, checks, fields and ops re-run

**Files:**
- Modify (only if #330 keys anything by an explicit type list): #330's checks, `fields` mapper, ops re-run endpoint
- Modify: `modules/ndopify-server/CLAUDE.md` — the financial types in #330's *mandate documents* extraction paragraph
  (schemas, required fields, dates, masking rule)
- Test: #330's extraction service test, extended (`<ExtractionService>FinancialTest.kt`), and #330's ops re-run
  endpoint test (one case)

**Interfaces:**
- Consumes: Tasks 1–2; #330's extraction service (the method the job calls per document), its checks and problem
  codes, its `fields` mapper.
- Produces: nothing new; the three types flow through #330's run.

- [ ] **Step 1: Write the failing tests** (extractor mocked to return Task 1 data; tenant zone `Africa/Douala`, `Clock`
  fixed at `2026-11-25T10:00:00Z`):
  - pay slip, all required fields at 0.95, `payMonth = 2026-10` → `EXTRACTED`, `issuedAt == 2026-10-01`,
    `expiresAt == null`, no problem codes; `fields` keys `employerName, employeeName, payMonth, grossSalary,
    netSalary, currency` with `netSalary` value `"350000"` and `payMonth` value `"2026-10"`;
  - pay slip with `netSalary` confidence 0.3 → `FAILED` with #330's low-confidence code; with `currency = null` → same;
  - pre-approval `validityDate = 2026-11-25` → `EXTRACTED`, `expiresAt == 2026-11-25`, no `DOCUMENT_EXPIRED`;
    `validityDate = 2026-11-24` → `EXTRACTED` + `DOCUMENT_EXPIRED`; `validityDate = null` → `FAILED`
    `EXPIRY_DATE_UNREADABLE`;
  - proof of funds → `EXTRACTED`, `issuedAt == statementDate`, `expiresAt == null`, `fields` has `accountLast4`
    `"0123"` and `amountKind` `"BALANCE"`; the stored `data` JSON string contains no run of 8 digits;
  - the extractor reporting the wrong type (#330's signal) for a `PROOF_OF_FUNDS` document → `FAILED` with #330's
    wrong-type code;
  - privacy: with a log appender attached (as #330's test does), no log line during the run contains `"350000"`,
    the holder's name, nor `"0123"`;
  - ops re-run with `type=PAY_SLIP` resets the tenant's pay slips to `UPLOADED` (#330's behaviour) and leaves a
    `NATIONAL_ID` document untouched.
- [ ] **Step 2:** Run them — Expected: FAIL until the types are wired (or PASS at once if #330 is fully generic; then
  keep the tests as the regression net).
- [ ] **Step 3:** Wire whatever #330 lists by type; document the financial types in `modules/ndopify-server/CLAUDE.md`.
- [ ] **Step 4:** Run `cd modules/ndopify-server && mvn verify` — Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate;
  needs local MySQL).
- [ ] **Step 5:** Commit `feat(server): extract the financial documents` + `Refs #333`.

## Release

Server only: merging deploys `ndopify-server`. No DTO or web-core release, no migration. #331 shows the new keys
(an unknown key falls back to a generic label, so the order of #331 and this story doesn't matter).
