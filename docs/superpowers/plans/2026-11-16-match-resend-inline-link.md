# Compact Match Resend Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move "Renvoyer le lien" from its own button row onto the match card's sent-status line as an inline text-style submit button, so the card keeps its height, without changing the resend API.

**Architecture:** The reviewed static prototype (`src/main/html`) is committed first, then the Thymeleaf template wraps the existing `match__answer` line and resend form in a `.match__status` flex row. A `.link-button--inline` variant drops the 44px min-height so the status line keeps its height. No DTO, server, web-core or database change.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, plain HTML/CSS/JS prototype.

**Spec:** GitHub issue [#184](https://github.com/wutsi/ndopify-mono/issues/184)

## Global Constraints

- UI copy is French; code, comments and docs are English.
- The resend stays a `POST` and a `type="submit"` button: it must work without JavaScript.
- The accessible name stays "Renvoyer le lien au client" (the `visually-hidden` " au client" remains).
- Only `modules/ndopify-partner-central` changes: no `ndopify-dto`, `ndopify-server`, `ndopify-web-core` or database change.
- The prototype is `src/main/html`; never serve it from Partner Central.
- `mvn verify` in `modules/ndopify-partner-central` is the final gate.

## Review Focus

1. **An ACTIVE match or one with an open visit must not render an empty status line.** The wrapper's `th:if` must match the current answer condition, and the resend form stays `resendable`-only.
2. **A long ACCEPTED line must wrap without an orphan separator.** The status text is "Intéressé le …" (latest step only), and there is no dot before the link, so a narrow phone wraps the link as a unit.
3. **The inline control must remain a no-JS POST.** It is a `type="submit"` button inside the existing form, not an `<a>` or a `type="button"`.
4. **The ~21px inline target must stay keyboard-focusable and named.** The global `:focus-visible` ring applies; the visually hidden " au client" keeps the accessible name complete.
5. **The standalone `.link-button` must keep its 44px target.** Only the new `--inline` variant drops `min-height`; "Renvoyer le code" and the other text-style buttons are unchanged.

---

### Task 1: Commit the reviewed prototype

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`

**Interfaces:**
- Consumes: the prototype reviewed in the grooming conversation (`http://localhost:4321/mandate.html?id=204`).
- Produces: the `.match__status`, `.match__resend` and `.link-button--inline` names and the visual reference Task 2 ports.

- [ ] **Step 1: Apply the reviewed prototype changes**

In `src/main/html/mandate.html`, replace the match template's answer paragraph and resend row with:

```html
<dl class="match__facts" data-match-facts></dl>
<!-- Sent: one meta line. A sent, unanswered match (or one the client is interested in
     but hasn't booked) also offers the link again, inline: the card keeps its height. -->
<div class="match__status" hidden data-match-status>
    <span class="match__answer" data-match-answer></span>
    <div class="match__resend" data-match-resend-actions hidden>
        <button type="button" class="link-button link-button--inline"
                data-match-resend>Renvoyer le lien<span
                class="visually-hidden"> au client</span></button>
    </div>
</div>
```

In the page head, bump the cache-busting query strings of the two changed assets and the script, for example:

```html
<link rel="stylesheet" href="assets/css/partner-central.css?v=20261116a">
<link rel="stylesheet" href="assets/css/home.css?v=20261116a">
<script src="assets/js/partner-central.js?v=20261116a" defer></script>
```

In `src/main/html/assets/css/home.css`, replace the `.match__answer` block with:

```css
/* The sent line: the date, then the client's link again when it can be resent. Both share one text line, so the
   action costs the card no height. */
.match__status {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    column-gap: var(--space-2);
    font-size: var(--step-hint);
}

.match__answer {
    font-size: inherit;
}

.match__resend {
    display: inline-flex;
    align-items: baseline;
}
```

In `src/main/html/assets/css/partner-central.css`, add after `.link-button:hover`:

```css
/* The same text-style button, inline in a meta line: no 44px target, so the line keeps its height. */
.link-button--inline {
    display: inline;
    min-height: 0;
}
```

In `src/main/html/assets/js/partner-central.js`, in `matchItem(match)`:

- add the status wrapper lookup and the `answerShown` flag:

```js
var statusLine = $("[data-match-status]", item);
var answerLine = $("[data-match-answer]", item);
var answerShown = match.status === "PROPOSED" || match.status === "EXPIRED";
answerLine.hidden = !answerShown;
```

- keep only the latest step for an interested match (the server's `MatchRules.answer` wording):

```js
answerLine.textContent = answer === "ACCEPTED" && match.answeredAt
    ? "Intéressé le " + formatShortDate(match.answeredAt)
    : sentText;
```

- hide the whole line when neither the answer nor the resend is shown:

```js
var resendActions = $("[data-match-resend-actions]", item);
var resend = match.status === "PROPOSED" && !match.visit;
resendActions.hidden = !resend;
statusLine.hidden = !answerShown && !resend;
```

- [ ] **Step 2: Verify the prototype at both widths**

Run, from `modules/ndopify-partner-central/src/main/html`:

```bash
python3 -m http.server 4321 --bind 127.0.0.1
```

Open `http://localhost:4321/mandate.html?id=204`, scroll to *Biens correspondants*, and check:

- **Intéressés (1)**: `Intéressé le …   Renvoyer le lien` on one line at 390px and 1280px.
- **Envoyés, en attente (1)**: `Envoyé le …   Renvoyer le lien` on one line at both widths.
- *À confirmer*, *Visites à venir*, *Nouveaux* and *Sans réponse* cards: no resend link.

Expected: no separate outline-button row; the two resendable cards are shorter than in the pre-change prototype.

- [ ] **Step 3: Commit the prototype**

```bash
git add modules/ndopify-partner-central/src/main/html/mandate.html \
  modules/ndopify-partner-central/src/main/html/assets/css/home.css \
  modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css \
  modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js
git commit -m "feat(npc): prototype the compact match resend link" -m "Refs #184"
```

---

### Task 2: Port it to the Thymeleaf page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html:422-436`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css:1244-1246`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css:532-534`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt:957-991`
- Modify: `modules/ndopify-partner-central/CLAUDE.md:318-321`

**Interfaces:**
- Consumes: Task 1's `.match__status`, `.match__resend` and `.link-button--inline` names.
- Produces: the production template with the same form action (`/mandates/{mid}/matches/{id}/resend`), the same button text and the same accessible name.

- [ ] **Step 1: Update the template test first**

In `MandateTemplateTest.resend - on a sent match without an open visit`, give the ACCEPTED fixture an answer date so its line is the interested one, then replace the block from `val proposed` through the `btn btn--outline` assertion with:

```kotlin
MatchFixtures.MATCH_B.copy(
    mandateId = 201,
    status = MatchStatus.ACCEPTED,
    proposedAt = proposedAt,
    answeredAt = proposedAt,
)
```

```kotlin
val proposed = page.selectFirst("[data-match-id=9001]")!!
val statusLine = proposed.selectFirst("[data-match-status]")!!
val form = statusLine.selectFirst("[data-match-resend-actions] form")!!
assertEquals("/mandates/201/matches/9001/resend", form.attr("action"))
assertEquals("post", form.attr("method"))
assertEquals("Renvoyer le lien au client", form.selectFirst("button")?.text())
assertEquals("link-button link-button--inline", form.selectFirst("button")?.className())
assertTrue(statusLine.selectFirst("[data-match-answer]")!!.text().startsWith("Envoyé le"))
```

In the same test, add the interested card's line and the ACTIVE card's absence:

```kotlin
assertTrue(
    page.selectFirst("[data-match-id=9003] [data-match-status] [data-match-answer]")!!
        .text().startsWith("Intéressé le"),
)
assertNull(page.selectFirst("[data-match-id=9002] [data-match-status]"))
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=MandateTemplateTest
```

Expected: FAIL — the rendered button still has class `btn btn--outline`, and there is no `[data-match-status]`.

- [ ] **Step 3: Change the template**

In `src/main/resources/templates/mandate.html`, replace the answer paragraph and the resend `th:block` with:

```html
<div class="match__status"
     th:if="${match.visit != null ? !#strings.isEmpty(match.sent) : match.answer != null}"
     data-match-status>
    <p class="match__answer" th:text="${match.visit != null ? match.sent : match.answer}"
       data-match-answer>Envoyé le 28 octobre 2026</p>
    <div class="match__resend" th:if="${match.resendable}" data-match-resend-actions>
        <form method="post"
              th:action="@{/mandates/{mid}/matches/{id}/resend(mid=${m.id},id=${match.id})}">
            <button type="submit" class="link-button link-button--inline" data-match-resend>Renvoyer le lien<span
                    class="visually-hidden"> au client</span></button>
        </form>
    </div>
</div>
```

- [ ] **Step 4: Add the CSS**

In `src/main/resources/public/assets/css/home.css`, replace the `.match__answer` block with the same `.match__status` / `.match__answer` / `.match__resend` block as Task 1.

In `src/main/resources/public/assets/css/partner-central.css`, add the same `.link-button--inline` variant after `.link-button:hover`.

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=MandateTemplateTest
```

Expected: PASS.

- [ ] **Step 6: Document the inline action**

In `modules/ndopify-partner-central/CLAUDE.md`, in the match page paragraph, change the resend sentence to say it sits inline on the sent-status line and uses `.link-button--inline`, not a button row. Keep the endpoint, `RESENDABLE`, the outcomes and the throttle wording.

- [ ] **Step 7: Verify the module and commit**

```bash
cd modules/ndopify-partner-central
mvn verify
```

Expected: BUILD SUCCESS.

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): show the match resend inline on the sent line" -m "Refs #184"
```

---

## Manual verification (after Task 2)

Start Partner Central against the local ndopify-server, open a signed search's mandate page (*Biens correspondants*), and check a `PROPOSED` card and an `ACCEPTED` card without a visit:

- the link sits on the sent-status line, with no outline-button row;
- it posts and shows the same `resent` / throttle flash as before;
- a card with a visit, and an `ACTIVE` card, show no link;
- at 390px both labels stay on one line; at 320px the link may wrap below the status without a leading dot;
- the login page's "Renvoyer le code" still has the standalone `.link-button` 44px target.
