package com.wutsi.ndopify.esignature.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class SecurityConfigurationTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Test
    fun `health answers without a session`() {
        val response = mvc().perform(get("/actuator/health")).andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"status\":\"UP\""))
    }

    @Test
    fun `the other actuator endpoints are denied`() {
        val response = mvc().perform(get("/actuator/beans")).andReturn().response

        assertEquals(403, response.status)
    }

    @Test
    fun `the test profile exposes only health, without details`() {
        val profile = YamlPropertySourceLoader()
            .load("test", ClassPathResource("application-test.yml"))
            .single()

        assertEquals("health", profile.getProperty("management.endpoints.web.exposure.include"))
        assertEquals("never", profile.getProperty("management.endpoint.health.show-details"))
    }

    private fun mvc(): MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
        .build()
}
