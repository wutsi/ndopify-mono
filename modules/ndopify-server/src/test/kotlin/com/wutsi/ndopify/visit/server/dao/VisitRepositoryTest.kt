package com.wutsi.ndopify.visit.server.dao

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitRepositoryTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `mandate and status are indexed`() {
        val count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = 'T_VISIT' AND index_name = 'I_VISIT_mandate_status'",
            Int::class.java,
        )

        assertEquals(1, count)
    }
}
