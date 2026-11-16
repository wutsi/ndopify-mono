# Public web MVP alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the pre-MVP copy of the public site's home, owner and agent pages, and the match page's answer copy, with the MVP prototype's — content only, no behaviour change.

**Architecture:** Copy edits to three Thymeleaf landing templates plus the match template, and one Kotlin change for the two answer flash sentences in `MatchPages`. The prototype `modules/ndopify-web/src/main/html/mvp/` is the source of truth: each task copies its text into the served template while keeping the template's Thymeleaf bindings, CTAs and URL guards untouched.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, Maven, Jsoup, JUnit 5 + `kotlin.test`.

**Spec:** GitHub story #190 — https://github.com/wutsi/ndopify-mono/issues/190 — and `modules/ndopify-web/src/main/html/mvp/README.md` (commit `e91b6f96`).

## Global Constraints

- Content only: no controller, client, security configuration, JavaScript or CSS edit; no new page, route or form; the CTAs (`.cta-row a`, 4 per page) and their `th:if` URL guards stay exactly as they are.
- Never modify `modules/ndopify-web/src/main/html/**` (both prototypes), `templates/listing.html`, `templates/fragments/listing-body.html` or `templates/visit.html`.
- UI text is French; comments are English.
- The `index.html` FAQ keeps **7** items: the prototype's 8 minus *« Les visites sont-elles payantes ? »*. No page may promise that visits are free (the visit fee is reworked later).
- In `index.html`, the second proof stat reads *0 FCFA* / *pour lancer votre recherche* — not *… et visiter*.
- Keep each served template's head bindings (`tenant` logo/icon fallbacks, canonical, `og:image`, `og:url`, JSON-LD) and its body attributes, nav and footer bindings; only their text changes.
- Conventional commits: `feat(web): …`. `git status` is clean at the end except the plan itself.
- `mvn verify` in `modules/ndopify-web` is green; coverage does not drop.

## Review Focus

- French typography: copy the prototype's characters verbatim (non-breaking spaces `&nbsp;` before `?`, `!`, `:`; `«`/`»`). A rewritten string with a plain space before `?` is a defect.
- Thymeleaf: in `th:text="|…|"`, a literal `|` would end the expression; the agent's first name is `${#strings.capitalize(page.agentFirstName)}`.
- A page rendered without its Partner Central URL has no `.cta-row` at all — the existing tests pin this, so a copy edit must not remove the `th:if`.
- No pre-MVP promise left anywhere in the four templates: *Pass Visite*, *sous 24 h*, *Poser une question*, certified meters/photos/videos/GPS as a marketing guarantee, *NIU*, *l'équipe examine votre dossier*.
- Answers stay final: no copy may offer *changer d'avis* / *Finalement, pas pour moi*.

---

### Task 1: Home page copy

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/index.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/home/IndexTemplateTest.kt`

**Interfaces:**
- Consumes: nothing new; the template keeps `tenant` and `findPropertyUrl` from the model.
- Produces: nothing new; `HomeController.VIEW` and the page contract are unchanged.

- [ ] **Step 1: Write the failing test**

In `IndexTemplateTest`, add:

```kotlin
    @Test
    fun `no pre-MVP promise`() {
        val text = render("tenant" to tenant, "findPropertyUrl" to "https://partner.ndopify.cm/find-property").text()

        listOf("Pass Visite", "sous 24 h", "visites sont-elles payantes", "vidéos", "GPS").forEach {
            assertFalse(text.contains(it), it)
        }
        assertTrue(text.contains("Lancer ma recherche"))
    }
```

Add the `assertFalse` import (`kotlin.test.assertFalse`).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=IndexTemplateTest`
Expected: FAIL — the served page still contains *Pass Visite*.

- [ ] **Step 3: Replace the page's text with the prototype's**

Source: `modules/ndopify-web/src/main/html/mvp/index.html`. Copy its text verbatim into the served template, section by section, keeping the served file's `th:*` attributes, `<body>` attributes, nav and footer markup:

- `<title>`, `meta[name=description]`, `og:title`, `og:description` — the prototype's strings.
- Hero: `hero__eyebrow`, `h1`, `hero__sub`, the three `proof-stats`.
- The four guarantee cards, the four steps, and the FAQ — **all of the prototype's items except the 6th** (*Les visites sont-elles payantes ?*); renumber the FAQ ids `faq-1` … `faq-7` and their `aria-controls`/`id` pairs.
- Every CTA label becomes **Lancer ma recherche**; keep `th:if="${findPropertyUrl}"` and `th:href="${findPropertyUrl}"`.
- Footer tagline: *Des agents vérifiés, à votre service.*

Deltas from the prototype, because of the visit-fee decision:
- second proof stat: `<li><strong>0 FCFA</strong>pour lancer votre recherche</li>` (drop *et visiter*).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=IndexTemplateTest`
Expected: PASS (including the existing CTA, tenant-logo and no-prototype-link tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/index.html \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/home/IndexTemplateTest.kt
git commit -m "feat(web): rewrite the home page copy for the MVP"
```

---

### Task 2: Owner page copy

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/owner.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/owner/OwnerTemplateTest.kt`

**Interfaces:**
- Consumes: nothing new; the template keeps `tenant` and `offerPropertyUrl`.
- Produces: nothing new; `OwnerController.VIEW` and the page contract are unchanged.

- [ ] **Step 1: Write the failing test**

In `OwnerTemplateTest`, add:

```kotlin
    @Test
    fun `no pre-MVP promise`() {
        val text = render("tenant" to tenant, "offerPropertyUrl" to "https://partner.ndopify.cm/offer-property").text()

        listOf("sous 24 h", "vidéos", "Pass Visite", "l'équipe examine votre dossier").forEach {
            assertFalse(text.contains(it), it)
        }
        assertTrue(text.contains("Proposer mon bien"))
    }
```

Add the `assertFalse` import if missing.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=OwnerTemplateTest`
Expected: FAIL — the served page still promises *Photos, vidéos…*.

- [ ] **Step 3: Replace the page's text with the prototype's**

Source: `modules/ndopify-web/src/main/html/mvp/bailleur.html`. Copy its text verbatim, keeping the served file's `th:*` attributes, `<body>` attributes, nav and footer markup:

- `<title>`, `meta[name=description]`, `og:title`, `og:description`.
- Hero (`hero__eyebrow`, `h1`, `hero__sub`, three `proof-stats`), the four guarantee cards, the four steps, and all six FAQ items (renumber `faq-1` … `faq-6`).
- CTA labels: **Proposer mon bien**, with `th:if="${offerPropertyUrl}"` / `th:href="${offerPropertyUrl}"` unchanged.
- Footer tagline: *Des agents vérifiés, à votre service.*

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=OwnerTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/owner.html \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/owner/OwnerTemplateTest.kt
git commit -m "feat(web): rewrite the owner page copy for the MVP"
```

---

### Task 3: Agent page copy

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/agent.html`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/agent/AgentTemplateTest.kt`

**Interfaces:**
- Consumes: nothing new; the template keeps `tenant` and `joinUrl`.
- Produces: nothing new; `AgentController.VIEW` and the page contract are unchanged.

- [ ] **Step 1: Write the failing test**

In `AgentTemplateTest`, add:

```kotlin
    @Test
    fun `no pre-MVP promise`() {
        val text = render("tenant" to tenant, "joinUrl" to "https://partner.ndopify.cm/join").text()

        listOf("NIU", "indemnité", "sous 24 h", "anti-sautage").forEach {
            assertFalse(text.contains(it), it)
        }
        assertTrue(text.contains("Rejoindre le réseau"))
    }
```

Add the `assertFalse` import if missing.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=AgentTemplateTest`
Expected: FAIL — the served page still promises the *Indemnité de visite* and *anti-sautage*.

- [ ] **Step 3: Replace the page's text with the prototype's**

Source: `modules/ndopify-web/src/main/html/mvp/agent.html`. Copy its text verbatim, keeping the served file's `th:*` attributes, `<body>` attributes, nav and footer markup:

- `<title>`, `meta[name=description]`, `og:title`, `og:description`.
- Hero (`hero__eyebrow`, `h1`, `hero__sub`, three `proof-stats`), the four cards (*Ce que Ndopify fait pour vous*), the four steps, and all seven FAQ items (renumber `faq-1` … `faq-7`).
- CTA labels: **Rejoindre le réseau**, with `th:if="${joinUrl}"` / `th:href="${joinUrl}"` unchanged.
- Footer tagline: *Des agents vérifiés, à votre service.*

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest=AgentTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/agent.html \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/agent/AgentTemplateTest.kt
git commit -m "feat(web): rewrite the agent page copy for the MVP"
```

---

### Task 4: Match page answer copy

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt:121-123`
- Modify: `modules/ndopify-web/src/main/resources/templates/match.html` (REJECTED block, ACCEPTED-no-visit sentence, header comment)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchPage.flash`, `page.agentFirstName` (already on the page).
- Produces: nothing new; `AnswerState` and `MatchPages.of(…)` keep their signatures.

- [ ] **Step 1: Write the failing tests**

In `MatchPagesTest`, replace the two expected flashes:

```kotlin
        assertEquals("C'est noté : ce logement vous intéresse, votre agent est prévenu.", page.flash)
```

(in `accepted`, and the same string at line 278) and:

```kotlin
        assertEquals("C'est noté : vous avez écarté ce logement, votre agent est prévenu.", page.flash)
```

In `MatchTemplateTest`, replace the `never says the agent got the answer` test with:

```kotlin
    @Test
    fun `says the agent got the answer`() {
        val rejected = render(page(MatchStatus.REJECTED, answer = "rejected"))
        assertEquals("C'est noté : vous avez écarté ce logement, votre agent est prévenu.", rejected.selectFirst(".proposal-flash")?.text())
        assertEquals(
            "Serge est prévenu et continue de chercher pour vous : il vous enverra d'autres logements.",
            rejected.select("[data-answer=REJECTED] p")[1].text(),
        )
        val accepted = render(page(MatchStatus.ACCEPTED, answer = "accepted"))
        assertEquals("C'est noté : ce logement vous intéresse, votre agent est prévenu.", accepted.selectFirst(".proposal-flash")?.text())
    }
```

Update the two remaining accepted-flash assertions in `MatchTemplateTest` (`accepted` and `accepted - next step asks for a visit`, both *C'est noté : ce logement vous intéresse.*) to the new sentence.

In the `no visit request` test, add the ACCEPTED-no-visit sentence:

```kotlin
        assertEquals(
            "Serge est prévenu. Pour organiser une visite, contactez-le : vous pourrez la demander ici dès que votre mandat de recherche sera signé.",
            doc.selectFirst("[data-answer=ACCEPTED] p:not(.answer__status)")?.text(),
        )
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=MatchPagesTest,MatchTemplateTest`
Expected: FAIL on the flash and copy assertions.

- [ ] **Step 3: Implement the copy**

In `MatchPages.kt`, the `flash = when { … }` block:

```kotlin
                answer == "accepted" && state == AnswerState.ACCEPTED ->
                    "C'est noté : ce logement vous intéresse, votre agent est prévenu."
                answer == "rejected" && state == AnswerState.REJECTED ->
                    "C'est noté : vous avez écarté ce logement, votre agent est prévenu."
```

In `match.html`:

- REJECTED block, second paragraph:

```html
                        <p th:text="|${#strings.capitalize(page.agentFirstName)} est prévenu et continue de chercher pour vous : il vous enverra d'autres logements.|">Serge est prévenu et continue de chercher pour vous : il vous enverra d'autres logements.</p>
```

- ACCEPTED block, the sentence shown when the mandate isn't approved and no visit is open:

```html
                        <p th:if="${page.visit?.form == null and page.visit?.open == null}"
                           th:text="|${#strings.capitalize(page.agentFirstName)} est prévenu. Pour organiser une visite, contactez-le : vous pourrez la demander ici dès que votre mandat de recherche sera signé.|">Serge est prévenu. Pour organiser une visite, contactez-le : vous pourrez la demander ici dès que votre mandat de recherche sera signé.</p>
```

- The template header comment (the `ACCEPTED` line): say the page states that the agent was told, and that the mandate-not-approved case is the “interested, no visit yet” block.

- [ ] **Step 4: Run the module's tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test`
Expected: PASS.

- [ ] **Step 5: Run the full module verification**

Run: `cd modules/ndopify-web && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt \
        modules/ndopify-web/src/main/resources/templates/match.html \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt
git commit -m "feat(web): say the agent was told of the customer's answer"
```
