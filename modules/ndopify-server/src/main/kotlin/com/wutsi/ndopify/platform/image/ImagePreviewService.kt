package com.wutsi.ndopify.platform.image

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Builds the "display copy" of an image: the JPEG a browser may see instead of the private original. The longest side
 * is [MAX_SIDE] px at most (never upscaled), the EXIF orientation is applied, a transparent image is flattened on
 * white, the metadata (EXIF, GPS, device) is dropped by the re-encode, and a tiled diagonal watermark crosses it.
 *
 * Decoding goes through ImageIO, with the JDK's JPEG/PNG readers plus TwelveMonkeys for WEBP and CMYK JPEG; the reader
 * is chosen from the bytes, so a page with no MIME type still decodes. The pixel cap is checked from the header before
 * any full decode. Every failure is an [UnprocessableImageException]: the caller never gets the original back.
 */
@Service
class ImagePreviewService {
    companion object {
        const val MAX_SIDE = 2000
        const val QUALITY = 0.82f
        const val MAX_PIXELS = 40_000_000L

        private const val FONT_RESOURCE = "contract/fonts/DejaVuSans.ttf"
        private const val WATERMARK_ALPHA = 102 // ~40% of #808080: readable on white paper and on a dark photo
        private const val WATERMARK_SIZE_RATIO = 0.035
        private const val WATERMARK_ROTATION = -Math.PI / 6 // -30°
        private val WATERMARK_COLOR = Color(0x80, 0x80, 0x80, WATERMARK_ALPHA)

        private val FONT: Font by lazy {
            try {
                ImagePreviewService::class.java.classLoader.getResourceAsStream(FONT_RESOURCE).use { stream ->
                    Font.createFont(Font.TRUETYPE_FONT, stream)
                }
            } catch (ex: Exception) {
                // The bundled font is always there; a font-less JRE still gets a watermark, in a JDK font
                Font(Font.SANS_SERIF, Font.BOLD, 12)
            }
        }
    }

    /**
     * The JPEG display copy of [original]. [mimeType] is only a hint: the reader comes from the bytes. [watermark] is
     * drawn tiled and diagonal (`<tenant name> - Mandat n° <id>`).
     */
    fun preview(original: ByteArray, mimeType: String?, watermark: String): ByteArray {
        val decoded = try {
            decode(original)
        } catch (ex: UnprocessableImageException) {
            throw ex
        } catch (ex: Exception) {
            throw UnprocessableImageException("The image cannot be decoded", ex)
        }

        return try {
            val (width, height) = size(decoded)
            val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = canvas.createGraphics()
            try {
                g.color = Color.WHITE
                g.fillRect(0, 0, width, height)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.drawImage(decoded, 0, 0, width, height, null)
                drawWatermark(g, width, height, watermark)
            } finally {
                g.dispose()
            }
            encode(canvas)
        } catch (ex: Exception) {
            throw UnprocessableImageException("The image cannot be re-encoded", ex)
        }
    }

    /** The image with its EXIF orientation applied. */
    private fun decode(original: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(original)).use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) {
                throw UnprocessableImageException("No reader for these bytes")
            }
            val reader = readers.next()
            try {
                reader.input = stream
                // The cap is read from the header: a 100 MP image is refused before its pixels are decoded
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width.toLong() * height > MAX_PIXELS) {
                    throw UnprocessableImageException("Too many pixels: $width x $height")
                }
                val image = reader.read(0) ?: throw UnprocessableImageException("No image in these bytes")
                return orient(image, orientation(original))
            } finally {
                reader.dispose()
            }
        }
    }

    /** EXIF orientation of the original, 1 (upright) when it carries none or its metadata can't be read. */
    private fun orientation(original: ByteArray): Int =
        try {
            ImageMetadataReader.readMetadata(ByteArrayInputStream(original))
                .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
                ?.getInteger(ExifIFD0Directory.TAG_ORIENTATION)
                ?: 1
        } catch (ex: Exception) {
            1
        }

    private fun orient(source: BufferedImage, orientation: Int): BufferedImage {
        val width = source.width
        val height = source.height
        val transform = when (orientation) {
            2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, width.toDouble(), 0.0)
            3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, width.toDouble(), height.toDouble())
            4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, height.toDouble())
            5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
            6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, height.toDouble(), 0.0)
            7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, height.toDouble(), width.toDouble())
            8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, width.toDouble())
            else -> return source
        }
        val target = if (orientation >= 5) {
            BufferedImage(height, width, BufferedImage.TYPE_INT_RGB)
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        }
        val g = target.createGraphics()
        try {
            g.drawImage(source, transform, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** The size of the display copy: [MAX_SIDE] longest side at most, never bigger than the original. */
    private fun size(image: BufferedImage): Pair<Int, Int> {
        val longest = maxOf(image.width, image.height)
        if (longest <= MAX_SIDE) {
            return image.width to image.height
        }
        val scale = MAX_SIDE.toDouble() / longest
        return (image.width * scale).roundToInt().coerceAtLeast(1) to
            (image.height * scale).roundToInt().coerceAtLeast(1)
    }

    /** A tiled, diagonal watermark covering the whole image, so cropping it out means retouching the picture. */
    private fun drawWatermark(g: Graphics2D, width: Int, height: Int, watermark: String) {
        if (watermark.isBlank()) {
            return
        }
        val fontSize = (minOf(width, height) * WATERMARK_SIZE_RATIO).coerceIn(12.0, 64.0).toFloat()
        g.font = FONT.deriveFont(Font.BOLD, fontSize)
        g.color = WATERMARK_COLOR

        val metrics = g.fontMetrics
        val textWidth = metrics.stringWidth(watermark)
        val stepX = (textWidth + metrics.height * 2).coerceAtLeast(1)
        val stepY = (metrics.height * 3).coerceAtLeast(1)

        g.rotate(WATERMARK_ROTATION, width / 2.0, height / 2.0)
        val diagonal = hypot(width.toDouble(), height.toDouble()).toInt()
        val startX = (width - diagonal) / 2
        val startY = (height - diagonal) / 2
        var row = 0
        var y = startY
        while (y < startY + diagonal + stepY) {
            var x = startX + if (row % 2 == 0) 0 else stepX / 2
            while (x < startX + diagonal + stepX) {
                g.drawString(watermark, x, y)
                x += stepX
            }
            y += stepY
            row++
        }
    }

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
