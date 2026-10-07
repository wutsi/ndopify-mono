package com.wutsi.ndopify.platform.mail

import jakarta.mail.Message
import jakarta.mail.Part
import jakarta.mail.Transport
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.springframework.mail.javamail.JavaMailSender

class MailSender(private val mail: JavaMailSender) {
    fun send(message: Email) {
        val msg = createMessage(message)
        Transport.send(msg)
    }

    private fun createMessage(message: Email): MimeMessage {
        val msg = mail.createMimeMessage()

        // Headers
        msg.addRecipients(
            Message.RecipientType.TO,
            arrayOf(message.recipient.toInternetAddress())
        )

        if (message.language != null) {
            msg.contentLanguage = arrayOf(message.language)
        }

        // Subject
        msg.subject = message.subject

        // Body
        if (message.attachments.isEmpty()) {
            msg.setContent(message.body, message.mimeType)
        } else {
            val parts = MimeMultipart()
            val body = MimeBodyPart()
            body.setContent(message.body, message.mimeType)
            body.setDisposition(Part.INLINE)
            parts.addBodyPart(body)

            // Attachment
            message.attachments.forEach { file ->
                val attachment = MimeBodyPart()
                attachment.attachFile(file)
                parts.addBodyPart(attachment)
            }
            msg.setContent(parts)
        }
        return msg
    }
}
