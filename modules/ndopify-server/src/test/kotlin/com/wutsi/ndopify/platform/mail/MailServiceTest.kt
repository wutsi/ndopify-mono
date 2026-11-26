package com.wutsi.ndopify.platform.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Message.RecipientType
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mail.MailSendException
import org.springframework.test.context.ActiveProfiles
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("qa")
class MailServiceTest {
    @Value("\${spring.mail.username}")
    private lateinit var username: String

    @Value("\${spring.mail.password}")
    private lateinit var password: String

    private lateinit var smtp: GreenMail

    @Autowired
    private lateinit var service: MailService

    private val message = Email(
        sender = Receipient(email = "no-reply@tenant1.com", displayName = "Tenant1"),
        recipient = Receipient(email = "ray.sponsible@gmail.com", displayName = "Ray Sponsible"),
        subject = "Hello",
        body = "Yo man",
        language = "en",
        mimeType = "text/html",
    )

    @BeforeTest
    fun setUp() {
        smtp = GreenMail(ServerSetupTest.SMTP)
        smtp.setUser(username, password)
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
        service.send(message)

        val messages = smtp.receivedMessages
        assertTrue(messages.isNotEmpty())
        assertEquals(message.subject, messages[0].subject)
        assertEquals(message.body, messages[0].content.toString())
        assertEquals(1, messages[0].getRecipients(RecipientType.TO).size)
        assertEquals(
            "${message.recipient.displayName} <${message.recipient.email}>",
            messages[0].getRecipients(RecipientType.TO)[0].toString()
        )
        assertEquals(
            "${message.sender?.displayName} <${message.sender?.email}>",
            messages[0].from[0].toString(),
        )

        val headers = messages[0].allHeaders
            .toList()
            .associate { header -> header.name to header.value }
        assertEquals(message.language, headers["Content-Language"])
        assertEquals(true, headers["Content-Type"]?.startsWith(message.mimeType))
        assertEquals(null, headers["Reply-To"], "a customer reply must not reach the agent")
    }

    @Test
    fun `sender without address uses the configured from`() {
        service.send(message.copy(sender = message.sender?.copy(email = "")))

        val messages = smtp.receivedMessages
        assertEquals("Tenant1 <no-reply@wutsi.com>", messages[0].from[0].toString())
    }

    @Test
    fun `sender without displayName`() {
        service.send(message.copy(sender = message.sender?.copy(displayName = null)))

        val messages = smtp.receivedMessages
        assertEquals("no-reply@tenant1.com", messages[0].from[0].toString())
    }

    @Test
    fun `display name cannot inject a header`() {
        val address = Receipient(
            email = "no-reply@tenant1.com",
            displayName = "Tenant1\r\nBcc: evil@example.com",
        ).toInternetAddress()

        assertFalse(address.personal.contains("\r"))
        assertFalse(address.personal.contains("\n"))
    }

    @Test
    fun `recipient without displayName`() {
        service.send(
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
        service.send(message.copy(language = null))

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

        service.send(message.copy(attachments = listOf(file)))

        val messages = smtp.receivedMessages
        assertTrue(messages.isNotEmpty())
    }

    @Test
    fun `a pinned Message-ID survives the send`() {
        service.send(
            message.copy(
                messageId = "mandate-1-1@wutsi.com",
                inReplyTo = "mandate-1-1@wutsi.com",
                references = listOf("mandate-1-1@wutsi.com"),
            ),
        )

        val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
        assertEquals("<mandate-1-1@wutsi.com>", headers["Message-ID"])
        assertEquals("<mandate-1-1@wutsi.com>", headers["In-Reply-To"])
        assertEquals("<mandate-1-1@wutsi.com>", headers["References"])
        assertEquals("Hello", headers["Thread-Topic"])
        assertEquals("wutsi.com", service.fromDomain)
    }

    @Test
    fun `several references are one space-separated header`() {
        service.send(
            message.copy(
                messageId = "mandate-1-1-x@wutsi.com",
                references = listOf("mandate-1-1@wutsi.com", "other@wutsi.com"),
            ),
        )

        val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
        assertEquals("<mandate-1-1@wutsi.com> <other@wutsi.com>", headers["References"])
    }

    @Test
    fun `no conversation headers without a message id`() {
        service.send(message)

        val headers = smtp.receivedMessages[0].allHeaders.toList().associate { it.name to it.value }
        assertNull(headers["In-Reply-To"], "no In-Reply-To on a platform e-mail")
        assertNull(headers["References"], "no References on a platform e-mail")
        assertNull(headers["Thread-Topic"], "no Thread-Topic on a platform e-mail")
        assertFalse(headers["Message-ID"].orEmpty().contains("mandate-"), "JavaMail keeps generating its own Message-ID")
    }

    @Test
    fun error() {
        // Stop
        smtp.stop()

        assertThrows<MailSendException> { service.send(message) }
    }
}
