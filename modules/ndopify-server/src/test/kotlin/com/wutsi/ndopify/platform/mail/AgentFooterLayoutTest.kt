package com.wutsi.ndopify.platform.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The agent footer signs the customer e-mails: it belongs to the white card, like the rest of the content, and is
 * separated from the sign-off by a divider.
 */
class AgentFooterLayoutTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
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
    fun `the agent footer is inside the white card`() {
        templates.forEach { path ->
            val doc = Jsoup.parse(resolver.resolve(path, context))
            val link = doc.selectFirst("a[href='https://wa.me/237699000001']")
            assertNotNull(link, "$path: the agent footer is missing")

            assertTrue(
                link.parents().any { isWhiteCard(it) },
                "$path: the agent footer renders outside the white card",
            )
        }
    }

    @Test
    fun `the agent footer has a divider`() {
        templates.forEach { path ->
            val doc = Jsoup.parse(resolver.resolve(path, context))
            val dividers = doc.select(".divider")

            // The card's own divider, then the footer's
            assertTrue(dividers.size >= 2, "$path: the agent footer has no divider")
            assertTrue(
                dividers[dividers.size - 1].parents().any { isWhiteCard(it) },
                "$path: the agent footer divider renders outside the white card",
            )
        }
    }

    private fun isWhiteCard(element: Element): Boolean =
        element.attr("style").replace(" ", "").contains("background-color:#ffffff")
}
