package com.wutsi.ndopify.party.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.dto.SearchPaymentMethodRequest
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class KycAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = KycAccessService(callerService)

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may read any party`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureIsPartyOrAdmin(999L)
    }

    @Test
    fun `party may read itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureIsPartyOrAdmin(100L)
    }

    @Test
    fun `party may not read another party`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertDenied { service.ensureIsPartyOrAdmin(200L) }
    }

    @Test
    fun `party without a party is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureIsPartyOrAdmin(100L) }
    }

    @Test
    fun `admin may not write an image of another party`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertDenied { service.ensureIsParty(200L) }
    }

    @Test
    fun `search is forced to the caller party`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertEquals(100L, service.restrict(SearchKycCaseRequest(partyId = 999L)).partyId)
    }

    @Test
    fun `search of a party-less caller is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.restrict(SearchKycCaseRequest()) }
    }

    @Test
    fun `admin search is not restricted`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        val request = SearchKycCaseRequest(partyId = 999L)
        assertSame(request, service.restrict(request))
    }

    @Test
    fun `party reads its own identification`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureCanRead(IdentificationEntity(id = "id-1", party = PartyEntity(id = 100)))
    }

    @Test
    fun `another party may not read the identification`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertDenied { service.ensureCanRead(IdentificationEntity(id = "id-1", party = PartyEntity(id = 200))) }
    }

    @Test
    fun `admin reads another party's payment method`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanRead(PaymentMethodEntity(id = "pm-1", party = PartyEntity(id = 200)))
    }

    @Test
    fun `identification search is forced to the caller party`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertEquals(100L, service.restrict(SearchIdentificationRequest(partyId = 999L)).partyId)
    }

    @Test
    fun `payment method search of a party-less caller is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.restrict(SearchPaymentMethodRequest()) }
    }

    @Test
    fun `admin identification search is not restricted`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        val request = SearchIdentificationRequest(partyId = 999L)
        assertSame(request, service.restrict(request))
    }
}
