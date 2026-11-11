package com.wutsi.ndopify.match.server.job

import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.CoBrokerageMatchMailet
import com.wutsi.ndopify.match.server.mail.MatchNotificationMailet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/NotifyCoBrokerageJob.sql"])
class NotifyCoBrokerageJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var ownMailet: MatchNotificationMailet

    @MockitoBean
    private lateinit var coBrokerageMailet: CoBrokerageMatchMailet

    @Test
    fun `notifies the search agent of a co-brokerage match`() {
        jobs.notifyAgents()

        val coBrokerage = argumentCaptor<List<MatchEntity>>()
        verify(coBrokerageMailet).send(argThat { id == 20L }, coBrokerage.capture())
        assertEquals(listOf(1L), coBrokerage.firstValue.map { it.id })

        val own = argumentCaptor<List<MatchEntity>>()
        verify(ownMailet).send(argThat { id == 20L }, own.capture())
        assertEquals(listOf(2L), own.firstValue.map { it.id })

        assertTrue(notifiedAt(1L))
        assertTrue(notifiedAt(3L))
        assertFalse(notifiedAt(4L))
    }

    private fun notifiedAt(id: Long): Boolean =
        jdbc.queryForList("SELECT notified_at FROM T_MATCH WHERE id=?", id).single()["notified_at"] != null
}
