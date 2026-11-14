package com.wutsi.ndopify.visit.server.dao

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.time.Duration
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitRepositoryTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var dao: VisitRepository

    @Autowired
    private lateinit var listingService: ListingService

    @Test
    fun `mandate and status are indexed`() {
        val count = jdbc.queryForObject(
            "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = 'T_VISIT' AND index_name = 'I_VISIT_mandate_status'",
            Int::class.java,
        )

        assertEquals(1, count)
    }

    @Test
    @Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/ExpireDueVisits.sql"])
    fun `due requests are past both the minimum delay and the requested slot`() {
        val now = System.currentTimeMillis()
        val due = dao.findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
            VisitStatus.REQUESTED,
            Date(now - Duration.ofHours(48).toMillis()),
            Date(now),
        )

        assertEquals(listOf(7201L), due.map { it.id })
    }

    @Test
    @Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
    fun `co-brokerage visits excludes own visits and closed statuses`() {
        val listing = listingService.findById(101)

        assertEquals(
            listOf(2L, 1L),
            dao.findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
                listing.id!!,
                listing.agent.id!!,
                setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
            ).map { it.id },
        )
    }

    @Test
    @Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
    fun `open visits by mandate or listing mandate`() {
        // Search side: mandate 4 has visits 1..4 and 6 (CONFIRMED, CHECKED_IN, REQUESTED, COMPLETED, CONFIRMED)
        assertEquals(
            setOf(1L, 3L, 6L),
            dao.findOpenByMandateOrListingMandate(
                4,
                setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
            ).map { it.id }.toSet(),
        )

        // Listing side: listing mandate 11 owns listing 101, which holds visits 1..5
        assertEquals(
            setOf(1L, 3L, 5L),
            dao.findOpenByMandateOrListingMandate(
                11,
                setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
            ).map { it.id }.toSet(),
        )
    }

    @Test
    @Sql(
        value = [
            "/db/test/clean.sql",
            "/db/test/visit/VisitFixtures.sql",
            "/db/test/visit/CoBrokerageVisitEndpoint.sql",
            "/db/test/visit/EndedMandates.sql",
        ],
    )
    fun `the sweep finds open visits of ended mandates on both sides`() {
        // Search side (mandates 1 and 4): 1, 3, 5, 6. Listing side (listing 101, mandate 11): 1, 3, 5.
        // CHECKED_IN 2 and COMPLETED 4 are left out.
        assertEquals(
            setOf(1L, 3L, 5L, 6L),
            dao.findOpenByEndedMandate(
                setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED),
                setOf(MandateStatus.CLOSED, MandateStatus.CANCELLED),
                OffsetLimitPageRequest(0, 100, Sort.by(Sort.Direction.ASC, "id")),
            ).map { it.id }.toSet(),
        )
    }
}
