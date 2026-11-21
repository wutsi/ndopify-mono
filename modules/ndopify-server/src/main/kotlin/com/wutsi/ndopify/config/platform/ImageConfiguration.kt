package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.image.ImageKit
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Builds the [ImageService] from `ndopify.image.type`: `image-kit` resizes storage URLs through ImageKit,
 * `local` returns them unchanged. Any other type is a configuration error, at startup.
 */
@Configuration
class ImageConfiguration(
    @param:Value("\${ndopify.image.type}") private val type: String,
    @param:Value("\${ndopify.image.image-kit.origin-url}") private val originUrl: String,
    @param:Value("\${ndopify.image.image-kit.endpoint-url}") private val endpointUrl: String,
) {
    @Bean
    fun imageService(): ImageService = when (type) {
        "image-kit" -> ImageKit(originUrl, endpointUrl)
        "local" -> ImageServiceLocal()
        else -> throw IllegalStateException("Unknown ndopify.image.type: $type")
    }
}
