package com.wutsi.ndopify.customer.server.service

import com.wutsi.ndopify.customer.dto.CreateCustomerRequest
import com.wutsi.ndopify.customer.dto.SearchCustomerRequest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreatePartyRequest
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.service.PartyService
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class CustomerService(
    private val dao: CustomerRepository,
    private val partyService: PartyService,
    private val clock: Clock,
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

    fun findByPartyOrNull(party: PartyEntity): CustomerEntity? {
        return dao.findByParty(party)
    }

    @Transactional
    fun findOrCreate(request: CreateCustomerRequest): CustomerEntity {
        val party = partyService.findOrCreate(
            CreatePartyRequest(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
            )
        )

        return findByPartyOrNull(party)
            ?: create(party, request)
    }

    private fun create(party: PartyEntity, request: CreateCustomerRequest): CustomerEntity {
        val now = Date(clock.millis())
        return dao.save(
            CustomerEntity(
                party = party,
                whatsappNumber = request.whatsappNumber,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }
}
