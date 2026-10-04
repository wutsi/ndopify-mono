package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface IdentificationRepository : CrudRepository<IdentificationEntity, String> {
    fun findByParty(party: PartyEntity): List<IdentificationEntity>
}
