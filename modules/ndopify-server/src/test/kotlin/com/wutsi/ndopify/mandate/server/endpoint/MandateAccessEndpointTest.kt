package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** Who may see or change a mandate: its agent (user 11 is agent 20's), the agent it's offered to, or an admin. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateAccessEndpoint.sql"])
class MandateAccessEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MandateRepository

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `agent gets its mandate`() {
        assertEquals("Mine", get(1).comments)
    }

    @Test
    fun `agent gets the mandate offered to it`() {
        assertEquals("Offered", get(3).comments)
    }

    @Test
    fun `agent can't get the mandate of another agent`() {
        assertDenied(rest.getForEntity("/v1/mandates/2", ErrorResponse::class.java))
    }

    @Test
    fun `agent can't get a mandate it declined`() {
        assertDenied(rest.getForEntity("/v1/mandates/4", ErrorResponse::class.java))
    }

    @Test
    fun `user who isn't an agent can't get a mandate`() {
        userId = 13L

        assertDenied(rest.getForEntity("/v1/mandates/1", ErrorResponse::class.java))
    }

    @Test
    fun `anonymous can't get a mandate`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/mandates/1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    @Test
    fun `not found before access denied`() {
        val response = rest.getForEntity("/v1/mandates/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `admin gets any mandate`() {
        admin = true

        assertEquals("Not mine", get(2).comments)
    }

    @Test
    fun `agent searches its own mandates only`() {
        assertEquals(listOf(1L), search("").map { it.id })
        assertEquals(listOf(1L), search("agent-id=20").map { it.id })
    }

    @Test
    fun `agent can't search the mandates of another agent`() {
        assertDenied(rest.getForEntity("/v1/mandates?agent-id=30", ErrorResponse::class.java))
        assertDenied(rest.getForEntity("/v1/mandates?agent-id=20&agent-id=30", ErrorResponse::class.java))
    }

    @Test
    fun `agent searches the mandates offered to it`() {
        // Not the one it accepted (its own), nor the one it declined
        assertEquals(listOf(3L), search("offered-to=20").map { it.id })
        assertEquals(listOf(3L), search("offered-to=20&id=3&id=4").map { it.id })
    }

    @Test
    fun `agent can't search the mandates offered to another agent`() {
        assertDenied(rest.getForEntity("/v1/mandates?offered-to=30", ErrorResponse::class.java))
        assertDenied(rest.getForEntity("/v1/mandates?offered-to=20&agent-id=30", ErrorResponse::class.java))
    }

    @Test
    fun `admin searches the mandates offered to an agent`() {
        admin = true

        assertEquals(listOf(3L), search("offered-to=20").map { it.id })
    }

    @Test
    fun `user who isn't an agent can't search`() {
        userId = 13L

        assertDenied(rest.getForEntity("/v1/mandates", ErrorResponse::class.java))
    }

    @Test
    fun `admin searches all mandates`() {
        admin = true

        assertEquals(listOf(1L, 2L, 3L, 4L), search("").map { it.id })
    }

    @Test
    fun `agent can't update the mandate of another agent`() {
        val response = rest.postForEntity(
            "/v1/mandates/2",
            UpdateMandateRequest(comments = "Hacked"),
            ErrorResponse::class.java,
        )

        assertDenied(response)
        assertEquals("Not mine", dao.findById(2L).get().comments)
    }

    @Test
    fun `agent can't update the mandate offered to it`() {
        val response = rest.postForEntity(
            "/v1/mandates/3",
            UpdateMandateRequest(comments = "Hacked"),
            ErrorResponse::class.java,
        )

        assertDenied(response)
        assertEquals("Offered", dao.findById(3L).get().comments)
    }

    @Test
    fun `agent can't request the approval of the mandate of another agent`() {
        assertDenied(rest.postForEntity("/v1/mandates/2/request-approval", null, ErrorResponse::class.java))
        assertEquals(MandateStatus.APPROVED, dao.findById(2L).get().status)
    }

    @Test
    fun `agent can't close the mandate of another agent`() {
        val request = CloseMandateRequest(finalPrice = Money(amount = 140000, currency = "XAF"))

        assertDenied(rest.postForEntity("/v1/mandates/2/close", request, ErrorResponse::class.java))
        assertEquals(MandateStatus.APPROVED, dao.findById(2L).get().status)
    }

    @Test
    fun `agent can't cancel the mandate of another agent`() {
        assertDenied(rest.postForEntity("/v1/mandates/2/cancel", CancelMandateRequest(), ErrorResponse::class.java))
        assertEquals(MandateStatus.APPROVED, dao.findById(2L).get().status)
    }

    @Test
    fun `anonymous can't cancel a mandate`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/mandates/1/cancel", CancelMandateRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MandateStatus.ASSIGNED, dao.findById(1L).get().status)
    }

    @Test
    fun `agent cancels its mandate`() {
        val response = rest.postForEntity("/v1/mandates/1/cancel", CancelMandateRequest(), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.CANCELLED, dao.findById(1L).get().status)
    }

    @Test
    fun `admin cancels the mandate of any agent`() {
        admin = true

        val response = rest.postForEntity("/v1/mandates/2/cancel", CancelMandateRequest(), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.CANCELLED, dao.findById(2L).get().status)
    }

    private fun get(id: Long) =
        rest.getForEntity("/v1/mandates/$id", GetMandateResponse::class.java).body!!.mandate

    private fun search(query: String) =
        rest.getForEntity("/v1/mandates?$query", SearchMandateResponse::class.java).body!!.mandates

    private fun assertDenied(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }
}
