package com.wutsi.ndopify.platform.email

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import kotlin.test.Test
import kotlin.test.assertFalse

class SesEmailVerifierHealthDisabledTest : BaseEndpointIntegrationTest() {
    @Test
    fun `SES is absent with the default provider`() {
        val body = rest.getForEntity("/actuator/health", Map::class.java).body!!

        val components = body["components"] as Map<*, *>
        assertFalse(components.containsKey("sesEmailVerifier"))
    }
}
