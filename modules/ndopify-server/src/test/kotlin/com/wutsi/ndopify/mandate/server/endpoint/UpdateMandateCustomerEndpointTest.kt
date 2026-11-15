package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.customer.dto.UpdateCustomerRequest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/UpdateMandateCustomerEndpoint.sql"])
class UpdateMandateCustomerEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private var admin = false

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Autowired
    private lateinit var contractDao: ContractRepository

    @Autowired
    private lateinit var customerDao: CustomerRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    private val request = UpdateCustomerRequest(
        firstName = "Raymond",
        lastName = "Sponsible",
        email = "raymond.sponsible@example.com",
        whatsappNumber = "+237699000111",
    )

    @Test
    fun update() {
        val response = rest.postForEntity("/v1/mandates/1/customer", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val customer = customerDao.findById(10L).get()
        assertEquals("+237699000111", customer.whatsappNumber)
        val party = partyDao.findById(100L).get()
        assertEquals("Raymond", party.firstName)
        assertEquals("Sponsible", party.lastName)
        assertEquals("raymond.sponsible@example.com", party.email)
        assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(1L).get().status)
    }

    @Test
    fun `pending approval - email changed cancels the contract`() {
        val response = rest.postForEntity("/v1/mandates/3/customer", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(3L).get().status)
        assertEquals(ContractStatus.CANCELLED, contractDao.findById("c-3").get().status)
    }

    @Test
    fun `pending approval - name change keeps the contract`() {
        val body = request.copy(email = "Ray.Sponsible@gmail.com", firstName = "Raymond")
        val response = rest.postForEntity("/v1/mandates/4/customer", body, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.PENDING_APPROVAL, mandateDao.findById(4L).get().status)
        assertEquals(ContractStatus.PENDING_SIGNATURE, contractDao.findById("c-4").get().status)
        assertEquals("Raymond", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `approved mandate - updated, contract untouched`() {
        val response = rest.postForEntity("/v1/mandates/5/customer", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(5L).get().status)
        assertEquals(ContractStatus.SIGNED, contractDao.findById("c-5").get().status)
        assertEquals("Raymond", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `closed - 409, nothing saved`() {
        val response = rest.postForEntity("/v1/mandates/6/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
        assertEquals("+237699123456", customerDao.findById(10L).get().whatsappNumber)
    }

    @Test
    fun `cancelled - 409, nothing saved`() {
        val response = rest.postForEntity("/v1/mandates/7/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `another agent's mandate - 403, nothing saved`() {
        val response = rest.postForEntity("/v1/mandates/2/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `admin - updates another agent's mandate`() {
        admin = true

        val response = rest.postForEntity("/v1/mandates/2/customer", request, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Raymond", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `another tenant's mandate - 404`() {
        val response = rest.postForEntity("/v1/mandates/8/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `unknown mandate - 404`() {
        val response = rest.postForEntity("/v1/mandates/999/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `email owned by another party - 409, nothing saved`() {
        val response = rest.postForEntity(
            "/v1/mandates/1/customer",
            request.copy(email = "taken@example.com"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.PARTY_EMAIL_ALREADY_EXISTS, response.body?.error?.code)
        val party = partyDao.findById(100L).get()
        assertEquals("Ray", party.firstName)
        assertEquals("ray.sponsible@gmail.com", party.email)
        assertEquals("+237699123456", customerDao.findById(10L).get().whatsappNumber)
    }

    @Test
    fun `email owned by another party on a pending approval mandate - contract stays`() {
        val response = rest.postForEntity(
            "/v1/mandates/3/customer",
            request.copy(email = "taken@example.com"),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.PARTY_EMAIL_ALREADY_EXISTS, response.body?.error?.code)
        assertEquals(MandateStatus.PENDING_APPROVAL, mandateDao.findById(3L).get().status)
        assertEquals(ContractStatus.PENDING_SIGNATURE, contractDao.findById("c-3").get().status)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `empty first name - 400`() {
        val response = rest.postForEntity(
            "/v1/mandates/1/customer",
            request.copy(firstName = ""),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/mandates/1/customer", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals("Ray", partyDao.findById(100L).get().firstName)
    }
}
