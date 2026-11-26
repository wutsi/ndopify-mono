# Partner Central — a document's extracted data and problems — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a mandate's document page, the agent reads what the AI extracted from the document — labelled and
formatted, with one instruction per problem code when the reading failed and a notice when the piece has expired —
and the mandate page marks an expired document.

**Architecture:** Three small pieces in `ndopify-partner-central`, on the `ui/document/` feature #329 creates: a
message bundle plus a `MessageSource`-backed label resolver for the server's field keys and problem codes; a pure
value formatter on `TenantFormatter`; a `DocumentReadingView` built from #328's `MandateDocument` and rendered by
`templates/mandate/document.html`. The mandate page's *Documents* rows gain one boolean. No endpoint, client method,
DTO, server or web-core change.

**Tech Stack:** Kotlin, Spring Boot MVC + Thymeleaf, Spring `MessageSource` + `messages.properties`, `TenantFormatter`,
JUnit + `kotlin.test` + mockito-kotlin, standalone `MockMvc`, real-Thymeleaf template tests.

**Spec:** GitHub issue #331 (enriched body: *Acceptance criteria* 1–13, *Decisions and assumptions*, *Prototype
approved*), epic #327. Approved prototype:
`modules/ndopify-partner-central/src/main/html/v2/mandate/document.html` (*Données lues* section),
`v2/mandate.html` (*Expirée* marker), `v2/assets/js/mandate-documents.js` (its samples, `FIELD_LABELS`,
`PROBLEM_INSTRUCTIONS`, the value formatters and `renderReading`), `v2/assets/css/documents.css` (the `#331` block),
and the `#331` rows of `v2/README.md`. Server contract: #330's plan
(`docs/superpowers/plans/330-server-ai-extraction-of-identity-and-ownership-documents.md`) and #333's
(`…/333-server-ai-extraction-of-financial-documents.md`); page contract: #329's
(`docs/superpowers/plans/329-npc-a-documents-section-on-the-mandate-page.md`).

## Global Constraints

- **Blocked by #329 and #330.** #329 creates `ui/document/` (`MandateDocumentController`, `MandateDocumentLabels`,
  `MandateDocumentRules`, `MandateDocumentViews` → `DocumentSectionView` / `DocumentRowView` / `DocumentView`,
  `MandateDocumentForms`), `templates/mandate/document.html`, `templates/fragments/mandate-documents.html`,
  `templates/mandate/document-new.html`, `src/main/assets/css/document.css` and the `NdMandateDocumentClient`'s
  `get(mandateId, documentId): MandateDocument`. #330 fills `fields`, `problemCodes`, `issuedAt`, `expiresAt`. Merge
  order of #333 is free. No migration, no DTO release, no server change, no `ndopify-web-core` change.
- Module: `modules/ndopify-partner-central` only (package `com.wutsi.ndopify.npc`). Never touch `src/main/html/` (the
  approved prototype stays as it is).
- **Copy:** every French string is the approved prototype's, verbatim (`vous`, punctuation, `&nbsp;` before `:`, `!`
  and `?`). Code, comments, KDoc, keys in English.
- **Formats:** dates through `TenantFormatter.date(LocalDate)` (tenant `dateFormat`, year included), counts through
  `count(Long)`, amounts through `compactMoney(Long, currencyCode)` (title #292), "today" through
  `TenantFormatter.today(clock)` in the tenant's time zone. Never a hard-coded pattern, currency or locale.
- **Message bundle:** `src/main/resources/messages.properties` (UTF-8), Spring Boot's default `MessageSource`
  (basename `messages`). Only the *keyed* texts live there — `document.field.<key>`, `document.problem.<code>` and the
  two generic fallbacks; the fixed page wording lives in Kotlin, as #329's labels do.
- **No JavaScript needed:** the reading is server-rendered. `MandateDocumentPage`'s page script (#329) is untouched.
- **No confidence shown**, no `data` JSON, no page path or URL in the HTML, no field value in a log (web-core's REST
  interceptor logs the method and URL only; this story adds no log line carrying a value).
- **Tests:** `kotlin.test` assertions, `mockito-kotlin` mocks, standalone `MockMvc`, real-Thymeleaf template tests that
  call `ScriptFixtures.write`. Gate: `cd modules/ndopify-partner-central && mvn verify`.

## Review Focus

1. **An `EXTRACTED` document whose `expiresAt` is past** — it is *not* `FAILED` and has no status for it: the notice
   must show, `DOCUMENT_EXPIRED` must never appear as an instruction, and the status pill stays *Analysé*.
2. **A key or code the bundle doesn't know** — a new server field or problem code shows the generic text with its
   value/instruction, never an empty row nor an exception (all five `PROBLEM_INSTRUCTIONS` and `Autre donnée` pinned).
3. **An unparseable value** — a date that isn't `yyyy-MM-dd`, a `payMonth` that isn't `yyyy-MM`, an amount that isn't a
   number: shown as sent, never a 500 (older payloads, #330's additive reading).
4. **`currency` is never a row and a null/blank value has none** — an amount with no `currency` field uses the tenant's
   currency; a blank optional field leaves no empty row.
5. **A closed or cancelled mandate** — fields, instructions and notice stay readable, the notice names no way out, and
   the *Expirée* row marker still shows (the list is read-only, not blank).

---

### Task 1: The message bundle, the labels and the value formats

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/messages.properties`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabels.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldValues.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabelsTest.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldValuesTest.kt`

**Interfaces — Produces:**
- `@Component class DocumentFieldLabels(private val messages: MessageSource)`:
  - `fun label(key: String): String` — `messages.getMessage("document.field.$key", null, "Autre donnée", Locale.FRENCH)`;
  - `fun instruction(code: String?): String` — the generic text for null/blank, else
    `messages.getMessage("document.problem.$code", null, GENERIC, Locale.FRENCH)` (see the constants below).
- `object DocumentFieldValues`:
  - `fun of(key: String, value: String, currency: String?, formatter: TenantFormatter): String`;
  - `val CURRENCY = "currency"`, `val HIDDEN = setOf(CURRENCY)` — the keys never shown as a row.
- `messages.properties` — exactly these entries (order: field keys, then problem codes, then the fallbacks):

```
document.field.number=Numéro
document.field.firstName=Prénom(s)
document.field.lastName=Nom
document.field.dateOfBirth=Date de naissance
document.field.issueDate=Date de délivrance
document.field.expiryDate=Date d'expiration
document.field.countryCode=Pays
document.field.titleNumber=Numéro du titre
document.field.owners=Propriétaires
document.field.department=Département
document.field.arrondissement=Arrondissement
document.field.locality=Localité
document.field.surface=Surface
document.field.parcelReference=Référence de la parcelle
document.field.coordinates=Coordonnées
document.field.grantor=Mandant
document.field.attorney=Mandataire
document.field.purpose=Objet
document.field.notary=Notaire
document.field.holderName=Titulaire
document.field.address=Adresse
document.field.provider=Fournisseur
document.field.title=Titre
document.field.summary=Résumé
document.field.employerName=Employeur
document.field.employeeName=Salarié
document.field.payMonth=Mois de paie
document.field.grossSalary=Salaire brut
document.field.netSalary=Salaire net
document.field.bankName=Banque
document.field.beneficiaries=Bénéficiaires
document.field.amount=Montant
document.field.validityDate=Valable jusqu'au
document.field.amountKind=Nature du montant
document.field.accountHolder=Titulaire du compte
document.field.statementDate=Date du relevé
document.field.accountLast4=Compte
document.field.unknown=Autre donnée

document.problem.DOCUMENT_TYPE_MISMATCH=Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec le bon type.
document.problem.DOCUMENT_UNREADABLE=L'IA n'a pas pu lire le document. Remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.
document.problem.DOCUMENT_PAGE_MISSING=Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, ou supprimez le document et ajoutez-le avec toutes ses pages.
document.problem.DOCUMENT_FIELD_LOW_CONFIDENCE=L'IA n'est pas sûre d'un champ obligatoire. Vérifiez-le sur la pièce : si la photo est floue, remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.
document.problem.EXPIRY_DATE_UNREADABLE=La date d'expiration de la pièce n'a pas pu être lue. Remplacez la page où elle figure par une photo plus nette, ou supprimez le document et ajoutez-le de nouveau.
document.problem.EXTRACTION_FAILED=La lecture du document a échoué. Remplacez ses pages, ou supprimez le document et ajoutez-le de nouveau pour relancer la lecture.
document.problem.unknown=Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.
```

  No `document.problem.DOCUMENT_EXPIRED`: it is the warning the notice carries, never an instruction.

**Value formats (`DocumentFieldValues.of`), in this order:**

| Key | Rendered as |
|---|---|
| `issueDate`, `expiryDate`, `validityDate`, `statementDate`, `dateOfBirth` | `LocalDate.parse(value)` → `formatter.date(date)`; a `DateTimeParseException` → `value` |
| `payMonth` | `YearMonth.parse(value).atDay(1)` → `formatter.date(day, "MMMM yyyy")`, first letter uppercased ("Octobre 2026"); a parse failure → `value` |
| `amount`, `grossSalary`, `netSalary` | `BigDecimal(value).toLong()` → `formatter.compactMoney(amount)` when `currency` is null/blank, else `formatter.compactMoney(amount, currency)`; a `NumberFormatException` → `value` |
| `accountLast4` | `"•••• $value"` when it matches `\d{4}`, else `value` |
| `surface` | `formatter.count(value.toLong()) + " m²"` when it parses, else `value` |
| `amountKind` | `BALANCE` → *Solde*, `CERTIFIED` → *Attesté*, else `value` |
| anything else | `value` |

  `currency` is the value of the sibling `currency` field, passed by the caller (null/blank: the formatter's tenant
  currency). `compactMoney` already renders the tenant's symbol when the code is the tenant's, the ISO code otherwise.

- [ ] **Step 1: Write the failing tests**

  `DocumentFieldLabelsTest` — a real `ResourceBundleMessageSource` (`basename = "messages"`,
  `setDefaultEncoding("UTF-8")`):
  ```kotlin
  private val labels = DocumentFieldLabels(
      ResourceBundleMessageSource().apply {
          setBasename("messages")
          setDefaultEncoding("UTF-8")
      },
  )

  @Test fun `known field keys are labelled`() = assertEquals("Numéro du titre", labels.label("titleNumber"))
  @Test fun `a known financial key is labelled`() = assertEquals("Salaire net", labels.label("netSalary"))
  @Test fun `an unknown key falls back`() = assertEquals("Autre donnée", labels.label("position"))
  @Test fun `each problem code has its instruction`() {
      assertEquals("Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec le bon type.", labels.instruction("DOCUMENT_TYPE_MISMATCH"))
      // … one assertion per code: DOCUMENT_UNREADABLE, DOCUMENT_PAGE_MISSING, DOCUMENT_FIELD_LOW_CONFIDENCE,
      // EXPIRY_DATE_UNREADABLE, EXTRACTION_FAILED — the texts above
  }
  @Test fun `an unknown or missing code falls back`() {
      assertEquals("Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.", labels.instruction("DOCUMENT_BACK_MISSING"))
      assertEquals("Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.", labels.instruction(null))
  }
  ```

  `DocumentFieldValuesTest` (`TenantFixtures.FORMATTER`; its `dateFormat` is `dd MMM yyyy`, currency `FCFA`):
  ```kotlin
  private fun value(key: String, value: String, currency: String? = null) =
      DocumentFieldValues.of(key, value, currency, FORMATTER)

  @Test fun `dates read in the tenant format`() {
      assertEquals("20 mai 2032", value("expiryDate", "2032-05-20"))
      assertEquals("14 juin 2019", value("issueDate", "2019-06-14"))
      assertEquals("8 mars 1979", value("dateOfBirth", "1979-03-08"))
  }
  @Test fun `a pay month reads as a month`() = assertEquals("Octobre 2026", value("payMonth", "2026-10"))
  @Test fun `amounts read short with the document currency`() {
      assertEquals("350K FCFA", value("netSalary", "350000", "XAF"))
      assertEquals("12,5M FCFA", value("amount", "12500000", "XAF"))
      assertEquals("125K EUR", value("amount", "125000", "EUR"))
  }
  @Test fun `an amount without a currency uses the tenant's`() =
      assertEquals("350K FCFA", value("amount", "350000"))
  @Test fun `an account number is masked`() = assertEquals("•••• 0123", value("accountLast4", "0123"))
  @Test fun `a surface is a grouped count`() = assertEquals("1 200 m²", value("surface", "1200").replace('\u00A0', ' ').replace('\u202F', ' '))
  @Test fun `amount kinds are words`() {
      assertEquals("Solde", value("amountKind", "BALANCE"))
      assertEquals("Attesté", value("amountKind", "CERTIFIED"))
  }
  @Test fun `unparseable values read as sent`() {
      assertEquals("vers 2030", value("expiryDate", "vers 2030"))
      assertEquals("octobre", value("payMonth", "octobre"))
      assertEquals("beaucoup", value("amount", "beaucoup"))
  }
  @Test fun `other values read as sent`() = assertEquals("Afriland First Bank", value("bankName", "Afriland First Bank"))
  ```

- [ ] **Step 2:** Run `cd modules/ndopify-partner-central && mvn test -Dtest='DocumentFieldLabelsTest,DocumentFieldValuesTest'`
  — Expected: FAIL (classes and bundle missing).

- [ ] **Step 3:** Write `messages.properties`, `DocumentFieldLabels` (KDoc: the bundle is the contract with #330/#333 —
  an unknown key or code reads the generic text, so a new server field needs one line here, not a code change;
  `DOCUMENT_EXPIRED` has no entry) and `DocumentFieldValues` (KDoc: keyed by #330/#333's `DocumentData` property names;
  a value it can't parse is shown as sent, never dropped).

- [ ] **Step 4:** Run the same command — Expected: PASS.

- [ ] **Step 5:** Commit `feat(npc): label and format a document's extracted fields` with `Refs #331`.

### Task 2: The reading view and the expired row marker

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentReadingView.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt`
  (`DocumentRowView`: add `val expired: Boolean = false`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt`
  (`section(mandate, documents, formatter, today: LocalDate)`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
  (pass its `today()` to `MandateDocumentViews.section`)
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViewsTest.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt` (extend)

**Interfaces — Produces:**
```kotlin
/** What the AI read on a document, as templates/mandate/document.html renders it. */
data class DocumentReadingView(
    val waiting: String? = null,            // UPLOADED / EXTRACTING
    val expiry: DocumentExpiryView? = null, // expiresAt before today
    val noData: Boolean = false,            // nothing to show: no fields
    val problems: List<String> = emptyList(),
    val rows: List<DocumentFieldRowView> = emptyList(),
)
data class DocumentExpiryView(val date: String, val since: String, val wayOut: String)
data class DocumentFieldRowView(val label: String, val value: String)

object MandateDocumentReadingViews {
    fun of(
        mandate: Mandate,
        document: MandateDocument,
        formatter: TenantFormatter,
        labels: DocumentFieldLabels,
        today: LocalDate,
    ): DocumentReadingView
}
```

`MandateDocumentReadingViews.of`:

- **rows** — `document.fields` in order, dropping `key in DocumentFieldValues.HIDDEN` and a null/blank value, each row
  `labels.label(key)` + `DocumentFieldValues.of(key, value, document.fields.firstOrNull { it.key == "currency" }?.value, formatter)`.
- **waiting** — `UPLOADED` → *Le document est reçu : sa lecture va commencer. Rien à faire, elle se lance d'elle-même ;
  ses données apparaîtront ici.* ; `EXTRACTING` → *L'IA lit le document. Ses données apparaîtront ici dès qu'elle aura
  terminé : rien à faire.* ; else null.
- **expiry** — `document.expiresAt?.takeIf { it.isBefore(today) }` → `DocumentExpiryView(date = formatter.date(it),
  since = if ("DOCUMENT_EXPIRED" in document.problemCodes) "Elle était déjà expirée quand l'IA l'a lue." else "Elle a
  expiré depuis sa lecture.", wayOut = …)`; `wayOut` is *Ajoutez une pièce en cours de validité : remplacez ses pages
  par celles de la nouvelle pièce, ou supprimez-la et ajoutez la nouvelle.* when
  `MandateDocumentRules.writable(mandate)`, else *Mandat fermé : cette pièce reste consultable telle quelle.* /
  *Mandat annulé : cette pièce reste consultable telle quelle.* after `mandate.status` (`CLOSED` / `CANCELLED`).
- **problems** — `FAILED` → `problemCodes.filter { it != "DOCUMENT_EXPIRED" }.map(labels::instruction)`, and
  `listOf(labels.instruction(null))` when that list is empty; else `emptyList()`.
- **noData** — `rows.isEmpty() && problems.isEmpty() && waiting == null`.

`MandateDocumentViews.section(mandate, documents, formatter, today)`: each row's `expired =
document.expiresAt?.isBefore(today) == true`; every other field as #329 has it.

**Consumes:** Task 1's `DocumentFieldLabels` and `DocumentFieldValues`; #329's `MandateDocumentRules.writable`,
`DocumentRowView`, `MandateRules.Tag`; #328's `MandateDocument`, `DocumentField`.

- [ ] **Step 1: Write the failing tests**

  `MandateDocumentReadingViewsTest` — fixture `DocumentFixtures` local to the test: a `TenantFormatter` from
  `TenantFixtures.FORMATTER`, `DocumentFieldLabels(ResourceBundleMessageSource …)`, `TODAY = LocalDate.of(2026, 11, 25)`,
  a `Mandate` (status `APPROVED`, then `CANCELLED`) and a helper `reading(document)`:
  - an `EXTRACTED` land title with `titleNumber, owners, department, arrondissement, locality, surface, issueDate`,
    `issuedAt = 2019-06-14` → `rows` labelled *Numéro du titre* … *Date de délivrance*, values `4521`,
    `A, B`, `500 m²`, `14 juin 2019`, in order; `waiting`, `expiry` null, `problems` empty, `noData` false.
  - the same with an extra `{ key = "position", value = "Chargée de clientèle" }` → a row *Autre donnée* /
    *Chargée de clientèle*.
  - a `currency = XAF` field beside `amount = 12500000` → no *Compte*/*currency* row, the amount row `12,5M FCFA`.
  - a `null` and a blank optional value → no row.
  - `UPLOADED` and `EXTRACTING` → `waiting` non-null (the two texts), `rows` empty, `noData` false.
  - `EXTRACTED` with no fields → `noData` true.
  - `FAILED` with `DOCUMENT_PAGE_MISSING, DOCUMENT_FIELD_LOW_CONFIDENCE` → the two instructions in that order, no
    `DOCUMENT_EXPIRED` one, `rows` empty; `FAILED` with `DOCUMENT_BACK_MISSING` → the generic text; `FAILED` with an
    empty list → the generic text once.
  - `EXTRACTED` + `expiresAt = 2025-01-12` + `DOCUMENT_EXPIRED` → `expiry.date == "12 janv. 2025"`,
    `expiry.since == "Elle était déjà expirée quand l'IA l'a lue."`, `wayOut` the replace sentence; the same document
    on a `CANCELLED` mandate → `wayOut == "Mandat annulé : cette pièce reste consultable telle quelle."`; a `CLOSED`
    mandate → *Mandat fermé …*.
  - `EXTRACTED` + `expiresAt = 2025-01-12` without the code → *Elle a expiré depuis sa lecture.*
  - `expiresAt = 2026-11-25` (today) and `2026-12-01` → no `expiry`.

  `MandateDocumentViewsTest` (extend) — `section(...)` with a document whose `expiresAt = 2025-01-12` and
  `TODAY = 2026-11-25` → that row `expired = true`, the others false; `expiresAt = 2026-11-25` → false.

- [ ] **Step 2:** Run `cd modules/ndopify-partner-central && mvn test -Dtest='MandateDocumentReadingViewsTest,MandateDocumentViewsTest'`
  — Expected: FAIL.

- [ ] **Step 3:** Implement the view classes, `MandateDocumentReadingViews`, the `expired` field, `section`'s `today`
  parameter and `MandateController`'s call to it.

- [ ] **Step 4:** Run the same command — Expected: PASS.

- [ ] **Step 5:** Commit `feat(npc): build the document reading view` with `Refs #331`.

### Task 3: Port the approved prototype into the page and the section

First step of the UI work: **port** the approved prototype (the `#331` parts of `v2/mandate/document.html`,
`v2/mandate.html` and the `#331` block of `v2/assets/css/documents.css`) into Thymeleaf and the production stylesheet —
markup, classes and texts as approved, no redesign.

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-documents.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/document.css`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt` (extend)
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt` (extend)
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt` (extend)
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt` (extend)

**Interfaces:**
- `MandateDocumentController` — add `DocumentFieldLabels` to its constructor and
  `private fun today(): LocalDate = formatter().today(Clock.fixed(Instant.ofEpochMilli(clock.millis()), ZoneOffset.UTC))`
  (as `MandateController` does); the document page's render adds the model attribute `reading =
  MandateDocumentReadingViews.of(mandate, document, formatter(), labels, today())`. Nothing else changes: the form
  actions, outcomes and JSON proxies of #329 stay as they are.

**The document template** (between the read-only hint and *Pages*, as the prototype):

```html
<section class="doc-reading" th:if="${reading.waiting != null or reading.expiry != null or reading.problems.size() > 0
        or reading.rows.size() > 0 or reading.noData}" aria-labelledby="reading-title">
    <h2 id="reading-title" class="doc-manage__title">Données lues</h2>
    <!-- expiry notice: .alert.alert--warning, title "Pièce expirée le " + reading.expiry.date,
         reading.expiry.since, reading.expiry.wayOut -->
    <!-- waiting: .hint with reading.waiting -->
    <!-- no data: .hint with "Aucune donnée n'a été lue sur ce document. Vérifiez la pièce elle-même : elle reste consultable." -->
    <!-- problems: .alert.alert--warning, title "Ce document n'a pas pu être lu", lead
         "Corrigez ce qui suit : la lecture repart d'elle-même.", <ul class="doc-problems"> one <li> per instruction -->
    <!-- rows: <p class="doc-reading__intro">Lues automatiquement sur la pièce : vérifiez-les sur l'original.</p>
         then <dl class="review doc-data"> one .review__item (.review__term / .review__value) per row -->
</section>
```

Copy the prototype's icons, classes and comments' intent; replace its `data-*` hooks with `th:if` / `th:text` /
`th:each`. Keep the section's French text exactly as approved, `&nbsp;` included (`th:text` for the dynamic parts).

**The section fragment:** each `doc-row` wraps its status pill in `<span class="doc-row__tags">` and adds
`<span class="status-tag status-tag--error" th:if="${row.expired}">Expirée</span>` after it.

**The stylesheet:** copy the prototype's `#331` block (`documents.css`: `.doc-row__tags`, `.doc-reading`,
`.doc-reading__intro`, `.doc-data` and its label/value columns, `.doc-problems`, the under-360 px stack, and the
corrected `.status-tag--error` comment) into `src/main/assets/css/document.css`.

- [ ] **Step 1: Write the failing tests**

  `MandateDocumentTemplateTest` (the engine and mocked clients of #329; add a real `DocumentFieldLabels`; stub the clock
  with `Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC)`):
  - `EXTRACTED` land title with the seven fields → `.doc-reading` has no `.alert--warning`, no `.hint`; `.doc-data`
    rows are the labelled pairs in order (`Numéro du titre`/`4521`, `Surface`/`500 m²`, `Date de délivrance`/
    `14 juin 2019`); the intro is present.
  - an unknown key `position` → the *Autre donnée* row; a `currency` field → no `Compte`/`XAF` row, the amount reads
    `12,5M FCFA`.
  - `UPLOADED` / `EXTRACTING` → the waiting `.hint` with its text, no `.doc-data`, no `.doc-problems`.
  - `EXTRACTED` with no fields → the no-data `.hint`; status pill *Analysé*.
  - `FAILED` with two codes → `.doc-problems li` are the two instructions in order; `DOCUMENT_EXPIRED` alone →
    `.doc-problems` absent.
  - expired + `DOCUMENT_EXPIRED` → the notice text includes `Pièce expirée le 12 janv. 2025` and the replace sentence;
    the same document on a `CANCELLED` mandate → *Mandat annulé : cette pièce reste consultable telle quelle.* and no
    *Remplacer* form (`Voir` only, #329).
  - `expiresAt` today / none → no notice.
  - the page still has no `<img>` and no page path; each test calls `ScriptFixtures.write`.

  `MandateTemplateTest` (extend) — the section's expired document row has *Analysé* and *Expirée* in
  `.doc-row__tags`, a valid one has no *Expirée*.

  `MandateDocumentControllerTest` / `MandateControllerTest` (extend) — the document page's model has `reading` built
  from the returned document (`result.modelAndView!!.model["reading"]`), and the mandate page's `documentSection`
  carries the marker.

- [ ] **Step 2:** Run `cd modules/ndopify-partner-central && mvn test -Dtest='MandateDocument*Test,MandateControllerTest,MandateTemplateTest'`
  — Expected: FAIL.

- [ ] **Step 3:** Port the markup and CSS, add the controller's `labels` / `today()` / `reading` and the section's
  `today()`.

- [ ] **Step 4:** Run the same command — Expected: PASS (the page-script tests compile against the rendered pages).

- [ ] **Step 5:** Commit `feat(npc): show what the AI read on a document` with `Refs #331`.

### Task 4: The module documentation and the full gate

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md`

- [ ] **Step 1:** In the module's *Pages* entry for the documents (#329's), add the #331 paragraph: the *Données lues*
  section (fields by key from `messages.properties` with the generic fallbacks, the value formats, no confidence, no
  JSON), the waiting / no-data / failed states, problem instructions and `DOCUMENT_EXPIRED` being the notice not an
  instruction, the expiry rule (`expiresAt` before the tenant's today, on every read) and the row's *Expirée* marker,
  read-only on a closed mandate, no re-analysis. Mention the bundle in the *Code Organization* paragraph (it is the
  first `messages.properties` of the module) and the prototype
  (`src/main/html/v2/mandate/document.html`, approved 2026-11-25).

- [ ] **Step 2:** Run `cd modules/ndopify-partner-central && mvn verify` — Expected: BUILD SUCCESS (ktlint, unit and
  template tests, page-script tests, JaCoCo gate).

- [ ] **Step 3:** Commit `docs(npc): document the extracted data on a document page` with `Refs #331`.

## Release

No release of its own: Partner Central deploys alone, on #329's released `ndopify-dto` and after #330's
`ndopify-server`. #333 may deploy before or after (an unknown key reads the generic label).
