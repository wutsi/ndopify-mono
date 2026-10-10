package com.wutsi.ndopify.platform.mail

interface EmailFilter {
    fun filter(html: String, tenantId: Long): String
}
