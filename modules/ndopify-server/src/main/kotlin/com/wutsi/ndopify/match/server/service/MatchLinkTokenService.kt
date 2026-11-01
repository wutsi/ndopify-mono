package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.server.service.AccessTokenProperties
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.stereotype.Service

/**
 * Issues the token the links of a match proposal e-mail carry (`?token=`), so that the customer opening one is signed
 * in to the public portal as its own party: the party `MatchEndpoints` checks on accept / reject. Gives the customer a
 * membership of the tenant with `public-portal` access first, as `ContractSignerService` does for the e-signature
 * portal. The token lives `ndopify.security.access-token.ttl.match-link`, and is a full `public-portal` token: anyone
 * the e-mail is forwarded to is signed in as the customer until then.
 *
 * No token (null) when the customer has no e-mail, or its address is already a member's as another party: the customer
 * then signs in. Runs in the caller's transaction.
 */
@Service
class MatchLinkTokenService(
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val applicationService: ApplicationService,
    private val userApplicationService: UserApplicationService,
    private val accessTokenService: AccessTokenService,
    private val accessTokenProperties: AccessTokenProperties,
) {
    companion object {
        const val TTL_KEY = "match-link"
    }

    fun create(customer: CustomerEntity): String? {
        val party = customer.party
        val membership = membershipService.findByPartyIdOrNull(party.id!!)
            ?: party.email.ifBlank { null }?.let { email ->
                membershipService.findOrCreate(user = userService.findByEmailOrCreate(email), party = party)
            }
            ?: return null
        if (membership.party?.id != party.id) {
            return null
        }

        membershipService.grantAccess(membership, ApplicationCode.PUBLIC_PORTAL)
        val userApplication = userApplicationService.findByMembershipAndApplicationOrNull(
            membership,
            applicationService.findByCode(ApplicationCode.PUBLIC_PORTAL),
        )
        return accessTokenService.create(
            application = ApplicationCode.PUBLIC_PORTAL,
            userId = membership.user.id!!,
            roles = userApplication?.roles?.map { role -> role.code }?.toTypedArray() ?: emptyArray(),
            tenantId = membership.tenantId,
            ttlSeconds = accessTokenProperties.ttl(TTL_KEY).seconds.toInt(),
            partyId = party.id,
        )
    }
}
