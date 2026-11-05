package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server customer endpoints (`/v1/customers`), called with the customer's `public-portal` token
 * (sent by `RestConfiguration`'s `Authorization` interceptor). ndopify-server answers 401 for a token it rejects, 403
 * for another customer's data, and 404 when the customer is gone. The client lets those exceptions propagate.
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
