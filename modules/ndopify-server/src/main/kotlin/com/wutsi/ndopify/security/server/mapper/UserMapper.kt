package com.wutsi.ndopify.security.server.mapper

import com.wutsi.ndopify.party.server.mapper.PartyMapper
import com.wutsi.ndopify.security.dto.User
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.stereotype.Service

@Service
class UserMapper(private val partyMapper: PartyMapper) {
    /** [membership] is the user's membership of the current tenant, which gives its party there. */
    fun toUser(entity: UserEntity, membership: MembershipEntity?): User {
        return User(
            id = entity.id ?: -1,
            email = entity.email,
            party = membership?.party?.let { party -> partyMapper.toParty(party) },
            createdAt = entity.createdAt,
        )
    }
}
