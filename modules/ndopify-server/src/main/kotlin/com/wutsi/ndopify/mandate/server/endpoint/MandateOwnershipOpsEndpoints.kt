package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.OverrideOwnershipVerificationRequest
import com.wutsi.ndopify.mandate.server.service.MandateOwnershipOverrideService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The admin's override of a LISTING mandate's owner verdict (#377), off in prod ([OpsEndpoint]): a mandatory reason
 * sets it, an empty call clears it. The global ops interceptor already refuses a non-admin; the local check keeps a
 * non-admin from loading the mandate, whatever the interceptor configuration.
 */
@RestController
@RequestMapping("/v1/mandates")
class MandateOwnershipOpsEndpoints(
    private val service: MandateOwnershipOverrideService,
    private val callerService: CallerService,
) {
    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/ownership/override")
    fun setOverride(
        @PathVariable id: Long,
        @RequestBody @Valid request: OverrideOwnershipVerificationRequest,
    ) {
        service.set(id, request.reason, ensureAdmin().getUserId())
    }

    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/ownership/override/clear")
    fun clearOverride(@PathVariable id: Long) {
        service.clear(id, ensureAdmin().getUserId())
    }

    /** 401 without a token is the security filter's; a non-admin is 403 before the mandate is read. */
    private fun ensureAdmin(): JwtPrincipal {
        val principal = callerService.getPrincipal()
        if (!callerService.isAdmin(principal)) {
            throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
        return principal
    }
}
