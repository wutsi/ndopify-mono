package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.image.ImageKit
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageConfigurationTest {
    @Test
    fun `local builds an ImageServiceLocal`() {
        assertTrue(ImageConfiguration("local", "", "").imageService() is ImageServiceLocal)
    }

    @Test
    fun `image-kit builds an ImageKit`() {
        val configuration = ImageConfiguration(
            type = "image-kit",
            originUrl = "https://bucket.s3.amazonaws.com",
            endpointUrl = "https://ik.imagekit.io/key",
        )
        assertTrue(configuration.imageService() is ImageKit)
    }

    @Test
    fun `an unknown type fails`() {
        assertFailsWith<IllegalStateException> {
            ImageConfiguration("none", "", "").imageService()
        }
    }
}
