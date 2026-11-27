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

/** The publish job's first pass applies the ownership gate: a blocked VERIFIED listing is retried on the next run. */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishVerifiedListingsJob.sql"])
class PublishVerifiedListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var publishedNotifier: ListingPublishedNotifier

    @Test
    fun `publishes only the listing whose owner is verified`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals("PUBLISHED", status(100))
        assertEquals("VERIFIED", status(101))
        assertEquals("VERIFIED", status(102))
    }

    @Test
    fun `a blocked listing is published on the first run after its owner is verified`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())
        jobs.publish()
        assertEquals("VERIFIED", status(101))

        jdbc.update(
            """
            INSERT INTO T_MANDATE_DOCUMENT(
                id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at,
                expires_at, version, created_at, modified_at
            ) VALUES
                (2000, 1, 2, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
                 '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
                 1, NULL, '2099-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
                (2001, 1, 2, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
                 '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
                 1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00')
            """.trimIndent(),
        )

        jobs.publish()

        assertEquals("PUBLISHED", status(101))
        assertEquals("VERIFIED", status(102))
    }

    private fun status(id: Long): String =
        jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=?", String::class.java, id)!!
}
