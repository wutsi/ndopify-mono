package com.wutsi.ndopify.customer.server.service

import com.wutsi.ndopify.customer.dto.SearchCustomerRequest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

@Service
class CustomerService(
    private val dao: CustomerRepository,
) {
    fun search(request: SearchCustomerRequest): List<CustomerEntity> {
        val spec = Specification<CustomerEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            if (request.partyIds.isNotEmpty()) {
                predicates.add(root.get<PartyEntity>("party").get<Long>("id").`in`(request.partyIds))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    fun findByIdOrNull(id: Long): CustomerEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findById(id: Long): CustomerEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.CUSTOMER_NOT_FOUND))
    }
}
