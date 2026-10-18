package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may see or change a contract, which holds its parties' legal data: an admin (see [CallerService.isAdmin]) or
 * one of its parties may see it, only an admin may change it, and only the party itself may sign, never an admin on
 * its behalf. 401 without a token, 403 CONTRACT_ACCESS_DENIED otherwise. Only for the API's callers: the server's own
 * calls (MandateService, jobs) aren't checked. Checked by the endpoints, without the contract's lock: its parties
 * never change.
 */
@Service
class ContractAccessService(
    private val callerService: CallerService,
    private val contractService: ContractService,
) {
    fun ensureCanRead(contract: ContractEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val partyId = callerService.findPartyIdOrNull(principal)
        if (partyId == null || contractService.findParties(contract).none { party -> party.partyId == partyId }) {
            throw accessDenied()
        }
    }

    fun ensureCanUpdate() {
        if (!callerService.isAdmin(callerService.getPrincipal())) {
            throw accessDenied()
        }
    }

    /**
     * The caller's party must be the contract party's (`contractPartyId`): no admin bypass, a signature is personal. A
     * contract party the contract doesn't have is left to [ContractService.sign] (404 CONTRACT_PARTY_NOT_FOUND).
     */
    fun ensureCanSign(contract: ContractEntity, contractPartyId: String) {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        val party = contractService.findParties(contract).find { party -> party.id == contractPartyId }
        if (party != null && (partyId == null || party.partyId != partyId)) {
            throw accessDenied()
        }
    }

    /**
     * The search the caller may run: an admin's as is, anyone else's restricted to the contracts its party is one of
     * the parties of (403 if it asks for another party's).
     */
    fun restrict(request: SearchContractRequest): SearchContractRequest {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return request
        }

        val partyId = callerService.findPartyIdOrNull(principal) ?: throw accessDenied()
        if (request.partyIds.any { id -> id != partyId }) {
            throw accessDenied()
        }
        return request.copy(partyIds = listOf(partyId))
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.CONTRACT_ACCESS_DENIED))
}
