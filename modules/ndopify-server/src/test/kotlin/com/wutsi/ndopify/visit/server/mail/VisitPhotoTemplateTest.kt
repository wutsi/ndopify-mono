package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The listing's photo, the same in every visit e-mail: shown when there is one, linked to the visit's page if any. */
class VisitPhotoTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull()),
    )

    private val templates = listOf(
        VisitRequestedMailet.TEMPLATE,
        VisitConfirmedMailet.TEMPLATE_CUSTOMER,
        VisitConfirmedMailet.TEMPLATE_AGENT,
        VisitReminderMailet.TEMPLATE_CUSTOMER,
        VisitReminderMailet.TEMPLATE_AGENT,
        VisitCancelledMailet.TEMPLATE,
        VisitExpiredMailet.TEMPLATE,
        VisitNoShowMailet.TEMPLATE,
        VisitCompletedMailet.TEMPLATE,
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "image_url" to "https://cdn.ndopify.com/7.jpg",
        "visit_url" to "https://www.ndopify.com/visits/1",
    )

    @Test
    fun `photo linked to the visit's page`() {
        templates.forEach { template ->
            val html = resolver.resolve(template, context)

            assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""), template)
            assertTrue(html.contains("<a href=\"https://www.ndopify.com/visits/1\""), template)
        }
    }

    @Test
    fun `no page - the photo without link`() {
        templates.forEach { template ->
            val html = resolver.resolve(template, context - "visit_url")

            assertTrue(html.contains("src=\"https://cdn.ndopify.com/7.jpg\""), template)
            assertFalse(html.contains("href=\"\""), template)
        }
    }

    @Test
    fun `no photo - no image`() {
        templates.forEach { template ->
            val html = resolver.resolve(template, context - "image_url")

            assertFalse(html.contains("cdn.ndopify.com/7.jpg"), template)
            assertTrue(html.contains("Ray Sponsible"), template)
        }
    }
}
