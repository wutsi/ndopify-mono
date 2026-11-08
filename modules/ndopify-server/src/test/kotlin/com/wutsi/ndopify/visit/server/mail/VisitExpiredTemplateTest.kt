package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitExpiredTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "other_party" to "Kim Possible",
        "listing_title" to "Nice apartment",
        "requested_date" to "02/12/2026",
        "requested_time" to "10:00",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
    )

    @Test
    fun `the customer may ask for another visit`() {
        val html = resolver.resolve(VisitExpiredMailet.TEMPLATE, context)

        assertTrue(html.contains("demander une nouvelle visite"))
        assertTrue(html.contains("Kim Possible"))
    }

    @Test
    fun `the agent is told to contact the customer`() {
        val html = resolver.resolve(
            VisitExpiredMailet.TEMPLATE,
            context + mapOf("recipient_name" to "Kim Possible", "other_party" to "Ray Sponsible", "agent_recipient" to true),
        )

        assertTrue(html.contains("Contactez votre client"))
        assertFalse(html.contains("demander une nouvelle visite"))
    }
}
