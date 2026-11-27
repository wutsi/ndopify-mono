package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.listing.server.service.ListingPublishedNotifier
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentGenerator
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The publish job's first pass with the gate off: every VERIFIED listing is sent, and the verdict is still frozen. */
@TestPropertySource(properties = ["ndopify.listing.publish.require-owner-verification=false"])
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishVerifiedListingsJob.sql"])
class PublishVerifiedListingsJobWithoutOwnerVerificationTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var publishedNotifier: ListingPublishedNotifier

    @Test
    fun `publishes every VERIFIED listing and freezes the verdict`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals("PUBLISHED", status(100))
        assertEquals("PUBLISHED", status(101))
        assertEquals("PUBLISHED", status(102))
        assertEquals(1, frozen(100))
        assertEquals(0, frozen(101))
        assertEquals(0, frozen(102))
    }

    private fun status(id: Long): String =
        jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=?", String::class.java, id)!!

    private fun frozen(id: Long): Int =
        jdbc.queryForObject("SELECT ownership_verified FROM T_LISTING WHERE id=?", Int::class.java, id)!!
}
