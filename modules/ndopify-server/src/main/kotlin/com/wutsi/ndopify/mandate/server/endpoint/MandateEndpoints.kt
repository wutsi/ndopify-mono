package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.DispatchMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateStatsRequest
import com.wutsi.ndopify.mandate.dto.SearchMandateStatsResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.mapper.MandateMapper
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.mandate.server.service.MandateStatsService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Only the mandate's agent or an admin may see or change a mandate ([MandateAccessService]): checked here for the
 * reads, which the server also does for itself, and by [MandateService] for the changes. Creating one is public.
 */
@RestController
@RequestMapping("/v1/mandates")
class MandateEndpoints(
    private val service: MandateService,
    private val access: MandateAccessService,
    private val mapper: MandateMapper,
    private val statsService: MandateStatsService,
) {
    @GetMapping
    fun search(@ModelAttribute request: SearchMandateRequest): SearchMandateResponse {
        val restricted = access.restrict(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchMandateResponse(
            mandates = service.search(restricted).map { mandate -> mapper.toMandateSummary(mandate) }
        )
    }

    @GetMapping("/stats")
    fun stats(@ModelAttribute request: SearchMandateStatsRequest): SearchMandateStatsResponse {
        if (request.mandateIds.isEmpty()) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = "mandate-id", type = ParameterType.PARAMETER_TYPE_QUERY),
                )
            )
        }
        val ids = request.mandateIds.distinct()
        val mandates = service.findAllByIds(ids)
        if (mandates.size != ids.size) {
            throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
        }
        mandates.forEach { access.ensureCanRead(it) }
        return SearchMandateStatsResponse(stats = statsService.search(ids))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetMandateResponse {
        val mandate = service.findById(id)
        access.ensureCanReadAsCustomerOrAgent(mandate) // The customer reads its own mandate too
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

    @PostMapping("/{id}/request-approval")
    fun requestApproval(@PathVariable id: Long) {
        service.requestApproval(id)
    }

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: Long, @RequestBody request: CancelMandateRequest) {
        service.cancel(id, request)
    }

    @PostMapping("/{id}/close")
    fun close(@PathVariable id: Long, @RequestBody request: CloseMandateRequest) {
        service.close(id, request)
    }
}
