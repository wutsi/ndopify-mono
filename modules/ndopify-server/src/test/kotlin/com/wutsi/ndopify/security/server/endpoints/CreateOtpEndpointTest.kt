package com.wutsi.ndopify.security.server.endpoints

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.dao.AuthFactorRepository
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.service.OtpGenerator
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/security/CreateOtpEndpoint.sql"])
class CreateOtpEndpointTest : TenantAwareEndpointIntegrationTest() {
    companion object {
        const val CODE = "123456"

        // T_AUTH_FACTOR.expires_at is a DATETIME (second precision): allow for rounding on either side.
        const val TOLERANCE_MS = 2000L
        const val MAIL_TIMEOUT_MS = 5000L
    }

    @Value("\${spring.mail.username}")
    private lateinit var username: String

    @Value("\${spring.mail.password}")
    private lateinit var password: String

    @MockitoBean
    private lateinit var otpGenerator: OtpGenerator

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var authFactorRepository: AuthFactorRepository

    @Autowired
    private lateinit var passwordEncryptor: PasswordEncryptor

    @BeforeEach
    override fun setUp() {
        super.setUp()

        doReturn(CODE).whenever(otpGenerator).generate()
    }

    @Test
    fun `existing user without OTP`() {
        val before = System.currentTimeMillis()
        val response =
            rest.postForEntity("/v1/otp", CreateOtpRequest(email = "ray.sponsible@gmail.com"), Void::class.java)
        val after = System.currentTimeMillis()

        assertEquals(HttpStatus.OK, response.statusCode)

        val user = userRepository.findByEmailIgnoreCase("ray.sponsible@gmail.com").get()
        assertEquals(1L, user.id)

        val otp = authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).get()
        assertNotNull(otp.salt)
        assertEquals(passwordEncryptor.encrypt(CODE, otp.salt), otp.data)
        assertNull(otp.lastLoggedInAt)
        assertExpiresIn(300L, before, after, otp.expiresAt?.time)
    }

    @Test
    fun `email is sent to the user`() {
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = "ray.sponsible@gmail.com"), Void::class.java)

        val message = receivedMessage()
        assertEquals("ray.sponsible@gmail.com", recipient(message).address)
        assertEquals("Ray Sponsible", recipient(message).personal)
        assertEquals("Votre code de vérification Ndopify", message.subject)

        val body = message.content.toString()
        assertTrue(body.contains(CODE), "the code must be in the email")
        assertTrue(body.contains("Ray Sponsible"), "the email must greet the user by name")
        assertTrue(body.contains("Ndopify"))
        assertTrue(body.contains("https://com-wutsi-ndopify-test.s3.us-east-1.amazonaws.com/www/assets/images/logo.png"))
        assertTrue(body.contains("pendant 5 minutes"), "the validity must match the default ttl (5 minutes)")
    }

    @Test
    fun `email sent with the code that was stored`() {
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = "ray.sponsible@gmail.com"), Void::class.java)

        val otp = authFactorRepository.findById(authFactorIdOf("ray.sponsible@gmail.com")).get()
        // (?<![#\w]) skips CSS colors such as #000000
        val code = Regex("""(?<![#\w])\d{6}\b""").find(receivedMessage().content.toString())?.value

        assertEquals(CODE, code)
        assertTrue(
            passwordEncryptor.matches(code!!, otp.data, otp.salt),
            "the emailed code must verify against the stored hash"
        )
    }

    @Test
    fun `unknown email fails`() {
        val response = rest.postForEntity("/v1/otp", CreateOtpRequest(email = "NEW.Agent@Gmail.com"), Void::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `email is case insensitive`() {
        val usersBefore = userRepository.count()

        val response =
            rest.postForEntity("/v1/otp", CreateOtpRequest(email = "Ray.Sponsible@GMAIL.com"), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(usersBefore, userRepository.count()) // no duplicate user

        val user = userRepository.findById(1L).get()
        assertNotNull(authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).orElse(null))
        assertEquals(1, smtp.receivedMessages.size)
    }

    @Test
    fun `custom ttl`() {
        val before = System.currentTimeMillis()
        val response = rest.postForEntity(
            "/v1/otp",
            CreateOtpRequest(email = "ray.sponsible@gmail.com", ttl = 600),
            Void::class.java
        )
        val after = System.currentTimeMillis()

        assertEquals(HttpStatus.OK, response.statusCode)

        val user = userRepository.findById(1L).get()
        val otp = authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).get()
        assertExpiresIn(600L, before, after, otp.expiresAt?.time)

        assertTrue(receivedMessage().content.toString().contains("pendant 10 minutes"))
    }

    @Test
    fun `partial minute ttl is rounded down in the email`() {
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = "ray.sponsible@gmail.com", ttl = 150), Void::class.java)

        // 2.5 minutes: the email must never promise more than the code really lasts
        assertTrue(receivedMessage().content.toString().contains("pendant 2 minutes"))
    }

    @Test
    fun `new request replaces the previous OTP`() {
        val before = System.currentTimeMillis()
        val response = rest.postForEntity("/v1/otp", CreateOtpRequest(email = "has-otp@gmail.com"), Void::class.java)
        val after = System.currentTimeMillis()

        assertEquals(HttpStatus.OK, response.statusCode)

        val user = userRepository.findById(2L).get()
        val otp = authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).get()
        assertEquals(22L, otp.id) // updated in place, not duplicated
        assertNotEquals("old-otp-hash", otp.data)
        assertNotEquals("old-salt", otp.salt)
        assertEquals(passwordEncryptor.encrypt(CODE, otp.salt), otp.data)
        assertExpiresIn(300L, before, after, otp.expiresAt?.time) // was expired since 2020

        assertEquals(1, smtp.receivedMessages.size)
        assertEquals("has-otp@gmail.com", recipient(receivedMessage()).address)
    }

    @Test
    fun `other auth factors are left untouched`() {
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = "has-otp@gmail.com"), Void::class.java)

        val user = userRepository.findById(2L).get()
        val password = authFactorRepository.findByUserAndAuthType(user, AuthType.PASSWORD).get()
        assertEquals(21L, password.id)
        assertEquals("secret-password", password.data)
        assertNull(password.expiresAt)
    }

    @Test
    fun `tenant falls back to the home tenant of the user`() {
        ignoreTenantIdHeader = true

        val response =
            rest.postForEntity("/v1/otp", CreateOtpRequest(email = "home-tenant@gmail.com"), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val message = receivedMessage()
        assertEquals("Votre code de vérification Autre Marque", message.subject)
        val body = message.content.toString()
        assertTrue(body.contains("Autre Marque"))
        assertTrue(body.contains("other-logo.png"))
    }

    @Test
    fun `request tenant wins over the home tenant of the user`() {
        // header says tenant 1 (default), the user's home tenant is 2
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = "home-tenant@gmail.com"), Void::class.java)

        assertEquals("Votre code de vérification Ndopify", receivedMessage().subject)
    }

    @Test
    fun `no tenant`() {
        ignoreTenantIdHeader = true

        val response =
            rest.postForEntity("/v1/otp", CreateOtpRequest(email = "has-otp@gmail.com"), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
        assertEquals(0, smtp.receivedMessages.size)

        // rolled back: the previous OTP is untouched
        assertEquals("old-otp-hash", authFactorRepository.findById(22L).get().data)
    }

    @Test
    fun `unknown tenant`() {
        overrideTenantId = 999L

        val response =
            rest.postForEntity("/v1/otp", CreateOtpRequest(email = "has-otp@gmail.com"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.TENANT_NOT_FOUND, response.body?.error?.code)
        assertEquals(0, smtp.receivedMessages.size)
        assertEquals("old-otp-hash", authFactorRepository.findById(22L).get().data)
    }

    @Test
    fun `email delivery failure`() {
        smtp.stop()

        val response = rest.postForEntity(
            "/v1/otp",
            CreateOtpRequest(email = "ray.sponsible@gmail.com"),
            ErrorResponse::class.java
        )

        // The caller must know the code was not sent...
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    @Test
    fun `blank email`() {
        val response = rest.postForEntity("/v1/otp", CreateOtpRequest(email = ""), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(1, authFactorRepository.findAll().count { it.authType == AuthType.OTP }) // only the fixture's OTP
        assertEquals(0, smtp.receivedMessages.size)
    }

    @Test
    fun `ttl below one minute`() {
        val response = rest.postForEntity(
            "/v1/otp",
            CreateOtpRequest(email = "ray.sponsible@gmail.com", ttl = 59),
            ErrorResponse::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)

        val user = userRepository.findById(1L).get()
        assertNull(authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).orElse(null))
        assertEquals(0, smtp.receivedMessages.size)
    }

    private fun receivedMessage(): MimeMessage {
        assertTrue(smtp.waitForIncomingEmail(MAIL_TIMEOUT_MS, 1), "no email received")
        assertEquals(1, smtp.receivedMessages.size)
        return smtp.receivedMessages[0]
    }

    private fun recipient(message: MimeMessage): InternetAddress =
        message.getRecipients(Message.RecipientType.TO).single() as InternetAddress

    private fun authFactorIdOf(email: String): Long {
        val user = userRepository.findByEmailIgnoreCase(email).get()
        return authFactorRepository.findByUserAndAuthType(user, AuthType.OTP).get().id!!
    }

    private fun assertExpiresIn(ttlSeconds: Long, before: Long, after: Long, expiresAt: Long?) {
        assertNotNull(expiresAt)
        assertTrue(
            expiresAt >= before + ttlSeconds * 1000 - TOLERANCE_MS &&
                expiresAt <= after + ttlSeconds * 1000 + TOLERANCE_MS,
            "expiresAt=$expiresAt is not ~${ttlSeconds}s after the request ($before..$after)"
        )
    }
}
