package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/KycServiceRecoverInterrupted.sql"])
class KycServiceRecoverInterruptedTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: KycService

    @Autowired
    private lateinit var dao: KycCaseRepository

    @BeforeEach
    fun setUpTenant() {
        TenantContext.set(1L)
    }

    @AfterEach
    fun tearDownTenant() {
        TenantContext.remove()
    }

    private fun case(id: String) = dao.findById(id).orElseThrow()

    @Test
    fun `interrupted case - back to submitted, retry counted`() {
        service.recoverInterrupted()

        val case = case("kyc-case-interrupted")
        assertEquals(KycStatus.SUBMITTED, case.status)
        assertEquals(1, case.retries)
        assertEquals(KycErrorCode.INTERRUPTED, case.errorCode)
        assertEquals("Verification interrupted", case.errorMessage)
    }

    @Test
    fun `interrupted case on its last retry - requires manual review`() {
        service.recoverInterrupted()

        val case = case("kyc-case-interrupted-last-retry")
        assertEquals(KycStatus.REQUIRES_MANUAL_REVIEW, case.status)
        assertEquals(3, case.retries)
        assertEquals(KycErrorCode.INTERRUPTED, case.errorCode)
    }

    @Test
    fun `only the interrupted cases of the tenant are recovered`() {
        val recovered = service.recoverInterrupted()

        assertEquals(
            setOf("kyc-case-interrupted", "kyc-case-interrupted-last-retry"),
            recovered.map { it.id }.toSet()
        )
    }

    @Test
    fun `case in progress for less than the timeout - untouched`() {
        service.recoverInterrupted()

        val case = case("kyc-case-in-progress")
        assertEquals(KycStatus.IN_PROGRESS, case.status)
        assertNull(case.retries)
    }

    @Test
    fun `submitted case - untouched`() {
        service.recoverInterrupted()

        val case = case("kyc-case-submitted")
        assertEquals(KycStatus.SUBMITTED, case.status)
        assertNull(case.retries)
    }

    @Test
    fun `case of another tenant - untouched`() {
        service.recoverInterrupted()

        TenantContext.set(2L)
        val case = case("kyc-case-other-tenant")
        assertEquals(KycStatus.IN_PROGRESS, case.status)
        assertNull(case.retries)
    }
}
