package com.wutsi.ndopify.platform.image

class ImageServiceLocal : ImageService {
    override fun transform(url: String, transformation: Transformation): String = url
}
