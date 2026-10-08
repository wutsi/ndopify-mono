package com.wutsi.ndopify.platform.momo.mtn.impl

import com.wutsi.ndopify.util.http.Http
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect.NORMAL
import java.net.http.HttpClient.Version.HTTP_1_1
import kotlin.test.Test

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MtnUserProviderSandboxTest {
    @Value("\${ndopify.mobile-money.mtn.collection.subscription-key}")
    private lateinit var subscriptionId: String

    private val http = Http(
        client = HttpClient.newBuilder()
            .version(HTTP_1_1)
            .followRedirects(NORMAL)
            .build(),
        objectMapper = JsonMapper(),
    )

    @Test
    fun get() {
        val provider = MtnUserProviderSandbox(
            subscriptionKey = subscriptionId,
            callbackUrl = "http://127.0.0.1/mtn/callback",
            http = http
        )

        val user = provider.get()
        assertTrue(user.id.isNotEmpty())
        assertTrue(user.apiKey.isNotEmpty())
    }
}
