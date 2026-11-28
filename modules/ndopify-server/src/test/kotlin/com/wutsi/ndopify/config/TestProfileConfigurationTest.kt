package com.wutsi.ndopify.config

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The deployed TEST profile (`application-test.yml`): only the health endpoint, without details; the ops routes stay
 * enabled (admin-only). PROD is already health-only with details off (`application-prod.yml`).
 */
class TestProfileConfigurationTest {
    private val profile = YamlPropertySourceLoader()
        .load("test", ClassPathResource("application-test.yml"))
        .single()

    @Test
    fun `only health is exposed`() =
        assertEquals("health", profile.getProperty("management.endpoints.web.exposure.include"))

    @Test
    fun `health details are never shown`() =
        assertEquals("never", profile.getProperty("management.endpoint.health.show-details"))

    @Test
    fun `the ops endpoints stay enabled`() =
        assertEquals("true", profile.getProperty("ndopify.endpoints.ops").toString())
}
