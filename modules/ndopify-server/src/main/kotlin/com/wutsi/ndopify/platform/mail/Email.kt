package com.wutsi.ndopify.platform.mail

import java.io.File

data class Email(
    val sender: Receipient? = null,
    val recipient: Receipient = Receipient(),
    val subject: String = "",
    val body: String = "",
    val language: String? = null,
    val mimeType: String = "text/html",
    val attachments: List<File> = emptyList(),

    /** The conversation's id for this message, without angle brackets: the header is `<id>`. */
    val messageId: String? = null,
    /** The id of the message this one answers, without angle brackets. */
    val inReplyTo: String? = null,
    /** The conversation's root ids, without angle brackets, in the `References` order. */
    val references: List<String> = emptyList(),
)
