package com.wutsi.ndopify.platform.mail

import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.eclipse.angus.mail.smtp.SMTPTransport
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.Date

class MailService(
    private val mail: JavaMailSender,
    private val from: String = "no-reply@wutsi.com",
) {
    /** The domain of the configured sender, for the message ids a caller builds (`<mandate-1-1@wutsi.com>`). */
    val fromDomain: String get() = from.substringAfterLast('@', "").ifBlank { "ndopify.com" }

    /**
     * Sends the message and returns the `Message-ID` the relay assigned, without angle brackets, or null when it named
     * none (Mailpit, GreenMail). SES replaces the `Message-ID` we set, so its reply is the only source of the delivered
     * id. Each send takes its own transport: the last response belongs to this message, whatever runs concurrently.
     */
    fun send(email: Email): String? {
        val sender = mail as JavaMailSenderImpl
        try {
            val msg = createMessage(email)
            val transport = sender.session.getTransport(protocol(sender))
            try {
                transport.connect(
                    sender.host,
                    sender.port,
                    sender.username?.ifEmpty { null },
                    sender.password?.ifEmpty { null },
                )
                prepare(msg)
                transport.sendMessage(msg, msg.allRecipients)
                return ServerMessageIdParser.parse((transport as? SMTPTransport)?.lastServerResponse, sender.host)
            } finally {
                runCatching { transport.close() }
            }
        } catch (ex: MessagingException) {
            throw MailSendException("Mail server connection failed", ex)
        }
    }

    private fun protocol(sender: JavaMailSenderImpl): String =
        sender.protocol ?: sender.session.getProperty("mail.transport.protocol") ?: "smtp"

    // What JavaMailSenderImpl does before handing the message to the transport: a sent date, saveChanges() and the
    // pinned Message-ID restored (Spring's doSend(), which we bypass to keep its transport).
    private fun prepare(msg: MimeMessage) {
        if (msg.sentDate == null) {
            msg.sentDate = Date()
        }
        val messageId = msg.messageID
        msg.saveChanges()
        if (messageId != null) {
            msg.setHeader("Message-ID", messageId)
        }
    }

    /**
     * JavaMail regenerates the Message-ID in `saveChanges()`, which the SMTP send always runs: a conversation's fixed
     * id would be replaced. Overriding `updateMessageID` is the only hook it offers.
     */
    private class PinnedMimeMessage(session: Session, private val id: String) : MimeMessage(session) {
        override fun updateMessageID() {
            setHeader("Message-ID", "<$id>")
        }
    }

    // Spring Boot wires a JavaMailSenderImpl (MailConfiguration is the only place a MailService is built)
    private fun newMessage(email: Email): MimeMessage =
        email.messageId?.let { id ->
            PinnedMimeMessage((mail as JavaMailSenderImpl).session, id).also { it.setHeader("Message-ID", "<$id>") }
        } ?: mail.createMimeMessage()

    private fun createMessage(message: Email): MimeMessage {
        val msg = newMessage(message)

        // Headers
        message.sender?.let { sender ->
            // An agent's name in the display name, on the platform's authenticated address: a foreign address in
            // From would break SPF / DKIM / DMARC and be read as spoofing. No Reply-To on purpose: replies go nowhere.
            val address = sender.email.ifBlank { from }
            msg.setFrom(Receipient(email = address, displayName = sender.displayName).toInternetAddress())
        }
        msg.addRecipients(
            Message.RecipientType.TO,
            arrayOf(message.recipient.toInternetAddress())
        )

        // Conversation headers: a mail client groups a message by its references and its subject
        message.inReplyTo?.let { msg.setHeader("In-Reply-To", "<$it>") }
        if (message.references.isNotEmpty()) {
            msg.setHeader("References", message.references.joinToString(" ") { "<$it>" })
        }
        if (message.messageId != null) {
            // Outlook threads by subject: it needs the fixed subject as the conversation topic
            msg.setHeader("Thread-Topic", message.subject)
        }

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
