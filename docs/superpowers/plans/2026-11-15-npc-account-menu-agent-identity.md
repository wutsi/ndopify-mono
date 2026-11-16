# NPC Account Menu Agent Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open Partner Central's account menu with an identity block — the signed-in agent's photo or initials, its full name and its e-mail — that links to its agent page (`/agent?id=…`), followed by a divider and *Modifier mon profil* / *Déconnexion*.

**Architecture:** The account menu (toggle + list) becomes one Thymeleaf fragment, `templates/fragments/user-menu.html`, used by the 14 signed-in pages instead of 14 inline copies. `ProfileRules.header(agent, model)` fills its model attributes; KYC, which reads only the party today, also reads the signed-in agent (`CurrentAgent`) to know the agent id. The prototype in `src/main/html/` (22 pages) is updated first and shown to the user before any served-code edit.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Thymeleaf, JUnit 5 + `kotlin.test` + mockito-kotlin + Jsoup, ktlint.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/171

## Global Constraints

- The prototype in `modules/ndopify-partner-central/src/main/html/` is the visual reference and the served fragment must match it. Nothing there is served, nor deleted. The prototype is built and shown to the user **before** the model, controller or served-template edits, and its OK gates Task 2.
- All user-facing text is French; code, comments, KDoc and commit messages are English.
- No `ndopify-dto`, `ndopify-web-core`, `ndopify-server` or database change: `ndopify-partner-central` only.
- The menu renders only when `signedIn` (`SignedInAdvice`): public pages and error pages have none.
- The `/agent` page is only ever opened for the signed-in agent's own id; the identity block is its only entry point from the menu.
- Assertions use `kotlin.test`, mocks `mockito-kotlin`, and tests mirror the main package.
- `mvn verify` in `modules/ndopify-partner-central` stays green and the JaCoCo gate does not drop.
- One commit per task, conventional commits (`feat(npc): …`, `refactor(npc): …`).

## Review Focus

- A signed-in page must never render an empty identity block nor `/agent?id=` without an id: `ProfileRules.header` sets `userAgentId`, `userName` and `userEmail`, and `KycController` gets the agent id from `CurrentAgent` — asserted in `ProfileRulesTest` (Task 2) and the two rendering tests (Tasks 3 and 4).
- An agent with no photo must show its initials in the block, never a broken image: `HomeTemplateTest` (Task 3) renders the `photoUrl = null` variant.
- A signed-out visitor (public pages, expired token) must see no menu at all: the `signedIn` guard moves inside the fragment; `KycTemplateTest` and `ProfileTemplateTest` signed-out tests stay green (Task 4).
- A long name or e-mail must wrap inside the menu instead of widening it on a phone: the e-mail is its own element (`.user-menu__account-email` with `overflow-wrap: anywhere`), asserted in `HomeTemplateTest` (Task 3) and checked on the prototype (Task 1).
- The identity block must stay the first item, above the divider and before *Modifier mon profil*: `HomeTemplateTest` asserts the first list item holds it (Task 3); `ProfileTemplateTest` asserts it on the profile page, including `aria-current` (Task 4).

---

## File Structure

- `modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css` — prototype styles of the identity block and its divider.
- `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js` — the prototype fills the initials/e-mail of every hook, not just the first.
- `modules/ndopify-partner-central/src/main/html/**/*.html` — the 22 prototype pages that show the menu.
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileRules.kt` — the menu's model attributes.
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt` — reads `CurrentAgent` for the menu.
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileController.kt` — marks the profile page in the menu (`accountCurrent`).
- `modules/ndopify-partner-central/src/main/resources/templates/fragments/user-menu.html` — the one account menu.
- `modules/ndopify-partner-central/src/main/resources/templates/**/*.html` — the 14 pages that include the fragment.
- `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css` — served styles of the identity block.
- `modules/ndopify-partner-central/CLAUDE.md` — the menu paragraph (identity block, fragment).
- Tests mirror the main paths under `modules/ndopify-partner-central/src/test/kotlin/`.

---

### Task 1: Prototype the account menu

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css:333` (`.user-menu__email`)
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js:173-187`
- Modify (root pages, links `agent.html` / `profile/edit.html`): `src/main/html/agent.html`, `index.html`, `kyc.html`, `listing.html`, `mandate.html`, `telegram.html`, `visit.html`
- Modify (nested pages, links `../agent.html` / `../profile/edit.html`): `src/main/html/kyc/confirmation.html`, `kyc/index.html`, `kyc/revue.html`, `listing/bien.html`, `listing/conditions.html`, `listing/emplacement.html`, `listing/equipements.html`, `listing/photos.html`, `listing/position.html`, `listing/remarques.html`, `mandate/close.html`, `mandate/customer-edit.html`, `mandate/edit.html`, `profile/edit.html`, `profile/photo.html`

**Interfaces:**
- Produces: the markup and CSS Task 3's fragment mirrors — `.user-menu__account`, `.user-menu__account-photo`, `.user-menu__account-text`, `.user-menu__account-name`, `.user-menu__account-email`, the *Modifier mon profil* label.

- [ ] **Step 1: Replace the prototype's e-mail line styles with the identity block's**

In `src/main/html/assets/css/partner-central.css`, replace the `.user-menu__email` rule with:

```css
.user-menu__account {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-3);
    color: var(--color-fg);
    text-decoration: none;
    border-bottom: 1px solid var(--color-border);
    margin-bottom: var(--space-1);
}

.user-menu__account:hover {
    background: var(--color-surface-alt);
}

.user-menu__account-photo {
    display: grid;
    place-items: center;
    flex: none;
    width: 2.5rem;
    height: 2.5rem;
    border-radius: var(--radius-control);
    background: var(--color-bg-subtle);
    font-size: var(--step-hint);
    overflow: hidden;
}

.user-menu__account-photo img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.user-menu__account-text {
    display: flex;
    flex-direction: column;
    min-width: 0;
}

.user-menu__account-name {
    font-weight: 600;
}

.user-menu__account-email {
    font-size: var(--step-hint);
    overflow-wrap: anywhere;
}
```

- [ ] **Step 2: Replace the e-mail line with the identity block in the root pages**

In each root page listed above, replace `<li class="user-menu__email" data-user-email></li>` with:

```html
<li>
    <a class="user-menu__account" href="agent.html">
        <span class="user-menu__account-photo" aria-hidden="true" data-user-initials>SN</span>
        <span class="user-menu__account-text">
            <span class="user-menu__account-name">Serge Ngono</span>
            <span class="user-menu__account-email" data-user-email>serge@gmail.com</span>
        </span>
    </a>
</li>
```

- [ ] **Step 3: Do the same in the nested pages**

Same block, with `href="../agent.html"`. In `profile/edit.html` and `profile/photo.html` the profile link below stays `href="edit.html"`; `profile/edit.html` keeps `aria-current="page"`.

- [ ] **Step 4: Rename the profile entry on every page**

Rename the entry text `Profil` to `Modifier mon profil` on every page (the href and `aria-current` stay as they are).

- [ ] **Step 5: Fill every hook in the prototype script**

In `initUserMenu` of `src/main/html/assets/js/partner-central.js`, update all nodes instead of the first:

```js
$all("[data-user-initials]").forEach(function (el) {
    el.textContent = initials;
});
$all("[data-user-email]").forEach(function (el) {
    if (email) {
        el.textContent = email;
    } else {
        el.hidden = true;
    }
});
```

- [ ] **Step 6: Verify the prototype covers every page**

Run:

```bash
grep -rl 'user-menu__email' modules/ndopify-partner-central/src/main/html
grep -rL 'user-menu__account' $(grep -rl 'user-menu"' modules/ndopify-partner-central/src/main/html --include='*.html')
grep -rn '>Profil<' modules/ndopify-partner-central/src/main/html
```

Expected: no output from the first and third; the second lists nothing (every page with the menu has the block).

- [ ] **Step 7: Show the prototype and wait for the OK**

Open `src/main/html/index.html`, `agent.html` and `profile/edit.html` (or the whole `src/main/html/`) for the user, and stop: Task 2 starts only once the design is accepted. If the feedback changes it, redo the affected steps before Task 2.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the account menu identity block"
```

---

### Task 2: The menu's model attributes

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileRules.kt:25-29`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt:46-49` (constructor), `:73`, `:183`, `:200` (call sites), `:222-225` (`header`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt:119-128`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt:66-72`, `:84-95`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt:63-90`

**Interfaces:**
- Produces: model attributes `userAgentId: Long` (the agent's id), `userName: String` (`"Prénom Nom"`), `userEmail: String` (the party's e-mail), beside the existing `userPhotoUrl: String?` and `initials: String`; `KycController(kycClient: NdKycClient, currentParty: CurrentParty, currentAgent: CurrentAgent)`.
- Consumes: `Agent.id`, `Agent.party`, `ProfileRules.fullName(agent)`.

- [ ] **Step 1: Write the failing tests**

In `ProfileRulesTest`, add:

```kotlin
@Test
fun `header names the signed-in agent`() {
    val model = ExtendedModelMap()

    ProfileRules.header(AGENT, model)

    assertEquals(20L, model["userAgentId"])
    assertEquals("Serge Ngono", model["userName"])
    assertEquals("serge@gmail.com", model["userEmail"])
    assertEquals(AGENT.party.photoUrl, model["userPhotoUrl"])
    assertEquals("SN", model["initials"])
}
```

(import `org.springframework.ui.ExtendedModelMap`)

In `HomeControllerTest.show`, beside the `initials` assertion:

```kotlin
assertEquals(AGENT.id, model(result)["userAgentId"])
assertEquals("Serge Ngono", model(result)["userName"])
assertEquals("serge@gmail.com", model(result)["userEmail"])
```

In `KycControllerTest`: add `private val currentAgent = mock<CurrentAgent>()`, hold the party in a `val`, return an agent with that party from the mock, pass `currentAgent` to `KycController`, and assert in `new case - first step`:

```kotlin
assertEquals(20L, model(result)["userAgentId"])
assertEquals("Ray Sponsible", model(result)["userName"])
assertEquals("ray@gmail.com", model(result)["userEmail"])
```

In `KycTemplateTest`: the same constructor and mocks (no new assertion yet).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ProfileRulesTest,HomeControllerTest,KycControllerTest'`
Expected: FAIL — `userAgentId` is null, and the KYC tests don't compile (three-argument constructor).

- [ ] **Step 3: Implement the attributes**

`ProfileRules.header` gains the three attributes (KDoc: the block's name, e-mail and link target, the photo/initials rule unchanged):

```kotlin
fun header(agent: Agent, model: Model) {
    model.addAttribute("userAgentId", agent.id)
    model.addAttribute("userName", fullName(agent))
    model.addAttribute("userEmail", agent.party.email)
    model.addAttribute("userPhotoUrl", agent.party.photoUrl?.ifBlank { null })
    model.addAttribute("initials", KycRules.initials(agent.party.firstName, agent.party.lastName))
}
```

`KycController` takes `private val currentAgent: CurrentAgent`, its `header` loses the party parameter and delegates the menu attributes to the agent:

```kotlin
private fun header(model: Model) {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    ProfileRules.header(agent, model)
}
```

and its three call sites become `header(model)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ProfileRulesTest,HomeControllerTest,KycControllerTest,KycTemplateTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src
git commit -m "feat(npc): name the signed-in agent in the header model"
```

---

### Task 3: The account menu fragment and the Espace agent

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/user-menu.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html:51-69`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css:307-352`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: the Task 2 attributes (`signedIn`, `userAgentId`, `userName`, `userEmail`, `userPhotoUrl`, `initials`) and optional `accountCurrent` (`"profile"` on the profile edit page).
- Produces: fragment `~{fragments/user-menu :: menu}`, rendered as `<nav class="user-menu">`; classes `.user-menu__account`, `.user-menu__account-photo`, `.user-menu__account-text`, `.user-menu__account-name`, `.user-menu__account-email`; the link `@{/agent(id=${userAgentId})}`; the entries *Modifier mon profil* (`/profile`) and *Déconnexion* (`POST /logout`).

- [ ] **Step 1: Write the failing template tests**

In `HomeTemplateTest`, add:

```kotlin
@Test
fun `the account menu names the signed-in agent and opens its agent page`() {
    val page = render(get("/"))

    val account = page.selectFirst(".user-menu__list > li")!!.selectFirst("a.user-menu__account")!!
    assertEquals("/agent?id=20", account.attr("href"))
    assertEquals("Serge Ngono", account.selectFirst(".user-menu__account-name")?.text())
    assertEquals("serge@gmail.com", account.selectFirst(".user-menu__account-email")?.text())
    assertEquals(AGENT.party.photoUrl, account.selectFirst(".user-menu__account-photo img")?.attr("src"))
    assertEquals("Modifier mon profil", page.select(".user-menu__item").first()?.text())
    assertEquals("/profile", page.select(".user-menu__item").first()?.attr("href"))
    assertNull(page.selectFirst(".user-menu__item[aria-current]"))
}

@Test
fun `an agent without a photo shows its initials in the account block`() {
    doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = null))).whenever(currentAgent).get()

    val account = render(get("/")).selectFirst("a.user-menu__account")!!

    assertEquals("SN", account.selectFirst(".user-menu__account-photo span")?.text())
    assertNull(account.selectFirst(".user-menu__account-photo img"))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=HomeTemplateTest`
Expected: FAIL — `.user-menu__account` does not exist.

- [ ] **Step 3: Create the fragment**

Create `templates/fragments/user-menu.html` with the menu, in French, with a leading HTML comment naming its model attributes:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<body>
<!--/*
  The header's account menu, on every signed-in page: the signed-in agent's identity — photo or initials, full name,
  e-mail — opening its own agent page (/agent?id=…), then a divider and the entries "Modifier mon profil" (/profile)
  and "Déconnexion" (POST /logout: a form, never a link). Model attributes: "signedIn", "userAgentId", "userName",
  "userEmail", "userPhotoUrl", "initials" (see ProfileRules.header), and optional "accountCurrent" ("profile" marks
  the profile edit page).
*/-->
<nav th:fragment="menu" class="user-menu" aria-label="Compte" th:if="${signedIn}">
    <button type="button" class="user-menu__toggle" aria-expanded="false" aria-controls="user-menu"
            data-user-menu-toggle>
        <span class="visually-hidden">Menu du compte</span>
        <img class="user-menu__photo" th:if="${userPhotoUrl}" th:src="${userPhotoUrl}" alt="" width="44" height="44"
             data-user-photo>
        <span aria-hidden="true" data-user-initials th:unless="${userPhotoUrl}" th:text="${initials}">NP</span>
    </button>
    <ul id="user-menu" class="user-menu__list" hidden data-user-menu-list>
        <li>
            <a class="user-menu__account" th:href="@{/agent(id=${userAgentId})}">
                <span class="user-menu__account-photo" aria-hidden="true">
                    <img th:if="${userPhotoUrl}" th:src="${userPhotoUrl}" alt="" width="40" height="40">
                    <span th:unless="${userPhotoUrl}" th:text="${initials}">NP</span>
                </span>
                <span class="user-menu__account-text">
                    <span class="user-menu__account-name" th:text="${userName}">Serge Ngono</span>
                    <span class="user-menu__account-email" th:text="${userEmail}">serge@gmail.com</span>
                </span>
            </a>
        </li>
        <li>
            <a class="user-menu__item" th:href="@{/profile}"
               th:attr="aria-current=${accountCurrent == 'profile' ? 'page' : null}">Modifier mon profil</a>
        </li>
        <li>
            <form method="post" th:action="@{/logout}" data-logout>
                <button class="user-menu__item" type="submit">Déconnexion</button>
            </form>
        </li>
    </ul>
</nav>
</body>
</html>
```

- [ ] **Step 4: Use the fragment in `index.html` and add the styles**

In `templates/index.html`, replace the whole `<nav class="user-menu">…</nav>` block (lines 51-69) with:

```html
<nav th:replace="~{fragments/user-menu :: menu}"></nav>
```

In `public/assets/css/partner-central.css`, after the `.user-menu__list` rule, add the same rules as Task 1 Step 1 (`.user-menu__account`, `:hover`, `-photo`, `-photo img`, `-text`, `-name`, `-email`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='HomeTemplateTest,HomeControllerTest,ProfileTemplateTest'`
Expected: PASS — including the existing `ProfileTemplateTest.home` assertions (`/profile` first item, no `[data-user-email]`, photo in `[data-user-photo]`, no `[data-user-initials]`).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources modules/ndopify-partner-central/src/test
git commit -m "feat(npc): show the signed-in agent in the account menu"
```

---

### Task 4: One account menu on every page

**Files:**
- Modify (replace the inline `<nav class="user-menu">…</nav>` with `<nav th:replace="~{fragments/user-menu :: menu}"></nav>`): `templates/agent.html`, `kyc.html`, `listing.html`, `listing/photos.html`, `listing/section.html`, `mandate.html`, `mandate/close.html`, `mandate/customer-edit.html`, `mandate/edit.html`, `profile/edit.html`, `profile/photo.html`, `telegram.html`, `visit.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileController.kt:153-185` (`edit`)
- Modify: `modules/ndopify-partner-central/CLAUDE.md:72-77`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt:122`, `:218`

**Interfaces:**
- Consumes: `~{fragments/user-menu :: menu}` (Task 3) and the model attribute `accountCurrent`.
- Produces: `accountCurrent = "profile"` on the profile edit page.

- [ ] **Step 1: Write the failing profile-page test**

In `ProfileTemplateTest`, beside the existing `/profile` and `aria-current` assertions, add:

```kotlin
val account = page.selectFirst("a.user-menu__account")!!
assertEquals("/agent?id=20", account.attr("href"))
assertEquals("Serge Ngono", account.selectFirst(".user-menu__account-name")?.text())
assertEquals("serge@gmail.com", account.selectFirst(".user-menu__account-email")?.text())
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ProfileTemplateTest`
Expected: FAIL — `.user-menu__account` does not exist on the profile page.

- [ ] **Step 3: Swap the 13 templates and mark the profile page**

Replace each template's inline `<nav class="user-menu">…</nav>` with `<nav th:replace="~{fragments/user-menu :: menu}"></nav>`.

In `ProfileController.edit`, add `model.addAttribute("accountCurrent", "profile")` beside `ProfileRules.header(agent, model)`.

- [ ] **Step 4: Update the module's menu documentation**

In `modules/ndopify-partner-central/CLAUDE.md` (the sign-in paragraph), say that the menu is `fragments/user-menu.html` on every signed-in page: the signed-in agent's identity block (photo or initials, name, e-mail, opening `/agent?id=<its id>`), then *Modifier mon profil* (`/profile`) and *Déconnexion* (`POST /logout`); `ProfileRules.header` fills it.

- [ ] **Step 5: Run the whole module**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS — ktlint, every test, JaCoCo gate.

Then check no page keeps its own menu:

```bash
grep -rn 'class="user-menu"' modules/ndopify-partner-central/src/main/resources/templates
```

Expected: only `templates/fragments/user-menu.html`.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "refactor(npc): share the account menu fragment on every page"
```
