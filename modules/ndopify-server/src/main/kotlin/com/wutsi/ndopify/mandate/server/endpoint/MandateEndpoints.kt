package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.DispatchMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.mapper.MandateMapper
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/mandates")
class MandateEndpoints(
    private val service: MandateService,
    private val mapper: MandateMapper,
) {
    @GetMapping
    fun search(@ModelAttribute request: SearchMandateRequest): SearchMandateResponse {
        val mandates = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchMandateResponse(mandates = mandates.map { mandate -> mapper.toMandateSummary(mandate) })
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetMandateResponse {
        val mandate = service.findById(id)
        return GetMandateResponse(mandate = mapper.toMandate(mandate))
    }

    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateMandateRequest,
    ): CreateMandateResponse {
        val mandate = service.create(request)
        return CreateMandateResponse(mandateId = mandate.id ?: -1)
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateMandateRequest,
    ) {
        service.update(id, request)
    }

    // Offers the mandate now. The same rules apply: nothing offered at night, MANUAL_ASSIGNMENT if overdue, nothing if
    // it already has a PENDING offer
    @OpsEndpoint
    @PostMapping("/{id}/dispatch")
    fun dispatch(@PathVariable id: Long): DispatchMandateResponse {
        val assignment = service.dispatch(id)
        return DispatchMandateResponse(assignmentId = assignment?.id)
    }
}
