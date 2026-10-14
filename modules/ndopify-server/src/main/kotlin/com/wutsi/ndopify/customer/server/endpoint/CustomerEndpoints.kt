package com.wutsi.ndopify.customer.server.endpoint

import com.wutsi.ndopify.customer.dto.GetCustomerResponse
import com.wutsi.ndopify.customer.dto.SearchCustomerRequest
import com.wutsi.ndopify.customer.dto.SearchCustomerResponse
import com.wutsi.ndopify.customer.server.mapper.CustomerMapper
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/customers")
class CustomerEndpoints(
    private val service: CustomerService,
    private val mapper: CustomerMapper,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): GetCustomerResponse {
        val customer = service.findById(id)
        return GetCustomerResponse(customer = mapper.toCustomer(customer))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchCustomerRequest,
    ): SearchCustomerResponse {
        val customers = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchCustomerResponse(customers = customers.map { customer -> mapper.toCustomerSummary(customer) })
    }
}
