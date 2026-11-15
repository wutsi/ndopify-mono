# Partner Central — Customer Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an agent correct a mandate customer's first/last name, e-mail and WhatsApp number from the mandate
page, and show that customer unboxed (full name, call window, formatted phone + WhatsApp button, e-mail).

**Architecture:** A mandate-scoped `POST /v1/mandates/{id}/customer` (new `UpdateCustomerRequest` in
`ndopify-dto`) updates the customer's party and WhatsApp number, cancelling a `PENDING_APPROVAL` contract only
when the e-mail changes. Partner Central gets a dedicated edit page (`GET|POST
/mandates/{id}/customer/edit`), reached from an unboxed contact block; every check is re-run server-side.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, JUnit 5 / `kotlin.test`, `mockito-kotlin`, JaCoCo, ktlint, MySQL
endpoint tests, Maven.

**Spec:** [story #125](https://github.com/wutsi/ndopify-mono/issues/125) (the design record is its description).

## Global Constraints

- UI text, e-mails and contracts are French; code, comments, logs and docs are English.
- Assertions use `kotlin.test`; mocks use `mockito-kotlin`.
- Editable statuses: `ASSIGNED`, `PENDING_APPROVAL`, `APPROVED`. `CLOSED` and `CANCELLED` are locked.
- A `PENDING_APPROVAL` mandate cancels its contract and returns to `ASSIGNED` only when the normalized e-mail
  changed. Name or phone changes leave the contract.
- The customer's e-mail is the tenant contact e-mail, not a login. An address another party owns is a 409
  `PARTY_EMAIL_ALREADY_EXISTS`, never a merge.
- No optimistic locking on the customer: last write wins.
- Every page works without JavaScript; `src/main/html/` is the prototype, never served and never deleted.
- Each commit touches one library module (`modules/ndopify-dto/`) or none. Library commits are
  `<type>(dto): …` with `Refs #125` in the body.
- Local builds against the unreleased DTO set the root `pom.xml` `<ndopify-dto.version>` to
  `0.0.90-SNAPSHOT` (the DTO's current version) and `mvn install` the DTO. That root POM edit is never committed.
- Dates/amounts shown to users go through `TenantFormatter`; the phone keeps `MandateViews.displayPhone`.

## Review Focus

- **E-mail change while out for signature:** the contract is cancelled and the mandate becomes `ASSIGNED`; the
  same address in another case/whitespace cancels nothing.
- **E-mail owned by another party:** a 409 shown as the e-mail field error, with the form kept and the customer
  row unchanged (no partial write).
- **Terminal / foreign mandates:** a direct POST on `CLOSED`/`CANCELLED` is a 409, another agent's mandate a 403,
  another tenant's a 404 — none writes anything.
- **Odd phone numbers:** a blank or non-E.164 stored number still renders the name and e-mail, without a
  WhatsApp button or a broken `tel:` link.
- **Unreadable customer / foreign mandate page:** the contact block shows its unavailable note and no
  "Modifier" link; the tests assert the customer is never fetched for another agent's mandate.

---

### Task 1: Prototype the unboxed contact block and the edit page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/mandate.html:131-152`
- Create: `modules/ndopify-partner-central/src/main/html/mandate/customer-edit.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css:963-975`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`

**Interfaces:**
- Produces: the approved visual reference the served templates mirror in Tasks 5–6.

- [ ] **Step 1: Replace the boxed contact block in `mandate.html`**

Use the pre-`95bde7ca` unboxed markup as the base (still visible in `home.css`):
`<div class="contact-card">` with `<strong data-contact="name">`, the role as a new
`<span class="contact-card__role" data-contact-role>`, `contact-card__period` ("à appeler …"), a
`contact-card__line contact-card__phone` holding a `tel:` link (`data-contact="phone"`) plus a
`btn btn--secondary btn--compact` WhatsApp button (`data-contact="whatsapp"`), a `contact-card__line` with the
`mailto:` e-mail (`data-contact="email"`), and a "Modifier" button linking to
`mandate/customer-edit.html?id=<id>`. The unavailable note stays.

- [ ] **Step 2: Add `mandate/customer-edit.html`**

Copy `mandate/edit.html`'s shell (header, back link, locked alert, page intro, form classes). One form with four
fields (`firstName`, `lastName`, `email`, `whatsappNumber`), the profile edit page's label/input/error markup,
and "Enregistrer" / "Annuler". The WhatsApp field follows DESIGN.md's phone-number rules: load intl-tel-input
25.11.2 (CSS + JS) and `util.js`; the visible `type="tel"` input has no `name`, and the library's hidden input
carries `name="whatsappNumber"` in E.164. Keep the hint about what the e-mail and the WhatsApp number are for (the
library shows the country code and example number itself). Two locked states: unavailable, closed.

- [ ] **Step 3: Give the prototype data first/last names**

In `partner-central.js`, replace each `contact: { name: … }` (10 fixtures) with
`firstName` / `lastName`, keeping `whatsapp`, `email`, `period`. Every read of `contact.name` uses
`firstName + " " + lastName` (ex: `clientNameOf`).

- [ ] **Step 4: Render the contact block and wire the link**

In `initMandate`, fill the role, full name, period, formatted phone, `tel:` href, WhatsApp href (digits only),
e-mail, and hide `[data-contact-edit]` when the mandate's status is `CLOSED` or `CANCELLED`. Read the contact
from the session's saved copy first (`mandateStates()[id].contact || MANDATES[id].contact`).

- [ ] **Step 5: Add `initCustomerEdit`**

Register `"customer-edit": initCustomerEdit`. It reads `?id=`, shows the locked view when `!ownsMandate(id)` or
the status is terminal, pre-fills the four inputs from the contact, and on submit stores the edited contact in
the session's mandate copy (`mandateStates()[id].contact`) and navigates back to `mandate.html?id=…`.
Bind the WhatsApp field through `NdUtil.phoneReady()` / `NdUtil.bindPhone()` (the `lazyPhone` helper used by
`rejoindre.js`): pre-fill it in E.164, keep the save button inactive until `utils.js` is in, map
`getValidationError()` to the existing French messages, and read `e164()` on submit. If `utils.js` can't load,
show the "cannot verify" error and keep the form unsavable.

- [ ] **Step 6: Remove the now-unused `.contact-person` rules**

Delete `.contact-person`, `.contact-person__name`, `.contact-person__period` from
`src/main/html/assets/css/home.css`; add `.contact-card__role` (the eyebrow's `font-size: var(--step-hint)` and
`color: var(--color-info-fg)`).

- [ ] **Step 7: Check it in a browser**

Run: `cd modules/ndopify-partner-central/src/main/html && python3 -m http.server 8000`
Open `http://localhost:8000/mandate.html?id=101`, then its "Modifier" link; a terminal mandate id shows no link.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the customer edit page"
```

**Stop here.** Show the user the two prototype pages and wait for their OK on the prototype and the plan before
Task 2. If the feedback changes the design, update this plan, commit and push it to `main`, then rebase.

---

### Task 2: Add `UpdateCustomerRequest` to ndopify-dto

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/customer/dto/UpdateCustomerRequest.kt`
- Modify locally (never committed): `pom.xml:52`

**Interfaces:**
- Produces: `UpdateCustomerRequest(firstName: String = "", lastName: String = "", email: String = "", whatsappNumber: String = "")`, all `@get:NotEmpty`.

- [ ] **Step 1: Write the request**

```kotlin
package com.wutsi.ndopify.customer.dto

import jakarta.validation.constraints.NotEmpty

/** The corrected details of a mandate's customer: names and e-mail of its party, plus its WhatsApp number. */
data class UpdateCustomerRequest(
    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val whatsappNumber: String = "",
)
```

- [ ] **Step 2: Install it locally and pin the server to the snapshot**

```bash
cd modules/ndopify-dto && mvn install -DskipTests
cd ../.. && sed -i '' 's#<ndopify-dto.version>0.0.89</ndopify-dto.version>#<ndopify-dto.version>0.0.90-SNAPSHOT</ndopify-dto.version>#' pom.xml
git diff -- pom.xml
```

Expected: only `<ndopify-dto.version>` changed, from `0.0.89` to `0.0.90-SNAPSHOT`.

- [ ] **Step 3: Commit only the DTO file**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/customer/dto/UpdateCustomerRequest.kt
git commit -m "feat(dto): add UpdateCustomerRequest" -m "Refs #125"
```

Verify with `git status --short`: `pom.xml` is still modified (the local snapshot pin), nothing else.

---

### Task 3: Update a mandate's customer in ndopify-server

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/mandate/UpdateMandateCustomerEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateCustomerEndpointTest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/customer/server/service/CustomerService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt`

**Interfaces:**
- Consumes: `UpdateCustomerRequest` (Task 2).
- Produces: `POST /v1/mandates/{id}/customer`, body `UpdateCustomerRequest`, `200`; `CustomerService.update(customer: CustomerEntity, whatsappNumber: String): CustomerEntity`; `MandateService.updateCustomer(id: Long, request: UpdateCustomerRequest)`.

- [ ] **Step 1: Write the SQL fixture**

Base it on `db/test/mandate/UpdateMandateEndpoint.sql` and `MandateAccessEndpoint.sql`. Tenants 1 and 2;
parties 100 (customer Ray Sponsible), 101 (taken@example.com), 200 (agent 20), 300 (agent 30), 900 (tenant 2);
users 11 → party 200, 12 → party 300; memberships; `T_CUSTOMER` 10 (party 100) and 90 (tenant 2); agents 20 and
30; contracts `c-3` `PENDING_SIGNATURE`, `c-4` `PENDING_SIGNATURE`, `c-5` `SIGNED`, `c-6` `DRAFT`. Mandates, all
for customer 10 unless noted:

| id | agent | status | contract |
|----|-------|--------|----------|
| 1 | 20 | `ASSIGNED` | none |
| 2 | 30 | `APPROVED` | none |
| 3 | 20 | `PENDING_APPROVAL` | `c-3` |
| 4 | 20 | `PENDING_APPROVAL` | `c-4` |
| 5 | 20 | `APPROVED` | `c-5` |
| 6 | 20 | `CLOSED` | none |
| 7 | 20 | `CANCELLED` | none |
| 8 | tenant 2 | `ASSIGNED` | none (customer 90) |

- [ ] **Step 2: Write the failing endpoint test**

`UpdateMandateCustomerEndpointTest : AuthorizationAwareEndpointIntegrationTest` with
`@Sql(clean.sql, /db/test/mandate/UpdateMandateCustomerEndpoint.sql)`, `private var admin = false`, and
`override val roles get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")`. Inject
`MandateRepository`, `ContractRepository`, `CustomerRepository`, `PartyRepository`. Request:
`UpdateCustomerRequest("Raymond", "Sponsible", "raymond.sponsible@example.com", "+237699000111")`.

Tests, each asserting state after the call:

```kotlin
@Test fun update() {
    val response = rest.postForEntity("/v1/mandates/1/customer", request, Any::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    val customer = customerDao.findById(10L).get()
    assertEquals("+237699000111", customer.whatsappNumber)
    val party = partyDao.findById(100L).get()
    assertEquals("Raymond", party.firstName)
    assertEquals("raymond.sponsible@example.com", party.email)
    assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(1L).get().status)
}

@Test fun `pending approval - e-mail changed cancels the contract`() {
    assertEquals(HttpStatus.OK, rest.postForEntity("/v1/mandates/3/customer", request, Any::class.java).statusCode)
    assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(3L).get().status)
    assertEquals(ContractStatus.CANCELLED, contractDao.findById("c-3").get().status)
}

@Test fun `pending approval - name change keeps the contract`() {
    val body = request.copy(email = "Ray.Sponsible@gmail.com", firstName = "Raymond")
    assertEquals(HttpStatus.OK, rest.postForEntity("/v1/mandates/4/customer", body, Any::class.java).statusCode)
    assertEquals(MandateStatus.PENDING_APPROVAL, mandateDao.findById(4L).get().status)
    assertEquals(ContractStatus.PENDING_SIGNATURE, contractDao.findById("c-4").get().status)
}
```

Also add: approved mandate updated (`/5`, stays `APPROVED`); closed `/6` and cancelled `/7` → `409`
`MANDATE_ALREADY_CLOSED`; another agent's `/2` → `403` `MANDATE_ACCESS_DENIED`; `admin = true` updates `/2`;
another tenant `/8` and unknown `/999` → `404 MANDATE_NOT_FOUND`; e-mail `taken@example.com` → `409`
`PARTY_EMAIL_ALREADY_EXISTS` and party 100 still "Ray"; the same conflict on `/3` (out for signature) leaves the
mandate `PENDING_APPROVAL` and `c-3` `PENDING_SIGNATURE` (the cancellation rolls back); `firstName = ""` → `400
HTTP_INVALID_PARAMETER` with `parameter.name == "firstName"`; `anonymousUser = true` → `401
AUTH_UNAUTHORIZED`. Mandates 1–7 share customer 10, so the `update` test's customer row proves every one of their
pages shows the correction.

- [ ] **Step 3: Run the test to watch it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateMandateCustomerEndpointTest`
Expected: FAIL — `404` for the unknown endpoint.

- [ ] **Step 4: Add `CustomerService.update`**

```kotlin
@Transactional
fun update(customer: CustomerEntity, whatsappNumber: String): CustomerEntity =
    dao.save(customer.copy(whatsappNumber = whatsappNumber, modifiedAt = Date(clock.millis())))
```

- [ ] **Step 5: Add `MandateService.updateCustomer`**

Inject `PartyService` into the constructor (new parameter). The lock order is the mandate update's — contract,
then mandate — and the contract is cancelled only for a changed e-mail on a mandate out for signature:

```kotlin
@Transactional
fun updateCustomer(id: Long, request: UpdateCustomerRequest) {
    val contractId = dao.findContractIdById(id)
    contractId?.let { contractService.findByIdForUpdate(it) }

    val mandate = findByIdForUpdate(id)
    access.ensureCanUpdate(mandate)
    ensureNotClosed(mandate)

    val customer = customerService.findById(mandate.customerId)
    val email = request.email.trim().lowercase()
    val emailChanged = email != customer.party.email.lowercase()
    if (emailChanged && mandate.status == MandateStatus.PENDING_APPROVAL) {
        if (mandate.contractId != contractId) {
            throw ConflictException(Error(ErrorCode.MANDATE_VERSION_CONFLICT))
        }
        mandate.contractId?.let { contractService.cancel(it) }
        dao.save(mandate.copy(status = MandateStatus.ASSIGNED, modifiedAt = Date(clock.millis())))
    }

    partyService.update(
        customer.party,
        UpdatePartyRequest(
            firstName = request.firstName.trim(),
            lastName = request.lastName.trim(),
            email = email,
        ),
    )
    customerService.update(customer, request.whatsappNumber.trim())
}
```

`PartyService.update` throws the 409 for an owned e-mail before anything is written; the transaction rolls the
contract cancellation back too.

- [ ] **Step 6: Add the endpoint**

```kotlin
@PostMapping("/{id}/customer")
fun updateCustomer(
    @PathVariable id: Long,
    @RequestBody @Valid request: UpdateCustomerRequest,
) {
    service.updateCustomer(id, request)
}
```

Import `jakarta.validation.Valid` and `com.wutsi.ndopify.customer.dto.UpdateCustomerRequest`.

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateMandateCustomerEndpointTest`
Expected: PASS. (Needs local MySQL, per the root `CLAUDE.md`.)

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src
git commit -m "feat(mandate): update a mandate's customer details"
```

---

### Task 4: Customer edit rules and the Partner Central client

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClient.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClientTest.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditForm.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRules.kt`
- Create: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRulesTest.kt`

**Interfaces:**
- Produces: `NdCustomerClient.update(mandateId: Long, request: UpdateCustomerRequest)`; `CustomerEditForm(firstName, lastName, email, whatsappNumber)` with `of(customer: Customer)`; `CustomerEditRules.errors(form): Map<String, String>`, `CustomerEditRules.request(form): UpdateCustomerRequest`.

- [ ] **Step 1: Write the client test**

```kotlin
@Test fun update() {
    server.expect(requestTo("http://localhost:8080/v1/mandates/7/customer"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"firstName":"Ray","lastName":"Sponsible","email":"ray@x.com","whatsappNumber":"+237699123456"}"""))
        .andRespond(withSuccess())
    client.update(7, UpdateCustomerRequest("Ray", "Sponsible", "ray@x.com", "+237699123456"))
    server.verify()
}
```

- [ ] **Step 2: Run it to watch it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdCustomerClientTest`
Expected: FAIL — `update` unresolved.

- [ ] **Step 3: Add `NdCustomerClient.update`**

```kotlin
/** Corrects the customer's details through its mandate: `POST /v1/mandates/{id}/customer`. */
fun update(mandateId: Long, request: UpdateCustomerRequest) {
    rest.post().uri("/v1/mandates/{id}/customer", mandateId).body(request).retrieve().toBodilessEntity()
}
```

- [ ] **Step 4: Write the failing rules test**

```kotlin
@Test fun errors() {
    assertEquals(emptyMap(), CustomerEditRules.errors(VALID))
    assertEquals("Saisissez le prénom.", CustomerEditRules.errors(VALID.copy(firstName = " "))["firstName"])
    assertEquals("Saisissez le nom.", CustomerEditRules.errors(VALID.copy(lastName = ""))["lastName"])
    assertEquals(
        "Cette adresse email semble incomplète. Exemple : nom@exemple.com",
        CustomerEditRules.errors(VALID.copy(email = "ray@"))["email"],
    )
    assertEquals("Saisissez le numéro WhatsApp.", CustomerEditRules.errors(VALID.copy(whatsappNumber = ""))["whatsappNumber"])
    assertNotNull(CustomerEditRules.errors(VALID.copy(whatsappNumber = "6 77 12 34 56"))["whatsappNumber"])
}

@Test fun request() {
    val request = CustomerEditRules.request(CustomerEditForm(" Ray ", " Sponsible ", " Ray@X.com ", "00237 699 123 456"))
    assertEquals("Ray", request.firstName)
    assertEquals("Sponsible", request.lastName)
    assertEquals("ray@x.com", request.email)
    assertEquals("+237699123456", request.whatsappNumber)
}
```

`VALID = CustomerEditForm("Ray", "Sponsible", "ray@x.com", "+237699123456")`.

- [ ] **Step 5: Implement `CustomerEditForm` and `CustomerEditRules`**

`CustomerEditForm` is a data class with defaults and `of(customer)` taking the party's names/e-mail and
`customer.whatsappNumber.orEmpty()`. `CustomerEditRules` holds the three regexes (same shapes as `JoinRules`:
`^\p{L}[\p{L}' .-]*$`, `^[^\s@]+@[^\s@]+\.[^\s@]{2,}$`, `^\+[1-9]\d{6,14}$`) with its own customer-facing
messages, and reuses `JoinRules.normalizePhone` for the `00`/separator normalization. `request` trims names and
e-mail, lowercases the e-mail, normalizes the phone.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdCustomerClientTest,CustomerEditRulesTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdCustomerClientTest.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditForm.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditRulesTest.kt
git commit -m "feat(npc): add the customer edit rules and client"
```

---

### Task 5: Show the customer unboxed on the mandate page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html:112-140`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/{MandateRulesTest,MandateViewsTest,MandateTemplateTest}.kt`

**Interfaces:**
- Consumes: `ContactView` (existing), `MandateRules.SHOWN`.
- Produces: `MandateRules.CUSTOMER_EDITABLE = ACTIVE`; `ContactView.whatsapp: String?` (raw stored number, for `tel:`); `MandateView.customerEditable: Boolean`.

- [ ] **Step 1: Write the failing view tests**

In `MandateRulesTest`:

```kotlin
assertEquals(
    setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL, MandateStatus.APPROVED),
    MandateRules.CUSTOMER_EDITABLE,
)
```

In `MandateViewsTest.contact` add `assertEquals("+237699123456", contact.whatsapp)` and
`assertTrue(view(PENDING).customerEditable)`; add
`assertFalse(view(MANDATE.copy(status = MandateStatus.CLOSED)).customerEditable)` using the existing fixtures.

- [ ] **Step 2: Update `MandateTemplateTest.contact`**

```kotlin
val card = page.selectFirst(".contact-card")!!
assertNull(page.selectFirst(".contact-person"))
assertEquals("Client", card.selectFirst("[data-contact-role]")?.text())
assertEquals("Ray Sponsible", card.selectFirst("[data-contact=name]")?.text())
assertEquals("tel:+237699123456", card.selectFirst("a[data-contact=phone]")?.attr("href"))
assertEquals("+237 6 99 12 34 56", card.selectFirst("[data-contact-number]")?.text())
assertEquals("https://wa.me/237699123456", card.selectFirst("a[data-contact=whatsapp]")?.attr("href"))
assertTrue(card.selectFirst("a[data-contact=whatsapp]")!!.hasClass("btn"))
assertEquals("mailto:ray@gmail.com", card.selectFirst("a[data-contact=email]")?.attr("href"))
assertEquals("/mandates/201/customer/edit", card.selectFirst("[data-contact-edit]")?.attr("href"))
```

Add `contact - closed: no edit link` (render `CLOSED`, assert `null` for `[data-contact-edit]`) and
`contact - no phone: no WhatsApp button or tel link` (`CUSTOMER.copy(whatsappNumber = "")`: both
`[data-contact=whatsapp]` and `[data-contact=phone]` are null, the e-mail line stays). Update the old assertion
that no `.btn` exists in the section: the `tel:` link is not a button, the WhatsApp link is.

- [ ] **Step 3: Run the tests to watch them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateRulesTest,MandateViewsTest,MandateTemplateTest`
Expected: FAIL — `CUSTOMER_EDITABLE`, `whatsapp`, `customerEditable` unresolved; `.contact-card` absent.

- [ ] **Step 4: Add the rules and view fields**

```kotlin
// MandateRules
/** The customer's contact details can be corrected: the mandate isn't CLOSED or CANCELLED. */
val CUSTOMER_EDITABLE: Set<MandateStatus> = ACTIVE
```

```kotlin
// ContactView
/** The stored number ("+237699123456"), for the tel: link. */
val whatsapp: String?,
```

Set `whatsapp = whatsapp` in `MandateViews.contact`, and `customerEditable = status in MandateRules.CUSTOMER_EDITABLE`
in `MandateView`/`MandateViews.of` next to `editable`.

- [ ] **Step 5: Replace the template block**

Render `.contact-card` as in the approved prototype (Task 1): role, full name, `contact-card__period`, a
`contact-card__line contact-card__phone` with `a[data-contact=phone]` (`tel:${c.whatsapp}`) and the compact
WhatsApp button (`th:if="${c.whatsappDigits}"`), the `mailto:` e-mail line, and the "Modifier" link wrapped in
`th:if="${m.customerEditable}"`. Keep the unavailable note. No version, no hidden fields.

- [ ] **Step 6: Update the served CSS**

In `src/main/resources/public/assets/css/home.css` remove `.contact-person*` and add `.contact-card__role`
(`font-size: var(--step-hint); color: var(--color-info-fg);`). `.contact-card`, `.contact-card__phone` and
`.btn--compact` already exist; do not re-add them.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateRulesTest,MandateViewsTest,MandateTemplateTest,MandateControllerTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
git commit -m "feat(npc): unbox the mandate customer contact block"
```

---

### Task 6: The customer edit page

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/CustomerEditController.kt`
- Create: `modules/ndopify-partner-central/src/main/resources/templates/mandate/customer-edit.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt:130-160,567-595`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/mandate.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/{CustomerEditControllerTest,CustomerEditTemplateTest,MandateControllerTest}.kt`

**Interfaces:**
- Consumes: `CustomerEditForm`, `CustomerEditRules`, `NdCustomerClient.update`, `MandateRules.CUSTOMER_EDITABLE`, `MandateViews`.
- Produces: `GET|POST /mandates/{id}/customer/edit`; outcomes `customer-saved` and `customer-saved-unsent` on `/mandates/{id}`.

- [ ] **Step 1: Write the failing controller tests**

Standalone `MockMvc` with mocks of `CurrentAgent`, `NdMandateClient`, `NdCustomerClient`, `TenantContext`,
`Clock` (follow `MandateEditTemplateTest.setUp` with `ProfileFixtures.mockLocations`; `MandateControllerTest`
has the `AGENT`, `PENDING`, `CUSTOMER`, `NOW` fixtures). Tests:

```kotlin
@Test fun `show - pre-filled`() {
    val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()
    assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
    assertEquals(CustomerEditForm("Ray", "Sponsible", "ray@gmail.com", "+237699123456"), model(result)["answers"])
    assertNull(model(result)["locked"])
}

@Test fun `save`() {
    val result = mvc.perform(
        post("/mandates/201/customer/edit")
            .param("firstName", "Raymond")
            .param("lastName", "Sponsible")
            .param("email", "raymond@example.com")
            .param("whatsappNumber", "+237699000111"),
    ).andReturn()
    assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
    assertEquals("customer-saved", result.flashMap["outcome"])
    verify(customerClient).update(
        201,
        UpdateCustomerRequest("Raymond", "Sponsible", "raymond@example.com", "+237699000111"),
    )
}

@Test fun `save - pending approval, e-mail changed`() {
    // mandate PENDING_APPROVAL, customer e-mail different
    val result = mvc.perform(post("/mandates/201/customer/edit") /* new e-mail */).andReturn()
    assertEquals("customer-saved-unsent", result.flashMap["outcome"])
}
```

Also add: `show - not found` and `show - customer unavailable` → `locked == "unavailable"`; `show - another
agent's mandate` (403 from the mandate client) → `locked == "unavailable"` and `verify(customerClient,
never()).get(any())` (the customer is never read); `show - closed` → `locked == "closed"`; `save - invalid` →
view `mandate/customer-edit`, `errors` non-empty, `verify(customerClient, never()).update(any(), any())`;
`save - e-mail conflict` → 409 `PARTY_EMAIL_ALREADY_EXISTS`, view again with `errors["email"]`; `save - moved
on` (403/404/409 other code) → `redirect:/mandates/201`, flash `changed`; `save - server failure` (500) → view
again, `error` set.

- [ ] **Step 2: Run them to watch them fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=CustomerEditControllerTest`
Expected: FAIL — 404 (no controller), or unresolved model keys.

- [ ] **Step 3: Implement `CustomerEditController`**

`@Controller @RequestMapping("/mandates/{id}/customer/edit")`, constructor `CurrentAgent`, `NdMandateClient`,
`NdCustomerClient`, `TenantContext`, `Clock`. Constants: `VIEW = "mandate/customer-edit"`, `LOCKED_UNAVAILABLE
= "unavailable"`, `LOCKED_CLOSED = "closed"`, `CHANGED_STATUSES = setOf(403, 404, 409)`, `SAVE_FAILED =
"Enregistrement impossible pour le moment. Réessayez dans quelques instants."`. Use `MandateController.OUTCOME`,
`MandateController.CUSTOMER_SAVED` and `MandateController.CUSTOMER_SAVED_UNSENT` (defined in Step 4) rather than
repeating the strings, so the flash attribute and its wording can't drift.

- `show`: agent (`SignInRequiredException`), `ProfileRules.header`; `load(id)` (a mandate client read kept only
  when `status in MandateRules.SHOWN`); null → locked unavailable; status not in `CUSTOMER_EDITABLE` → locked
  closed; customer read failing → locked unavailable; else render the form.
- `save`: agent; reload the mandate, `make sure` it is still in `CUSTOMER_EDITABLE` else redirect back with
  `changed` (the `MandateController.current`/`back` pattern); reload the customer (needed for the outcome and a
  correct form on error); errors → render; `customerClient.update`; on success flash
  `MandateController.CUSTOMER_SAVED_UNSENT` when `mandate.status == PENDING_APPROVAL &&
  !form.email.trim().equals(customer.party.email, ignoreCase = true)`, else `MandateController.CUSTOMER_SAVED`;
  back to `/mandates/{id}` under `MandateController.OUTCOME`. `PARTY_EMAIL_ALREADY_EXISTS` → render with
  `mapOf("email" to "Cette adresse email est déjà utilisée par un autre contact.")`; another 403/404/409 →
  `changed`; other errors → log and render with `SAVE_FAILED`.
- `form(...)`: adds `mandate` (`MandateViews.of(mandate, null, null, null, emptyMap(), clock.millis(),
  TenantFormatter(tenantContext.getTenant()))`), `mandateId`, `answers`, `errors`, `error`.
- `locked(id, reason, model)`: adds `mandateId` and `locked`, returns `VIEW`.

- [ ] **Step 4: Add the outcome wording to `MandateController`**

Constants `CUSTOMER_SAVED = "customer-saved"`, `CUSTOMER_SAVED_UNSENT = "customer-saved-unsent"`; in `outcome()`:

```kotlin
CUSTOMER_SAVED -> "Coordonnées ${view.words.of} enregistrées."
CUSTOMER_SAVED_UNSENT ->
    "Coordonnées ${view.words.of} enregistrées. Le mandat est revenu à « Assigné » : envoyez-le à nouveau " +
        "pour signature."
```

Add `MandateControllerTest` cases mirroring `outcome - sent`:

```kotlin
@Test fun `outcome - customer saved`() {
    val result = mvc.perform(get("/mandates/201").flashAttr("outcome", "customer-saved")).andReturn()
    assertEquals("Coordonnées du client enregistrées.", model(result)["outcomeText"])
    assertEquals("success", model(result)["outcomeStyle"])
}
```

and the `customer-saved-unsent` sentence.

- [ ] **Step 5: Write the failing template test**

`CustomerEditTemplateTest` renders through the controller with a real Thymeleaf engine (copy
`MandateEditTemplateTest`'s setup). Assert: `#firstName`, `#lastName`, `#email`, `#whatsappNumber` values; the
visible phone input still posts its E.164 value (`name="whatsappNumber"` in the HTML, before `mandate.js` moves
the name to the hidden input intl-tel-input syncs); the submit button; the back link to `/mandates/201`;
`errors["firstName"]` rendered when the form is shown again; the locked text for `closed` and `unavailable`.

- [ ] **Step 6: Create the template**

`templates/mandate/customer-edit.html`, copying `mandate/edit.html`'s shell and the profile edit page's
field/error markup. One form, four fields, no hidden version; "Enregistrer" and "Annuler". The locked alert
renders `locked == 'unavailable'` and `locked == 'closed'` separately.
The WhatsApp field follows the profile edit page: load intl-tel-input 25.11.2 (CSS + JS) and `util.js` before
`mandate.js`; the visible `type="tel"` input keeps `name="whatsappNumber"` and its E.164 value so the form still
posts without JS. Extend `mandate.js` (which already owns the mandate pages) with a `customer-edit` initializer
using `join.js`'s `initPhones` pattern: move the name to a hidden input, `NdUtil.phoneReady()` then
`NdUtil.bindPhone()`, map `getValidationError()` to the French messages, and re-check the phone on submit so a
number the library can't read never reaches the server empty.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=CustomerEditControllerTest,CustomerEditTemplateTest,MandateControllerTest,MandateTemplateTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src
git commit -m "feat(npc): edit a mandate's customer details"
```

---

### Task 7: Docs and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the mandate page bullet)
- Modify: `modules/ndopify-server/CLAUDE.md` (the customer domain bullet)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Document the Partner Central page**

Add to the mandate bullet: the unboxed contact block, its Modifier link only outside `CLOSED`/`CANCELLED`, the
`CustomerEditController` route, the client call, the field rules (the WhatsApp field via intl-tel-input /
`util.js`, posting E.164), and the `customer-saved` / `customer-saved-unsent` outcomes (a pending contract is
cancelled when the e-mail changes).

- [ ] **Step 2: Document the server endpoint**

In the **customer** bullet: `POST /v1/mandates/{id}/customer` updates the customer's names, e-mail and WhatsApp
number for the mandate's agent or an admin; a `PENDING_APPROVAL` mandate whose e-mail changes cancels its
contract and returns to `ASSIGNED`; a closed/cancelled mandate is a 409; an e-mail another party owns is a 409
`PARTY_EMAIL_ALREADY_EXISTS`.

- [ ] **Step 3: Verify every touched module**

```bash
cd modules/ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS for both, ktlint clean, JaCoCo gates met. (The server needs local MySQL.)

- [ ] **Step 4: Check the tree**

Run: `git status --short`
Expected: only `pom.xml` modified (`<ndopify-dto.version>0.0.90-SNAPSHOT`, the local pin), nothing else. Never
commit it.

- [ ] **Step 5: Commit the docs**

```bash
git add modules/ndopify-partner-central/CLAUDE.md modules/ndopify-server/CLAUDE.md
git commit -m "docs(npc): document the customer edit page"
```

- [ ] **Step 6: Report**

Tell the user the story is implemented and green, and that it is ready for `push 125`. Do not push.
