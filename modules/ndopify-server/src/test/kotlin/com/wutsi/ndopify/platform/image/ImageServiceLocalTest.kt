package com.wutsi.ndopify.platform.image

import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageServiceLocalTest {
    private val service = ImageServiceLocal()
    private val url = "https://cdn.ndopify.com/listing/1/picture.jpg"

    @Test
    fun `transform returns the URL unchanged with the default transformation`() {
        assertEquals(url, service.transform(url, Transformation()))
    }

    @Test
    fun `transform ignores the dimension and the format`() {
        val transformation = Transformation(dimension = Dimension(320, 200), format = Format.PNG)

        assertEquals(url, service.transform(url, transformation))
    }
}
