package com.wutsi.ndopify.listing.server.image

import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.CropMode
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingImagesTest {
    private val url = "https://bucket.s3.amazonaws.com/listings/1/images/a.jpg"
    private val service = object : ImageService {
        override fun transform(url: String, transformation: Transformation): String =
            "w-${transformation.dimension!!.width}"
    }

    @Test
    fun `the ladder is 240 to 2048`() {
        assertEquals(listOf(240, 480, 960, 1440, 2048), ListingImages.WIDTHS)
    }

    @Test
    fun `a variant is exact 3 by 2, auto format at quality 75`() {
        assertEquals(
            Transformation(
                dimension = Dimension(960, 640),
                aspectRatio = Dimension(3, 2),
                format = Format.AUTO,
                crop = Crop.MAINTAIN_RATIO,
                cropMode = CropMode.EXTRACT,
                quality = 75,
            ),
            ListingImages.transformation(960),
        )
    }

    @Test
    fun `variants are the ladder below the source width, ascending`() {
        val variants = ListingImages.variants(url, 700, service)

        assertEquals(listOf(240, 480), variants.map(ImageVariant::width))
        assertEquals(listOf(160, 320), variants.map(ImageVariant::height))
        assertEquals(listOf("w-240", "w-480"), variants.map(ImageVariant::url))
    }

    @Test
    fun `an unknown source width keeps the whole ladder`() {
        assertEquals(
            listOf(240, 480, 960, 1440, 2048),
            ListingImages.variants(url, null, service).map(ImageVariant::width),
        )
    }

    @Test
    fun `a source below the smallest step gets one variant at its own width`() {
        val variants = ListingImages.variants(url, 200, service)

        assertEquals(listOf(200), variants.map(ImageVariant::width))
    }

    @Test
    fun `a blank URL has no variant and no social image`() {
        assertTrue(ListingImages.variants(" ", 4000, service).isEmpty())
        assertEquals("", ListingImages.social(" ", service))
    }

    @Test
    fun `the social image is 1200 by 630`() {
        assertEquals(
            Transformation(
                dimension = Dimension(1200, 630),
                aspectRatio = Dimension(1200, 630),
                format = Format.AUTO,
                crop = Crop.MAINTAIN_RATIO,
                cropMode = CropMode.EXTRACT,
                quality = 75,
            ),
            ListingImages.SOCIAL,
        )
        assertEquals("w-1200", ListingImages.social(url, service))
    }

    @Test
    fun `the local service leaves every URL unchanged`() {
        val variants = ListingImages.variants(url, 4000, ImageServiceLocal())

        assertEquals(ListingImages.WIDTHS, variants.map(ImageVariant::width))
        assertTrue(variants.all { it.url == url })
        assertEquals(url, ListingImages.social(url, ImageServiceLocal()))
    }
}
