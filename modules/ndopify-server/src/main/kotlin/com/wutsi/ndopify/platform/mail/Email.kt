package com.wutsi.ndopify.platform.mail

import java.io.File

data class Email(
    val sender: Receipient? = null,
    val recipient: Receipient = Receipient(),
    val subject: String = "",
    val body: String = "",
    val language: String? = null,
    val mimeType: String = "text/html",
    val attachments: List<File> = emptyList()
)
