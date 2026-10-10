package com.wutsi.ndopify.npc.client.ndopify

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NdTenantClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdTenantClient

    @BeforeEach
    fun setUp() {
        val rest = RestTemplate()
        server = MockRestServiceServer.bindTo(rest).build()
        client = NdTenantClient("http://localhost:8080", rest)
    }

    @Test
    fun search() {
        server.expect(requestTo("http://localhost:8080/v1/tenants?active=true"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"tenants":[${tenant(1, "CM", true)}]}""", MediaType.APPLICATION_JSON))

        val response = client.search()

        server.verify()
        assertEquals(listOf(1L), response.tenants.map { it.id })
    }

    @Test
    fun searchEmpty() {
        server.expect(requestTo("http://localhost:8080/v1/tenants?active=true"))
            .andRespond(withSuccess("""{"tenants":[]}""", MediaType.APPLICATION_JSON))

        assertTrue(client.search().tenants.isEmpty())
    }

    @Test
    fun serverError() {
        server.expect(requestTo("http://localhost:8080/v1/tenants?active=true"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<HttpServerErrorException> { client.search() }
    }

    // Mirrors the server's serialization: every non-null field is present, null ones are omitted.
    private fun tenant(
        id: Long,
        countryCode: String,
        active: Boolean,
    ) = """
        {
            "id": $id,
            "name": "tenant-$id",
            "domainName": "tenant-$id.ndopify.com",
            "countryCode": "$countryCode",
            "currencyCode": "XAF",
            "locales": ["fr", "en"],
            "currencySymbol": "FCFA",
            "numberFormat": "#,###,##0",
            "monetaryFormat": "#,###,##0 FCFA",
            "dateTimeFormat": "dd/MM/yyyy HH:mm",
            "dateFormat": "dd/MM/yyyy",
            "timeFormat": "HH:mm",
            "active": $active,
            "createdAt": "2026-01-01T00:00:00.000+00:00"
        }
    """.trimIndent()
}
