package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.agent.dto.GetIdentityChangeResponse
import com.wutsi.ndopify.agent.dto.SearchIdentityChangeRequest
import com.wutsi.ndopify.agent.dto.SearchIdentityChangeResponse
import com.wutsi.ndopify.agent.dto.UpdateIdentityChangeRequest
import com.wutsi.ndopify.agent.dto.VerifyIdentityChangeResponse
import com.wutsi.ndopify.agent.server.mapper.IdentityChangeMapper
import com.wutsi.ndopify.agent.server.service.IdentityChangeService
import com.wutsi.ndopify.common.dto.HttpHeader
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/identity-changes")
class IdentityChangeEndpoints(
    private val service: IdentityChangeService,
    private val mapper: IdentityChangeMapper,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): GetIdentityChangeResponse {
        val change = service.findById(id, tenantId)
        return GetIdentityChangeResponse(change = mapper.toIdentityChange(change))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchIdentityChangeRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): SearchIdentityChangeResponse {
        val changes = service.search(request, tenantId)
        return SearchIdentityChangeResponse(changes = changes.map { change -> mapper.toIdentityChangeSummary(change) })
    }

    /**
     * Modify the mobile change request.
     * This should be call to set perform the manual verification of the mobile change request.
     * The request will be updated with the new status and any relevant information.
     */
    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateIdentityChangeRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.update(id, request, tenantId)
    }

    /**
     * Initiate the auto-verification of the identity change request.
     * The system will attempt to verify the identity change by sending a request to the MoMo gateway.
     * If the verification fails, the status of the request will be updated accordingly.
     */
    @PostMapping("/{id}/verify")
    fun verify(
        @PathVariable id: Long,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): VerifyIdentityChangeResponse {
        val change = service.verify(id, tenantId)
        return VerifyIdentityChangeResponse(
            status = change.status,
            errorCode = change.errorCode,
            failureReason = change.failureReason,
        )
    }
}
