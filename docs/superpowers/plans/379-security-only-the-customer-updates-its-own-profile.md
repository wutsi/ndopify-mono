# Only the customer updates its own profile — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /v1/mandates/{id}/customer` accepts only the mandate's customer, and Partner Central's mandate page
loses the customer edit page and shows the contact block read-only.

**Architecture:** `MandateService.updateCustomer` calls `MandateAccessService.ensureIsCustomer` (the rule that already
guards a match answer) instead of `ensureCanUpdate`; everything else in the transaction stays as it is. Partner
Central deletes the page (`CustomerEditController` / `CustomerEditForm` / `CustomerEditRules` / the template and their
tests), the client call and the view/rules/outcome plumbing that served it, and keeps the contact block's one
remaining action, *Planifier une visite*.

**Tech Stack:** Kotlin, Spring Boot, `TestRestTemplate` against MySQL, JUnit 5 / `kotlin.test`, mockito-kotlin,
Thymeleaf + Jsoup template tests, Flyway test fixtures, esbuild asset bundle.

**Spec:** story #379 — https://github.com/wutsi/ndopify-mono/issues/379 — enriched body (*Context*, *Acceptance
criteria*, *Decisions and assumptions*, *Dependencies*, *Risks*, *Out of scope*).

## Global Constraints

- `UI: no`, `Shared code: none`: no prototype change (`src/main/html/` is never edited nor deleted), no `ndopify-dto`
  / `ndopify-web-core` change, no release, no migration.
- **Server rule:** only the customer's own party may write. `ensureIsCustomer` allows the caller whose party (JWT
  user → membership → party) is the mandate's customer's; **no admin bypass and no agent** — an `admin-console` token
  with the `admin` role and a mandate's agent both get 403.
- The endpoint, its request DTO and its other rules are unchanged: error order 404 `MANDATE_NOT_FOUND` → 403
  `MANDATE_ACCESS_DENIED` → 409 `MANDATE_ALREADY_CLOSED` → 409 `PARTY_EMAIL_ALREADY_EXISTS`; a `PENDING_APPROVAL`
  mandate whose e-mail changes still cancels its contract and returns to `ASSIGNED`; anonymous stays 401 (the
  security filter, before the endpoint).
- Lock order is untouched: `updateCustomer` locks the contract (if any) then the mandate, before the access check.
  The access check stays where it is — after the mandate load.
- The customer write is reached with a `public-portal` token (a match link, `MatchLinkTokenService`); the agent's and
  the admin's tokens are `partner-central` / `admin-console`. The application is irrelevant to the rule.
- **Partner Central:** no new page, no new copy for this story. The contact block keeps role, name, call window,
  phone + WhatsApp, e-mail; it keeps *Planifier une visite* for a signed search with an ACCEPTED match and no open
  visit, and renders no empty action row. The edit routes are gone (404).
- Test conventions: `kotlin.test` assertions, mockito-kotlin mocks, each endpoint test owns its SQL fixture.
- `mvn verify` green in `modules/ndopify-server` (local MySQL; ktlint, tests, JaCoCo) and in
  `modules/ndopify-partner-central` (ktlint, unit/template tests, page-script tests, coverage; a clean run downloads
  Node with `npm ci`, so it needs network).

## Review Focus

1. **The customer posts through a mandate owned by another agent** (a customer has several mandates; only its party
   matters) → 200, the details are saved. Task 1.
2. **The mandate's own agent posts** → 403 `MANDATE_ACCESS_DENIED` and nothing is written — the behaviour the story
   removes. Task 1.
3. **An admin posts** → 403 (`ensureIsCustomer` has no admin bypass) and nothing is written. Task 1.
4. **A `PENDING_APPROVAL` mandate and an e-mail another party owns** → 409 `PARTY_EMAIL_ALREADY_EXISTS`; the failed
   transaction rolls back, so the mandate stays `PENDING_APPROVAL` and its contract `PENDING_SIGNATURE`. Task 1.
5. **Partner Central's contact block**: a signed search whose ACCEPTED match has no open visit still shows
   *Planifier une visite*; there is no *Modifier* link and `GET /mandates/{id}/customer/edit` answers 404. Task 3.

---

### Task 1: `POST /v1/mandates/{id}/customer` — the customer only

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
  (`updateCustomer`, its KDoc)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateCustomerEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/mandate/UpdateMandateCustomerEndpoint.sql`

**Interfaces:**
- Consumes: `MandateAccessService.ensureIsCustomer(mandate: MandateEntity)` (existing: 403
  `MANDATE_ACCESS_DENIED` unless the caller's party is the customer's; no admin, no agent) and
  `MandateAccessService.ensureCanUpdate` (left as it is, for the other mandate writes).
- Produces: no new API — the same route, request and responses; only who may call it changes.

- [ ] **Step 1: Extend the SQL fixture** — replace its three `T_PARTY` / `T_USER` / `T_MEMBERSHIP` inserts with the
  block below: every existing row and id stays, and party 102 with users 13/14 and their memberships are added:

```sql
-- 100: customer Ray Sponsible (user 13, the caller)   101: another party owning taken@example.com
-- 102: a party that is neither the customer nor an agent (user 14)
-- 200: agent 20 (user 11)   300: agent 30 (user 12)   900: tenant 2
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'taken@example.com', 'Taken', 'Address'),
    (102, 1, 'another.party@example.com', 'Another', 'Party'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (900, 2, 'other.tenant@example.com', 'Other', 'Tenant');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'kim.possible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'john.doe@gmail.com', false, '2024-06-10 00:00:00'),
    (13, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (14, 'another.party@example.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 200, false),
    (12, 1, 12, 300, false),
    (13, 1, 13, 100, false),
    (14, 1, 14, 102, false);
```

- [ ] **Step 2: Rewrite the test class's caller** — the default caller becomes the customer (user 13, a
  `public-portal` token, as a match link gives it); the agent, the other party and the admin are selected per test.
  Replace the `roles` / `admin` members with:

```kotlin
    private enum class Caller { CUSTOMER, AGENT, OTHER_PARTY, ADMIN }

    private var caller = Caller.CUSTOMER

    override val application: String
        get() = when (caller) {
            Caller.CUSTOMER, Caller.OTHER_PARTY -> ApplicationCode.PUBLIC_PORTAL
            Caller.ADMIN -> ApplicationCode.ADMIN_CONSOLE
            Caller.AGENT -> ApplicationCode.PARTNER_CENTRAL
        }

    override val roles: Array<String>
        get() = if (caller == Caller.ADMIN) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @BeforeEach
    fun signInAsCustomer() {
        caller = Caller.CUSTOMER
        userId = 13L
    }
```

  (imports: `com.wutsi.ndopify.refdata.dto.ApplicationCode`, `com.wutsi.ndopify.refdata.dto.RoleCode`,
  `org.junit.jupiter.api.BeforeEach`; the superclass `@BeforeEach` runs first and sets `userId` to 11, this one then
  sets 13.)

- [ ] **Step 3: Turn the existing tests into the customer's** — keep their names, request and assertions, unless
  listed here:
  - `update`, `pending approval - email changed cancels the contract`, `pending approval - name change keeps the
    contract`, `approved mandate - updated, contract untouched`, `closed - 409, nothing saved`, `cancelled - 409,
    nothing saved`, `another tenant's mandate - 404`, `unknown mandate - 404`, `email owned by another party - 409,
    nothing saved`, `email owned by another party on a pending approval mandate - contract stays`, `empty first name -
    400`, `anonymous - 401`: **unchanged**; they now run as the customer and stay 200/409/404/400/401.
  - `another agent's mandate - 403, nothing saved` → still 403, but the caller must now be selected: the agent of
    mandate 1 (user 11, party 200) posting to mandate 2 (agent 30):
    ```kotlin
        caller = Caller.AGENT
        userId = 11L
    ```
  - `admin - updates another agent's mandate` → now `admin - 403, nothing saved`: `caller = Caller.ADMIN`,
    `userId = 11L`, expect `HttpStatus.FORBIDDEN`, `ErrorCode.MANDATE_ACCESS_DENIED`, and party 100's first name still
    `Ray`.

- [ ] **Step 4: Add the missing cases**:

```kotlin
    @Test
    fun `its own agent - 403, nothing saved`() {
        caller = Caller.AGENT
        userId = 11L // party 200, agent 20: the mandate's agent

        val response = rest.postForEntity("/v1/mandates/1/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
        assertEquals("+237699123456", customerDao.findById(10L).get().whatsappNumber)
    }

    @Test
    fun `a party that is neither the customer nor an agent - 403, nothing saved`() {
        caller = Caller.OTHER_PARTY
        userId = 14L

        val response = rest.postForEntity("/v1/mandates/1/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `another agent's mandate - the customer updates its own details`() {
        val response = rest.postForEntity("/v1/mandates/2/customer", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Raymond", partyDao.findById(100L).get().firstName)
        assertEquals("+237699000111", customerDao.findById(10L).get().whatsappNumber)
    }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateMandateCustomerEndpointTest`
Expected: FAIL — `its own agent` and `admin` get 200 (the agent still updates), and
`another agent's mandate - the customer…` gets 403. (`a party that is neither…` already answers 403 before the
change: it guards against a regression.)

- [ ] **Step 6: Implement the rule** — in `MandateService.updateCustomer`, replace
  `access.ensureCanUpdate(mandate)` with `access.ensureIsCustomer(mandate)`. Rewrite its KDoc to carry the rule and
  the unchanged order:

```kotlin
    /**
     * Corrects the mandate's customer's own details, which every one of that customer's mandate pages shows, for that
     * customer and nobody else ([MandateAccessService.ensureIsCustomer]: no admin bypass, no agent): 404
     * MANDATE_NOT_FOUND, 403 MANDATE_ACCESS_DENIED, 409 MANDATE_ALREADY_CLOSED (closed or cancelled), then 409
     * PARTY_EMAIL_ALREADY_EXISTS when another party already owns the e-mail. A PENDING_APPROVAL mandate whose e-mail
     * changes goes back to ASSIGNED and its contract is cancelled: the customer mustn't sign a contract sent to the
     * old address, and request-approval creates a new one. A name or phone change leaves the contract. Like a
     * signature, the contract is locked before the mandate.
     */
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateMandateCustomerEndpointTest`
Expected: PASS (17 tests: the 14 kept/renamed plus the 3 added).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateCustomerEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/mandate/UpdateMandateCustomerEndpoint.sql
git commit -m "feat(server): only the customer updates its own profile" -m "Refs #379"
```

---

### Task 2: Document the server rule

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the `customer` bullet and the mandate *Access* paragraph)

- [ ] **Step 1: Rewrite the `customer` bullet's rule** — replace "for the mandate's agent or an admin" with the
  customer-only rule and its error order:

```
  customer of an e-mail. `POST /v1/mandates/{id}/customer` (`MandateService.updateCustomer`) corrects the mandate's
  customer's own details: its party's names and e-mail, and its WhatsApp number — for that customer only
  (`MandateAccessService.ensureIsCustomer`; no admin bypass, no agent): 404 `MANDATE_NOT_FOUND` → 403
  `MANDATE_ACCESS_DENIED` → 409 `MANDATE_ALREADY_CLOSED` → 409 `PARTY_EMAIL_ALREADY_EXISTS`. A
  `PENDING_APPROVAL` mandate whose e-mail changes cancels its contract and returns to `ASSIGNED` (the customer must
  not sign a contract sent to the old address); a closed or cancelled mandate is a 409 `MANDATE_ALREADY_CLOSED`, an
  e-mail another party owns a 409 `PARTY_EMAIL_ALREADY_EXISTS` with nothing written. Tenant-scoped.
```

- [ ] **Step 2: Name the exception in the mandate *Access* paragraph** — after the sentence about `GET /{id}` being
  open to the customer, add:

```
  `POST /{id}/customer` is its customer's only (`ensureIsCustomer`), with no admin nor agent.
```

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): the customer alone updates its own details" -m "Refs #379"
```

---

### Task 3: Remove Partner Central's customer edit page

**Files:**
- Delete: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditController.kt`
- Delete: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditForm.kt`
- Delete: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRules.kt`
- Delete: `modules/ndopify-partner-central/src/main/resources/templates/mandate/customer-edit.html`
- Delete: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditControllerTest.kt`
- Delete: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRulesTest.kt`
- Delete: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/js/mandate.js`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/home.css` (two comments only)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `MandateRules.CUSTOMER_EDITABLE`, `MandateView.customerEditable`, `NdCustomerClient.update`, the outcomes
  `customer-saved` / `customer-saved-unsent` and the `customer-edit` page script no longer exist; the mandate page's
  contact block carries only `data-contact-schedule` in `[data-contact-actions]`.

- [ ] **Step 1: Update the surviving tests first** (this is the failing test):
  - `MandateControllerTest`: delete `outcome - customer saved` and `outcome - customer saved, out for signature`; add
    ```kotlin
    @Test
    fun `customer edit page - gone`() {
        mvc.perform(get("/mandates/201/customer/edit"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }
    ```
  - `MandateTemplateTest`:
    - in `contact()`, replace the two `data-contact-edit` assertions (aria-label and href) with
      `assertNull(card.selectFirst("[data-contact-edit]"))`, and reword its comment "The e-mail, and the way to
      correct it" to "The e-mail";
    - in `approved - landlord`, `a sale search's page words the buyer` and `a sale listing's page words the seller`,
      replace each `assertEquals("Modifier les coordonnées …", page.selectFirst("[data-contact-edit]")?.attr("aria-label"))`
      with `assertNull(page.selectFirst("[data-contact-edit]"))`;
    - in `contact - plan a visit for an accepted match`, keep the three assertions on the schedule link and replace the
      two-action assertions with `val actions = page.select("[data-contact-actions]").select("a")` /
      `assertEquals(1, actions.size)` / `assertTrue(actions[0].hasAttr("data-contact-schedule"))`;
    - in `contact - no visit link once the accepted match has one`, replace
      `assertNotNull(page.selectFirst("[data-contact-edit]"))` with
      `assertNull(page.selectFirst("[data-contact-actions]"))`;
    - rename `contact - closed, no edit row` to `contact - closed, no action row` (its two `assertNull`s and the phone
      assertion stay).
  - `MandateViewsTest`: delete the `customer editable - closed` test and the last line of `contact()`
    (`assertTrue(view(PENDING).customerEditable)`).
  - `MandateRulesTest`: delete the three-line `MandateRules.CUSTOMER_EDITABLE` assertion in `statuses`.
  - `NdCustomerClientTest`: delete the `update()` test and the `UpdateCustomerRequest` import (let ktlint flag any
    other import left unused).
  - Delete the three `CustomerEdit*Test.kt` files.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest,MandateViewsTest,MandateRulesTest,NdCustomerClientTest'`
Expected: FAIL — the template still renders `data-contact-edit` (so the `assertNull`s fail) and
`GET /mandates/201/customer/edit` answers 200.

- [ ] **Step 3: Remove the page and its plumbing**
  - Delete the four main files listed above (`CustomerEditController.kt`, `CustomerEditForm.kt`, `CustomerEditRules.kt`,
    `customer-edit.html`); the three test classes are already deleted.
  - `NdCustomerClient`: delete `update` and the `UpdateCustomerRequest` import; keep `get` and `search`.
  - `MandateRules`: delete `CUSTOMER_EDITABLE` and its comment.
  - `MandateView`: delete `customerEditable` and its comment. `MandateViews`: delete the
    `customerEditable = status in MandateRules.CUSTOMER_EDITABLE,` line.
  - `MandateController`: delete `CUSTOMER_SAVED` / `CUSTOMER_SAVED_UNSENT` and their comment, and their two branches
    of `outcome` (`CUSTOMER_SAVED`, `CUSTOMER_SAVED_UNSENT`) with their French texts.
  - `templates/mandate.html`: in the contact-card comment, drop the "Modifier" sentence; change the action row to
    `th:if="${canScheduleVisit == true}"` and delete the `data-contact-edit` link (keep the schedule link and its
    `th:if`); leave everything else, including the contact lines, intact.
  - `src/main/assets/js/mandate.js`: drop the customer edit page from the header comment and the "Ported from"
    sentence (`initCustomerEdit`), delete the `/* ---------- mandate/customer-edit.html ---------- */` section
    (`PHONE_ERRORS`, `PHONE_INVALID`, `PHONE_UNAVAILABLE`, `initCustomerEdit`) and the
    `if (page === "customer-edit") initCustomerEdit();` line. Keep `$`, `setFieldError` and everything else — the
    mandate edit page uses them.
  - `src/main/assets/css/home.css`: reword the two comments only — the contact card comment loses "then
    'Modifier'", and `.contact-card__actions`'s comment becomes the card's one action (plan a visit), apart from the
    contact lines. No selector or rule changes.

- [ ] **Step 4: Run the module's tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS — no `CustomerEdit*` class left, the mandate and template tests green, the page-script tests green.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditController.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditForm.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClient.kt \
        modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/resources/templates/mandate/customer-edit.html \
        modules/ndopify-partner-central/src/main/assets/js/mandate.js \
        modules/ndopify-partner-central/src/main/assets/css/home.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClientTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViewsTest.kt
git commit -m "feat(npc): remove the customer edit page" -m "Refs #379"
```

  (No change under `src/main/html/`: check with `git status --porcelain modules/ndopify-partner-central/src/main/html`.)

---

### Task 4: Document the read-only contact block

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the mandate page paragraph, *Pages*)

- [ ] **Step 1: Replace the customer edit sentences** — the paragraph's contact-block part becomes read-only, and
  the page-script sentence loses the third page:

```
  The customer's contact block (`.contact-card`) is unboxed and read-only: role ("Client" / "Bailleur"), full name,
  "à appeler" when the mandate has a call window, the number as a `tel:` link with a compact WhatsApp button beside
  it, and the e-mail as a `mailto:` link. It has no "Modifier" link and no edit page: only the customer updates its
  own details (`POST /v1/mandates/{id}/customer`), so an agent who spots a typo can only ask it.
  `src/main/assets/js/mandate.js` only enhances the mandate and its edit page (`data-page` `mandate` / `mandate-edit`).
  When a signed search has an ACCEPTED match without an open visit
```

  Keep the rest of the paragraph (the visit page, outcomes, the 403/404 handling) verbatim.

- [ ] **Step 2: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): the customer's contact block is read-only" -m "Refs #379"
```

---

### Task 5: Full verification

- [ ] **Step 1: Run the server's gate** (a local MySQL must be running):

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, `UpdateMandateCustomerEndpointTest` green, JaCoCo thresholds met.

- [ ] **Step 2: Run Partner Central's gate**:

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, unit/template/page-script tests green, JaCoCo thresholds met.

- [ ] **Step 3: Check the prototype is untouched** (the story's AC 11):

Run: `git status --porcelain modules/ndopify-partner-central/src/main/html`
Expected: no output.
