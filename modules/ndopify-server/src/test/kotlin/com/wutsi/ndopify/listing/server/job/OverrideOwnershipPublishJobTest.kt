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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The automatic publication sees the override (#377) on the same verdict as the endpoint. */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class OverrideOwnershipPublishJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var publishedNotifier: ListingPublishedNotifier

    @Test
    fun `an overridden owner lets the publish job send the listing`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())
        jobs.publish()
        assertEquals("VERIFIED", status(104))

        jdbc.update(
            """
            UPDATE T_MANDATE
            SET ownership_override_reason = 'Titre au nom de son père, vérifié en agence',
                ownership_override_by = 11,
                ownership_override_at = '2026-01-01 00:00:00'
            WHERE id = 5
            """.trimIndent(),
        )

        jobs.publish()

        assertEquals("PUBLISHED", status(104))
    }

    private fun status(id: Long): String =
        jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=?", String::class.java, id)!!
}
