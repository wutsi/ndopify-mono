package com.wutsi.ndopify.platform.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        "agent_photo_url" to "https://cdn.ndopify.com/kim.png",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
    )

    private val templates = listOf(
        "/mail/contract/signature_request.mjml",
        "/mail/match/proposed.mjml",
        "/mail/match/visit_reminder.mjml",
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

    @Test
    fun `the agent footer shows the photo next to its details in a group`() {
        templates.forEach { path ->
            val doc = Jsoup.parse(resolver.resolve(path, context))
            val link = assertNotNull(
                doc.selectFirst("a[href='https://wa.me/237699000001']"),
                "$path: the agent footer is missing",
            )
            val details = link.parents().first { isColumn(it) }
            val group = details.parents().first { isGroup(it) }
            val photo = assertNotNull(
                group.selectFirst("img[alt='Photo de Kim Possible']"),
                "$path: the agent photo is missing",
            )
            val photoColumn = photo.parents().first { isColumn(it) }

            // mj-group renders as a full-width wrapper holding its columns inline-block: they never stack on a phone
            assertEquals(photoColumn.parent(), group, "$path: the photo and the details are not in one mj-group")
            assertEquals(2, group.children().count { isColumn(it) }, "$path: the footer needs 2 columns")
            assertTrue(photoColumn.attr("style").contains("width: 20%"), "$path: the photo column has no width")
            assertTrue(details.attr("style").contains("width: 80%"), "$path: the details column has no width")
            assertTrue(details.text().contains("Kim Possible"), "$path: the details do not name the agent")
            assertTrue(details.text().contains("WhatsApp"), "$path: the details have no WhatsApp link")
            assertTrue(details.text().contains("Envoyé via Ndopify"), "$path: the details do not carry the tenant line")
            assertFalse(details.text().contains("Votre agent"), "$path: the details still say 'Votre agent'")
            assertEquals(0, doc.select("img[alt='Ndopify']").size, "$path: the footer still shows the tenant logo")
        }
    }

    @Test
    fun `the agent footer without a photo keeps the details full width`() {
        templates.forEach { path ->
            val doc = Jsoup.parse(resolver.resolve(path, context - "agent_photo_url"))
            val details = assertNotNull(
                doc.selectFirst("a[href='https://wa.me/237699000001']"),
                "$path: the agent footer is missing",
            ).parents().first { isColumn(it) }

            assertNull(details.selectFirst("img"), "$path: a photo renders without one")
            assertEquals(1, details.parent()!!.children().count { isColumn(it) }, "$path: the empty photo column stays")
            assertTrue(details.attr("style").contains("width: 100%"), "$path: the details are not full width")
            assertTrue(details.text().contains("Envoyé via Ndopify"), "$path: the tenant line is missing")
        }
    }

    private fun isWhiteCard(element: Element): Boolean =
        element.attr("style").replace(" ", "").contains("background-color:#ffffff")

    private fun isColumn(element: Element): Boolean =
        element.classNames().any { it.startsWith("mj-column-per-") }

    // mj-group's wrapper: the full-width column that holds the group's own columns side by side
    private fun isGroup(element: Element): Boolean =
        element.classNames().contains("mj-column-per-100") &&
            element.children().count { isColumn(it) } == 2
}
