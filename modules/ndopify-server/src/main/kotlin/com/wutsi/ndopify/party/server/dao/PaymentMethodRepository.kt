package com.wutsi.ndopify.party.dao

import com.wutsi.ndopify.party.domain.PaymentMethodEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentMethodRepository : CrudRepository<PaymentMethodEntity, Long>,
    JpaSpecificationExecutor<PaymentMethodEntity> {
    fun findByHash(hash: String): PaymentMethodEntity?
}
