package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.server.dao.MembershipRepository
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/** Memberships are @TenantId-filtered: everything here is about the current tenant only. */
@Service
class MembershipService(
    private val dao: MembershipRepository,
    private val userApplicationDao: UserApplicationRepository,
    private val applicationService: ApplicationService,
) {
    /** The current tenant, which a user must have to be looked up in it: fails with a 400 otherwise. */
    fun currentTenantId(): Long {
        return TenantContext.get()
            ?: throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = HttpHeader.TENANT_ID, type = ParameterType.PARAMETER_TYPE_HEADER),
                )
            )
    }

    fun findByUserOrNull(user: UserEntity): MembershipEntity? {
        return dao.findByUser(user)?.takeIf { !it.deleted }
    }

    fun findByPartyIdOrNull(partyId: Long): MembershipEntity? {
        return dao.findByPartyId(partyId)?.takeIf { !it.deleted }
    }

    /** Makes the user a member of the current tenant, as the given party if it isn't linked to one yet. */
    @Transactional
    fun findOrCreate(user: UserEntity, party: PartyEntity?): MembershipEntity {
        val membership = dao.findByUser(user)
        return when {
            membership == null -> dao.save(MembershipEntity(user = user, party = party))
            membership.party == null && party != null -> dao.save(membership.copy(party = party))
            else -> membership
        }
    }

    @Transactional
    fun grantAccess(membership: MembershipEntity, applicationCode: String) {
        val application = applicationService.findByCode(applicationCode)
        if (userApplicationDao.findByMembershipAndApplication(membership, application).isEmpty) {
            userApplicationDao.save(
                UserApplicationEntity(
                    membership = membership,
                    application = application,
                )
            )
        }
    }
}
