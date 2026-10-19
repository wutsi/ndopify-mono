package com.wutsi.ndopify.contract.server.service

import com.github.mustachejava.MustacheFactory
import com.lowagie.text.pdf.BaseFont
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.springframework.stereotype.Component
import org.xhtmlrenderer.pdf.ITextRenderer
import qrcode.QRCode
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringWriter
import java.util.Base64

/** A signature, as printed in the signed contract. */
data class SignatureBlock(
    val legalName: String,
    val signedAt: String, // Already formatted by TenantFormatter
    val signatureHash: String,
    val qrCodePng: ByteArray,
)

/**
 * Renders the signed PDF of a contract: the `<body>` of its HTML content (its `<head>` is dropped), followed by a
 * *Signatures* section, one block per signature (`/contract/signatures.html`). Rendered by Flying Saucer, which only
 * knows XHTML and CSS 2.1: the content is parsed by Jsoup (MJML's output isn't well-formed) and its comments and
 * scripts removed. Every text is in DejaVu Sans, embedded, since the PDF's built-in fonts can't print the tenant
 * formatter's no-break spaces nor names in Latin Extended (ex: Ŋ, Ɛ). Nothing is fetched for the signatures: their
 * QR codes are data URIs.
 */
@Component
class SignedContractRenderer(private val mustache: MustacheFactory) {
    companion object {
        private val FONTS = listOf("DejaVuSans.ttf", "DejaVuSans-Bold.ttf", "DejaVuSansMono.ttf")
            .map { font -> "contract/fonts/$font" }

        // !important: wins over the content's inline styles. Its fonts would be the built-in ones, and its MJML
        // columns (inline-block) can't be split across pages: a long one would start on the next page
        private const val CSS = """
            @page { size: A4; margin: 15mm; }
            * { font-family: 'DejaVu Sans' !important; }
            .mj-outlook-group-fix { display: block !important; }
            .signatures { margin-top: 24px; }
            .signature { width: 100%; page-break-inside: avoid; border-top: 1px solid #cccccc; padding: 8px 0; }
            .signature__qr { width: 110px; vertical-align: top; }
            .signature__hash { font-family: 'DejaVu Sans Mono' !important; font-size: 8pt; word-wrap: break-word; }
            .signature__hint { font-size: 8pt; color: #555555; }
        """
    }

    // Solid squares, without spacing between them: easier to scan
    fun qrCode(url: String): ByteArray = QRCode.ofSquares().withSize(8).withInnerSpacing(0).build(url).renderToBytes()

    /** Returns the PDF's bytes. */
    fun render(contentHtml: String, signatures: List<SignatureBlock>): ByteArray {
        val source = Jsoup.parse(contentHtml)
        source.select("script").remove()
        removeComments(source.body()) // Ex: MSO conditional comments of the MJML output

        // A document of our own: the content's body only, then the signatures
        val doc = Jsoup.parse("<html><head><style>$CSS</style></head><body></body></html>")
        doc.body().appendChildren(source.body().childNodes().toList())
        doc.body().append(signaturesHtml(signatures))
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml)

        val out = ByteArrayOutputStream()
        val renderer = ITextRenderer()
        FONTS.forEach { font -> renderer.fontResolver.addFont(font, BaseFont.IDENTITY_H, BaseFont.EMBEDDED) }
        renderer.setDocument(W3CDom().fromJsoup(doc), "")
        renderer.layout()
        renderer.createPDF(out)
        return out.toByteArray()
    }

    private fun signaturesHtml(signatures: List<SignatureBlock>): String {
        val context = mapOf(
            "signatures" to signatures.map { signature ->
                mapOf(
                    "legalName" to signature.legalName,
                    "signedAt" to signature.signedAt,
                    "signatureHash" to signature.signatureHash,
                    "qrCodeDataUri" to "data:image/png;base64," +
                        Base64.getEncoder().encodeToString(signature.qrCodePng),
                )
            },
        )
        val template = javaClass.getResourceAsStream("/contract/signatures.html")!!
        val writer = StringWriter()
        InputStreamReader(template, Charsets.UTF_8).use { reader ->
            mustache.compile(reader, "signatures").execute(writer, context)
        }
        return writer.toString()
    }

    private fun removeComments(node: Node) {
        node.childNodes().toList().forEach { child ->
            if (child is Comment) {
                child.remove()
            } else {
                removeComments(child)
            }
        }
    }
}
