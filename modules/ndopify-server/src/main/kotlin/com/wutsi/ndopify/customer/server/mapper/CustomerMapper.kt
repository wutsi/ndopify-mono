package com.wutsi.ndopify.customer.server.mapper

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.party.server.mapper.PartyMapper
import org.springframework.stereotype.Service

@Service
class CustomerMapper(private val partyMapper: PartyMapper) {
    fun toCustomer(entity: CustomerEntity): Customer {
        return Customer(
            id = entity.id ?: -1,
            party = partyMapper.toParty(entity.party),
            whatsappNumber = entity.whatsappNumber,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toCustomerSummary(entity: CustomerEntity): CustomerSummary {
        return CustomerSummary(
            id = entity.id ?: -1,
            party = partyMapper.toParty(entity.party),
            whatsappNumber = entity.whatsappNumber,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }
}
