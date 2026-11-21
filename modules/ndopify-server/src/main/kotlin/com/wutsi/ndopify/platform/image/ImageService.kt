package com.wutsi.ndopify.platform.image

interface ImageService {
    fun transform(url: String, transformation: Transformation): String
}
