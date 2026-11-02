package com.wutsi.ndopify.listing.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The PUBLISHED listings of mandates closed without their listing taken off the market, caught up by the job. */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/CloseListingsJob.sql"])
class CloseListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun close() {
        jobs.close()

        assertClosed(101, "SOLD")
        assertClosed(102, "RENTED")
        assertClosed(103, "CANCELLED")
        assertUnchanged(104, "PUBLISHED") // Mandate still approved
        assertUnchanged(105, "DRAFT") // Never published
    }

    // Read without the tenant filter: the test thread has no tenant
    private fun assertClosed(id: Long, status: String) {
        val listing = jdbc.queryForMap("SELECT status, closed_at FROM T_LISTING WHERE id=$id")
        assertEquals(status, listing["status"], "Listing#$id")
        assertNotNull(listing["closed_at"], "Listing#$id")
    }

    private fun assertUnchanged(id: Long, status: String) {
        val listing = jdbc.queryForMap("SELECT status, closed_at FROM T_LISTING WHERE id=$id")
        assertEquals(status, listing["status"], "Listing#$id")
        assertNull(listing["closed_at"], "Listing#$id")
    }
}
