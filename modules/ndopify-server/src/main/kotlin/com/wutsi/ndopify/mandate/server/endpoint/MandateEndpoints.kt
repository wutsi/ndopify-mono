package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.server.mapper.ContractMapper
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.DispatchMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.mapper.MandateMapper
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
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
    private val contractService: ContractService,
    private val contractMapper: ContractMapper,
    private val listingService: ListingService,
) {
    @GetMapping
    fun search(@ModelAttribute request: SearchMandateRequest): SearchMandateResponse {
        val restricted = access.restrict(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        val mandates = service.search(restricted)
        val contracts = contracts(mandates)
        return SearchMandateResponse(
            mandates = mandates.map { mandate -> mapper.toMandateSummary(mandate, mandate.contractId?.let { contracts[it] }) }
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetMandateResponse {
        val mandate = service.findById(id)
        access.ensureCanRead(mandate)
        return GetMandateResponse(mandate = mapper.toMandate(mandate, listingService.findIdByMandateId(id)))
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

    /**
     * The contracts of the mandates, by id, in one query. Nothing the caller couldn't read: a mandate's agent is a party
     * of its contract, and a mandate only gets one once assigned (so not while it's only offered).
     */
    private fun contracts(mandates: List<MandateEntity>): Map<String, ContractSummary> {
        val ids = mandates.mapNotNull { mandate -> mandate.contractId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return contractService.search(SearchContractRequest(ids = ids, limit = ids.size))
            .associate { contract -> contract.id to contractMapper.toContractSummary(contract) }
    }
}
