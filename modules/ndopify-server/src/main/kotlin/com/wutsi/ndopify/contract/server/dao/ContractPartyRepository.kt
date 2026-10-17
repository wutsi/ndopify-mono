package com.wutsi.ndopify.contract.server.dao

import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractPartyRepository : CrudRepository<ContractPartyEntity, String> {
    fun findByContract(contract: ContractEntity): List<ContractPartyEntity>
}
