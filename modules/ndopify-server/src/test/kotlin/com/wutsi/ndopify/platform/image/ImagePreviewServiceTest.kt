package com.wutsi.ndopify.platform.image

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.GpsDirectory
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The "display copy" of an image: a JPEG resized so its longest side is [ImagePreviewService.MAX_SIDE] at most (never
 * upscaled), flattened on white, upright whatever its EXIF orientation, watermarked and stripped of its metadata.
 */
class ImagePreviewServiceTest {
    private val service = ImagePreviewService()

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/files/preview/$name")!!.use { it.readBytes() }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out)
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))

    private fun assertJpeg(bytes: ByteArray) {
        assertTrue(bytes.size > 2, "no bytes")
        assertEquals(0xFF, bytes[0].toInt() and 0xFF, "not a JPEG")
        assertEquals(0xD8, bytes[1].toInt() and 0xFF, "not a JPEG")
    }

    private fun Int.channel() = this and 0xFF

    private fun assertRed(image: BufferedImage, x: Int, y: Int, message: String) {
        val color = Color(image.getRGB(x, y))
        assertTrue(color.red > color.blue + 60, "$message (${color.red}/${color.blue})")
    }

    private fun assertBlue(image: BufferedImage, x: Int, y: Int, message: String) {
        val color = Color(image.getRGB(x, y))
        assertTrue(color.blue > color.red + 60, "$message (${color.red}/${color.blue})")
    }

    /** The EXIF fixture, its orientation tag (0x0112) set to [value]. */
    private fun withOrientation(value: Int): ByteArray {
        val bytes = resource("exif-orientation-6.jpg").copyOf()
        val tag = byteArrayOf(0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00)
        val index = (0..bytes.size - tag.size).first { offset ->
            tag.indices.all { bytes[offset + it] == tag[it] }
        }
        bytes[index + 8] = value.toByte()
        return bytes
    }

    @Test
    fun `preview - a large photo is resized to 2000px's longest side`() {
        val preview = service.preview(jpeg(3000, 1000), "image/jpeg", "Ndopify - Mandat n° 301")

        assertJpeg(preview)
        val image = decode(preview)
        assertEquals(2000, image.width)
        assertEquals((1000 * 2000.0 / 3000).roundToInt(), image.height)
    }

    @Test
    fun `preview - a small image is never upscaled`() {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB), "png", out)

        val preview = service.preview(out.toByteArray(), "image/png", "Ndopify - Mandat n° 301")

        assertJpeg(preview)
        val image = decode(preview)
        assertEquals(100, image.width)
        assertEquals(50, image.height)
    }

    @Test
    fun `preview - a transparent image is flattened on white`() {
        val source = BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(source, "png", out)

        val preview = service.preview(out.toByteArray(), "image/png", "Ndopify - Mandat n° 301")

        val pixel = decode(preview).getRGB(50, 25)
        val color = Color(pixel)
        assertTrue(color.red >= 250, "red ${color.red}")
        assertTrue(color.green >= 250, "green ${color.green}")
        assertTrue(color.blue >= 250, "blue ${color.blue}")
    }

    @Test
    fun `preview - a webp is decoded`() {
        val preview = service.preview(resource("sample.webp"), "image/webp", "Ndopify - Mandat n° 301")

        assertJpeg(preview)
        assertEquals(40, decode(preview).width)
    }

    @Test
    fun `preview - a cmyk jpeg is decoded`() {
        val preview = service.preview(resource("cmyk.jpg"), "image/jpeg", "Ndopify - Mandat n° 301")

        assertJpeg(preview)
        assertEquals(40, decode(preview).width)
    }

    @Test
    fun `preview - every exif orientation is applied`() {
        val portrait = setOf(5, 6, 7, 8)

        for (orientation in 1..8) {
            val image = decode(service.preview(withOrientation(orientation), "image/jpeg", "Ndopify - Mandat n° 301"))
            if (orientation in portrait) {
                assertEquals(20, image.width, "width of orientation $orientation")
                assertEquals(40, image.height, "height of orientation $orientation")
            } else {
                assertEquals(40, image.width, "width of orientation $orientation")
                assertEquals(20, image.height, "height of orientation $orientation")
            }
        }
    }

    @Test
    fun `preview - the exif orientation turns the picture the right way`() {
        // The fixture: left half red, right half blue
        assertRed(decode(service.preview(withOrientation(1), "image/jpeg", "")), 3, 3, "upright: top-left red")
        assertBlue(decode(service.preview(withOrientation(1), "image/jpeg", "")), 36, 3, "upright: top-right blue")
        assertBlue(decode(service.preview(withOrientation(2), "image/jpeg", "")), 3, 3, "mirrored: top-left blue")
        assertBlue(decode(service.preview(withOrientation(3), "image/jpeg", "")), 3, 3, "upside down: top-left blue")
        assertRed(decode(service.preview(withOrientation(6), "image/jpeg", "")), 3, 3, "rotated right: top red")
        assertBlue(decode(service.preview(withOrientation(6), "image/jpeg", "")), 3, 36, "rotated right: bottom blue")
    }

    @Test
    fun `preview - the watermark is drawn on white`() {
        val preview = service.preview(jpeg(400, 400), "image/jpeg", "Ndopify - Mandat n° 301")

        val image = decode(preview)
        val marked = (0 until image.width).any { x ->
            (0 until image.height).any { y -> image.getRGB(x, y).let { it.channel() < 245 } }
        }
        assertTrue(marked, "no watermark pixel on the white image")
    }

    @Test
    fun `preview - the watermark is readable on a dark photo`() {
        val source = BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB)
        source.createGraphics().apply {
            color = Color.BLACK
            fillRect(0, 0, 400, 400)
            dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(source, "jpg", out)

        val image = decode(service.preview(out.toByteArray(), "image/jpeg", "Ndopify - Mandat n° 301"))

        val marked = (0 until image.width).any { x ->
            (0 until image.height).any { y -> image.getRGB(x, y).let { it.channel() > 10 } }
        }
        assertTrue(marked, "no watermark pixel on the black image")
    }

    @Test
    fun `preview - the exif and gps metadata are stripped`() {
        val preview = service.preview(resource("exif-orientation-6.jpg"), "image/jpeg", "Ndopify - Mandat n° 301")

        val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(preview))
        assertTrue(metadata.getDirectoriesOfType(ExifIFD0Directory::class.java).isEmpty(), "EXIF kept")
        assertTrue(metadata.getDirectoriesOfType(GpsDirectory::class.java).isEmpty(), "GPS kept")
    }

    @Test
    fun `preview - a pixel bomb is refused before any decode`() {
        assertFailsWith<UnprocessableImageException> {
            service.preview(pngHeader(10_000, 10_000), "image/png", "Ndopify - Mandat n° 301")
        }
    }

    @Test
    fun `preview - bytes that are not an image are refused`() {
        assertFailsWith<UnprocessableImageException> {
            service.preview("not an image at all".toByteArray(), "image/jpeg", "Ndopify - Mandat n° 301")
        }
    }

    /** A PNG holding only its signature and IHDR: enough for a reader to report 10000x10000 without decoding pixels. */
    private fun pngHeader(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val data = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 13)) // IHDR length
            write("IHDR".toByteArray())
            write(intBytes(width))
            write(intBytes(height))
            write(byteArrayOf(8, 2, 0, 0, 0)) // bit depth, colour type, compression, filter, interlace
        }.toByteArray()
        out.write(data)
        val crc = CRC32().apply { update(data, 4, data.size - 4) }
        out.write(intBytes(crc.value.toInt()))
        return out.toByteArray()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )
}
