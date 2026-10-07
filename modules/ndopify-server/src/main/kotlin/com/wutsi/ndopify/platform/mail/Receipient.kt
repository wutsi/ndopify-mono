package com.wutsi.ndopify.platform.mail

import jakarta.mail.internet.InternetAddress

data class MailAddress(
    val email: String = "",
    val displayName: String? = null,
) {
    fun toInternetAddress(): InternetAddress {
        return if (displayName != null) {
            InternetAddress(email, displayName)
        } else {
            InternetAddress(email)
        }
    }
}
