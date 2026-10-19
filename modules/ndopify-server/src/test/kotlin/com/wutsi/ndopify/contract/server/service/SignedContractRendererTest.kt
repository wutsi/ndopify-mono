package com.wutsi.ndopify.contract.server.service

import com.github.mustachejava.DefaultMustacheFactory
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import dev.jcputney.mjml.MjmlRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedContractRendererTest {
    private val renderer = SignedContractRenderer(DefaultMustacheFactory())

    private val qrCode = renderer.qrCode("https://sign.ndopify.com/verify/c-1/" + "a".repeat(64))

    private fun block(legalName: String, hash: Char) = SignatureBlock(
        legalName = legalName,
        signedAt = "18 octobre 2026 à 14:32",
        signatureHash = hash.toString().repeat(64),
        qrCodePng = qrCode,
    )

    // On one line: a hash may be wrapped
    private fun text(pdf: ByteArray): String = Loader.loadPDF(pdf).use { doc -> PDFTextStripper().getText(doc) }
        .replace("\r", "")
        .replace("\n", "")

    @Test
    fun `render keeps the body and drops the head`() {
        val pdf = renderer.render(
            "<html><head><title>SECRET-TITLE</title></head><body><p>Article 1</p></body></html>",
            listOf(block("Ray Sponsible", 'a')),
        )

        assertEquals("%PDF", String(pdf.copyOfRange(0, 4), Charsets.US_ASCII))
        val text = text(pdf)
        assertTrue(text.contains("Article 1"))
        assertFalse(text.contains("SECRET-TITLE"))
    }

    @Test
    fun `render adds one block per signature`() {
        val text = text(
            renderer.render(
                "<html><body><p>Article 1</p></body></html>",
                listOf(block("Ray Sponsible", 'a'), block("John Smith", 'b')),
            )
        )

        assertTrue(text.contains("Signatures"))
        assertTrue(text.contains("Ray Sponsible"))
        assertTrue(text.contains("John Smith"))
        assertTrue(text.contains("18 octobre 2026 à 14:32"))
        assertTrue(text.contains("a".repeat(64)))
        assertTrue(text.contains("b".repeat(64)))
        assertTrue(text.contains("Scannez le QR code pour vérifier cette signature."))
    }

    @Test
    fun `render embeds the QR codes`() {
        val pdf = renderer.render(
            "<html><body><p>Article 1</p></body></html>",
            listOf(block("Ray Sponsible", 'a'), block("John Smith", 'b')),
        )

        val images = Loader.loadPDF(pdf).use { doc ->
            doc.pages.sumOf { page ->
                page.resources.xObjectNames.count { name -> page.resources.isImageXObject(name) }
            }
        }
        assertEquals(2, images)
    }

    @Test
    fun `render escapes the legal name`() {
        val text = text(renderer.render("<html><body></body></html>", listOf(block("<b>Ray</b> & Co", 'a'))))

        assertTrue(text.contains("<b>Ray</b> & Co"))
    }

    @Test
    fun `renders non-latin1 characters`() {
        val text = text(
            renderer.render(
                "<html><body><p>Fait à Yaoundé</p></body></html>",
                listOf(block("Ŋgo Ɛkwa", 'a').copy(signedAt = "18 octobre 2026 à 14 h 32")),
            )
        )

        assertTrue(text.contains("Ŋgo Ɛkwa"))
        assertTrue(text.contains("Fait à Yaoundé"))
        assertTrue(text.contains("14 h 32"))
    }

    @Test
    fun `renders the mandate contract template`() {
        val html = MailBodyResolver(
            renderer = MjmlRenderer.create(),
            mustache = DefaultMustacheFactory(),
            filters = listOf(MailFilterNull()),
        ).resolve(
            "/mandate/contract/search.mjml",
            mapOf(
                "contract_number" to "MNDT-2026-000001",
                "contract_date" to "17/10/2026",
                "duration_months" to "6",
                "client_name" to "Ray Sponsible",
                "tenant_name" to "Ndopify",
                "property_type" to "Appartement",
                "city" to "Yaoundé",
                "budget" to "150 000 XAF / mois",
            ),
        )

        val pdf = renderer.render(html, listOf(block("Ray Sponsible", 'a')))
        val text = text(pdf)

        assertTrue(text.contains("MNDT-2026-000001"))
        // On the first page: MJML's inline-block column must not be pushed to the next page whole
        val firstPage = Loader.loadPDF(pdf).use { doc -> PDFTextStripper().apply { endPage = 1 }.getText(doc) }
        assertTrue(firstPage.contains("MNDT-2026-000001"))
        assertTrue(text.contains("a".repeat(64)))
    }

    @Test
    fun `qrCode is a PNG`() {
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            qrCode.copyOfRange(0, 8),
        )
    }
}
