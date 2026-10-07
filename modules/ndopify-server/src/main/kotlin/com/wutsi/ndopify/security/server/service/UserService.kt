package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class UserService(private val dao: UserRepository) {
    fun findByPartyIdOrNull(partyId: Long): UserEntity? {
        val user = dao.findByPartyId(partyId)
        if (user == null || user.deleted) {
            return null
        }
        return user
    }

    fun findByEmailOrNull(email: String): UserEntity? {
        val user = dao.findByEmailIgnoreCase(email).orElse(null)
        if (user == null || user.deleted) {
            return null
        }
        return user
    }

    @Transactional
    fun findByEmailOrCreate(email: String): UserEntity {
        val user = findByEmailOrNull(email)
        return if (user == null) {
            dao.save(UserEntity(email = email.lowercase()))
        } else {
            user
        }
    }

    @Transactional
    fun create(party: PartyEntity): UserEntity {
        return dao.save(
            UserEntity(
                tenantId = party.tenantId,
                email = party.email.lowercase(),
                party = party
            )
        )
    }
}
