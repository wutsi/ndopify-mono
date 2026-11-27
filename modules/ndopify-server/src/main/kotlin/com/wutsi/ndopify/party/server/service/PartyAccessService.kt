package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may read or change a party: [ensureIsParty] for a write (the caller's own party, no admin bypass), and
 * [ensureCanSearch] for the search (an admin only).
 */
@Service
class PartyAccessService(private val callerService: CallerService) {
    /** 401 AUTH_UNAUTHORIZED without a token, then 403 HTTP_ACCESS_DENIED when the caller's party isn't [party]. */
    fun ensureIsParty(party: PartyEntity) {
        val principal = callerService.getPrincipal()
        if (party.id == null || callerService.findPartyIdOrNull(principal) != party.id) {
            throw accessDenied()
        }
    }

    /** 401 AUTH_UNAUTHORIZED without a token, then 403 HTTP_ACCESS_DENIED unless the caller is an admin. */
    fun ensureCanSearch() {
        val principal = callerService.getPrincipal()
        if (!callerService.isAdmin(principal)) {
            throw accessDenied()
        }
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
