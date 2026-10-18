package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server customer endpoints (`/v1/customers`): the person behind a mandate.
 * Customers are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant receives it.
 */
@Service
class NdCustomerClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Customer =
        rest.get()
            .uri("/v1/customers/{id}", id)
            .retrieve()
            .body<GetCustomerResponse>()
            ?.customer
            ?: Customer()
}
