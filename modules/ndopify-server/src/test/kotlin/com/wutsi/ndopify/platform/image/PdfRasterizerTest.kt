package com.wutsi.ndopify.platform.image

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rasterizer that turns a PDF into one JPEG per page: 200 DPI at most, 2000 px on the longest side, in the PDF's
 * order. Every refusal says why: encrypted, unreadable, empty, or more pages than the caller allows.
 */
class PdfRasterizerTest {
    private val rasterizer = PdfRasterizer()

    /** A PDF of [pages] pages of [width] x [height] points, each filled with [color]. No file fixture. */
    private fun pdf(
        pages: Int,
        width: Float = 200f,
        height: Float = 100f,
        color: (Int) -> Color = { Color.WHITE },
    ): ByteArray = PDDocument().use { document ->
        repeat(pages) { index ->
            val page = PDPage(PDRectangle(width, height))
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.setNonStrokingColor(color(index))
                content.addRect(0f, 0f, width, height)
                content.fill()
            }
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))

    private fun assertJpeg(bytes: ByteArray) {
        assertTrue(bytes.size > 2, "no bytes")
        assertEquals(0xFF, bytes[0].toInt() and 0xFF, "not a JPEG")
        assertEquals(0xD8, bytes[1].toInt() and 0xFF, "not a JPEG")
    }

    @Test
    fun `rasterize - a page becomes one jpeg at 200 dpi`() {
        val images = rasterizer.rasterize(pdf(1), 10)

        assertEquals(1, images.size)
        assertJpeg(images[0])
        val image = decode(images[0])
        // 200 x 100 pt at 200 DPI: 555.5 x 277.8 px, the renderer truncating
        assertTrue(image.width in 555..556, "width ${image.width}")
        assertTrue(image.height in 277..278, "height ${image.height}")
    }

    @Test
    fun `rasterize - the pages keep the pdf's order`() {
        val images = rasterizer.rasterize(
            pdf(3) { index -> listOf(Color.RED, Color.GREEN, Color.BLUE)[index] },
            10,
        )

        assertEquals(3, images.size)
        val colors = images.map { image -> Color(decode(image).getRGB(556 / 2, 278 / 2)) }
        assertTrue(colors[0].red > colors[0].blue + 60, "page 1 is not red ($colors)")
        assertTrue(colors[1].green > colors[1].red + 60 && colors[1].green > colors[1].blue + 60, "page 2 is not green ($colors)")
        assertTrue(colors[2].blue > colors[2].red + 60, "page 3 is not blue ($colors)")
    }

    @Test
    fun `rasterize - a page over 2000px on its longest side is scaled down to 2000px`() {
        val image = decode(rasterizer.rasterize(pdf(1, width = 4000f, height = 2000f), 10).single())

        assertEquals(2000, image.width)
        assertEquals(1000, image.height)
    }

    @Test
    fun `rasterize - more pages than allowed is TOO_MANY_PAGES`() {
        val ex = assertFailsWith<PdfRasterizationException> { rasterizer.rasterize(pdf(3), 2) }

        assertEquals(PdfRasterizationException.Reason.TOO_MANY_PAGES, ex.reason)
    }

    @Test
    fun `rasterize - an encrypted pdf is ENCRYPTED`() {
        val encrypted = PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle(200f, 100f)))
            document.protect(StandardProtectionPolicy("owner", "user", AccessPermission()))
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

        val ex = assertFailsWith<PdfRasterizationException> { rasterizer.rasterize(encrypted, 10) }

        assertEquals(PdfRasterizationException.Reason.ENCRYPTED, ex.reason)
    }

    @Test
    fun `rasterize - a corrupt pdf is UNREADABLE`() {
        val ex = assertFailsWith<PdfRasterizationException> { rasterizer.rasterize("not a pdf".toByteArray(), 10) }

        assertEquals(PdfRasterizationException.Reason.UNREADABLE, ex.reason)
    }

    @Test
    fun `rasterize - a pdf without a page is EMPTY`() {
        val empty = PDDocument().use { document -> ByteArrayOutputStream().also { document.save(it) }.toByteArray() }

        val ex = assertFailsWith<PdfRasterizationException> { rasterizer.rasterize(empty, 10) }

        assertEquals(PdfRasterizationException.Reason.EMPTY, ex.reason)
    }
}
