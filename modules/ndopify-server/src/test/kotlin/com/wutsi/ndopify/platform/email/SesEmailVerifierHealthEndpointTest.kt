package com.wutsi.ndopify.platform.email

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.GetAccountRequest
import software.amazon.awssdk.services.sesv2.model.GetAccountResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestPropertySource(
    properties = [
        "ndopify.email.verification.provider=ses",
        "ndopify.email.verification.health.up-ttl-minutes=0",
        "ndopify.email.verification.health.down-ttl-seconds=0",
    ],
)
class SesEmailVerifierHealthEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: SesV2Client

    @Test
    fun `SES is UP with its account flags`() {
        doReturn(account())
            .whenever(client).getAccount(any<GetAccountRequest>())

        val component = component(health())

        assertEquals("UP", component["status"])
        val details = component["details"] as Map<*, *>
        assertEquals(true, details["sendingEnabled"])
        assertEquals(false, details["productionAccessEnabled"])
        assertTrue(details["latency"] is Number)
    }

    @Test
    fun `a failing SES is DOWN and leaks nothing`() {
        doThrow(RuntimeException("AKIA-secret ray@gmail.com"))
            .whenever(client).getAccount(any<GetAccountRequest>())

        val body = health()

        assertEquals("DOWN", component(body)["status"])
        assertFalse(body.toString().contains("AKIA-secret"))
        assertFalse(body.toString().contains("ray@gmail.com"))
        assertEquals("RuntimeException", (component(body)["details"] as Map<*, *>)["error"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun component(body: Map<*, *>): Map<String, Any> =
        (body["components"] as Map<String, Any>)["sesEmailVerifier"] as Map<String, Any>

    private fun health(): Map<*, *> = rest.getForEntity("/actuator/health", Map::class.java).body!!

    private fun account() = GetAccountResponse.builder()
        .sendingEnabled(true)
        .productionAccessEnabled(false)
        .build()
}

@TestPropertySource(
    properties = [
        "ndopify.email.verification.provider=ses",
        "ndopify.email.verification.health.up-ttl-minutes=1",
        "ndopify.email.verification.health.down-ttl-seconds=0",
    ],
)
class SesEmailVerifierHealthCacheTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: SesV2Client

    @Test
    fun `the cached UP answer makes two probes one call`() {
        doReturn(GetAccountResponse.builder().sendingEnabled(true).productionAccessEnabled(true).build())
            .whenever(client).getAccount(any<GetAccountRequest>())

        rest.getForEntity("/actuator/health", Map::class.java)
        rest.getForEntity("/actuator/health", Map::class.java)

        verify(client, times(1)).getAccount(any<GetAccountRequest>())
    }
}
