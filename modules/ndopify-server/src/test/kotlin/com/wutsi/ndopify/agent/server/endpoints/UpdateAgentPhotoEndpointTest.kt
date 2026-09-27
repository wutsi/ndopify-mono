package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.UpdateImageRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentImageEndpoint.sql"])
class UpdateAgentPhotoEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Test
    fun photo() {
        val request = UpdateImageRequest(url = "https://cdn.example.com/new-photo.jpg")

        val response = rest.postForEntity("/v1/agents/1/photo", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(1L).get()
        assertEquals("https://cdn.example.com/new-photo.jpg", agent.photoUrl)

        // Agency logo must remain untouched
        assertEquals("https://cdn.example.com/old-logo.jpg", agent.agencyLogoUrl)
    }

    @Test
    fun `no url`() {
        val request = UpdateImageRequest(url = "")

        val response = rest.postForEntity("/v1/agents/1/photo", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateImageRequest(url = "https://cdn.example.com/new-photo.jpg")

        val response = rest.postForEntity("/v1/agents/999/photo", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
