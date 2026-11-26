package com.wutsi.ndopify.platform.image

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Semaphore
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The vision copy of an image: the JPEG an LLM may read instead of the stored original. The longest side fits
 * [ResizeOptions.maxPixels] (~1300×1300 equivalent, the `deepseek-flash` resample target, which also bills at most
 * 1024 tokens per image), so a 12 MP phone photo becomes a ~1.7 MP JPEG: the model never gets the original.
 *
 * The dimensions come from the header: a page over [ResizeOptions.maxSourcePixels] (40 MP, ~160 MB once decoded, for a
 * `-Xmx300m` standard-1x dyno) is refused before any pixel is decoded, and a bigger page is decoded subsampled down
 * to [ResizeOptions.maxDecodePixels]. A transparent image is composited on white, the EXIF orientation is applied, the
 * metadata is dropped by the re-encode and the aspect is kept (never upscaled). At most 2 images are decoded at once.
 *
 * It has no storage, mandate nor KYC rule: the caller owns the bytes.
 */
@Component
class ImageResizer {
    companion object {
        /** How many images may be decoded at once: the decoded pixels are the memory peak of the job. */
        private const val MAX_CONCURRENT_DECODES = 2

        private val SEMAPHORE = Semaphore(MAX_CONCURRENT_DECODES)

        /** The smallest integer subsampling factor whose decoded pixels stay under [maxDecodePixels]; 1 when none. */
        internal fun subsamplingFactor(width: Int, height: Int, maxDecodePixels: Long): Int {
            val pixels = width.toLong() * height
            if (pixels <= maxDecodePixels) {
                return 1
            }
            return ceil(sqrt(pixels.toDouble() / maxDecodePixels)).toInt().coerceAtLeast(1)
        }
    }

    /**
     * [source] as a JPEG vision copy under [options]. Throws [ImageResizeException] when the bytes can't be read as an
     * image or are too large; the source stream is read to its end and never written.
     */
    fun resize(source: InputStream, options: ResizeOptions = ResizeOptions.VISION): ByteArray {
        val bytes = try {
            source.readBytes()
        } catch (ex: Exception) {
            throw ImageResizeException("The image cannot be read", ex)
        }
        return try {
            resize(bytes, options)
        } catch (ex: ImageResizeException) {
            throw ex
        } catch (ex: Exception) {
            throw ImageResizeException("The image cannot be processed", ex)
        }
    }

    private fun resize(bytes: ByteArray, options: ResizeOptions): ByteArray {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) {
                throw ImageResizeException("No reader for these bytes")
            }
            val reader = readers.next()
            try {
                reader.input = stream
                // The cap comes from the header: a 40 MP page is refused before its pixels are decoded
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width.toLong() * height > options.maxSourcePixels) {
                    throw ImageResizeException("Too many pixels: $width x $height")
                }

                val decoded = decode(reader, width, height, bytes, options)
                return encode(scale(decoded, options), options)
            } finally {
                reader.dispose()
            }
        }
    }

    /** The page with its EXIF orientation applied, decoded subsampled under [ResizeOptions.maxDecodePixels]. */
    private fun decode(reader: ImageReader, width: Int, height: Int, bytes: ByteArray, options: ResizeOptions): BufferedImage {
        SEMAPHORE.acquire()
        try {
            val factor = subsamplingFactor(width, height, options.maxDecodePixels)
            val image = read(reader, factor, width.toLong() * height <= 16_000_000)
            return orient(image, orientation(bytes))
        } finally {
            SEMAPHORE.release()
        }
    }

    /**
     * Reads the page at [factor]'s subsampling; a reader that refuses it falls back to a plain decode when the page is
     * small enough to fit memory unsubsampled.
     */
    private fun read(reader: ImageReader, factor: Int, canDecodeFully: Boolean): BufferedImage {
        if (factor > 1) {
            val param = reader.defaultReadParam
            try {
                param.setSourceSubsampling(factor, factor, 0, 0)
                return reader.read(0, param) ?: throw ImageResizeException("No image in these bytes")
            } catch (ex: Exception) {
                if (!canDecodeFully) {
                    throw ImageResizeException("The image cannot be subsampled", ex)
                }
            }
        }
        return reader.read(0, reader.defaultReadParam)
            ?: throw ImageResizeException("No image in these bytes")
    }

    /** EXIF orientation of the page, 1 (upright) when it carries none or its metadata can't be read. */
    private fun orientation(bytes: ByteArray): Int = try {
        ImageMetadataReader.readMetadata(ByteArrayInputStream(bytes))
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
            else -> null
        }
        if (transform == null) {
            return flatten(source)
        }

        val targetWidth = if (orientation >= 5) height else width
        val targetHeight = if (orientation >= 5) width else height
        val target = canvas(targetWidth, targetHeight)
        val g = target.createGraphics()
        try {
            g.drawImage(source, transform, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** [source] on a white, opaque canvas of its size — a transparent page must not come out black. */
    private fun flatten(source: BufferedImage): BufferedImage {
        val target = canvas(source.width, source.height)
        val g = target.createGraphics()
        try {
            g.drawImage(source, 0, 0, null)
        } finally {
            g.dispose()
        }
        return target
    }

    private fun canvas(width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
        } finally {
            g.dispose()
        }
        return target
    }

    /** [image] scaled down to fit [ResizeOptions.maxPixels] (never up), its aspect kept. */
    private fun scale(image: BufferedImage, options: ResizeOptions): BufferedImage {
        val pixels = image.width.toLong() * image.height
        if (pixels <= options.maxPixels) {
            return image
        }

        var factor = sqrt(options.maxPixels.toDouble() / pixels)
        var width = floor(image.width * factor).toInt().coerceAtLeast(1)
        var height = floor(image.height * factor).toInt().coerceAtLeast(1)
        // Rounding down must never leave the copy over the cap (ex: 1501 x 1126 for a 4:3 12 MP page)
        while (width.toLong() * height > options.maxPixels) {
            factor *= 0.999
            width = floor(image.width * factor).toInt().coerceAtLeast(1)
            height = floor(image.height * factor).toInt().coerceAtLeast(1)
        }

        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = target.createGraphics()
        try {
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(image, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return target
    }

    private fun encode(image: BufferedImage, options: ResizeOptions = ResizeOptions.VISION): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = options.jpegQuality / 100.0f
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}

/** What [ImageResizer] targets. [VISION] is the one the document extraction uses. */
data class ResizeOptions(
    val maxPixels: Long = 1_690_000,
    val jpegQuality: Int = 85,
    val maxSourcePixels: Long = 40_000_000,
    val maxDecodePixels: Long = 7_000_000,
) {
    companion object {
        val VISION = ResizeOptions()
    }
}
