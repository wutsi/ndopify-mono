package com.wutsi.ndopify.web.ui.pwa

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.wutsi.ndopify.refdata.dto.Tenant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `/manifest.json` with a fake tenant in the model, as `TenantAdvice` does. */
class ManifestControllerTest {
    @ControllerAdvice
    class FakeTenantAdvice {
        @ModelAttribute("tenant")
        fun tenant(): Tenant = tenant
    }

    companion object {
        val FULL = Tenant(
            name = "Ndopify Cameroun",
            iconUrl = "https://cdn.ndopify.cm/icon.png",
            icon192Url = "https://cdn.ndopify.cm/icon-192.png",
            icon512Url = "https://cdn.ndopify.cm/icon-512.png",
            maskableIconUrl = "https://cdn.ndopify.cm/icon-maskable-512.png",
            appleTouchIconUrl = "https://cdn.ndopify.cm/apple-touch-icon.png",
        )
        var tenant = FULL
    }

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ManifestController())
        .setControllerAdvice(FakeTenantAdvice())
        .build()

    private fun manifest(): JsonNode =
        ObjectMapper().readTree(
            mvc.perform(get("/manifest.json")).andExpect(status().isOk).andReturn().response.contentAsString,
        )

    @BeforeEach
    fun reset() {
        tenant = FULL
    }

    @Test
    fun `the branding is the tenant's`() {
        val json = manifest()

        assertEquals("Ndopify Cameroun", json["name"].asText())
        assertEquals("Ndopify Cameroun", json["short_name"].asText())
        assertEquals("fr", json["lang"].asText())
        assertEquals("/", json["start_url"].asText())
        assertEquals("/", json["scope"].asText())
        assertEquals("standalone", json["display"].asText())
        assertEquals("#1d7edf", json["theme_color"].asText())
        assertEquals("#ffffff", json["background_color"].asText())
    }

    @Test
    fun `the tenant's variants are declared as-is`() {
        val icons = manifest()["icons"]

        assertEquals(3, icons.size())
        assertEquals("https://cdn.ndopify.cm/icon-192.png", icons[0]["src"].asText())
        assertEquals("192x192", icons[0]["sizes"].asText())
        assertEquals("any", icons[0]["purpose"].asText())
        assertEquals("https://cdn.ndopify.cm/icon-512.png", icons[1]["src"].asText())
        assertEquals("512x512", icons[1]["sizes"].asText())
        assertEquals("any", icons[1]["purpose"].asText())
        assertEquals("https://cdn.ndopify.cm/icon-maskable-512.png", icons[2]["src"].asText())
        assertEquals("512x512", icons[2]["sizes"].asText())
        assertEquals("maskable", icons[2]["purpose"].asText())
    }

    @Test
    fun `a partial variant set is never mixed with Ndopify's`() {
        tenant = Tenant(name = "A", iconUrl = "https://cdn/a.png", icon512Url = "https://cdn/512.png")
        val icons = manifest()["icons"]

        assertEquals(1, icons.size())
        assertEquals("https://cdn/512.png", icons[0]["src"].asText())
        assertTrue(icons.all { it["src"].asText().startsWith("https://cdn/") })
    }

    @Test
    fun `iconUrl alone keeps its intrinsic size`() {
        tenant = Tenant(name = "A", iconUrl = "https://cdn/a.png")
        val icons = manifest()["icons"]

        assertEquals(1, icons.size())
        assertEquals("https://cdn/a.png", icons[0]["src"].asText())
        assertNull(icons[0]["sizes"])
    }

    @Test
    fun `no icon uses Ndopify's set`() {
        tenant = Tenant(name = "A")
        val icons = manifest()["icons"]

        assertEquals(3, icons.size())
        assertEquals("/assets/images/icon-192.png", icons[0]["src"].asText())
        assertEquals("192x192", icons[0]["sizes"].asText())
        assertEquals("/assets/images/icon-512.png", icons[1]["src"].asText())
        assertEquals("512x512", icons[1]["sizes"].asText())
        assertEquals("/assets/images/icon-maskable-512.png", icons[2]["src"].asText())
        assertEquals("maskable", icons[2]["purpose"].asText())
    }

    @Test
    fun `a tenant without a name falls back to Ndopify`() {
        tenant = Tenant()
        assertEquals("Ndopify", manifest()["name"].asText())
    }

    @Test
    fun `the response is a manifest`() {
        mvc.perform(get("/manifest.json"))
            .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
    }
}
