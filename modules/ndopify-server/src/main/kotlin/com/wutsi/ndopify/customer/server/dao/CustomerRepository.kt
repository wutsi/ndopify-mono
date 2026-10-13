package com.wutsi.ndopify.customer.server.dao

import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository

interface CustomerRepository : CrudRepository<CustomerEntity, Long>, JpaSpecificationExecutor<CustomerEntity> {
    fun findByParty(party: PartyEntity): CustomerEntity?
}
