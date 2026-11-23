package com.wutsi.ndopify.agent.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the template, which [WelcomeMailetTest] doesn't (its resolver is a mock). */
class WelcomeTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
        "tenant_name" to "Ndopify",
        "recipient_name" to "John Doe",
        "kyc_url" to "https://partner.ndopify.com/kyc",
    )

    // The agent handles rentals and sales: the mail names both, with no condition
    // The agent handles rentals and sales: the mail names both, with no condition
    @Test
    fun `the welcome mail names buyers and sellers`() {
        val html = resolver.resolve("/mail/agent/welcome.mjml", context)

        assertTrue(html.contains("Renforcer la confiance des locataires, acheteurs, bailleurs et vendeurs"))
        assertTrue(html.contains("John Doe"))
        assertTrue(html.contains("https://partner.ndopify.com/kyc"))
    }
}
