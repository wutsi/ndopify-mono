package com.wutsi.ndopify.platform.mail

import jakarta.mail.internet.InternetAddress

data class Receipient(
    val email: String = "",
    val displayName: String? = null,
) {
    fun toInternetAddress(): InternetAddress {
        // A header value can't carry a line break: that's how a display name would inject a second header
        val name = displayName?.replace(Regex("[\\r\\n]+"), " ")
        return if (name != null) {
            InternetAddress(email, name)
        } else {
            InternetAddress(email)
        }
    }
}
