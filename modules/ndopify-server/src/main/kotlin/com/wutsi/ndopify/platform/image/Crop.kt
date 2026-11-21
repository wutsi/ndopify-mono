package com.wutsi.ndopify.platform.image

/**
 * ImageKit's crop (`c-`) value: how an image is fitted into the requested size. Lowercased in the URL.
 */
enum class Crop {
    MAINTAIN_RATIO,
    PAD_RESIZE,
    FORCE,
    AT_MAX,
    AT_MAX_ENLARGE,
    AT_LEAST
}
