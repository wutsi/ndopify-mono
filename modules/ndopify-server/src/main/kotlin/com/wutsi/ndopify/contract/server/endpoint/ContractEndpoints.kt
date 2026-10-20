package com.wutsi.ndopify.contract.server.endpoint

import com.wutsi.ndopify.contract.dto.CreateContractRequest
import com.wutsi.ndopify.contract.dto.CreateContractResponse
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.GetContractSignatureResponse
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import com.wutsi.ndopify.contract.dto.SetContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.mapper.ContractMapper
import com.wutsi.ndopify.contract.server.service.ContractAccessService
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.contract.server.service.SignedContractService
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
 * Only an admin or one of its parties may see a contract, only an admin may change it, and only the party itself may
 * sign ([ContractAccessService]), except a signature's verification, which is public.
 */
@RestController
@RequestMapping("/v1/contracts")
class ContractEndpoints(
    private val service: ContractService,
    private val access: ContractAccessService,
    private val mapper: ContractMapper,
    private val signedContractService: SignedContractService,
) {
    // Ex: the contracts of a list of mandates: GET /v1/contracts?id=c-1&id=c-2
    @GetMapping
    fun search(@ModelAttribute request: SearchContractRequest): SearchContractResponse {
        val restricted = access.restrict(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        val contracts = service.search(restricted)
        return SearchContractResponse(contracts = contracts.map { contract -> mapper.toContractSummary(contract) })
    }

    @PostMapping
    fun create(@RequestBody @Valid request: CreateContractRequest): CreateContractResponse {
        access.ensureCanUpdate()
        val contract = service.create(request)
        return CreateContractResponse(contractId = contract.id)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): GetContractResponse {
        val contract = service.findById(id)
        access.ensureCanRead(contract)
        return toResponse(contract)
    }

    // Public, without access check: the hash is the secret printed in the signed PDF's QR code, and only a few facts are returned
    @GetMapping("/{id}/signatures/{hash}")
    fun getSignature(
        @PathVariable id: String,
        @PathVariable hash: String,
    ): GetContractSignatureResponse {
        val (contract, party) = service.findSignature(id, hash)
        return GetContractSignatureResponse(signature = mapper.toContractSignature(contract, party))
    }

    // The document's URL, while the contract is DRAFT or PENDING_SIGNATURE
    @PostMapping("/{id}/content")
    fun setContent(
        @PathVariable id: String,
        @RequestBody @Valid request: SetContractRequest,
    ) {
        access.ensureCanUpdate()
        service.setContent(id, request)
    }

    // DRAFT → PENDING_SIGNATURE, and asks each party by e-mail to sign it
    @PostMapping("/{id}/request-signature")
    fun requestSignature(@PathVariable id: String) {
        access.ensureCanUpdate()
        service.requestSignature(id)
    }

    // Generates the signed PDF of a SIGNED contract that has none (ex: its generation after the last signature failed)
    @OpsEndpoint
    @PostMapping("/{id}/signed-content")
    fun generateSignedContent(@PathVariable id: String) {
        access.ensureCanUpdate()
        signedContractService.generate(id)
    }

    @PostMapping("/{id}/sign")
    fun sign(
        @PathVariable id: String,
        @RequestBody @Valid request: SignContractRequest,
    ) {
        access.ensureCanSign(service.findById(id), request.partyId)
        service.sign(id, request)
    }

    private fun toResponse(contract: ContractEntity): GetContractResponse {
        return GetContractResponse(contract = mapper.toContract(contract, service.findParties(contract)))
    }
}
