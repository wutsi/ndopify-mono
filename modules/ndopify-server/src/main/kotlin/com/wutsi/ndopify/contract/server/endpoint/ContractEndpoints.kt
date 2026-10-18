package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.CreateContractResponse
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.mapper.ContractMapper
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/contracts")
class ContractEndpoints(
    private val service: ContractService,
    private val mapper: ContractMapper,
) {
    // Ex: the contracts of a list of mandates: GET /v1/contracts?id=c-1&id=c-2
    @GetMapping
    fun search(@ModelAttribute request: SearchContractRequest): SearchContractResponse {
        val contracts = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchContractResponse(contracts = contracts.map { contract -> mapper.toContractSummary(contract) })
    }

    @PostMapping
    fun create(@RequestBody @Valid request: CreateContractRequest): CreateContractResponse {
        val contract = service.create(request)
        return CreateContractResponse(contractId = contract.id)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): GetContractResponse {
        return toResponse(service.findById(id))
    }

    // The document's URL, while the contract is DRAFT or PENDING_SIGNATURE
    @PostMapping("/{id}/content")
    fun setContent(
        @PathVariable id: String,
        @RequestBody @Valid request: SetContractRequest,
    ) {
        service.setContent(id, request)
    }

    // DRAFT → PENDING_SIGNATURE, and asks each party by e-mail to sign it
    @PostMapping("/{id}/request-signature")
    fun requestSignature(@PathVariable id: String) {
        service.requestSignature(id)
    }

    @PostMapping("/{id}/sign")
    fun sign(
        @PathVariable id: String,
        @RequestBody @Valid request: SignContractRequest,
    ) {
        service.sign(id, request)
    }

    private fun toResponse(contract: ContractEntity): GetContractResponse {
        return GetContractResponse(contract = mapper.toContract(contract, service.findParties(contract)))
    }
}
