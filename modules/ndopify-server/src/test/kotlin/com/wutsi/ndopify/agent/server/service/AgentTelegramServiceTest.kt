package com.wutsi.ndopify.agent.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.dao.AgentTelegramRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.domain.AgentTelegramEntity
import com.wutsi.ndopify.agent.server.event.AgentTelegramCodeCreatedEvent
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.server.service.MembershipService
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AgentTelegramServiceTest {
    private val dao = mock<AgentTelegramRepository>()
    private val generator = mock<TelegramCodeGenerator>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val membershipService = mock<MembershipService>()
    private val now = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()
    private val service = AgentTelegramService(
        dao = dao,
        generator = generator,
        eventPublisher = eventPublisher,
        clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
        membershipService = membershipService,
        ttlMinutes = 15,
    )

    private val agent = AgentEntity(id = 10L, party = PartyEntity(id = 100L))

    init {
        doReturn(1L).whenever(membershipService).currentTenantId()
        doReturn("123456").whenever(generator).generate()
        whenever(dao.save(any())).thenAnswer { it.arguments[0] as AgentTelegramEntity }
    }

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
}
