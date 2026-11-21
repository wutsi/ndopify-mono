package com.wutsi.ndopify.platform.image

class ImageKit(
    private val originUrl: String,
    private val endpoint: String
) : ImageService {
    override fun transform(url: String, transformation: Transformation): String {
        if (!url.startsWith(originUrl)) {
            return url
        }

        val path = url.substring(originUrl.length)
        val tr = mutableListOf<String>()
        val qs = StringBuilder()

        transformation.dimension?.let {
            if (it.width > 0) {
                tr.add("w-${it.width}")
            }
            if (it.height > 0) {
                tr.add("h-${it.height}")
            }
        }

        transformation.format?.let {
            tr.add("f-${it.name.lowercase()}")
        }

        if (transformation.aspectRatio != null) {
            tr.add("ar-${transformation.aspectRatio.width}-${transformation.aspectRatio.height}")
        }

        if (tr.isNotEmpty()) {
            qs.append("/tr:").append(tr.joinToString(","))
        }

        return endpoint + qs.toString() + path
    }
}
