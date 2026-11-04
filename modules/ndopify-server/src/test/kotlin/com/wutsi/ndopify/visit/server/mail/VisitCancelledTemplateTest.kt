package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitCancelledTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Kim Possible",
        "other_party" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "cancelled_by_label" to "Le client a annulé la visite.",
        "cancellation_reason" to "Empêchement",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("annul"))
        assertTrue(html.contains("Empêchement"))
        assertTrue(html.contains("https://partner.ndopify.com/visits/1"))
    }

    @Test
    fun `without link nor reason`() {
        val html = resolver.resolve(VisitCancelledMailet.TEMPLATE, context - "visit_url" - "cancellation_reason")

        assertFalse(html.contains("https://partner.ndopify.com/visits/1"))
        assertTrue(html.contains("annul"))
    }
}
