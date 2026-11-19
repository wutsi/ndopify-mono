package com.wutsi.ndopify.common.dto

/** Where to PUT the file ([url]) and the [path] to attach afterwards. Both null when the storage can't presign (local). */
data class PresignUploadResponse(val url: String? = null, val path: String? = null)
