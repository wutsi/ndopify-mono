package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.refdata.server.dao.ApplicationRepository
import com.wutsi.ndopify.security.server.dao.MembershipRepository
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/contract/RequestContractSignatureEndpoint.sql"])
class RequestContractSignatureEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: ContractRepository

    @Autowired
    private lateinit var userDao: UserRepository

    @Autowired
    private lateinit var membershipDao: MembershipRepository

    @Autowired
    private lateinit var applicationDao: ApplicationRepository

    @Autowired
    private lateinit var userApplicationDao: UserApplicationRepository

    @Test
    fun `request signature`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-1/request-signature",
            null,
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val contract = dao.findById("c-1").get()
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
        assertDeadlineIn7Days(contract.signatureDeadline!!.time)

        // Each party is asked to sign, asynchronously
        assertTrue(smtp.waitForIncomingEmail(5000, 2), "no email received")
        val messages = smtp.receivedMessages.sortedBy { message -> message.allRecipients.single().toString() }
        assertEquals(2, messages.size)
        assertEquals(
            listOf("John Smith <john.smith@gmail.com>", "Ray Sponsible <ray.sponsible@gmail.com>"),
            messages.map { message -> message.allRecipients.single().toString() },
        )
        messages.forEach { message ->
            assertEquals("Ndopify: Votre contrat MNDT-2024-000001 est prêt à être signé", message.subject)
        }

        val body = messages[1].content.toString()
        assertTrue(body.contains("Ray Sponsible"))
        assertTrue(body.contains("Mandat de recherche"))
        assertFalse(body.contains("https://cdn.ndopify.com/contracts/c-1.pdf")) // Only reached through the sign page
        val deadline = contract.signatureDeadline!!.toInstant().atZone(ZoneId.of("Africa/Douala"))
        // The fixture leaves the tenant's date_format to the T_TENANT default
        assertTrue(body.contains(deadline.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
        assertTrue(body.contains("https://sign.ndopify.com/sign/cp-11"))
        assertTrue(messages[0].content.toString().contains("https://sign.ndopify.com/sign/cp-12"))
    }

    @Test
    fun `request signature again resets the deadline`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-2/request-signature",
            null,
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val contract = dao.findById("c-2").get()
        assertEquals(ContractStatus.PENDING_SIGNATURE, contract.status)
        assertDeadlineIn7Days(contract.signatureDeadline!!.time) // Was past

        // Only the party that hasn't signed yet is asked again
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertFalse(smtp.waitForIncomingEmail(1000, 2), "only one email expected")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Votre contrat MNDT-2024-000002 est prêt à être signé", message.subject)
        assertTrue(message.content.toString().contains("https://sign.ndopify.com/sign/cp-21"))
    }

    @Test
    fun `parties that don't sign are not asked to`() {
        val response = rest.postForEntity(
            "/v1/contracts/c-3/request-signature",
            null,
            Any::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ContractStatus.PENDING_SIGNATURE, dao.findById("c-3").get().status)

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertFalse(smtp.waitForIncomingEmail(1000, 2), "only one email expected")
        assertEquals(
            "Ray Sponsible <ray.sponsible@gmail.com>",
            smtp.receivedMessages.single().allRecipients.single().toString(),
        )
    }

    @Test
    fun `signer gets e-signature access`() {
        val response = rest.postForEntity("/v1/contracts/c-1/request-signature", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val user = userDao.findByEmailIgnoreCase("ray.sponsible@gmail.com").get()
        val membership = membershipDao.findByUser(user)!!
        assertEquals(100L, membership.party?.id)
        assertEquals(1, eSignatureAccesses(membership))
    }

    @Test
    fun `party not asked to sign gets nothing`() {
        val response = rest.postForEntity("/v1/contracts/c-3/request-signature", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(membershipDao.findByPartyId(100L))
        assertNull(membershipDao.findByPartyId(101L))
        assertFalse(userDao.findByEmailIgnoreCase("john.smith@gmail.com").isPresent)
    }

    @Test
    fun `existing membership is reused`() {
        val usersBefore = userDao.count()
        val membershipsBefore = membershipDao.count()

        val response = rest.postForEntity("/v1/contracts/c-7/request-signature", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(usersBefore, userDao.count())
        assertEquals(membershipsBefore, membershipDao.count())
        val membership = membershipDao.findByPartyId(102L)!!
        assertEquals(1, access(membership, ApplicationCode.PARTNER_CENTRAL))
        assertEquals(1, eSignatureAccesses(membership))
    }

    @Test
    fun `second request creates nothing`() {
        rest.postForEntity("/v1/contracts/c-1/request-signature", null, Any::class.java)
        val users = userDao.count()
        val memberships = membershipDao.count()

        val response = rest.postForEntity("/v1/contracts/c-1/request-signature", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(users, userDao.count())
        assertEquals(memberships, membershipDao.count())
        assertEquals(1, eSignatureAccesses(membershipDao.findByPartyId(100L)!!))
    }

    @Test
    fun `email of another party's member`() {
        val response = rest.postForEntity("/v1/contracts/c-8/request-signature", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT, response.body?.error?.code)
        assertEquals(ContractStatus.DRAFT, dao.findById("c-8").get().status)
        assertNull(membershipDao.findByPartyId(103L))
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    @Test
    fun `contract expired`() {
        assertConflict("c-4", ErrorCode.CONTRACT_EXPIRED, ContractStatus.EXPIRED)
    }

    @Test
    fun `contract cancelled`() {
        assertConflict("c-5", ErrorCode.CONTRACT_CANCELLED, ContractStatus.CANCELLED)
    }

    @Test
    fun `contract already signed`() {
        assertConflict("c-6", ErrorCode.CONTRACT_ALREADY_SIGNED, ContractStatus.SIGNED)
    }

    @Test
    fun `contract not found`() {
        assertNotFound("xxx")
    }

    @Test
    fun `contract from another tenant is not found`() {
        assertNotFound("c-9")
    }

    private fun eSignatureAccesses(membership: MembershipEntity) =
        access(membership, ApplicationCode.E_SIGNATURE)

    private fun access(
        membership: MembershipEntity,
        code: String,
    ): Int {
        val application = applicationDao.findByCode(code).get()
        return userApplicationDao.findByMembershipAndApplication(membership, application).stream().count().toInt()
    }

    // ndopify.contract.signature.ttl-days
    private fun assertDeadlineIn7Days(deadline: Long) {
        val expected = System.currentTimeMillis() + Duration.ofDays(7).toMillis()
        assertTrue(Math.abs(expected - deadline) < Duration.ofMinutes(1).toMillis(), "deadline: $deadline")
    }

    private fun assertConflict(id: String, code: String, status: ContractStatus) {
        val response = rest.postForEntity("/v1/contracts/$id/request-signature", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(status, dao.findById(id).get().status)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    private fun assertNotFound(id: String) {
        val response = rest.postForEntity("/v1/contracts/$id/request-signature", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.CONTRACT_NOT_FOUND, response.body?.error?.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }
}
