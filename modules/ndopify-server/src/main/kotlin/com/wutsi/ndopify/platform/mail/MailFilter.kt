package com.wutsi.ndopify.platform.mail

interface MailFilter {
    fun filter(html: String): String
}
