package com.wutsi.ndopify.contract.server.mail

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

/** Renders the template, which [ContractSignatureMailetTest] doesn't (its resolver is a mock). */
class ContractSignatureTemplateTest {
    private val resolver = MailBodyResolver(
        renderer = MjmlRenderer.create(),
        mustache = DefaultMustacheFactory(ClasspathResolver()),
        filters = listOf(MailFilterNull()),
    )

    private val context = mapOf(
        "tenant_name" to "Ndopify",
        "recipient_name" to "Ray Sponsible",
        "contract_type" to "Contrat de bail",
        "contract_number" to "BAIL-2026-000042",
        "deadline" to "01/01/2027",
        "sign_url" to "https://sign.ndopify.com/sign/cp-1",
        "agent_full_name" to "Kim Possible",
        "agent_whatsapp" to "+237 6 99 00 00 01",
        "agent_whatsapp_url" to "https://wa.me/237699000001",
        "agent_whatsapp_reply_url" to "https://wa.me/237699000001?text=Bonjour",
        "agent_customer_email" to true,
        "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
    )

    @Test
    fun `the agent prepared the contract`() {
        val html = resolver.resolve("/mail/contract/signature_request.mjml", context)
        val text = Jsoup.parse(html).text()

        assertTrue(text.contains("Je vous ai préparé votre Contrat de bail"))
        assertTrue(text.contains("Kim Possible"))
        assertTrue(html.contains("Me répondre sur WhatsApp"))
        assertTrue(html.contains("Envoyé via Ndopify"))
        assertFalse(text.contains("L'équipe Ndopify"))
        assertFalse(text.contains("Nous vous invitons"))
        assertFalse(html.contains("alt=\"Logo\""), "the agent copy keeps the divider, never the tenant logo")
    }

    @Test
    fun `a contract without an agent keeps the tenant voice`() {
        val html = resolver.resolve(
            "/mail/contract/signature_request.mjml",
            context - "agent_full_name" - "agent_whatsapp" - "agent_whatsapp_url" - "agent_whatsapp_reply_url" -
                "agent_customer_email",
        )

        val text = Jsoup.parse(html).text()
        assertTrue(text.contains("Merci de le lire attentivement"))
        assertTrue(text.contains("Cordialement, L'équipe Ndopify"))
        assertFalse(html.contains("Je vous ai préparé"))
        assertFalse(html.contains("Me répondre sur WhatsApp"))
        assertFalse(html.contains("wa.me/"))
        assertTrue(html.contains("alt=\"Logo\""), "a contract without an agent keeps today's tenant logo")
    }

    @Test
    fun `the preview carries the deadline`() {
        val doc = Jsoup.parse(resolver.resolve("/mail/contract/signature_request.mjml", context))
        val preview = assertNotNull(doc.selectFirst("div[style*='display: none']"))

        assertEquals("À signer avant le 01/01/2027", preview.text())
    }
}
