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

    @Test
    fun `crop values`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        Crop.entries.forEach {
            val result = service.transform(url, Transformation(crop = it))
            assertEquals(
                "https://ik.imagekit.io/cx8qxsgz4d/tr:c-${it.name.lowercase()}/foo/bar/image.jpg",
                result,
            )
        }
    }

    @Test
    fun `crop mode values`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        CropMode.entries.forEach {
            val result = service.transform(url, Transformation(cropMode = it))
            assertEquals(
                "https://ik.imagekit.io/cx8qxsgz4d/tr:cm-${it.name.lowercase()}/foo/bar/image.jpg",
                result,
            )
        }
    }

    @Test
    fun `crop with a size`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(dimension = Dimension(100, 200), crop = Crop.AT_MAX),
        )
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-100,h-200,c-at_max/foo/bar/image.jpg",
            result,
        )
    }

    @Test
    fun `quality alone`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(url, Transformation(quality = 80))
        assertEquals("https://ik.imagekit.io/cx8qxsgz4d/tr:q-80/foo/bar/image.jpg", result)
    }

    @Test
    fun `all parameters in order`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(
                dimension = Dimension(1280, 1280),
                aspectRatio = Dimension(3, 2),
                format = Format.JPG,
                crop = Crop.AT_MAX,
                cropMode = CropMode.PAD_EXTRACT,
                quality = 80,
            ),
        )
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-1280,h-1280,f-jpg,ar-3-2,c-at_max,cm-pad_extract,q-80/foo/bar/image.jpg",
            result,
        )
    }

    @Test
    fun `a URL outside the origin is unchanged with a crop and a quality`() {
        val url = "https://cdn.example.org/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(crop = Crop.AT_MAX, quality = 80),
        )
        assertEquals(url, result)
    }
}
