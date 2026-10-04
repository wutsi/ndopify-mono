package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource
import kotlin.test.assertEquals

abstract class AbstractAgentEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var ds: DataSource

    fun assertNeighborhoods(agentId: Long, expectedNeighborhoodIds: List<Long>) {
        val actualNeighborhoodIds = ds.connection.use { conn ->
            conn.prepareStatement("SELECT neighborhood_id FROM T_AGENT_NEIGHBORHOOD WHERE agent_id = ?").use { stmt ->
                stmt.setLong(1, agentId)
                stmt.executeQuery().use { rs ->
                    val ids = mutableListOf<Long>()
                    while (rs.next()) {
                        ids.add(rs.getLong("neighborhood_id"))
                    }
                    ids
                }
            }
        }
        assertEquals(expectedNeighborhoodIds.sorted(), actualNeighborhoodIds.sorted())
    }
}
