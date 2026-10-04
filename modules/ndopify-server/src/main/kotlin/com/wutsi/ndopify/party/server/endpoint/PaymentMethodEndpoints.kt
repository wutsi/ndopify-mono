package com.wutsi.ndopify.party.server.endpoint

import com.wutsi.ndopify.party.dto.GetPaymentMethodResponse
import com.wutsi.ndopify.party.dto.SearchPaymentMethodResponse
import com.wutsi.ndopify.party.server.mapper.PaymentMethodMapper
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.party.server.service.PaymentMethodService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1")
class PaymentMethodEndpoints(
    private val service: PaymentMethodService,
    private val partyService: PartyService,
    private val mapper: PaymentMethodMapper,
) {
    @GetMapping("/parties/{id}/payment-methods")
    fun getByParty(
        @PathVariable id: Long,
    ): SearchPaymentMethodResponse {
        val party = partyService.findById(id)
        val paymentMethods = service.findByParty(party)
        return SearchPaymentMethodResponse(
            paymentMethods = paymentMethods.map { paymentMethod -> mapper.toPaymentMethodSummary(paymentMethod) },
        )
    }

    @GetMapping("/payment-methods/{id}")
    fun get(
        @PathVariable id: String,
    ): GetPaymentMethodResponse {
        val paymentMethod = service.findById(id)
        return GetPaymentMethodResponse(paymentMethod = mapper.toPaymentMethod(paymentMethod))
    }
}
