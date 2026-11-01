package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import com.wutsi.ndopify.customer.dto.SearchCustomerResponse
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

    /** The customers [ids], in one call (none without [ids]). */
    fun search(ids: List<Long>): List<CustomerSummary> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return rest.get()
            .uri { uri ->
                ids.forEach { uri.queryParam("id", it) }
                uri.path("/v1/customers")
                    .queryParam("limit", ids.size)
                    .build()
            }
            .retrieve()
            .body<SearchCustomerResponse>()
            ?.customers
            ?: emptyList()
    }
}
