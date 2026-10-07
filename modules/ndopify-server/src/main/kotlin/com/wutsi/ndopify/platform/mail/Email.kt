package com.wutsi.ndopify.platform.mail

import java.io.File

data class Mail(
    val sender: MailAddress? = null,
    val recipient: MailAddress = MailAddress(),
    val subject: String = "",
    val body: String = "",
    val language: String? = null,
    val mimeType: String = "text/html",
    val attachments: List<File> = emptyList()
)
