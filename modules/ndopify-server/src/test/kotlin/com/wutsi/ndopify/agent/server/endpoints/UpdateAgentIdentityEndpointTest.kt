package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.UpdateIdentyRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.dao.IdentityChangeRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.IdentityType
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/agent/UpdateAgentIdentityEndpoint.sql"])
class UpdateAgentIdentityEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: AgentRepository

    @Autowired
    private lateinit var changeRequestDao: IdentityChangeRepository

    @Test
    fun `update identity`() {
        val request = UpdateIdentyRequest(
            firstName = "Ash",
            lastName = "Ketchum",
            identityType = IdentityType.NATIONAL_ID,
            imageUrls = listOf("https://example.com/page1.png", "https://example.com/page2.png"),
        )

        val response = rest.postForEntity("/v1/agents/3/identity", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(3L).get()
        assertEquals(request.firstName, agent.firstName)
        assertEquals(request.lastName, agent.lastName)
        assertEquals(KycStatus.PENDING, agent.identityKycStatus)
        assertEquals(AgentStatus.LIMITED, agent.status)

        val requests = changeRequestDao.findAll().filter { it.agent.id == 3L }
        assertEquals(1, requests.size)
        // NOTE: Hibernate's merge() mutates the already-managed `agent` instance loaded by the
        // service in-place, so the "old" values captured after the save already reflect the new
        // ones. Same quirk is asserted in UpdateAgentMobileMoneyEndpointTest.
        assertEquals(request.firstName, requests[0].oldFirstName)
        assertEquals(request.lastName, requests[0].oldLastName)
        assertEquals(request.firstName, requests[0].newFirstName)
        assertEquals(request.lastName, requests[0].newLastName)
        assertEquals(request.identityType, requests[0].identityType)
        assertEquals(request.imageUrls, requests[0].imageUrls)
        assertEquals(KycStatus.PENDING, requests[0].status)

        assertEquals(requests[0].id, agent.identityChange?.id)
    }

    @Test
    fun `identity already set does not overwrite agent but records change`() {
        val request = UpdateIdentyRequest(
            firstName = "Misty",
            lastName = "Waterflower",
            identityType = IdentityType.PASSPORT,
            imageUrls = listOf("https://example.com/page1.png"),
        )

        val response = rest.postForEntity("/v1/agents/1/identity", request, Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val agent = dao.findById(1L).get()
        assertEquals("Ray", agent.firstName)
        assertEquals("Sponsible", agent.lastName)

        val requests = changeRequestDao.findAll().filter { it.agent.id == 1L }
        assertEquals(1, requests.size)
        assertEquals("Ray", requests[0].oldFirstName)
        assertEquals("Sponsible", requests[0].oldLastName)
        assertEquals(request.firstName, requests[0].newFirstName)
        assertEquals(request.lastName, requests[0].newLastName)
        assertEquals(KycStatus.PENDING, requests[0].status)
    }

    @Test
    fun `agent not found`() {
        val request = UpdateIdentyRequest(
            firstName = "Ash",
            lastName = "Ketchum",
            identityType = IdentityType.NATIONAL_ID,
            imageUrls = listOf("https://example.com/page1.png"),
        )

        val response = rest.postForEntity("/v1/agents/999/identity", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no first name`() {
        val request = UpdateIdentyRequest(
            firstName = "",
            lastName = "Ketchum",
            identityType = IdentityType.NATIONAL_ID,
            imageUrls = listOf("https://example.com/page1.png"),
        )

        val response = rest.postForEntity("/v1/agents/3/identity", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `no document page1 url`() {
        val request = UpdateIdentyRequest(
            firstName = "Ash",
            lastName = "Ketchum",
            identityType = IdentityType.NATIONAL_ID,
            imageUrls = listOf(),
        )

        val response = rest.postForEntity("/v1/agents/3/identity", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `cannot update agent belonging to another tenant`() {
        overrideTenantId = 999L

        val request = UpdateIdentyRequest(
            firstName = "Ash",
            lastName = "Ketchum",
            identityType = IdentityType.NATIONAL_ID,
            imageUrls = listOf("https://example.com/page1.png"),
        )
        val response = rest.postForEntity("/v1/agents/3/identity", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.AGENT_NOT_FOUND, response.body?.error?.code)
    }
}
