package com.wutsi.ndopify.platform.image

import java.awt.Dimension

/**
 * What [ImageService.transform] applies to an image. A null parameter adds nothing to the URL, so an empty
 * transformation leaves it unchanged.
 *
 * [quality] is ImageKit's `q-` value, between 1 and 100; null keeps ImageKit's default quality.
 */
data class Transformation(
    val dimension: Dimension? = null,
    val aspectRatio: Dimension? = null,
    val format: Format? = null,
    val crop: Crop? = null,
    val cropMode: CropMode? = null,
    val quality: Int? = null,
) {
    init {
        require(quality == null || quality in 1..100) { "quality must be between 1 and 100: $quality" }
    }
}
