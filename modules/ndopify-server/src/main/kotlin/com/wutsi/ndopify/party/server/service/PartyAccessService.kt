package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/** Who may change a party: only the caller whose party it is (JWT user → membership → party). No admin bypass. */
@Service
class PartyAccessService(private val callerService: CallerService) {
    /** 401 AUTH_UNAUTHORIZED without a token, then 403 HTTP_ACCESS_DENIED when the caller's party isn't [party]. */
    fun ensureIsParty(party: PartyEntity) {
        val principal = callerService.getPrincipal()
        if (party.id == null || callerService.findPartyIdOrNull(principal) != party.id) {
            throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
    }
}
