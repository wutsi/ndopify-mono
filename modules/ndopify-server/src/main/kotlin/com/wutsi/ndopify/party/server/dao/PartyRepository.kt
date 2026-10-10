package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PartyRepository : CrudRepository<PartyEntity, Long>, JpaSpecificationExecutor<PartyEntity> {
    fun findByEmail(email: String): PartyEntity?
}
