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

/** Renders the templates, which [VisitConfirmedMailetTest] doesn't (its resolver is a mock). */
class VisitConfirmedTemplateTest {
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
        "agent_photo_url" to "https://cdn.ndopify.com/agent.jpg",
        "agent_whatsapp" to "+237699000001",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "customer_name" to "Ray Sponsible",
        "customer_whatsapp" to "+237600000001",
        "visit_date" to "02/12/2026",
        "visit_time" to "10:00",
        "on_site" to true,
        "place" to "Bonapriso, Douala",
        "landmark" to "près de la boulangerie Zepol",
        "address" to "Rue 1.234, Bonapriso, Derrière la pharmacie",
        "code" to "1234",
        "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
        "by_agent" to false,
    )

    private val agentContext = onSite + mapOf(
        "recipient_name" to "Kim Possible",
        "visit_url" to "https://partner.ndopify.com/visits/1",
    )

    @Test
    fun `customer - ON_SITE`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite)

        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Nice apartment"))
        assertTrue(html.contains("02/12/2026"))
        assertTrue(html.contains("10:00"))
        assertTrue(html.contains("Bonapriso, Douala"))
        assertTrue(html.contains("près de la boulangerie Zepol"))
        assertTrue(html.contains("Je vous attends sur place"))
        assertTrue(html.contains("présentez-le-moi"))
        assertTrue(html.contains("Je vous communiquerai l'adresse exacte et l'itinéraire"))
        assertFalse(html.contains("Rue 1.234"))
        assertFalse(html.contains("<b>Adresse</b>"))
        assertTrue(html.contains("1234"))
        assertTrue(html.contains("Je vous confirme notre visite"))
        assertFalse(html.contains("a planifié votre visite"))
        assertFalse(html.contains("Votre agent"))
        assertTrue(html.contains("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz"))
        assertTrue(html.contains("https://wa.me/237699000001"))
        assertTrue(html.contains("src=\"https://cdn.ndopify.com/agent.jpg\""))
        assertFalse(html.contains("Je vous appellerai en vidéo"))
    }

    @Test
    fun `customer - booked by the agent`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite + mapOf("by_agent" to true))

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Je vous confirme la visite que j'ai organisée"))
        assertTrue(html.contains("Vous pouvez annuler la visite"))
        assertTrue(html.contains("1234"))
        assertFalse(html.contains("Je vous confirme notre visite"))
    }

    @Test
    fun `customer - VIDEO`() {
        val html = resolver.resolve(
            VisitConfirmedMailet.TEMPLATE_CUSTOMER,
            onSite - "address" + mapOf("on_site" to false),
        )

        assertTrue(html.contains("Je vous appellerai en vidéo"))
        assertTrue(html.contains("sur WhatsApp"))
        assertTrue(html.contains("+237699000001"))
        assertTrue(html.contains("près de la boulangerie Zepol"))
        assertTrue(html.contains("1234"))
        assertTrue(html.contains("lisez-le-moi"))
        assertFalse(html.contains("Rue 1.234"))
        assertFalse(html.contains("Je vous communiquerai l'adresse"))
    }

    @Test
    fun `the copy speaks as the agent, replies on WhatsApp`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite)

        assertFalse(html.contains("Votre agent"))
        assertFalse(html.contains("L'équipe Ndopify"))
        assertTrue(html.contains("Je vous confirme notre visite"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Les réponses à cet e-mail ne me parviennent pas"))
        assertTrue(html.contains("https://wa.me/237699000001?text="))
        assertTrue(html.contains("Envoyé via Ndopify"))
    }

    @Test
    fun `the reply button and its box need a WhatsApp number`() {
        val html = resolver.resolve(
            VisitConfirmedMailet.TEMPLATE_CUSTOMER,
            onSite - "agent_whatsapp_reply_url" - "agent_whatsapp_url" - "agent_whatsapp",
        )

        assertFalse(html.contains("Me répondre sur WhatsApp"))
        assertFalse(html.contains("Les réponses à cet e-mail ne me parviennent pas"))
        assertFalse(html.contains("wa.me/"))
    }

    @Test
    fun `the preview carries the event`() {
        val doc = Jsoup.parse(resolver.resolve(VisitConfirmedMailet.TEMPLATE_CUSTOMER, onSite))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("02/12/2026 à 10:00 · Nice apartment", preview.text())
    }

    @Test
    fun `agent - ON_SITE`() {
        val html = resolver.resolve(VisitConfirmedMailet.TEMPLATE_AGENT, agentContext)

        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Demandez le code de visite"))
        assertTrue(html.contains("https://partner.ndopify.com/visits/1"))
        assertFalse(html.contains("1234"))
        // No agent signature footer on the agent's own copy
        assertFalse(html.contains("https://wa.me/237699000001"))
    }

    @Test
    fun `agent - VIDEO`() {
        val html = resolver.resolve(
            VisitConfirmedMailet.TEMPLATE_AGENT,
            agentContext + mapOf("on_site" to false),
        )

        assertTrue(html.contains("appelez le client en vidéo sur WhatsApp"))
        assertTrue(html.contains("+237600000001"))
        assertFalse(html.contains("Demandez le code de visite"))
    }
}
