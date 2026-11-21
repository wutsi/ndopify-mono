package com.wutsi.ndopify.platform.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation

/**
 * An [ImageService] mock whose `transform` answers the URL ImageKit would build for the slot (its dimension, format and
 * quality), so that a mailet test's `image_url` proves both the resize and the slot it asked for.
 */
fun mockMailImageService(): ImageService {
    val imageService = mock<ImageService>()
    doAnswer { invocation ->
        val slot = invocation.arguments[1] as Transformation
        val params = listOfNotNull(
            slot.dimension?.let { "w-${it.width},h-${it.height}" },
            slot.format?.let { "f-${it.name.lowercase()}" },
            slot.quality?.let { "q-$it" },
        ).joinToString(",")
        "https://ik.imagekit.io/key/tr:$params/${(invocation.arguments[0] as String).substringAfterLast('/')}"
    }.whenever(imageService).transform(any(), any())
    return imageService
}
