package com.wutsi.ndopify.platform.image

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a PDF into one JPEG per page, in the PDF's order: a page a scanner or an e-mail delivered becomes an ordinary
 * photo page, so nothing downstream (preview, extraction) has to know where it came from. The original bytes are only
 * read, never stored.
 *
 * A page is rendered at [MAX_DPI] at most and with [MAX_SIDE] px on its longest side, which keeps every page under
 * 4 MP — below the preview's pixel cap — so no separate pixel check is needed. The page count is checked before any
 * page is rendered, so a refused PDF costs nothing.
 */
@Component
class PdfRasterizer {
    companion object {
        const val MAX_SIDE = 2000
        const val MAX_DPI = 200f
        const val QUALITY = 0.82f

        private const val POINTS_PER_INCH = 72f
    }

    /**
     * One JPEG per page, in the PDF's order. Throws [PdfRasterizationException]: `ENCRYPTED` for a password-protected
     * PDF, `UNREADABLE` for bytes that aren't a readable PDF, `EMPTY` for one without a page, and `TOO_MANY_PAGES` when
     * it has more than [maxPages] pages.
     */
    fun rasterize(pdf: ByteArray, maxPages: Int): List<ByteArray> {
        val document = try {
            Loader.loadPDF(pdf)
        } catch (ex: InvalidPasswordException) {
            throw PdfRasterizationException(PdfRasterizationException.Reason.ENCRYPTED, "The PDF is protected", ex)
        } catch (ex: Exception) {
            throw PdfRasterizationException(PdfRasterizationException.Reason.UNREADABLE, "The PDF cannot be read", ex)
        }

        return document.use { document ->
            val pages = document.numberOfPages
            if (pages == 0) {
                throw PdfRasterizationException(PdfRasterizationException.Reason.EMPTY, "The PDF has no page")
            }
            if (pages > maxPages) {
                throw PdfRasterizationException(
                    PdfRasterizationException.Reason.TOO_MANY_PAGES,
                    "The PDF has more pages than allowed",
                )
            }
            val renderer = PDFRenderer(document)
            (0 until pages).map { index -> render(renderer, document.getPage(index), index) }
        }
    }

    private fun render(renderer: PDFRenderer, page: PDPage, index: Int): ByteArray = try {
        encode(renderer.renderImage(index, scale(page.cropBox ?: page.mediaBox), ImageType.RGB))
    } catch (ex: PdfRasterizationException) {
        throw ex
    } catch (ex: Exception) {
        throw PdfRasterizationException(
            PdfRasterizationException.Reason.UNREADABLE,
            "The PDF page cannot be rendered",
            ex,
        )
    }

    /** [MAX_DPI] at most, and never more than [MAX_SIDE] px on the page's longest side. */
    private fun scale(box: PDRectangle): Float {
        val longest = max(box.width, box.height).coerceAtLeast(1f)
        return min(MAX_DPI / POINTS_PER_INCH, MAX_SIDE / longest)
    }

    /** Mirrors [ImagePreviewService]'s encoder: a JPEG at [QUALITY], without metadata. */
    private fun encode(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
