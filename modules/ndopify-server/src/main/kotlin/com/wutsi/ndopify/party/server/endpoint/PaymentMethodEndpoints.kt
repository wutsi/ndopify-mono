package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.GetPaymentMethodResponse
import com.wutsi.ndopify.party.dto.SearchPaymentMethodRequest
import com.wutsi.ndopify.party.dto.SearchPaymentMethodResponse
import com.wutsi.ndopify.party.server.mapper.PaymentMethodMapper
import com.wutsi.ndopify.party.server.service.PaymentMethodService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/payment-methods")
class PaymentMethodEndpoints(
    private val service: PaymentMethodService,
    private val mapper: PaymentMethodMapper,
) {
    @GetMapping
    fun search(
        @ModelAttribute request: SearchPaymentMethodRequest,
    ): SearchPaymentMethodResponse {
        val paymentMethods = service.search(request)
        return SearchPaymentMethodResponse(
            paymentMethods = paymentMethods.map { paymentMethod -> mapper.toPaymentMethodSummary(paymentMethod) },
        )
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): GetPaymentMethodResponse {
        val paymentMethod = service.findById(id)
        return GetPaymentMethodResponse(paymentMethod = mapper.toPaymentMethod(paymentMethod))
    }
}
