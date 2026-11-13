package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders the reminder template, which [VisitReminderMailetTest] doesn't (its resolver is a mock). */
class VisitReminderTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val onSite = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "listing_title" to "Nice apartment",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237699000001",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "on_site" to true,
        "place" to "Bonapriso, Douala",
        "landmark" to "près de la boulangerie Zepol",
        "address" to "Rue 1.234, Bonapriso, Derrière la pharmacie",
        "code" to "1234",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
    )

    @Test
    fun `customer - ON_SITE`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_CUSTOMER, onSite)

        assertTrue(html.contains("1234"))
        assertTrue(html.contains("Bonapriso, Douala"))
        assertTrue(html.contains("près de la boulangerie Zepol"))
        assertTrue(html.contains("Je vous communiquerai l'adresse exacte et l'itinéraire"))
        assertTrue(html.contains("Je vous attends sur place"))
        assertTrue(html.contains("Présentez-le-moi"))
        assertFalse(html.contains("Rue 1.234"))
        assertFalse(html.contains("<b>Adresse</b>"))
    }

    @Test
    fun `customer - VIDEO`() {
        val html = resolver.resolve(
            VisitReminderMailet.TEMPLATE_CUSTOMER,
            onSite - "address" + mapOf("on_site" to false),
        )

        assertTrue(html.contains("1234"))
        assertTrue(html.contains("près de la boulangerie Zepol"))
        assertTrue(html.contains("Je vous appellerai en vidéo"))
        assertTrue(html.contains("Lisez-le-moi"))
        assertFalse(html.contains("Rue 1.234"))
        assertFalse(html.contains("Je vous communiquerai l'adresse"))
    }
}
