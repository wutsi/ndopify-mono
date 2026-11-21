package com.wutsi.ndopify.platform.image

import java.awt.Dimension

data class Transformation(
    val dimension: Dimension? = null,
    val aspectRatio: Dimension? = null,
    val format: Format? = null,
)
