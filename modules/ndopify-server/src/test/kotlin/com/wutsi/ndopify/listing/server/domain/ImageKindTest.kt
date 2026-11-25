package com.wutsi.ndopify.listing.server.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ImageKindTest {
    @Test
    fun `parse the model's kinds`() {
        assertEquals(ImageKind.PHOTO, ImageKind.parse("PHOTO"))
        assertEquals(ImageKind.FLOORPLAN, ImageKind.parse("FLOORPLAN"))
        assertEquals(ImageKind.MAP, ImageKind.parse("MAP"))
        assertEquals(ImageKind.MAP, ImageKind.parse(" map "))
        assertEquals(ImageKind.FLOORPLAN, ImageKind.parse("floorplan"))
    }

    @Test
    fun `anything else is a photo`() {
        assertEquals(ImageKind.PHOTO, ImageKind.parse(null))
        assertEquals(ImageKind.PHOTO, ImageKind.parse(""))
        assertEquals(ImageKind.PHOTO, ImageKind.parse("PICTURE"))
    }

    @Test
    fun `an image is a photo until the verification says otherwise`() {
        assertEquals(ImageKind.PHOTO, ListingImageEntity(id = 11L).kind)
    }
}
