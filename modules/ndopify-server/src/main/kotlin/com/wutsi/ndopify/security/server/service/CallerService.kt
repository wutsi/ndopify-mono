package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.stereotype.Service

/**
 * Who calls the API, from the request's access token. Only as trustworthy as the token: they aren't signed yet (see
 * [AccessTokenService]).
 */
@Service
class CallerService(
    private val accessTokenService: AccessTokenService,
    private val userService: UserService,
    private val membershipService: MembershipService,
) {
    /** 401 AUTH_UNAUTHORIZED without a token. */
    fun getPrincipal(): JwtPrincipal {
        return accessTokenService.getPrincipal()
    }

    /** An admin of the current tenant: signed in to the admin console, with its `admin` role. */
    fun isAdmin(principal: JwtPrincipal): Boolean {
        return principal.getApplication() == ApplicationCode.ADMIN_CONSOLE &&
            principal.getRoles().contains(RoleCode.ADMIN)
    }

    /** The caller's party in the current tenant, through its membership. Null if it isn't a member, or has no party. */
    fun findPartyIdOrNull(principal: JwtPrincipal): Long? {
        return userService.findByIdOrNull(principal.getUserId())
            ?.let { user -> membershipService.findByUserOrNull(user) } // Tenant-filtered (@TenantId)
            ?.party?.id
    }
}
