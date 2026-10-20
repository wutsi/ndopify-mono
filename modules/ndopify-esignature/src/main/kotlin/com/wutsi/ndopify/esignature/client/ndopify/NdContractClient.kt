package com.wutsi.ndopify.esignature.client.ndopify

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractSignature
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.contract.dto.GetContractResponse
import com.wutsi.ndopify.contract.dto.GetContractSignatureResponse
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SearchContractResponse
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server contract endpoints (`/v1/contracts`): a contract and its signature by a contract party.
 * Contracts are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant receives it.
 */
@Service
class NdContractClient(
    @param:Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: String): Contract =
        rest.get()
            .uri("/v1/contracts/{id}", id)
            .retrieve()
            .body<GetContractResponse>()
            ?.contract
            ?: Contract()

    /** Newest first. Query parameter names match the `@BindParam` aliases on [SearchContractRequest]. */
    fun search(request: SearchContractRequest): List<ContractSummary> =
        rest.get()
            .uri { uri ->
                request.ids.forEach { uri.queryParam("id", it) }
                request.statuses.forEach { uri.queryParam("status", it) }
                request.partyIds.forEach { uri.queryParam("party-id", it) }
                request.contractPartyIds.forEach { uri.queryParam("contract-party-id", it) }
                uri.path("/v1/contracts")
                    .queryParam("limit", request.limit)
                    .queryParam("offset", request.offset)
                    .build()
            }
            .retrieve()
            .body<SearchContractResponse>()
            ?.contracts
            ?: emptyList()

    /** Null when ndopify-server answers 404 `CONTRACT_SIGNATURE_NOT_FOUND` for [hash]; any other error, incl. another 404, is thrown. */
    fun getSignature(id: String, hash: String): ContractSignature? =
        try {
            rest.get()
                .uri("/v1/contracts/{id}/signatures/{hash}", id, hash)
                .retrieve()
                .body<GetContractSignatureResponse>()
                ?.signature
        } catch (ex: HttpClientErrorException.NotFound) {
            // Only ndopify-server's own answer is "invalid": an unknown route, a proxy's 404 or a 404 without a
            // readable body must never read as an invalid signature.
            if (errorCode(ex) == ErrorCode.CONTRACT_SIGNATURE_NOT_FOUND) null else throw ex
        }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    /** [SignContractRequest.partyId] is a *contract party* id; ndopify-server checks it is the caller's. */
    fun sign(id: String, request: SignContractRequest) {
        rest.post()
            .uri("/v1/contracts/{id}/sign", id)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
