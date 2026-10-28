package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingImageRequest
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may read or change a listing. The reads are public (`PublicEndpoints.TENANT`), with or without a token, for
 * the PUBLISHED listings and their VERIFIED images. The others aren't checked yet, and are the agent's work in
 * progress: only an admin (see [CallerService.isAdmin]) or the listing's agent may read them. 404 LISTING_NOT_FOUND
 * for anybody else, as if it didn't exist, rather than a 403 that would tell it does. Only they may change it: 401
 * without a token, 403 HTTP_ACCESS_DENIED otherwise. Only for the API's callers: the server's own reads and changes
 * (jobs, listeners) aren't checked.
 */
@Service
class ListingAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
) {
    fun ensureCanRead(listing: ListingEntity) {
        if (listing.status != ListingStatus.PUBLISHED && !canReadAll(listing)) {
            throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        }
    }

    /** The images the caller may see: an admin's or the listing's agent's as is, anybody else's VERIFIED only. */
    fun restrict(listing: ListingEntity, request: SearchListingImageRequest): SearchListingImageRequest {
        return if (canReadAll(listing)) request else request.copy(statuses = listOf(ImageStatus.VERIFIED))
    }

    /**
     * The search the caller may run: an admin's as is, an agent's own listings (`agent-id`, its own id) as is,
     * anything else restricted to PUBLISHED, whatever statuses it asks for.
     */
    fun restrict(request: SearchListingRequest): SearchListingRequest {
        val principal = callerService.getPrincipalOrNull()
        if (principal != null) {
            if (callerService.isAdmin(principal)) {
                return request
            }
            if (request.agentId != null && request.agentId == findAgentId(principal)) {
                return request
            }
        }
        return request.copy(statuses = listOf(ListingStatus.PUBLISHED))
    }

    /** Creating the listing of a mandate: an admin, or the mandate's agent. */
    fun ensureCanCreate(mandate: MandateEntity) {
        ensureCanChange(mandate.agentId)
    }

    /** Updating, sending for verification or publication, adding or removing a photo: an admin, or its agent. */
    fun ensureCanUpdate(listing: ListingEntity) {
        ensureCanChange(listing.agent.id)
    }

    private fun ensureCanChange(agentId: Long?) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }
        if (agentId == null || findAgentId(principal) != agentId) {
            throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
    }

    private fun canReadAll(listing: ListingEntity): Boolean {
        val principal = callerService.getPrincipalOrNull() ?: return false
        return callerService.isAdmin(principal) || listing.agent.id == findAgentId(principal)
    }

    private fun findAgentId(principal: JwtPrincipal): Long? {
        return callerService.findPartyIdOrNull(principal)
            ?.let { partyId -> agentService.findByPartyIdOrNull(partyId) }
            ?.id
    }
}
