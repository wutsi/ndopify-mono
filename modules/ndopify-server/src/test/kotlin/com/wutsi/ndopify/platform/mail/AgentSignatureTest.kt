package com.wutsi.ndopify.platform.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.filter.HtmlEscapeFilter
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The customer e-mails sent on behalf of an agent carry the agent's details in the footer only: the sign-off stays
 * neutral, so the content doesn't read as if somebody else wrote it.
 */
class AgentSignatureTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(HtmlEscapeFilter()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
    )

    private val templates = listOf(
        "/mail/contract/signature_request.mjml",
        "/mail/match/proposed.mjml",
        "/mail/visit/cancelled.mjml",
        "/mail/visit/no_show.mjml",
        "/mail/visit/expired.mjml",
        "/mail/visit/reminder.mjml",
        "/mail/visit/completed.mjml",
        "/mail/visit/confirmed.mjml",
        "/mail/mandate/assigned.mjml",
        "/mail/mandate/assigned_listing.mjml",
    )

    @Test
    fun `the sign-off does not name the author`() {
        templates.forEach { path ->
            val signOff = Jsoup.parse(resolver.resolve(path, context))
                .select("div")
                .single { it.text().trim().startsWith("Cordialement") }

            assertEquals("Cordialement,", signOff.text().trim(), "$path: the sign-off names an author")
        }
    }

    @Test
    fun `the tenant team signs when there is no agent`() {
        val text = Jsoup.parse(
            resolver.resolve("/mail/contract/signature_request.mjml", context - "agent_full_name"),
        ).text()

        assertTrue(text.contains("Cordialement, L'équipe Ndopify"))
        assertFalse(text.contains("Kim Possible"), "the footer must not render without an agent")
    }
}
