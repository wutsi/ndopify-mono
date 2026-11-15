# Telegram Agent Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an agent link its Telegram account to its Ndopify agent account: request a 6-digit code in Partner Central, receive it by e-mail, send `/connect <code>` to the bot, which records the chat id for #136.

**Architecture:** A global `T_AGENT_TELEGRAM` row — deliberately not `@TenantId`, like `T_USER` — holds the pending code and the linked chat id, so #134's webhook (no JWT, no tenant) can resolve it by the code alone. The server issues the code from `POST /v1/agents/me/telegram/code` and e-mails it asynchronously through the existing mailet pipeline. #134's `TelegramUpdateHandler` gains a small `TelegramCommand` dispatch; the agent domain answers `/connect`. Partner Central shows the status from `Agent.telegramConnected` and posts to a BFF route that calls the server.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), Thymeleaf, Flyway, ktlint, JUnit 5 + `kotlin.test` + mockito-kotlin.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/135

**Prerequisite:** #134 (`docs/superpowers/plans/2026-11-15-telegram-bot.md`) is merged. This plan modifies `TelegramClient`, `TelegramUpdateHandler`, `TelegramConfiguration`, `ndopify.telegram.*` and `TELEGRAM_TOKEN`, which #134 creates. If a name from #134's plan differs from the one below, follow the code #134 merged and keep this plan's interfaces.

## Global Constraints

- **Prototype gate (Task 1):** the implementation stops after Task 1, shows the prototype to the user, and waits for the OK before any DTO, server or BFF change. Feedback that changes the design updates this plan (a `docs(npc)` commit on `main`) and rebases the branch on it.
- **DTO release:** the root `pom.xml` pins `0.0.90`; this plan needs `Agent.telegramConnected` and the new error code. `mvn install` the DTO and point `<ndopify-dto.version>` at its `0.0.91-SNAPSHOT` locally (an uncommitted change, reverted before `push`). Never commit a `-SNAPSHOT`.
- **Migration:** a new `V11_1__agent-telegram.sql`; never edit an existing migration (the TEST database is deployed). `clean.sql` gets the new table.
- **Tenancy:** `AgentTelegramEntity` is **not** `@TenantId` — the webhook has no tenant and looks the row up by code. Do not add a manual tenant predicate either. `agent_id` and `chat_id` are globally unique.
- **Code rules:** 6 digits, 15-minute TTL (`ndopify.agent.telegram.code-ttl-minutes`), one code per agent, single use, one chat per agent account. No reconnect, no disconnect.
- **Secrets:** never log the bot token, the webhook secret, nor the connection code.
- **Text:** UI, e-mail and bot replies in French; code, comments, logs and docs in English. Public classes and methods get KDoc for their non-obvious rules.
- **Tests:** `kotlin.test` assertions, mockito-kotlin mocks; server endpoint tests use `TestRestTemplate` and local MySQL; `mvn verify` stays green in every touched module (server's JaCoCo line gate is 0.95).
- **Commits:** conventional; each touches one library module (`ndopify-dto`) or none.

## Review Focus

1. A code stored for an agent of **another tenant** is linked from the webhook, which carries no token and no tenant (global lookup).
2. An expired, unknown or already-used code answers the invalid message and writes nothing.
3. A chat already linked to another agent is refused, and neither link changes.
4. An already-connected agent's code request is 409 with no e-mail; the stored row is untouched.
5. `/connect` without a code (or with a malformed one) answers invalid without touching the database; a non-command message still answers `OK`.

---

### Task 1: Prototype the home banner

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html` (after the `home-intro` header, before the assignments section)
- Modify if needed: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- Produces: the approved markup and French copy the served page copies in Task 13.

- [ ] **Step 1: Add the home banner**

In `index.html`, right after the `</header>` of `home-intro` and before the assignments status paragraph, add a banner using the existing `alert alert--info` classes (see the KYC banners of the served page for the icon markup):

```html
<div class="alert alert--info" data-telegram-banner>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">
        <circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/>
        <path d="M6 10.2 9 13l5-6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <div>
        <p class="alert__title">Recevez vos missions sur Telegram</p>
        <p>Les nouvelles missions vous arrivent directement dans Telegram.</p>
        <p><button class="btn btn--primary" type="button">Recevoir mon code par e-mail</button></p>
    </div>
</div>
```

Add only the CSS the banner needs (its margin below the header); reuse `alert` and `btn` otherwise.

- [ ] **Step 2: Check the prototype in a browser**

Open `modules/ndopify-partner-central/src/main/html/index.html` (without JavaScript, then with it). The banner must read well at phone width and keep the page usable without JavaScript.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html
git commit -m "feat(npc): prototype the telegram connection"
```

- [ ] **Step 4: Stop for the prototype gate**

Show the banner and its rendered copy to the user; wait for the OK on the prototype before Task 2. Any change to the design goes back into this plan first.

---

### Task 2: DTO — the connected flag and the error code

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/agent/dto/Agent.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`

**Interfaces:**
- Produces: `Agent.telegramConnected: Boolean = false`; `ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED = "urn:wutsi:ndopify:error:agent:telegram-already-connected"`.

- [ ] **Step 1: Add the field and the code**

After `whatsappNumber` in `Agent`, add:

```kotlin
val telegramConnected: Boolean = false, // True once the agent has linked its Telegram account
```

In `ErrorCode`, in the `agent` group, after `AGENT_ALREADY_EXISTS`:

```kotlin
val AGENT_TELEGRAM_ALREADY_CONNECTED = "$PREFIX:agent:telegram-already-connected"
```

- [ ] **Step 2: Install the snapshot**

Run: `cd modules/ndopify-dto && mvn install`
Expected: BUILD SUCCESS, version `0.0.91-SNAPSHOT`.

- [ ] **Step 3: Point the modules at the snapshot (local only)**

In the root `pom.xml`, set `<ndopify-dto.version>0.0.91-SNAPSHOT</ndopify-dto.version>`. This change is **never committed**: `push` reverts it and bumps to the released version instead.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/agent/dto/Agent.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): report the agent telegram connection"
```

---

### Task 3: Store the link

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V11_1__agent-telegram.sql`
- Modify: `modules/ndopify-server/src/test/resources/db/test/clean.sql`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/domain/AgentTelegramEntity.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/dao/AgentTelegramRepository.kt`

**Interfaces:**
- Produces:
  - `data class AgentTelegramEntity(id: Long? = null, tenantId: Long = 0, agentId: Long = 0, code: String? = null, codeExpiresAt: Date? = null, chatId: Long? = null, linkedAt: Date? = null, createdAt: Date = Date(), modifiedAt: Date = Date())`
  - `AgentTelegramRepository.findByAgentId(agentId: Long): AgentTelegramEntity?`
  - `AgentTelegramRepository.findByCode(code: String): AgentTelegramEntity?`
  - `AgentTelegramRepository.findByChatId(chatId: Long): AgentTelegramEntity?`
  - `AgentTelegramRepository.link(code: String, chatId: Long, now: Date): Int` — the conditional update, 1 when it linked, 0 when the code was gone/expired or the row already linked.

- [ ] **Step 1: Write the migration**

```sql
CREATE TABLE T_AGENT_TELEGRAM(
  id                BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id         BIGINT NOT NULL,
  agent_id          BIGINT NOT NULL,

  code              VARCHAR(6) DEFAULT NULL,
  code_expires_at   DATETIME DEFAULT NULL,
  chat_id           BIGINT DEFAULT NULL,
  linked_at         DATETIME DEFAULT NULL,

  created_at        DATETIME NOT NULL DEFAULT NOW(),
  modified_at       DATETIME NOT NULL DEFAULT NOW() ON UPDATE NOW(),

  -- NULLs are allowed to repeat in a MySQL unique index: only issued codes and linked chats must be unique.
  UNIQUE (agent_id),
  UNIQUE (code),
  UNIQUE (chat_id),

  CONSTRAINT FK_AGENT_TELEGRAM_agent FOREIGN KEY(agent_id) REFERENCES T_AGENT(id),
  CONSTRAINT FK_AGENT_TELEGRAM_tenant FOREIGN KEY(tenant_id) REFERENCES T_TENANT(id),
  PRIMARY KEY(id)
) ENGINE = InnoDB;

CREATE INDEX I_AGENT_TELEGRAM_tenant ON T_AGENT_TELEGRAM(tenant_id);
```

- [ ] **Step 2: Clean the table between tests**

In `clean.sql`, add `DELETE FROM T_AGENT_TELEGRAM;` just before `DELETE FROM T_AGENT;`.

- [ ] **Step 3: Write the entity and the repository**

`AgentTelegramEntity` has no association and no `@TenantId`: the webhook loads it without a tenant. Its KDoc says why (like `UserEntity`'s). `createdAt` / `modifiedAt` default to `Date()`.

In `AgentTelegramRepository` (a `CrudRepository`), declare the three finders and:

```kotlin
@Modifying(clearAutomatically = true)
@Query(
    "UPDATE AgentTelegramEntity e SET e.chatId = :chatId, e.code = NULL, e.codeExpiresAt = NULL, " +
        "e.linkedAt = :now, e.modifiedAt = :now " +
        "WHERE e.code = :code AND e.codeExpiresAt > :now AND e.chatId IS NULL"
)
fun link(code: String, chatId: Long, now: Date): Int
```

- [ ] **Step 4: Apply the migration**

Run: `cd modules/ndopify-server && mvn test -Dtest=ResetDBTest`
Expected: PASS — Flyway applies `V11_1` (local MySQL required).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V11_1__agent-telegram.sql \
        modules/ndopify-server/src/test/resources/db/test/clean.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/domain/AgentTelegramEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/dao/AgentTelegramRepository.kt
git commit -m "feat(agent): store the telegram connection"
```

---

### Task 4: Issue a connection code

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/TelegramCodeGenerator.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeCreatedEvent.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/TelegramCodeGeneratorTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeCreatedEventTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt`

**Interfaces:**
- Produces:
  - `class TelegramCodeGenerator { fun generate(): String }` — 6 digits, `SecureRandom`.
  - `data class AgentTelegramCodeCreatedEvent(val tenantId: Long, val agentId: Long, val code: String, val ttlMinutes: Long)`, whose `toString` prints `code=******` (like `OtpCreatedEvent`) so the code is never logged.
  - `class AgentTelegramService(dao, generator, eventPublisher, clock, membershipService, @Value("\${ndopify.agent.telegram.code-ttl-minutes}") ttlMinutes: Long)` with `fun requestCode(agent: AgentEntity)`, `fun isConnected(agentId: Long): Boolean` (Task 7) and `fun connect(code: String, chatId: Long): AgentTelegramLinkResult` (Task 8).

- [ ] **Step 1: Write the failing generator test**

`TelegramCodeGeneratorTest`, mirroring `OtpGeneratorTest`:

```kotlin
class TelegramCodeGeneratorTest {
    @Test
    fun generate() {
        val codes = (1..100).map { TelegramCodeGenerator().generate() }
        codes.forEach { assertEquals(6, it.length); assertTrue(it.all(Char::isDigit)) }
    }
}
```

- [ ] **Step 2: Run it, then implement `TelegramCodeGenerator`**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramCodeGeneratorTest` — FAIL, unresolved.
Implement it as a `@Service`: `fun generate() = (SecureRandom().nextInt(900_000) + 100_000).toString()`.
Run again — PASS.

- [ ] **Step 3: Write the failing event and service tests**

`AgentTelegramCodeCreatedEventTest`, mirroring `OtpCreatedEventTest`:

```kotlin
@Test
fun `toString masks the code`() {
    val event = AgentTelegramCodeCreatedEvent(tenantId = 2L, agentId = 10L, code = "123456", ttlMinutes = 15)

    assertFalse(event.toString().contains("123456"), "the clear code must never be logged")
    assertEquals("AgentTelegramCodeCreatedEvent(tenantId=2, agentId=10, code=******, ttlMinutes=15)", event.toString())
}
```

`AgentTelegramServiceTest` mocks `AgentTelegramRepository`, `TelegramCodeGenerator`, `ApplicationEventPublisher`, `Clock` and `MembershipService`; `membershipService.currentTenantId()` returns `1L`, `clock.millis()` returns a fixed `now`, `generator.generate()` returns `"123456"`. The service is built with `ttlMinutes = 15`.

```kotlin
@Test
fun `request stores a 6-digit code valid 15 minutes and publishes it`() {
    doReturn(null).whenever(dao).findByAgentId(10L)
    val saved = argumentCaptor<AgentTelegramEntity>()

    service.requestCode(agent)

    verify(dao).save(saved.capture())
    assertEquals(1L, saved.firstValue.tenantId)
    assertEquals(10L, saved.firstValue.agentId)
    assertEquals("123456", saved.firstValue.code)
    assertEquals(Date(now + 15 * 60_000), saved.firstValue.codeExpiresAt)
    assertNull(saved.firstValue.chatId)
    verify(eventPublisher).publishEvent(AgentTelegramCodeCreatedEvent(1L, 10L, "123456", 15))
}

@Test
fun `a new request replaces the previous code`() {
    val existing = AgentTelegramEntity(id = 3L, tenantId = 1L, agentId = 10L, code = "000000", codeExpiresAt = Date(now))
    doReturn(existing).whenever(dao).findByAgentId(10L)

    service.requestCode(agent)

    val saved = argumentCaptor<AgentTelegramEntity>()
    verify(dao).save(saved.capture())
    assertEquals(3L, saved.firstValue.id)
    assertEquals("123456", saved.firstValue.code)
}

@Test
fun `an already connected agent is refused`() {
    doReturn(AgentTelegramEntity(id = 3L, tenantId = 1L, agentId = 10L, chatId = 555L)).whenever(dao).findByAgentId(10L)

    val ex = assertFailsWith<ConflictException> { service.requestCode(agent) }

    assertEquals(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED, ex.error.code)
    verify(dao, never()).save(any())
    verify(eventPublisher, never()).publishEvent(any())
}
```

`agent` is `AgentEntity(id = 10L, party = PartyEntity(id = 100L))`.

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentTelegramCodeCreatedEventTest,AgentTelegramServiceTest'`
Expected: FAIL — `AgentTelegramService`, the event and its masked `toString` are unresolved.

- [ ] **Step 5: Implement the event and `requestCode`**

`AgentTelegramCodeCreatedEvent.toString` prints `code=******` for `code`, as `OtpCreatedEvent` does.

`requestCode` is `@Transactional(rollbackOn = [Exception::class])`: it reads `findByAgentId(agent.id!!)`, throws `ConflictException(Error(code = AGENT_TELEGRAM_ALREADY_CONNECTED))` when the row has a `chatId`, otherwise saves the row (reusing its id, setting `tenantId` on a new one) with the generated code and `codeExpiresAt = Date(clock.millis() + ttlMinutes * 60_000)`, then publishes `AgentTelegramCodeCreatedEvent(membershipService.currentTenantId(), agent.id!!, code, ttlMinutes)`. KDoc: the code replaces the previous one, and an already-linked agent can't get one.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramCodeGeneratorTest,AgentTelegramCodeCreatedEventTest,AgentTelegramServiceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/TelegramCodeGenerator.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeCreatedEvent.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/TelegramCodeGeneratorTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeCreatedEventTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt
git commit -m "feat(agent): issue a telegram connection code"
```

---

### Task 5: E-mail the code

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentTelegramCodeMailet.kt`
- Create: `modules/ndopify-server/src/main/resources/mail/agent/telegram_code.mjml`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeEventListener.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentTelegramCodeMailetTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeEventListenerTest.kt`

**Interfaces:**
- Consumes: `AgentTelegramCodeCreatedEvent`, `AgentTelegramService.requestCode`.
- Produces: `class AgentTelegramCodeMailet(tenantService, mailService, mailBodyResolver, membershipService, @Value("\${ndopify.telegram.username}") botUsername: String) { fun send(agent: AgentEntity, code: String, ttlMinutes: Long) }`; `AgentService.sendTelegramCode(agentId: Long, code: String, ttlMinutes: Long)` — the `@Transactional` service method the listener calls, like `sendWelcomeEmail`.

- [ ] **Step 1: Write the failing mailet test**

Mirror `OtpMailetTest` (mock the four collaborators, a tenant with a logo, a membership whose user is `ray.sponsible@gmail.com` and whose party is `Ray Sponsible`). The mailet is built with `botUsername = "ndopify_bot"`.

```kotlin
@Test
fun `send the code email`() {
    doReturn(tenant).whenever(tenantService).findById(1L)
    doReturn("<html>telegram</html>").whenever(mailBodyResolver).resolve(any(), any())

    mailet.send(agent, "123456", 15)

    val email = argumentCaptor<Email>()
    verify(mailService).send(email.capture())
    assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
    assertEquals("Votre code de connexion Telegram Ndopify", email.firstValue.subject)

    val context = argumentCaptor<Map<String, Any?>>()
    verify(mailBodyResolver).resolve(eq("/mail/agent/telegram_code.mjml"), context.capture())
    assertEquals(
        mapOf(
            "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
            "tenant_name" to "Ndopify",
            "recipient_name" to "Ray Sponsible",
            "telegram_code" to "123456",
            "ttl" to 15L,
            "bot_url" to "https://t.me/ndopify_bot",
        ),
        context.firstValue,
    )
}
```

Also assert that a missing membership throws (the listener logs it): `doReturn(null).whenever(membershipService).findByPartyIdOrNull(100L)` then `assertFailsWith<IllegalStateException>`.

- [ ] **Step 2: Write the failing listener test**

`AgentTelegramCodeEventListenerTest`: `onAgentTelegramCodeCreated` sets `TenantContext` to the event's tenant, calls `agentService.sendTelegramCode(agentId, code, ttlMinutes)`, removes the context afterwards, and logs a failure without rethrowing. Assert `TenantContext.get()` inside a `doAnswer` on the service call, and that it is null after. A thrown exception must not escape. (No listener test exists to copy in this repo; build it around the mocked `AgentService` and `AgentTelegramCodeMailet`, mirroring `OtpEventListener`'s body.)

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentTelegramCode*Test'`
Expected: FAIL — the mailet and listener are unresolved.

- [ ] **Step 4: Implement the mailet**

`AgentTelegramCodeMailet.send`:
- resolves `membershipService.findByPartyIdOrNull(agent.party.id!!)` and `tenantService.findById(membershipService.currentTenantId())`;
- the recipient is the membership's **user** e-mail (`user.email`), the display name the party's `fullName()`;
- the subject is `"Votre code de connexion Telegram {{tenant_name}}"` with `{{tenant_name}}` replaced by the tenant's name;
- the body comes from `/mail/agent/telegram_code.mjml` with the context asserted above.

- [ ] **Step 5: Write the e-mail template**

`telegram_code.mjml` copies `mail/security/otp.mjml`'s structure (logo, divider, sign-off) with this French copy:

```
Bonjour {{recipient_name}},
Pour recevoir vos nouvelles missions sur Telegram, ouvrez le bot Ndopify et envoyez-lui ce code :
{{telegram_code}}
Il expire dans {{ttl}} minutes. Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.
[mj-button href="{{bot_url}}"]Ouvrir Telegram[/mj-button]
L'équipe {{tenant_name}}
```

- [ ] **Step 6: Implement the listener and the service method**

`AgentTelegramCodeEventListener` is `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`, copied from `OtpEventListener` (tenant + `DefaultKVLogger` set and removed, failure logged, never rethrown) and calls `agentService.sendTelegramCode(event.agentId, event.code, event.ttlMinutes)`.

`AgentService` injects `AgentTelegramCodeMailet` and gains, next to `sendWelcomeEmail`:

```kotlin
/** Sends the Telegram connection code of an agent. Called by AgentTelegramCodeEventListener on its own thread,
 *  with TenantContext set: the transaction gives the mailet a session to load the membership. */
@Transactional
fun sendTelegramCode(agentId: Long, code: String, ttlMinutes: Long) {
    telegramMailet.send(findById(agentId), code, ttlMinutes)
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentTelegramCode*Test'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mail/AgentTelegramCodeMailet.kt \
        modules/ndopify-server/src/main/resources/mail/agent/telegram_code.mjml \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeEventListener.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/mail/AgentTelegramCodeMailetTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/event/AgentTelegramCodeEventListenerTest.kt
git commit -m "feat(agent): e-mail the telegram connection code"
```

---

### Task 6: The code-request endpoint

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTelegramEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/RequestTelegramCodeEndpointTest.kt`

**Interfaces:**
- Consumes: `AgentTelegramService.requestCode`, `CallerService`, `AgentService`.
- Produces: `POST /v1/agents/me/telegram/code` → 200, no body.

- [ ] **Step 1: Write the failing endpoint test**

Extend `AbstractAgentEndpointTest`. Reuse `/db/test/clean.sql` + `/db/test/agent/GetAgentEndpoint.sql` and add the caller's global identity:

```kotlin
@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        "INSERT INTO T_USER(id, email) VALUES (11, 'ray.sponsible@gmail.com'), (12, 'no.agent@example.com')",
        "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES (101, 1, 'no.agent@example.com', 'No', 'Agent')",
        "INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id) VALUES (11, 1, 11, 100), (12, 1, 12, 101)",
    ],
)
class RequestTelegramCodeEndpointTest : AbstractAgentEndpointTest() {
    @Autowired
    private lateinit var dao: AgentTelegramRepository

    @Test
    fun `request a code`() {
        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(1L)!!
        assertEquals(1L, link.tenantId)
        assertEquals(6, link.code!!.length)
        assertTrue(link.code!!.all(Char::isDigit))
        assertTrue(link.codeExpiresAt!!.after(Date()))
        assertNull(link.chatId)
        awaitAsyncTasks()
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    }

    @Test
    fun `an already connected agent cannot request a code`() {
        // INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES (1, 1, 555, NOW())
        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED, response.body!!.error.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    @Test
    fun `not an agent`() {
        userId = 12L

        val response = rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body!!.error.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        assertEquals(HttpStatus.UNAUTHORIZED, rest.postForEntity("/v1/agents/me/telegram/code", null, ErrorResponse::class.java).statusCode)
    }
}
```

Annotate the already-connected test with a method-level `@Sql(statements = ["INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES (1, 1, 555, NOW())"])`: Spring runs the class-level `@Sql` first, then the method's, so the fixture is in place for that test only.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=RequestTelegramCodeEndpointTest`
Expected: FAIL — 404/405, the route doesn't exist.

- [ ] **Step 3: Implement the endpoint**

```kotlin
@RestController
@RequestMapping("/v1/agents/me/telegram")
class AgentTelegramEndpoints(
    private val callerService: CallerService,
    private val agentService: AgentService,
    private val telegramService: AgentTelegramService,
) {
    @PostMapping("/code")
    fun requestCode() {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
            ?: throw NotFoundException(Error(code = ErrorCode.AGENT_NOT_FOUND))
        val agent = agentService.findByPartyIdOrNull(partyId)
            ?: throw NotFoundException(Error(code = ErrorCode.AGENT_NOT_FOUND))
        telegramService.requestCode(agent)
    }
}
```

KDoc: the caller's agent (404 `AGENT_NOT_FOUND`), and 409 `AGENT_TELEGRAM_ALREADY_CONNECTED` when the chat is linked.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=RequestTelegramCodeEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTelegramEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/RequestTelegramCodeEndpointTest.kt
git commit -m "feat(agent): request a telegram connection code"
```

---

### Task 7: Report `telegramConnected`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mapper/AgentMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentMeEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentEndpointTest.kt`

**Interfaces:**
- Consumes: `AgentTelegramService.isConnected(agentId)`.
- Produces: `AgentMapper.toAgent(entity: AgentEntity, telegramConnected: Boolean): Agent`.

- [ ] **Step 1: Add the failing assertions**

In `GetAgentMeEndpointTest`: the default fixture has no link row, so add `assertFalse(agent.telegramConnected)` to `me()`, plus a test that inserts `T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id)` for agent 1 and asserts `assertTrue(...)`. In `GetAgentEndpointTest`, assert false in `get()` (and true with a custom `@Sql` insert). Cover a row with only a code (not linked) → false.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='GetAgent*EndpointTest'`
Expected: FAIL — `telegramConnected` is missing (unresolved) or always false.

- [ ] **Step 3: Implement it**

`AgentMapper.toAgent(entity, telegramConnected)` sets the field. `AgentEndpoints` injects `AgentTelegramService` and passes `telegramService.isConnected(agent.id!!)` in `get` and `me`. `isConnected` is `dao.findByAgentId(agentId)?.chatId != null`.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='GetAgent*EndpointTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/mapper/AgentMapper.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentMeEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/GetAgentEndpointTest.kt
git commit -m "feat(agent): report whether telegram is connected"
```

---

### Task 8: `/connect` in the bot

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCommand.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramLinkResult.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramCommand.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramCommandTest.kt`

**Interfaces:**
- Produces:
  - `interface TelegramCommand { fun replyTo(message: TelegramMessage): String? }` — the reply to send, or null when the message isn't its command.
  - `TelegramUpdateHandler(client, commands: List<TelegramCommand>)`: a message gets the first non-null reply, else `OK`; an update without a message sends nothing.
  - `enum class AgentTelegramLinkResult { CONNECTED, INVALID_CODE, CHAT_ALREADY_LINKED }`
  - `AgentTelegramService.connect(code: String, chatId: Long): AgentTelegramLinkResult`
  - `AgentTelegramCommand` with `CONNECTED`, `INVALID` and `CHAT_LINKED` companion constants (the French replies).

- [ ] **Step 1: Write the failing `connect` tests**

Add to `AgentTelegramServiceTest` (same mocks, `now`):

```kotlin
@Test
fun `a valid code links the chat`() {
    doReturn(row(code = "123456", expiresAt = Date(now + 60_000))).whenever(dao).findByCode("123456")
    doReturn(null).whenever(dao).findByChatId(777L)
    doReturn(1).whenever(dao).link("123456", 777L, Date(now))

    assertEquals(AgentTelegramLinkResult.CONNECTED, service.connect("123456", 777L))

    verify(dao).link("123456", 777L, Date(now))
}

@Test
fun `an expired code is invalid`() {
    doReturn(row(code = "123456", expiresAt = Date(now - 1))).whenever(dao).findByCode("123456")

    assertEquals(AgentTelegramLinkResult.INVALID_CODE, service.connect("123456", 777L))
    verify(dao, never()).link(any(), any(), any())
}

@Test
fun `an unknown code is invalid`() {
    doReturn(null).whenever(dao).findByCode("000000")

    assertEquals(AgentTelegramLinkResult.INVALID_CODE, service.connect("000000", 777L))
    verify(dao, never()).link(any(), any(), any())
}

@Test
fun `a code taken meanwhile is invalid`() {
    doReturn(row()).whenever(dao).findByCode("123456")
    doReturn(null).whenever(dao).findByChatId(777L)
    doReturn(0).whenever(dao).link("123456", 777L, Date(now))

    assertEquals(AgentTelegramLinkResult.INVALID_CODE, service.connect("123456", 777L))
}

@Test
fun `a chat linked to another agent is refused`() {
    doReturn(row()).whenever(dao).findByCode("123456")
    doReturn(AgentTelegramEntity(id = 9L, agentId = 20L, chatId = 777L)).whenever(dao).findByChatId(777L)

    assertEquals(AgentTelegramLinkResult.CHAT_ALREADY_LINKED, service.connect("123456", 777L))
    verify(dao, never()).link(any(), any(), any())
}

@Test
fun `a code of another tenant links that agent`() {
    doReturn(AgentTelegramEntity(id = 9L, tenantId = 2L, agentId = 20L, code = "123456", codeExpiresAt = Date(now + 60_000)))
        .whenever(dao).findByCode("123456")
    doReturn(null).whenever(dao).findByChatId(777L)
    doReturn(1).whenever(dao).link("123456", 777L, Date(now))

    assertEquals(AgentTelegramLinkResult.CONNECTED, service.connect("123456", 777L))
}
```

`row()` is `AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, code = "123456", codeExpiresAt = Date(now + 60_000))`.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramServiceTest`
Expected: FAIL — `connect` and the enum are unresolved.

- [ ] **Step 3: Implement `connect`**

`@Transactional`: `findByCode(code)`; INVALID when null, past `codeExpiresAt`, or already carrying a chat; CHAT_ALREADY_LINKED when `findByChatId(chatId) != null`; then `dao.link(code, chatId, now)` — CONNECTED on 1, INVALID on 0. KDoc: the webhook has no tenant, the lookup is by the code alone, and the conditional update makes a code single use.

- [ ] **Step 4: Write the failing command and handler tests**

`AgentTelegramCommandTest` (mock `AgentTelegramService`, messages via `TelegramMessage(chat = TelegramChat(id = 777L), text = …)`):

```kotlin
@Test
fun `a valid connect command links the chat`() {
    doReturn(AgentTelegramLinkResult.CONNECTED).whenever(service).connect("123456", 777L)
    assertEquals(AgentTelegramCommand.CONNECTED, command.replyTo(message("/connect 123456")))
}

@Test
fun `the group form with the bot name also works`() {
    doReturn(AgentTelegramLinkResult.CONNECTED).whenever(service).connect("123456", 777L)
    assertEquals(AgentTelegramCommand.CONNECTED, command.replyTo(message("/connect@ndopify_bot 123456")))
}

@Test
fun `an invalid code says so`() {
    doReturn(AgentTelegramLinkResult.INVALID_CODE).whenever(service).connect("123456", 777L)
    assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect 123456")))
}

@Test
fun `a chat linked elsewhere is refused`() { … CHAT_ALREADY_LINKED → AgentTelegramCommand.CHAT_LINKED }

@Test
fun `a connect without a code is invalid without a lookup`() {
    assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect")))
    assertEquals(AgentTelegramCommand.INVALID, command.replyTo(message("/connect 12345")))
    verifyNoInteractions(service)
}

@Test
fun `another message is not its command`() {
    assertNull(command.replyTo(message("bonjour")))
    assertNull(command.replyTo(TelegramMessage(chat = TelegramChat(id = 777L), text = null)))
    verifyNoInteractions(service)
}
```

`TelegramUpdateHandlerTest`: keep #134's `OK` cases, constructing `TelegramUpdateHandler(client, emptyList())`; add one test with a command whose `replyTo` returns `"reçu"` — the client sends `"reçu"`, and the command was called once.

- [ ] **Step 5: Implement the dispatch**

`TelegramUpdateHandler.handle`: `val message = update.message ?: return`; `client.sendMessage(message.chat.id, commands.firstNotNullOfOrNull { it.replyTo(message) } ?: "OK")`. `TelegramConfiguration`'s handler bean takes `commands: List<TelegramCommand>` and passes them.

- [ ] **Step 6: Implement the command**

`AgentTelegramCommand` (a `@Component`): trim `message.text`, return null when blank; the head is the text up to the first space; accept `/connect` and `/connect@<anything>` case-insensitively; the rest must be exactly 6 digits, else `INVALID` without a service call; map CONNECTED / CHAT_ALREADY_LINKED / INVALID_CODE to the constants. KDoc: the head form covers the `/connect@bot` Telegram sends in groups.

- [ ] **Step 7: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AgentTelegramServiceTest,AgentTelegramCommandTest,TelegramUpdateHandlerTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCommand.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramLinkResult.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramCommand.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramCommandTest.kt
git commit -m "feat(telegram): link an agent with /connect"
```

---

### Task 9: The webhook links without a tenant

**Files:**
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTelegramWebhookEndpointTest.kt`

**Interfaces:**
- Consumes: `/telegram/webhook` (#134), `AgentTelegramRepository`, `TelegramClient` (mocked).

- [ ] **Step 1: Write the failing integration test**

```kotlin
@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
@Sql(
    scripts = ["/db/test/clean.sql", "/db/test/agent/GetAgentEndpoint.sql"],
    statements = [
        // Agent 2 belongs to tenant 2; the webhook below carries no token and no tenant.
        "INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, code, code_expires_at) " +
            "VALUES (2, 2, '123456', DATE_ADD(UTC_TIMESTAMP(), INTERVAL 10 MINUTE))",
    ],
)
class AgentTelegramWebhookEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var dao: AgentTelegramRepository

    @Test
    fun `a connect from another tenant links its agent`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":777},"text":"/connect 123456"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(2L)!!
        assertEquals(777L, link.chatId)
        assertNull(link.code)
        assertNull(link.codeExpiresAt)
        verify(client).sendMessage(777L, AgentTelegramCommand.CONNECTED)
    }

    @Test
    fun `an unknown code gets the invalid message and writes nothing`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":2,"message":{"message_id":3,"chat":{"id":777},"text":"/connect 000000"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val link = dao.findByAgentId(2L)!!
        assertNull(link.chatId)
        assertEquals("123456", link.code)
        verify(client).sendMessage(777L, AgentTelegramCommand.INVALID)
    }
}
```

`DATE_ADD(UTC_TIMESTAMP(), …)` because the datasource reads dates as UTC (see the server's `CLAUDE.md`).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramWebhookEndpointTest`
Expected: FAIL — the chat isn't linked (the handler doesn't dispatch yet in a real context) or the table fixture is missing.

- [ ] **Step 3: Fix the wiring until it passes**

The expected gaps are a missing `TelegramCommand` bean or a handler not given the command list; fix only what the test proves. The webhook path is public (#134).

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramWebhookEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/AgentTelegramWebhookEndpointTest.kt
git commit -m "test(telegram): link an agent from the webhook"
```

---

### Task 10: Configuration, deployment and documentation

**Files:**
- Modify: `modules/ndopify-server/src/main/resources/application.yml`
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml`
- Modify: `.github/workflows/ndopify-server-main.yml`
- Modify: `CLAUDE.md`
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: `ndopify.telegram.username` (the mailet), `ndopify.agent.telegram.code-ttl-minutes` (the service).

- [ ] **Step 1: Add the properties**

In `application.yml`, inside #134's `ndopify.telegram` block, add `username: ${TELEGRAM_BOT_USERNAME}` (no default: the bot's public username). Add a new block next to the other domains:

```yaml
    agent:
        telegram:
            code-ttl-minutes: 15          # How long the 6-digit connection code stays valid
```

In `application-qa.yml`, inside the `telegram` block, add `username: qa-bot`.

- [ ] **Step 2: Pass the username to Heroku**

In `ndopify-server-main.yml`'s `deploy-test` job, add `TELEGRAM_BOT_USERNAME: ${{secrets.TELEGRAM_BOT_USERNAME}}` to the `env` of the `Prepare Heroku Files` step and `heroku config:set --app "$HEROKU_APP" TELEGRAM_BOT_USERNAME="$TELEGRAM_BOT_USERNAME"` next to the other `config:set` lines.

- [ ] **Step 3: Document it**

Root `CLAUDE.md`, *Environment Setup*: add `TELEGRAM_BOT_USERNAME` (the bot's public username, used in the connection e-mail's `https://t.me/<bot>` link) next to #134's Telegram variables. `modules/ndopify-server/CLAUDE.md`: in the **agent** domain paragraph, add the Telegram link (a global `T_AGENT_TELEGRAM` row, the 15-minute code e-mailed to the login address, `/connect <code>` from the bot, no reconnect), and add `telegram` to the `platform/` line if #134 didn't.

- [ ] **Step 4: Check the configuration parses**

Run: `cd modules/ndopify-server && mvn test -Dtest=ResetDBTest`
Expected: PASS — the `qa` profile supplies `telegram.token` and `telegram.username`, so the context starts.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/test/resources/application-qa.yml \
        .github/workflows/ndopify-server-main.yml \
        CLAUDE.md modules/ndopify-server/CLAUDE.md
git commit -m "chore(telegram): deploy the bot username"
```

---

### Task 11: Server verification

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass (local MySQL required).

- [ ] **Step 2: Fix and commit anything it reports**

Use `mvn antrun:run@ktlint-format` for formatting, a conventional `fix(agent): …` / `test(agent): …` commit otherwise. Then run `mvn verify` again.

---

### Task 12: The BFF route that requests a code

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramRules.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramControllerTest.kt`

**Interfaces:**
- Produces: `NdAgentClient.requestTelegramCode()`; `POST /telegram/code` → flash `telegramCodeSent = true` on success and `telegramError` otherwise.

- [ ] **Step 1: Add the client call**

```kotlin
/** Asks the server to e-mail a Telegram connection code to the signed-in agent. */
fun requestTelegramCode() {
    rest.post().uri("/v1/agents/me/telegram/code").retrieve().toBodilessEntity()
}
```

- [ ] **Step 2: Write the failing controller test**

`TelegramControllerTest` (standalone `MockMvc`, mocked `CurrentAgent` and `NdAgentClient`):

```kotlin
@Test
fun `the home banner sends the code and comes back home`() {
    val result = mvc.perform(post("/telegram/code")).andReturn()

    assertEquals("redirect:/", result.response.redirectedUrl)
    assertEquals(true, result.flashMap[TelegramRules.SENT])
    verify(agentClient).requestTelegramCode()
}

@Test
fun `a connected agent gets the already-connected message`() {
    doThrow(conflict(ErrorCode.AGENT_TELEGRAM_ALREADY_CONNECTED)).whenever(agentClient).requestTelegramCode()
    val result = mvc.perform(post("/telegram/code")).andReturn()
    assertEquals(TelegramRules.ALREADY_CONNECTED, result.flashMap[TelegramRules.ERROR])
    assertNull(result.flashMap[TelegramRules.SENT])
}

@Test
fun `a server failure is reported`() {
    doThrow(ResourceAccessException("down")).whenever(agentClient).requestTelegramCode()

    val result = mvc.perform(post("/telegram/code")).andReturn()

    assertEquals(TelegramRules.SEND_FAILED, result.flashMap[TelegramRules.ERROR])
    assertNull(result.flashMap[TelegramRules.SENT])
}
```

Use the `conflict(code)` helper of `ProfileControllerTest` for the 409. The setup stubs `currentAgent.get()` (the controller throws `SignInRequiredException` without it, like `ProfileController`).

- [ ] **Step 3: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest`
Expected: FAIL — the controller and rules are unresolved.

- [ ] **Step 4: Implement the rules and controller**

`TelegramRules`: `SENT = "telegramCodeSent"`, `ERROR = "telegramError"`, `ALREADY_CONNECTED = "Votre compte Telegram est déjà connecté."`, `SEND_FAILED = "L'envoi du code a échoué. Réessayez dans quelques instants."`.

`TelegramController` (`@Controller @RequestMapping("/telegram")`): `POST /code` requires `currentAgent.get()`, calls `agentClient.requestTelegramCode()`, flashes `SENT` on success; on `HttpClientErrorException` with 409 flashes `ALREADY_CONNECTED`, on any other `RestClientException` logs and flashes `SEND_FAILED`; returns `redirect:/`. KDoc: the redirect target is fixed, never a URL from the request.

- [ ] **Step 5: Run it to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdAgentClient.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram
git commit -m "feat(npc): request a telegram connection code"
```

---

### Task 13: The home banner

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: `Agent.telegramConnected`, the flash attributes of Task 12.
- Produces: the model attribute `telegramConnected`.

- [ ] **Step 1: Write the failing tests**

`HomeControllerTest`: `show()` puts `telegramConnected = false` on the model for `AGENT`, and `true` when `currentAgent.get()` returns `AGENT.copy(telegramConnected = true)`.

`HomeTemplateTest`:
- not connected → `doc.select("[data-telegram-banner]")` has one element, whose form action is `/telegram/code`;
- connected → no `[data-telegram-banner]`;
- with the `telegramCodeSent` flash attribute (add it to the request: `.flashAttr(TelegramRules.SENT, true)`) → the banner shows the sent message and the `/connect` instruction;
- with `telegramError` → the banner shows the message.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='HomeControllerTest,HomeTemplateTest'`
Expected: FAIL — no `telegramConnected` attribute, no banner.

- [ ] **Step 3: Implement the banner**

`HomeController.show` adds `model.addAttribute("telegramConnected", agent.telegramConnected)` next to the other profile flags. In `index.html`, render the approved Task 1 banner after the `home-intro` header, before the KYC banners: `th:if="${not telegramConnected}"`, the success line when `telegramCodeSent`, the error line from `telegramError`, and wrap the button in a `<form method="post" action="/telegram/code">`. Never show it for a connected agent.

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='HomeControllerTest,HomeTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/index.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "feat(npc): invite the agent to connect telegram"
```

---

### Task 14: Partner Central verification

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass.

- [ ] **Step 2: Fix and commit anything it reports**

Use `mvn antrun:run@ktlint-format` for formatting, a conventional `fix(npc): …` / `test(npc): …` commit otherwise. Then run `mvn verify` again.
