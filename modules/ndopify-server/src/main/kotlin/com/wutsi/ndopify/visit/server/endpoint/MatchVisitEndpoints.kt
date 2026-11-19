package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.visit.dto.CreateMatchVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.server.service.VisitService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The agent books a visit for a match its customer accepted, straight to `CONFIRMED`: the customer then gets the date,
 * the address and its code, as for a visit the agent scheduled. The match's agent (or an admin) alone books it; the
 * match is looked up first (404), then the mandate's access (403), before any 409.
 */
@RestController
@RequestMapping("/v1/matches")
class MatchVisitEndpoints(
    private val service: VisitService,
    private val matchService: MatchService,
    private val mandateService: MandateService,
    private val mandateAccess: MandateAccessService,
) {
    // The match's agent (or an admin) books the visit: nobody books it for another agent's customer
    @PostMapping("/{id:\\d+}/visits")
    fun create(@PathVariable id: Long, @RequestBody @Valid request: CreateMatchVisitRequest): CreateVisitResponse {
        val match = matchService.findById(id)
        mandateAccess.ensureCanUpdate(mandateService.findById(match.mandate.id!!))
        return CreateVisitResponse(visitId = service.createForAgent(id, request).id!!)
    }
}
