package com.wutsi.ndopify.listing.server.mapper

import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingMapperTest {
    private val url = "https://bucket.s3.amazonaws.com/listings/1/images/a.jpg"
    private val service = object : ImageService {
        override fun transform(url: String, transformation: Transformation): String =
            "w-${transformation.dimension!!.width}"
    }
    private val image = ListingImageEntity(id = 9, listingId = 1, url = url, width = 4000, height = 3000)

    @Test
    fun `toListingImage adds the ladder and the social image`() {
        val dto = ListingMapper(service).toListingImage(image)

        assertEquals(url, dto.url)
        assertEquals(listOf(240, 480, 960, 1440, 2048), dto.variants.map { it.width })
        assertEquals(listOf("w-240", "w-480", "w-960", "w-1440", "w-2048"), dto.variants.map { it.url })
        assertEquals("w-1200", dto.socialUrl)
    }

    @Test
    fun `toListingImage leaves a URL outside ImageKit unchanged`() {
        val dto = ListingMapper(ImageServiceLocal()).toListingImage(image)

        assertTrue(dto.variants.all { it.url == url })
        assertEquals(url, dto.socialUrl)
    }
}
