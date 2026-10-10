package com.wutsi.ndopify

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import com.wutsi.ndopify.common.dto.HttpHeader
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.AfterTest
import kotlin.test.assertEquals

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("qa")
abstract class BaseEndpointIntegrationTest : ClientHttpRequestInterceptor {
    companion object {
        const val DEVICE_ID = "device-test"
    }

    @Value("\${spring.mail.username}")
    private lateinit var username: String

    @Value("\${spring.mail.password}")
    private lateinit var password: String

    protected lateinit var smtp: GreenMail

    @Autowired
    protected lateinit var rest: TestRestTemplate

    private val folder = File(File(System.getProperty("user.home")), "__ndopify")

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        request.headers.remove(HttpHeader.DEVICE_ID)
        request.headers.add(HttpHeader.DEVICE_ID, DEVICE_ID)
        return execution.execute(request, body)
    }

    @BeforeEach
    fun setUp() {
        rest.restTemplate.interceptors.add(this)

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

    protected fun download(
        url: String,
        expectedStatusCode: Int,
        expectedFileName: String?,
        expectedContentType: String,
        accessToken: String? = null,
    ): File? {
        folder.mkdirs()
        val cnn = URL(url).openConnection() as HttpURLConnection
        try {
            if (accessToken != null) {
                cnn.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            cnn.connect()

            assertEquals(expectedStatusCode, cnn.responseCode)

            if (expectedStatusCode == 200) {
                assertEquals(expectedContentType, cnn.contentType)
                assertEquals("attachment; filename=\"$expectedFileName\"", cnn.getHeaderField("Content-Disposition"))

                val file = File(folder, expectedFileName ?: "")
                val output = FileOutputStream(file)
                output.use {
                    IOUtils.copy(cnn.inputStream, output)
                }
                return file
            } else {
                return null
            }
        } finally {
            cnn.disconnect()
        }
    }
}
