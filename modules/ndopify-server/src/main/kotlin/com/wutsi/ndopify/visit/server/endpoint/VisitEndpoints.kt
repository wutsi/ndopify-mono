package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import com.wutsi.ndopify.visit.server.mapper.VisitMapper
import com.wutsi.ndopify.visit.server.service.VisitAccessService
import com.wutsi.ndopify.visit.server.service.VisitService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The customer requests a visit of a match it accepted, its agent schedules it, checks it in with the customer's code
 * and out afterwards. Who may do what is checked by [VisitAccessService]: the mandate's customer requests, gives its
 * feedback and cancels as itself; the visit's agent schedules (an admin too), checks in, marks a no-show and checks out
 * (never an admin). The visit (or the match, when requested) is looked up first: 404, then 403.
 */
@RestController
@RequestMapping("/v1/visits")
class VisitEndpoints(
    private val service: VisitService,
    private val access: VisitAccessService,
    private val mapper: VisitMapper,
    private val matchService: MatchService,
    private val mandateService: MandateService,
    private val mandateAccess: MandateAccessService,
    private val listingService: ListingService,
) {
    @PostMapping
    fun create(@RequestBody @Valid request: CreateVisitRequest): CreateVisitResponse {
        val match = matchService.findById(request.matchId)
        access.ensureCanRequest(mandateService.findById(match.mandate.id!!))
        return CreateVisitResponse(visitId = service.create(request).id!!)
    }

    // The agent (or an admin) confirms the visit: the customer gets its code and the address
    @PostMapping("/{id}/schedule")
    fun schedule(@PathVariable id: Long, @RequestBody @Valid request: ScheduleVisitRequest) {
        val visit = service.findById(id)
        mandateAccess.ensureCanUpdate(mandateService.findById(visit.mandateId))
        service.schedule(id, request)
    }

    // The agent checks the customer in with the code it reads to it; an admin can't
    @PostMapping("/{id}/check-in")
    fun checkIn(@PathVariable id: Long, @RequestBody @Valid request: CheckInVisitRequest) {
        access.ensureIsAgent(service.findById(id))
        service.checkIn(id, request)
    }

    // The agent reports the customer's absence (absent, or refused the code); an admin can't
    @PostMapping("/{id}/no-show")
    fun noShow(@PathVariable id: Long, @RequestBody @Valid request: NoShowVisitRequest) {
        access.ensureIsAgent(service.findById(id))
        service.noShow(id, request)
    }

    // The agent closes the visit with its private comment; an admin can't
    @PostMapping("/{id}/check-out")
    fun checkOut(@PathVariable id: Long, @RequestBody @Valid request: CheckOutVisitRequest) {
        access.ensureIsAgent(service.findById(id))
        service.checkOut(id, request)
    }

    // The customer sees its code and, for an ON_SITE visit, the exact address; the agent and the admins see neither
    @GetMapping("/{id:\\d+}")
    fun get(@PathVariable id: Long): GetVisitResponse {
        val visit = service.findById(id)
        access.ensureCanRead(visit)
        val listing = listingService.findById(visit.listingId)
        return GetVisitResponse(visit = mapper.toVisit(visit, listing, showCode = access.isCustomer(visit)))
    }

    // An agent's search is restricted to its own visits, a customer's to its own mandates
    @GetMapping
    fun search(@ModelAttribute request: SearchVisitRequest): SearchVisitResponse {
        val restricted = access.restrict(request)
        val visits = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
        return SearchVisitResponse(visits = visits.map { mapper.toVisitSummary(it) })
    }
}
