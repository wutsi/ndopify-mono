# Mandate document messages never mention the AI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every AI mention from the user-facing copy of Partner Central's mandate document page — two problem instructions, the waiting hint, the expiry notice and the meta description — without changing any behaviour.

**Architecture:** Copy-only. The five approved French strings are replaced where they are produced: the message bundle (`document.problem.*`), the `MandateDocumentReadingViews` constants, and the document template's description and static no-JavaScript fallbacks. The approved prototype under `modules/ndopify-partner-central/src/main/html/v2` is already updated and committed; production must match it word for word. No `ndopify-server` / `ndopify-dto` change, no migration, no layout or behaviour change.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, `ResourceBundleMessageSource`, JUnit 5 / `kotlin.test` / mockito-kotlin, `MockMvc` + a real Thymeleaf engine, Maven with ktlint and JaCoCo (+ the `page-scripts.test.mjs` run of `mvn test`).

**Spec:** GitHub issue #371 (enriched body, tables and acceptance criteria) and the approved prototype
`modules/ndopify-partner-central/src/main/html/v2/mandate/document.html` + `.../assets/js/mandate-documents.js`
(approved 2026-11-26).

## Global Constraints

- **Partner Central only.** No `ndopify-dto`, no `ndopify-server`, no release, no migration, no `pom.xml` change.
- **UI: yes, copy only.** Layout, CSS, behaviour, statuses, problem codes and their order, field rows and page
  actions stay exactly as they are. The approved prototype is the baseline: **do not edit it**, port its texts.
- **Exact copy** (French, as approved — never paraphrase):

  | Where | Value |
  |---|---|
  | `document.problem.DOCUMENT_UNREADABLE` | `Le document n'a pas pu être lu. Remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.` |
  | `document.problem.DOCUMENT_FIELD_LOW_CONFIDENCE` | `Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est floue, remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.` |
  | `MandateDocumentReadingViews.WAITING_EXTRACTING` | `La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire.` |
  | `MandateDocumentReadingViews.ALREADY_EXPIRED` | `Elle était déjà expirée lors de sa lecture.` |
  | `document.html` meta description | `Un document d'un mandat : ce qui en a été lu, ses problèmes, ses pages.` |

- **Unchanged on purpose:** `WAITING_UPLOADED` (*Le document est reçu : sa lecture va commencer…*), *Ce document n'a
  pas pu être lu*, *Corrigez ce qui suit : la lecture repart d'elle-même*, *Lues automatiquement sur la pièce :
  vérifiez-les sur l'original*, *Aucune donnée n'a été lue sur ce document…*, *Elle a expiré depuis sa lecture.*, the
  statuses *Reçu / En cours d'analyse / Analysé / À corriger*, `DOCUMENT_PAGE_MISSING`, `EXPIRY_DATE_UNREADABLE`,
  `EXTRACTION_FAILED` and the generic instruction.
- **No user-facing string may contain `IA`, `AI` or `intelligence artificielle`.** Rendered strings, the meta
  description, the static no-JS fallbacks and the HTML comments shipped in `document.html`. Comments in Kotlin, JS and
  CSS that are never rendered are out of scope, except in the files this plan touches, where the wording is
  neutralised while it is at hand.
- Tests use `kotlin.test` assertions, never `org.junit.jupiter.api.Assertions.*`; mocks are mockito-kotlin. The web
  module needs neither MySQL nor a running server.
- `mvn verify` green in `modules/ndopify-partner-central` (ktlint, tests, JaCoCo, page-script tests).
- Commits: conventional commits, English, one per task; no `-SNAPSHOT` change.

## Review Focus

1. **The meta description** — nothing asserts it today; a wrong edit is invisible. Task 3 adds the assertion.
2. **The served HTML comment** — `document.html` ships a normal `<!-- … -->` comment describing the section; it is
   visible in the browser's source. The new test asserts the rendered HTML carries no `l'IA`.
3. **The static no-JS fallbacks** — the text inside `th:text` elements never reaches `MockMvc` output (the model
   replaces it), so only a template read or a grep proves it; Task 4 does both.
4. **Two copies of every string** — production and the prototype's `PROBLEM_INSTRUCTIONS` / static markup. A string
   changed in one place only leaves the review and the app disagreeing; Task 4 compares them.
5. **The waiting hint keeps its two branches** — `UPLOADED` must keep its approved wording; only `EXTRACTING` changes.

---

### Task 1: The two problem instructions (`messages.properties`)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/messages.properties:41,43`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabelsTest.kt:42-56`

**Interfaces:**
- Consumes: `DocumentFieldLabels.instruction(code: String): String`, which resolves `document.problem.<code>` from the
  `messages` bundle (an unknown code falls back to `document.problem.unknown`).
- Produces: nothing new; the same method now returns the neutral copy.

- [ ] **Step 1: Write the failing expectations**

In `DocumentFieldLabelsTest`, in `each problem code has its instruction`, replace the two `assertEquals` expected
values with:

```kotlin
assertEquals(
    "Le document n'a pas pu être lu. Remplacez ses pages par des photos plus nettes, droites et sans reflet, " +
        "ou supprimez-le et ajoutez-le de nouveau.",
    labels.instruction("DOCUMENT_UNREADABLE"),
)
assertEquals(
    "Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est floue, " +
        "remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.",
    labels.instruction("DOCUMENT_FIELD_LOW_CONFIDENCE"),
)
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=DocumentFieldLabelsTest`
Expected: FAIL on both assertions, the actual values still starting with `L'IA`.

- [ ] **Step 3: Replace the two bundle values**

In `messages.properties`, set exactly:

```properties
document.problem.DOCUMENT_UNREADABLE=Le document n'a pas pu être lu. Remplacez ses pages par des photos plus nettes, droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.
document.problem.DOCUMENT_FIELD_LOW_CONFIDENCE=Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est floue, remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.
```

Leave every other `document.problem.*` line untouched.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=DocumentFieldLabelsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/messages.properties \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/DocumentFieldLabelsTest.kt
git commit -m "feat(npc): word the mandate document problem instructions without the AI"
```

### Task 2: The waiting hint and the expiry notice (`MandateDocumentReadingViews`)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViews.kt:11-26`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViewsTest.kt:26-44`

**Interfaces:**
- Consumes: `DocumentStatus.EXTRACTING` selects `WAITING_EXTRACTING`; a document whose `problemCodes` contain
  `DOCUMENT_EXPIRED` and whose `expiresAt` is before `today` gets `DocumentReadingView.expiry.since = ALREADY_EXPIRED`.
- Produces: the same members with neutral values; `WAITING_UPLOADED`, `EXPIRED_SINCE`, `FIND_A_VALID_ONE`, `CLOSED`,
  `CANCELLED` are unchanged.

- [ ] **Step 1: Write the failing expectations**

In `MandateDocumentReadingViewsTest`'s companion object, replace the constants with:

```kotlin
const val WAITING_EXTRACTING =
    "La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire."
const val ALREADY_EXPIRED = "Elle était déjà expirée lors de sa lecture."
const val LOW_CONFIDENCE =
    "Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est floue, " +
        "remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau."
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentReadingViewsTest`
Expected: FAIL on the EXTRACTING waiting case, the already-expired notice and the low-confidence instruction.

- [ ] **Step 3: Replace the three constants and neutralise the KDoc**

In `MandateDocumentReadingViews.kt`:

```kotlin
private const val WAITING_EXTRACTING =
    "La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire."
private const val ALREADY_EXPIRED = "Elle était déjà expirée lors de sa lecture."
```

In the object's KDoc, describe the data as "what was read on a document" instead of "what the AI read" (the same
wording the prototype uses). Change nothing else in the file.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentReadingViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViewsTest.kt
git commit -m "feat(npc): neutralise the document waiting and expiry notices"
```

### Task 3: The document page — description, no-JS fallbacks, shipped comment

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html:3,10,57-85`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt:396-400,438-476`

**Interfaces:**
- Consumes: `fragments/head :: head(title, description, cdn)` renders `<meta name="description" th:content="${description}">`;
  `MandateDocumentReadingViews` supplies `reading.waiting` and `reading.expiry.since`.
- Produces: the rendered page's meta description and the static fallback texts; the Thymeleaf model and controller are
  untouched.

- [ ] **Step 1: Write the failing expectations**

In `MandateDocumentTemplateTest`:

1. In `document - waiting for the reading`, replace the `DocumentStatus.EXTRACTING` expected value with

```kotlin
"La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire.",
```

2. In `document - a failed reading shows one instruction per code`, replace the second expected instruction with

```kotlin
"Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le sur la pièce : si la photo est floue, " +
    "remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.",
```

3. In `document - DOCUMENT_EXPIRED is the notice, never an instruction`, replace the expected notice with

```kotlin
assertEquals("Elle était déjà expirée lors de sa lecture.", notice.selectFirst("[data-expired-when]")?.text())
```

4. Add one test for the head and the served comment:

```kotlin
@Test
fun `document - nothing names the AI`() {
    val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID"))

    assertEquals(
        "Un document d'un mandat : ce qui en a été lu, ses problèmes, ses pages.",
        page.selectFirst("meta[name=description]")?.attr("content"),
    )
    assertFalse(page.html().contains("l'IA"))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: FAIL on the EXTRACTING hint, the low-confidence instruction, the expiry notice and the new meta assertion.

- [ ] **Step 3: Apply the approved texts to the template**

In `document.html`:

1. Head, `fragments/head :: head(...)` description argument:
   `|Un document d'un mandat : ce qui en a été lu, ses problèmes, ses pages.|`
2. Expiry paragraph fallback: `Elle était déjà expirée lors de sa lecture.`
3. Waiting paragraph fallback:
   `La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire.`
4. In the file's comments (the Thymeleaf `<!--/* … */-->` header and the `<!-- … -->` blocks), say "what was read"
   instead of "what the AI read" / "when the AI read it". Change no markup, class, attribute or model expression.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt
git commit -m "feat(npc): neutralise the mandate document page description and fallbacks"
```

### Task 4: Verify no AI wording survives and the module is green

**Files:**
- Verify only; no file expected to change.

**Interfaces:**
- Consumes: the three tasks above.
- Produces: the evidence the story's acceptance criteria 5, 6 and 7 ask for.

- [ ] **Step 1: Grep the rendered strings and the template**

```bash
rg -n "l'IA|L'IA|intelligence artificielle" \
  modules/ndopify-partner-central/src/main/resources/messages.properties \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentReadingViews.kt
```

Expected: no output.

- [ ] **Step 2: Compare production copy with the approved prototype**

```bash
rg -n "Le document n'a pas pu être lu|Un champ obligatoire n'a pas pu être lu|La lecture du document est en cours|Elle était déjà expirée lors de sa lecture|ce qui en a été lu" \
  modules/ndopify-partner-central/src/main/html/v2/mandate/document.html \
  modules/ndopify-partner-central/src/main/html/v2/assets/js/mandate-documents.js
```

Expected: each of the five strings appears in the prototype with exactly the wording the production files now carry
(the prototype's description says *du mandat* where production says *d'un mandat*: that difference is approved).

- [ ] **Step 3: Read the static fallbacks by hand**

Open `templates/mandate/document.html`: the text inside the `[data-expired-when]` and `[data-waiting-text]`
`th:text` paragraphs is the no-JS rendering and must read the new copy (MockMvc replaces it, so no test sees it).

- [ ] **Step 4: Run the module's full verification**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS — ktlint, unit and template tests, JaCoCo gate and `page-scripts.test.mjs`.

- [ ] **Step 5: Commit (only if a file changed)**

If Steps 1-4 required no edit, nothing to commit; otherwise commit the fix with
`git commit -m "fix(npc): align the mandate document copy with the prototype"`.
