package com.wutsi.ndopify.platform.image

import org.junit.jupiter.api.Test
import java.awt.Dimension
import kotlin.test.assertEquals

class ImageKitTest {
    val service = ImageKit(
        "https://www.example.com",
        "https://ik.imagekit.io/cx8qxsgz4d"
    )
    val url = "https://www.example.com/foo/bar/image.jpg"


    @Test
    fun transform() {
        val transformation = Transformation(
            dimension = Dimension(100, 200),
            format = Format.WEBP
        )

        val result = service.transform(url, transformation)
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-100,h-200,f-webp/foo/bar/image.jpg",
            result
        )
    }

    @Test
    fun `no width`() {
        val transformation = Transformation(
            dimension = Dimension(0, 200),
            format = Format.WEBP
        )

        val result = service.transform(url, transformation)
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:h-200,f-webp/foo/bar/image.jpg",
            result
        )
    }

    @Test
    fun `no height`() {
        val transformation = Transformation(
            dimension = Dimension(100, 0),
            format = Format.WEBP
        )

        val result = service.transform(url, transformation)
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-100,f-webp/foo/bar/image.jpg",
            result
        )
    }

    @Test
    fun `no format`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val transformation = Transformation(
            dimension = java.awt.Dimension(100, 200),
            format = null
        )

        val result = service.transform(url, transformation)
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-100,h-200/foo/bar/image.jpg",
            result
        )
    }

    @Test
    fun `aspect ratio`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val transformation = Transformation(
            aspectRatio = java.awt.Dimension(3, 2),
        )

        val result = service.transform(url, transformation)
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:ar-3-2/foo/bar/image.jpg",
            result
        )
    }

    @Test
    fun `no transformation`() {
        val transformation = Transformation(
            dimension = null,
            format = null
        )

        val result = service.transform(url, transformation)
        assertEquals("https://ik.imagekit.io/cx8qxsgz4d/foo/bar/image.jpg", result)
    }
}
