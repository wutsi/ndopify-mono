package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.GetContractResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server contract endpoints (`/v1/contracts`): a mandate's contract, signed by its customer.
 * Contracts are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant receives it.
 */
@Service
class NdContractClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

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
}
