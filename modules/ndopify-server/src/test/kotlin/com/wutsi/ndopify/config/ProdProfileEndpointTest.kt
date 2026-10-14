package com.wutsi.ndopify.config

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** application-prod.yml: only health is public, without details. */
@ActiveProfiles("prod")
class ProdProfileEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun `health is exposed, without details`() {
        val response = rest.getForEntity("/actuator/health", String::class.java)

        // DOWN (503) when MTN or S3 are not reachable, as in tests: the status is what matters here
        assertTrue(response.statusCode in listOf(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE))
        assertTrue(response.body!!.contains("\"status\""))
        assertFalse(response.body!!.contains("components"))
    }

    @Test
    fun `other actuator endpoints are not exposed`() {
        listOf("beans", "flyway", "info", "scheduledtasks", "mappings").forEach { endpoint ->
            val response = rest.getForEntity("/actuator/$endpoint", String::class.java)

            assertFalse(response.statusCode.is2xxSuccessful, endpoint)
        }
    }

    @Test
    fun `swagger is disabled`() {
        listOf("/api.html", "/v3/api-docs").forEach { path ->
            val response = rest.getForEntity(path, String::class.java)

            assertFalse(response.statusCode.is2xxSuccessful, path)
        }
    }
}
