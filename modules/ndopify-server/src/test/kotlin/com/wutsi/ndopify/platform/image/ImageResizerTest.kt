package com.wutsi.ndopify.platform.image

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The vision copy of a page: a JPEG of at most [ResizeOptions.maxPixels] pixels (never upscaled), EXIF-oriented,
 * flattened on white, its metadata dropped, and refused before decoding when it's over [ResizeOptions.maxSourcePixels].
 */
class ImageResizerTest {
    private val resizer = ImageResizer()

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/files/preview/$name")!!.use { it.readBytes() }

    private fun identity(name: String): ByteArray =
        javaClass.getResourceAsStream("/files/identity/$name")!!.use { it.readBytes() }

    private fun jpeg(width: Int, height: Int, background: Color = Color.BLACK): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = background
            fillRect(0, 0, width, height)
            dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun png(width: Int, height: Int, alpha: Boolean = false): ByteArray {
        val type = if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): BufferedImage = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)))

    private fun assertJpeg(bytes: ByteArray) {
        assertTrue(bytes.size > 2, "no bytes")
        assertEquals(0xFF, bytes[0].toInt() and 0xFF, "not a JPEG")
        assertEquals(0xD8, bytes[1].toInt() and 0xFF, "not a JPEG")
    }

    @Test
    fun `resize - a 12 MP photo becomes a JPEG of at most 1_69 MP, longest side around 1500`() {
        val photo = jpeg(4000, 3000)
        val copy = photo.copyOf()

        val resized = resizer.resize(ByteArrayInputStream(photo))

        assertJpeg(resized)
        val image = decode(resized)
        assertTrue(image.width.toLong() * image.height <= ResizeOptions.VISION.maxPixels)
        assertEquals(4.0 / 3.0, image.width.toDouble() / image.height, 0.02)
        assertTrue(image.width in 1490..1510, "longest side is ${image.width}")
        assertContentEquals(copy, photo, "the source bytes were modified")
    }

    @Test
    fun `resize - never upscales`() {
        val image = decode(resizer.resize(ByteArrayInputStream(png(400, 300))))

        assertEquals(400, image.width)
        assertEquals(300, image.height)
    }

    @Test
    fun `resize - a page over the header cap is refused before decoding`() {
        val photo = jpeg(4000, 3000)

        assertFailsWith<ImageResizeException> {
            resizer.resize(ByteArrayInputStream(photo), ResizeOptions(maxSourcePixels = 1_000_000))
        }
    }

    @Test
    fun `resize - a transparent page is flattened on white`() {
        val image = decode(resizer.resize(ByteArrayInputStream(png(400, 300, alpha = true))))

        assertEquals(Color.WHITE.rgb, image.getRGB(0, 0))
        assertEquals(Color.WHITE.rgb, image.getRGB(399, 299))
    }

    @Test
    fun `resize - applies the EXIF orientation`() {
        val original = decode(resource("exif-orientation-6.jpg"))
        assertTrue(original.width > original.height, "the fixture is not a landscape photo")

        val image = decode(resizer.resize(ByteArrayInputStream(resource("exif-orientation-6.jpg"))))

        assertTrue(image.height > image.width, "the photo was not rotated")
    }

    @Test
    fun `resize - a webp page decodes`() {
        val image = decode(resizer.resize(ByteArrayInputStream(resource("sample.webp"))))

        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
    }

    @Test
    fun `resize - a corrupt page is refused`() {
        assertFailsWith<ImageResizeException> { resizer.resize(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
    }

    @Test
    fun `subsamplingFactor - the smallest factor that keeps the decode under the cap`() {
        assertEquals(2, ImageResizer.subsamplingFactor(4000, 3000, 7_000_000))
        assertEquals(1, ImageResizer.subsamplingFactor(1000, 750, 7_000_000))
        assertEquals(3, ImageResizer.subsamplingFactor(8000, 6000, 7_000_000))
        assertEquals(15, ImageResizer.subsamplingFactor(5000, 4000, 100_000))
    }

    @Test
    fun `subsamplingFactor - every decoded size stays under the cap`() {
        listOf(
            1000 to 750,
            4000 to 3000,
            8000 to 6000,
            6000 to 4500,
        ).forEach { (width, height) ->
            val factor = ImageResizer.subsamplingFactor(width, height, 7_000_000)
            val decoded = (width / factor).toLong() * (height / factor)
            assertTrue(decoded <= 7_000_000, "$width x $height decoded to $decoded at factor $factor")
        }
    }

    /** A PNG whose alpha channel is fully transparent: the corner must be white, not black, once flattened. */
    @Test
    fun `resize - keeps the bytes of the identity fixtures decodable`() {
        listOf("cm/cni-front.png", "cm/passport.webp", "us/driver-license-front.jpg").forEach { name ->
            val image = decode(resizer.resize(ByteArrayInputStream(identity(name))))
            assertTrue(image.width > 0, "$name")
            assertTrue(image.height > 0, "$name")
        }
    }
}
