package com.wutsi.ndopify.contract.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.Test
import kotlin.test.assertFalse
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
    )

    @Test
    fun `the e-mail does not speak as the tenant team`() {
        val html = resolver.resolve("/mail/contract/signature_request.mjml", context)

        assertTrue(html.contains("Merci de le lire attentivement"))
        assertFalse(html.contains("Nous vous invitons"))
        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("https://wa.me/237699000001"))
    }
}
