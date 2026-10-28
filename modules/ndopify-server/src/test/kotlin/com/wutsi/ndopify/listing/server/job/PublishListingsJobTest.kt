package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentGenerator
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A listing published by the job, and its customer and agent told, with the link to its page on the public portal. */
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingsJob.sql"])
class PublishListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @Test
    fun publish() {
        doReturn(ListingContentInfo(title = "Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
            .whenever(generator).generate(any())

        jobs.publish()

        // Read without the tenant filter: the test thread has no tenant
        val listing = jdbc.queryForMap("SELECT status, url FROM T_LISTING WHERE id=100")
        assertEquals("PUBLISHED", listing["status"])
        val url = "/yaounde/bastos/listing/100/appartement-a-louer-3-chambres-yaounde-bastos"
        assertEquals(url, listing["url"])

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "no email received")
        val messages = smtp.receivedMessages.sortedBy { message -> message.allRecipients.single().toString() }
        assertEquals(2, messages.size)
        assertEquals("Kim Possible <kim.possible@gmail.com>", messages[0].allRecipients.single().toString())
        assertEquals("Ndopify: Votre annonce à Yaoundé est publiée", messages[0].subject)
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", messages[1].allRecipients.single().toString())
        assertEquals("Ndopify: Votre bien à Yaoundé est en ligne", messages[1].subject)
        messages.forEach { message ->
            assertTrue(
                message.content.toString().contains("https://www.ndopify.com$url"),
                "no link to the listing",
            )
        }
    }
}
