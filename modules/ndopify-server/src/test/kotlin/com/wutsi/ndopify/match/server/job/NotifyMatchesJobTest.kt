package com.wutsi.ndopify.match.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchNotificationMailet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/match/NotifyMatchesJob.sql"])
class NotifyMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var mailet: MatchNotificationMailet

    @Test
    fun notifyAgents() {
        jobs.notifyAgents()

        assertEquals(mapOf(20L to listOf(1L, 2L), 21L to listOf(3L)), sent())
        assertEquals(listOf(1L, 2L, 3L, 4L), notified())
    }

    @Test
    fun `nothing left on the next run`() {
        jobs.notifyAgents()
        jobs.notifyAgents()

        verify(mailet, times(2)).send(any(), any())
    }

    @Test
    fun `dismissed match not sent`() {
        jobs.notifyAgents()

        assertEquals(false, sent().values.flatten().contains(5L))
        assertEquals(false, notified().contains(5L))
    }

    @Test
    fun `failed agent is retried next run`() {
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(argThat { id == 20L }, any())

        jobs.notifyAgents()

        assertEquals(listOf(3L, 4L), notified()) // 21's sent and marked, 20's not
        verify(mailet).send(argThat { id == 21L }, any())

        reset(mailet)
        jobs.notifyAgents()

        assertEquals(mapOf(20L to listOf(1L, 2L)), sent())
        verify(mailet, never()).send(argThat { id == 21L }, any())
        assertEquals(listOf(1L, 2L, 3L, 4L), notified())
    }

    // The match ids sent to each agent
    private fun sent(): Map<Long, List<Long>> {
        val agent = argumentCaptor<AgentEntity>()
        val matches = argumentCaptor<List<MatchEntity>>()
        verify(mailet, atLeastOnce()).send(agent.capture(), matches.capture())
        return agent.allValues.zip(matches.allValues).associate { (agent, matches) ->
            agent.id!! to matches.map { match -> match.id!! }.sorted()
        }
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun notified(): List<Long> =
        jdbc.queryForList("SELECT id FROM T_MATCH WHERE notified_at IS NOT NULL ORDER BY id", Long::class.java).filterNotNull()
}
