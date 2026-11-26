package com.wutsi.ndopify.visit.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "on_site" to true,
        "place" to "Bonapriso, Douala",
        "landmark" to "près de la boulangerie Zepol",
        "address" to "Rue 1.234, Bonapriso, Derrière la pharmacie",
        "code" to "1234",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
        "agent_customer_email" to true,
    )

    @Test
    fun `customer - ON_SITE`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_CUSTOMER, onSite)

        assertTrue(html.contains("1234"))
        assertTrue(html.contains("Bonapriso, Douala"))
        assertTrue(html.contains("près de la boulangerie Zepol"))
        assertTrue(html.contains("Je vous communiquerai l'adresse exacte et l'itinéraire"))
        assertTrue(html.contains("Je vous attends sur place"))
        assertTrue(html.contains("présentez-le-moi"))
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
        assertTrue(html.contains("lisez-le-moi"))
        assertFalse(html.contains("Rue 1.234"))
        assertFalse(html.contains("Je vous communiquerai l'adresse"))
    }

    @Test
    fun `the reminder speaks as the agent`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_CUSTOMER, onSite)

        assertFalse(html.contains("Votre agent"))
        assertTrue(html.contains("Petit rappel"))
        assertTrue(html.contains("notre visite du bien"))
        assertTrue(html.contains("Je vous attends sur place"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(html.contains("L'équipe Ndopify"))
    }

    @Test
    fun `no number, no reply`() {
        val html = resolver.resolve(
            VisitReminderMailet.TEMPLATE_CUSTOMER,
            onSite - "agent_whatsapp_reply_url" - "agent_whatsapp_url" - "agent_whatsapp",
        )

        assertFalse(html.contains("Me répondre sur WhatsApp"))
        assertFalse(html.contains("Les réponses à cet e-mail ne me parviennent pas"))
        assertFalse(html.contains("wa.me/"))
    }

    @Test
    fun `the preview carries the event`() {
        val doc = Jsoup.parse(resolver.resolve(VisitReminderMailet.TEMPLATE_CUSTOMER, onSite))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("Demain 02/12/2026 à 10:00 · Nice apartment", preview.text())
    }

    private val agent = onSite + mapOf(
        "recipient_name" to "Kim Possible",
        "customer_name" to "Ray Sponsible",
        "customer_whatsapp" to "+237600000001",
        "customer_whatsapp_url" to "https://wa.me/237600000001",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun `agent`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_AGENT, agent)

        assertEquals("+237600000001", Jsoup.parse(html).selectFirst("""a[href="https://wa.me/237600000001"]""")?.text())
        assertTrue(html.contains("Demandez le code de visite"))
    }

    @Test
    fun `agent - VIDEO`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_AGENT, agent + mapOf("on_site" to false))

        assertTrue(html.contains("appelez le client en vidéo sur WhatsApp"))
        assertEquals("+237600000001", Jsoup.parse(html).selectFirst("""a[href="https://wa.me/237600000001"]""")?.text())
        assertFalse(html.contains("Demandez le code de visite"))
    }

    @Test
    fun `agent - without the customer WhatsApp URL`() {
        val html = resolver.resolve(VisitReminderMailet.TEMPLATE_AGENT, agent - "customer_whatsapp_url")

        assertTrue(html.contains("+237600000001"))
        assertTrue(Jsoup.parse(html).select("""a[href^="https://wa.me/"]""").isEmpty())
    }
}
