package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.dto.SearchPaymentMethodRequest
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may read or create a party's KYC data, identifications and payment methods: the party itself (JWT user →
 * membership → party) or an admin ([CallerService.isAdmin]). The image writes stay the party's alone
 * ([ensureIsParty], no admin bypass: a KYC document is personal). 401 AUTH_UNAUTHORIZED without a token; 403
 * HTTP_ACCESS_DENIED otherwise.
 */
@Service
class KycAccessService(private val callerService: CallerService) {
    /**
     * 403 HTTP_ACCESS_DENIED unless the caller's party is [partyId]. No admin bypass: used by the image writes, whose
     * rule is the case's own party's only.
     */
    fun ensureIsParty(partyId: Long?) {
        val principal = callerService.getPrincipal()
        if (partyId == null || callerService.findPartyIdOrNull(principal) != partyId) {
            throw accessDenied()
        }
    }

    /** Return for an admin, else [ensureIsParty]: who may read, submit or create a party's KYC data. */
    fun ensureIsPartyOrAdmin(partyId: Long?) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }
        if (partyId == null || callerService.findPartyIdOrNull(principal) != partyId) {
            throw accessDenied()
        }
    }

    /** The case's own party or an admin. */
    fun ensureCanRead(case: KycCaseEntity) {
        ensureIsPartyOrAdmin(case.party.id)
    }

    /** The image's case's own party or an admin. */
    fun ensureCanRead(image: KycImageEntity) {
        ensureCanRead(image.kycCase)
    }

    /** The identification's own party or an admin. */
    fun ensureCanRead(identification: IdentificationEntity) {
        ensureIsPartyOrAdmin(identification.party.id)
    }

    /** The payment method's own party or an admin. */
    fun ensureCanRead(paymentMethod: PaymentMethodEntity) {
        ensureIsPartyOrAdmin(paymentMethod.party.id)
    }

    /**
     * The search an admin may run as asked, a non-admin only on its own party: the request's `partyId` is replaced by
     * the caller's, never rejected. A caller without a party is 403 HTTP_ACCESS_DENIED.
     */
    fun restrict(request: SearchKycCaseRequest): SearchKycCaseRequest {
        val partyId = callerPartyIdOrNull() ?: return request
        return request.copy(partyId = partyId)
    }

    /** A non-admin's search is forced to its own party, whatever partyId it passes. */
    fun restrict(request: SearchIdentificationRequest): SearchIdentificationRequest {
        val partyId = callerPartyIdOrNull() ?: return request
        return request.copy(partyId = partyId)
    }

    /** A non-admin's search is forced to its own party, whatever partyId it passes. */
    fun restrict(request: SearchPaymentMethodRequest): SearchPaymentMethodRequest {
        val partyId = callerPartyIdOrNull() ?: return request
        return request.copy(partyId = partyId)
    }

    /** Null for an admin (the search isn't restricted), else the caller's party; 403 HTTP_ACCESS_DENIED without one. */
    private fun callerPartyIdOrNull(): Long? {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return null
        }
        return callerService.findPartyIdOrNull(principal)
            ?: throw accessDenied()
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
