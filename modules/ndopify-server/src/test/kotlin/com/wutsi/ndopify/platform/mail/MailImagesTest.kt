package com.wutsi.ndopify.platform.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailImagesTest {
    private val url = "https://cdn.ndopify.com/7.jpg"

    @Test
    fun `cover is 1200 by 800, JPEG at quality 75`() {
        assertEquals(
            Transformation(dimension = Dimension(1200, 800), format = Format.JPG, quality = 75),
            MailImages.COVER,
        )
    }

    @Test
    fun `thumbnail is 160 by 120, JPEG at quality 75`() {
        assertEquals(
            Transformation(dimension = Dimension(160, 120), format = Format.JPG, quality = 75),
            MailImages.THUMBNAIL,
        )
    }

    @Test
    fun `imageUrl resizes a non-blank URL for the slot`() {
        val imageService = mock<ImageService>()
        doReturn("resized").whenever(imageService).transform(url, MailImages.COVER)

        assertEquals("resized", MailImages.imageUrl(url, imageService, MailImages.COVER))
    }

    @Test
    fun `imageUrl is null without a URL`() {
        val imageService = mock<ImageService>()

        assertNull(MailImages.imageUrl(null, imageService, MailImages.COVER))
        assertNull(MailImages.imageUrl(" ", imageService, MailImages.COVER))
        verify(imageService, never()).transform(any(), any())
    }

    @Test
    fun `the local image service returns the URL unchanged`() {
        assertEquals(url, MailImages.imageUrl(url, ImageServiceLocal(), MailImages.COVER))
    }
}
