package com.wutsi.ndopify.party.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dao.PartyRepository
import com.wutsi.ndopify.party.domain.PartyEntity
import com.wutsi.ndopify.party.dto.CreatePartyRequest
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.refdata.dto.KycStatus
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class PartyService(
    private val dao: PartyRepository,
    private val clock: Clock,
) {
    fun findById(id: Long): PartyEntity {
        return dao.findById(id).orElseThrow {
            NotFoundException(
                error = Error(
                    code = ErrorCode.PARTY_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        }
    }

    fun findByIdOrNull(id: Long): PartyEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findByEmailOrNull(email: String): PartyEntity? {
        return dao.findByEmail(email.lowercase())
    }

    @Transactional
    fun create(request: CreatePartyRequest): PartyEntity {
        ensureEmailUnique(request.email.lowercase())

        val now = Date(clock.millis())
        return dao.save(
            PartyEntity(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email.lowercase(),
                kycStatus = KycStatus.PENDING,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    @Transactional
    fun update(party: PartyEntity, request: UpdatePartyRequest): PartyEntity {
        val email = request.email?.lowercase()
        if (email != null) {
            ensureEmailUnique(email, party.id)
        }

        val now = Date(clock.millis())
        return dao.save(
            party.copy(
                firstName = request.firstName ?: party.firstName,
                lastName = request.lastName ?: party.lastName,
                email = request.email?.lowercase() ?: party.email,
                modifiedAt = now,
            )
        )
    }

    private fun ensureEmailUnique(email: String, partyId: Long? = null) {
        val existingParty = findByEmailOrNull(email)
        if (existingParty != null && existingParty.id != partyId) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.PARTY_EMAIL_ALREADY_EXISTS,
                    parameter = Parameter(value = email)
                )
            )
        }
    }
}
