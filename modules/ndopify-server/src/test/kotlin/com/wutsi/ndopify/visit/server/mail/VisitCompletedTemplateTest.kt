package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitCompletedTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
    )

    @Test
    fun render() {
        val html = resolver.resolve(VisitCompletedMailet.TEMPLATE, context)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("Le bien vous int"))
        assertTrue(html.contains("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz"))
    }

    @Test
    fun `without link`() {
        val html = resolver.resolve(VisitCompletedMailet.TEMPLATE, context - "visit_url")

        assertFalse(html.contains(" https://www.ndopify.com/visits/1"))
        assertTrue(html.contains("Le bien vous int"))
    }
}
