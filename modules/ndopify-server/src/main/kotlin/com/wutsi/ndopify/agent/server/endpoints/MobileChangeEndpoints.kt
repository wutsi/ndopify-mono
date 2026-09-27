package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.agent.dto.GetMobileChangeResponse
import com.wutsi.ndopify.agent.dto.SearchMobileChangeRequest
import com.wutsi.ndopify.agent.dto.SearchMobileChangeResponse
import com.wutsi.ndopify.agent.dto.UpdateMobileChangeRequest
import com.wutsi.ndopify.agent.dto.VerifyMobileChangeResponse
import com.wutsi.ndopify.agent.server.mapper.MobileChangeMapper
import com.wutsi.ndopify.agent.server.service.MobileChangeService
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
@RequestMapping("/v1/mobile-change-requests")
class MobileChangeEndpoints(
    private val service: MobileChangeService,
    private val mapper: MobileChangeMapper,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): GetMobileChangeResponse {
        val change = service.findById(id, tenantId)
        return GetMobileChangeResponse(change = mapper.toMobileChange(change))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchMobileChangeRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): SearchMobileChangeResponse {
        val changes = service.search(request, tenantId)
        return SearchMobileChangeResponse(changes = changes.map { change -> mapper.toMobileChangeSummary(change) })
    }

    /**
     * Modify the mobile change request.
     * This should be call to set perform the manual verification of the mobile change request.
     * The request will be updated with the new status and any relevant information.
     */
    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateMobileChangeRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.update(id, request, tenantId)
    }

    /**
     * Initiate the auto-verification of the mobile change request.
     * The system will attempt to verify the mobile change by sending a request to the MoMo gateway.
     * If the verification fails, the status of the request will be updated accordingly.
     */
    @PostMapping("/{id}/verify")
    fun verify(
        @PathVariable id: Long,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): VerifyMobileChangeResponse {
        val change = service.verify(id, tenantId)
        return VerifyMobileChangeResponse(
            status = change.status,
            errorCode = change.errorCode,
            failureReason = change.failureReason,
        )
    }
}
