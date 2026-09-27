package com.wutsi.ndopify.platform.momo.mtn.model

enum class MtnEnvironment(val baseUrl: String) {
    SANDBOX("https://sandbox.momodeveloper.mtn.com"),
    PRODUCTION("https://momodeveloper.mtn.com"),
}
