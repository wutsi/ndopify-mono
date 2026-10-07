package com.wutsi.ndopify.platform.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import jakarta.mail.Message.RecipientType
import jakarta.mail.MessagingException
import jakarta.mail.Session
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MailSenderTest {
    private val port = 8025
    private lateinit var smtp: GreenMail

    @Autowired
    private lateinit var session: Session

    @Autowired
    private lateinit var sender: MailSender

    private val message = Email(
        sender = MailAddress(email = "no-reply@tenant1.com", displayName = "Tenant1"),
        recipient = MailAddress(email = "ray.sponsible@gmail.com", displayName = "Ray Sponsible"),
        subject = "Hello",
        body = "Yo man",
        language = "en",
        mimeType = "text/html",
    )

    @BeforeTest
    fun setUp() {
        smtp = GreenMail(ServerSetup.SMTP.port(port))
        smtp.setUser("wutsi", "secret")
        smtp.start()
    }

    @AfterTest
    fun tearDown() {
        if (smtp.isRunning) {
            smtp.stop()
        }
    }

    @Test
    fun send() {
        sender.send(message)

        val messages = smtp.receivedMessages
        assertTrue(messages.isNotEmpty())
        assertEquals(message.subject, messages[0].subject)
        assertEquals(message.body, messages[0].content.toString())
        assertEquals(1, messages[0].getRecipients(RecipientType.TO).size)
        assertEquals(
            "${message.recipient.displayName} <${message.recipient.email}>",
            messages[0].getRecipients(RecipientType.TO)[0].toString()
        )

        val headers = messages[0].allHeaders
            .toList()
            .associate { header -> header.name to header.value }
        assertEquals(message.language, headers["Content-Language"])
        assertEquals(true, headers["Content-Type"]?.startsWith(message.mimeType))
    }

    @Test
    fun `recipient without displayName`() {
        sender.send(
            message.copy(recipient = message.recipient.copy(displayName = null))
        )

        val messages = smtp.receivedMessages
        assertEquals(
            message.recipient.email,
            messages[0].getRecipients(RecipientType.TO)[0].toString()
        )
    }

    @Test
    fun `send without language`() {
        sender.send(message.copy(language = null))

        val messages = smtp.receivedMessages
        assertTrue(messages.isNotEmpty())
        val headers = messages[0].allHeaders
            .toList()
            .associate { header -> header.name to header.value }
        assertEquals(null, headers["Content-Language"])
    }

    @Test
    fun `send with attachment`() {
        val file = File.createTempFile("test", ".txt")
        file.writeText("Hello world")

        sender.send(message.copy(attachments = listOf(file)))

        val messages = smtp.receivedMessages
        assertTrue(messages.isNotEmpty())
    }

    @Test
    fun error() {
        // Stop
        smtp.stop()

        assertThrows<MessagingException> { sender.send(message) }
    }
}
