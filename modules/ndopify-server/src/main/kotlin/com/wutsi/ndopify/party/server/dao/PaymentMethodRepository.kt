package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentMethodRepository : CrudRepository<PaymentMethodEntity, String>,
    JpaSpecificationExecutor<PaymentMethodEntity> {
    fun findByParty(party: PartyEntity): List<PaymentMethodEntity>
    fun findByPartyAndTypeAndNumber(party: PartyEntity, type: PaymentMethodType, number: String): PaymentMethodEntity?
}
