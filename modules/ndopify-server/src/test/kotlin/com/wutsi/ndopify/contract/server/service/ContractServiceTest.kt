package com.wutsi.ndopify.contract.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractType
import com.wutsi.ndopify.contract.dto.CreateContractParty
import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.server.dao.ContractPartyRepository
import com.wutsi.ndopify.contract.server.dao.ContractRepository
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.mail.ContractSignatureMailet
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The terms set at send: the duration bound, the fee's currency and the contracts that can no longer change. */
class ContractServiceTest {
    private val dao = mock<ContractRepository>()
    private val tenantService = mock<TenantService>()
    private val partyService = mock<PartyService>()
    private val numberGenerator = mock<ContractNumberGenerator>()
    private val service = ContractService(
        dao = dao,
        partyDao = mock<ContractPartyRepository>(),
        mandateDao = mock<MandateRepository>(),
        agentService = mock<AgentService>(),
        partyService = partyService,
        signerService = mock<ContractSignerService>(),
        tenantService = tenantService,
        numberGenerator = numberGenerator,
        signatureMailet = mock<ContractSignatureMailet>(),
        eventPublisher = mock<ApplicationEventPublisher>(),
        clock = mock<Clock>(),
        tx = mock<TransactionTemplate>(),
        signatureTtlDays = 7,
    )

    @BeforeTest
    fun setUp() {
        TenantContext.set(1L)
        doReturn(TenantEntity(id = 1L, currencyCode = "XAF")).whenever(tenantService).findById(1L)
        // create(): the saved contract is the one read back
        doAnswer { invocation -> invocation.arguments[0] }.whenever(dao).save(any())
        doReturn("MNDT-2026-000001").whenever(numberGenerator).generate(any(), any(), any(), any())
        doReturn(mock<PartyEntity>()).whenever(partyService).findById(any())
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `create - a sale contract keeps its commission`() {
        val contract = service.create(request(ContractType.SALE_LISTING, commissionRate = 7))

        assertEquals(7, contract.commissionRate)
    }

    @Test
    fun `create - a sale contract needs a commission`() {
        val exception = assertFailsWith<BadRequestException> {
            service.create(request(ContractType.SALE_SEARCH, commissionRate = null))
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("commissionRate", exception.error.parameter?.name)
    }

    @Test
    fun `create - an out-of-range commission is 400`() {
        for (rate in listOf(0, 21)) {
            assertEquals("commissionRate", assertFailsWith<BadRequestException> {
                service.create(request(ContractType.SALE_SEARCH, commissionRate = rate))
            }.error.parameter?.name)
        }
        verify(dao, never()).save(any())
    }

    @Test
    fun `create - a rental ignores the commission`() {
        val contract = service.create(request(ContractType.SEARCH, commissionRate = 7))

        assertNull(contract.commissionRate)
    }

    @Test
    fun `set terms - a sale's commission, a rental's ignored`() {
        doReturn(ContractEntity(id = "c-1", status = ContractStatus.DRAFT, type = ContractType.SALE_SEARCH))
            .whenever(dao).findByIdForUpdate("c-1")
        assertEquals(7, service.setTerms("c-1", commissionRate = 7).commissionRate)

        doReturn(ContractEntity(id = "c-2", status = ContractStatus.DRAFT, type = ContractType.SEARCH))
            .whenever(dao).findByIdForUpdate("c-2")
        assertNull(service.setTerms("c-2", commissionRate = 7).commissionRate)
    }

    @Test
    fun `set terms - an out-of-range sale commission is 400`() {
        doReturn(ContractEntity(id = "c-1", status = ContractStatus.DRAFT, type = ContractType.SALE_LISTING))
            .whenever(dao).findByIdForUpdate("c-1")

        assertEquals("commissionRate", assertFailsWith<BadRequestException> {
            service.setTerms("c-1", commissionRate = 0)
        }.error.parameter?.name)
        assertEquals("commissionRate", assertFailsWith<BadRequestException> {
            service.setTerms("c-1", commissionRate = 21)
        }.error.parameter?.name)
    }

    private fun request(type: ContractType, commissionRate: Int?) = CreateContractRequest(
        type = type,
        durationMonths = 6,
        commissionRate = commissionRate,
        parties = listOf(
            CreateContractParty(
                partyId = 100L,
                legalName = "Ray Sponsible",
                legalEmail = "ray.sponsible@gmail.com",
                role = ContractPartyRole.MANDATOR,
            ),
        ),
    )

    @Test
    fun `set terms - applies the duration and the fee`() {
        val contract = ContractEntity(id = "c-1", status = ContractStatus.DRAFT, durationMonths = 6)
        doReturn(contract).whenever(dao).findByIdForUpdate("c-1")
        doReturn(contract.copy(durationMonths = 3, visitFeeAmount = 5000, visitFeeCurrency = "XAF"))
            .whenever(dao).save(any())

        val updated = service.setTerms("c-1", durationMonths = 3, visitFee = Money(5000, "XAF"))

        assertEquals(3, updated.durationMonths)
        assertEquals(5000, updated.visitFeeAmount)
        assertEquals("XAF", updated.visitFeeCurrency)
    }

    @Test
    fun `set terms - a blank fee currency is the tenant's and nulls keep the current values`() {
        val contract = ContractEntity(id = "c-1", status = ContractStatus.DRAFT, durationMonths = 6)
        doReturn(contract).whenever(dao).findByIdForUpdate("c-1")
        doReturn(contract.copy(visitFeeAmount = 5000, visitFeeCurrency = "XAF")).whenever(dao).save(any())

        val updated = service.setTerms("c-1", visitFee = Money(5000, ""))

        assertEquals(6, updated.durationMonths)
        assertEquals(5000, updated.visitFeeAmount)
        assertEquals("XAF", updated.visitFeeCurrency)
    }

    @Test
    fun `set terms - refuses an out-of-range duration, a negative fee or a foreign currency`() {
        doReturn(ContractEntity(id = "c-1", status = ContractStatus.DRAFT, durationMonths = 6))
            .whenever(dao).findByIdForUpdate("c-1")

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, assertFailsWith<BadRequestException> {
            service.setTerms("c-1", durationMonths = 0)
        }.error.code)
        assertEquals("durationMonths", assertFailsWith<BadRequestException> {
            service.setTerms("c-1", durationMonths = 37)
        }.error.parameter?.name)
        assertEquals("visitFee.amount", assertFailsWith<BadRequestException> {
            service.setTerms("c-1", visitFee = Money(-1, "XAF"))
        }.error.parameter?.name)
        assertEquals("visitFee.currency", assertFailsWith<BadRequestException> {
            service.setTerms("c-1", visitFee = Money(1, "EUR"))
        }.error.parameter?.name)
        verify(dao, never()).save(any())
    }

    @Test
    fun `set terms - a signed, cancelled or expired contract is locked`() {
        for (status in listOf(ContractStatus.SIGNED, ContractStatus.CANCELLED, ContractStatus.EXPIRED)) {
            doReturn(ContractEntity(id = "c-1", status = status)).whenever(dao).findByIdForUpdate("c-1")

            assertFailsWith<ConflictException> { service.setTerms("c-1", durationMonths = 3) }
        }
        verify(dao, never()).save(any())
    }
}
