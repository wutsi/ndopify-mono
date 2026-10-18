package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.ZoneId
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Signed by the party itself: user 11 is party 100, user 12 party 101. */
@Sql(value = ["/db/test/clean.sql", "/db/test/contract/SignContractEndpoint.sql"])
class SignContractEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ContractRepository

    @Autowired
    private lateinit var partyDao: ContractPartyRepository

    @Autowired
    private lateinit var mandateDao: MandateRepository

    private fun request(partyId: String) = SignContractRequest(
        partyId = partyId,
        ipAddress = "192.168.1.10",
        userAgent = "Mozilla/5.0 (iPhone)",
    )

    @Test
    fun `first signature`() {
        val response = rest.postForEntity("/v1/contracts/c-1/sign", request("cp-11"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val contract = dao.findById("c-1").get()
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
        assertNull(contract.signedAt)
        assertNull(contract.validFrom) // Only from the last signature
        assertNull(contract.validUntil)

        val party = partyDao.findById("cp-11").get()
        assertTrue(party.signed)
        assertEquals("192.168.1.10", party.ipAddress)
        assertEquals("Mozilla/5.0 (iPhone)", party.userAgent)
        assertNotNull(party.signedAt)
        assertFalse(partyDao.findById("cp-12").get().signed)
        assertEquals(MandateStatus.PENDING_APPROVAL, mandateDao.findById(1L).get().status) // Not signed by all yet
    }

    @Test
    fun `last signature signs the contract`() {
        userId = 12L

        val response = rest.postForEntity("/v1/contracts/c-2/sign", request("cp-22"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val contract = dao.findById("c-2").get()
        assertEquals(ContractStatus.SIGNED, contract.status)
        assertNotNull(contract.signedAt)
        assertTrue(partyDao.findByContract(contract).all { party -> party.signed })

        // Valid from the last signature, for its 6 months (in the tenant's time zone)
        assertEquals(contract.signedAt?.time, contract.validFrom?.time)
        val validUntil = contract.validFrom!!.toInstant().atZone(ZoneId.of("Africa/Douala")).plusMonths(6).toInstant()
        assertEquals(Date.from(validUntil).time, contract.validUntil?.time)

        // ContractSignedEvent: its mandate is approved
        assertEquals(MandateStatus.APPROVED, mandateDao.findById(2L).get().status)
    }

    @Test
    fun `mandate no longer waiting for approval`() {
        val response = rest.postForEntity("/v1/contracts/c-6/sign", request("cp-61"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ContractStatus.SIGNED, dao.findById("c-6").get().status)
        assertEquals(MandateStatus.CANCELLED, mandateDao.findById(6L).get().status)
    }

    @Test
    fun `contract without mandate`() {
        val response = rest.postForEntity("/v1/contracts/c-7/sign", request("cp-71"), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ContractStatus.SIGNED, dao.findById("c-7").get().status)
        assertNull(mandateDao.findByContractId("c-7"))
    }

    @Test
    fun `party already signed`() {
        assertConflict("c-2", "cp-21", ErrorCode.CONTRACT_PARTY_ALREADY_SIGNED)
    }

    @Test
    fun `contract already signed`() {
        assertConflict("c-3", "cp-31", ErrorCode.CONTRACT_NOT_PENDING)
    }

    @Test
    fun `draft contract`() {
        assertConflict("c-5", "cp-51", ErrorCode.CONTRACT_NOT_PENDING)
    }

    @Test
    fun `past the signature deadline`() {
        assertConflict("c-4", "cp-41", ErrorCode.CONTRACT_EXPIRED)
    }

    @Test
    fun `party of another contract`() {
        userId = 12L

        val response = rest.postForEntity("/v1/contracts/c-1/sign", request("cp-22"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_PARTY_NOT_FOUND, response.body?.error?.code)
        assertFalse(partyDao.findById("cp-22").get().signed)
    }

    @Test
    fun `contract not found`() {
        val response = rest.postForEntity("/v1/contracts/xxx/sign", request("cp-11"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `contract from another tenant is not found`() {
        val response = rest.postForEntity("/v1/contracts/c-9/sign", request("cp-91"), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
    }

    private fun assertConflict(contractId: String, partyId: String, code: String) {
        val status = dao.findById(contractId).get().status
        val signedAt = partyDao.findById(partyId).get().signedAt

        val response = rest.postForEntity(
            "/v1/contracts/$contractId/sign",
            request(partyId),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(status, dao.findById(contractId).get().status)
        assertEquals(signedAt, partyDao.findById(partyId).get().signedAt)
    }
}
