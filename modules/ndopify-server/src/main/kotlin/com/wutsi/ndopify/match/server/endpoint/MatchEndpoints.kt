package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.ProposeMatchRequest
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.dto.RunMatchResponse
import com.wutsi.ndopify.match.dto.SearchMatchRequest
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.match.server.mapper.MatchMapper
import com.wutsi.ndopify.match.server.service.MatchProposalService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * A match is seen and dismissed by whoever may see / change its search mandate ([MandateAccessService]): its agent, or
 * an admin, who also keeps it for later (shortlist) and proposes it to the customer, alone or with others of the same
 * mandate in one e-mail; only the customer accepts or rejects it. The mandate (or match) is
 * looked up first: 404, then 403. A search needs at least one `mandate-id` (400
 * `HTTP_MISSING_PARAMETER`), and each of them must be found and readable. `GET /{id}` is also open to the mandate's
 * customer, for a match proposed to it or answered (PROPOSED, ACCEPTED, REJECTED), else 403 `MANDATE_ACCESS_DENIED`.
 */
@RestController
@RequestMapping("/v1/matches")
class MatchEndpoints(
    private val service: MatchService,
    private val proposalService: MatchProposalService,
    private val mapper: MatchMapper,
    private val mandateService: MandateService,
    private val access: MandateAccessService,
) {
    // Ex: the listings to propose for 2 searches: GET /v1/matches?mandate-id=12&mandate-id=15
    @GetMapping
    fun search(@ModelAttribute request: SearchMatchRequest): SearchMatchResponse {
        if (request.mandateIds.isEmpty()) {
            throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = "mandate-id", type = ParameterType.PARAMETER_TYPE_QUERY),
                )
            )
        }
        request.mandateIds.distinct().forEach { id -> access.ensureCanRead(mandateService.findById(id)) }
        val matches = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchMatchResponse(matches = matches.map { match -> mapper.toMatch(match) })
    }

    // The customer sees a match once proposed to it (and after it answered), not before: 403 MANDATE_ACCESS_DENIED
    // Digits only: with the ops endpoints off, POST /run must be a 404, not a 405 of this path
    @GetMapping("/{id:\\d+}")
    fun get(@PathVariable id: Long): GetMatchResponse {
        val match = service.findById(id)
        val customerOnly = access.ensureCanReadAsCustomerOrAgent(match.mandate)
        if (customerOnly && match.status !in SEEN_BY_CUSTOMER) {
            throw ForbiddenException(error = Error(code = ErrorCode.MANDATE_ACCESS_DENIED))
        }
        return GetMatchResponse(match = mapper.toMatch(match))
    }

    @PostMapping("/{id}/dismiss")
    fun dismiss(@PathVariable id: Long) {
        access.ensureCanUpdate(service.findById(id).mandate)
        service.dismiss(id)
    }

    // The agent keeps it to propose later
    @PostMapping("/{id}/shortlist")
    fun shortlist(@PathVariable id: Long) {
        access.ensureCanUpdate(service.findById(id).mandate)
        service.shortlist(id)
    }

    @PostMapping("/{id}/propose")
    fun propose(@PathVariable id: Long) {
        access.ensureCanUpdate(service.findById(id).mandate)
        proposalService.propose(listOf(id))
    }

    // Several matches of one mandate, in one e-mail to its customer: 404, 400 (other mandates), then 401 / 403, then 409
    @PostMapping("/propose")
    fun propose(@RequestBody @Valid request: ProposeMatchRequest) {
        access.ensureCanUpdate(proposalService.findOfOneMandate(request.matchIds).first().mandate)
        proposalService.propose(request.matchIds)
    }

    // Only the mandate's customer answers a proposal (MandateAccessService.ensureIsCustomer)
    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: Long) {
        access.ensureIsCustomer(service.findById(id).mandate)
        proposalService.accept(id)
    }

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long, @RequestBody @Valid request: RejectMatchRequest) {
        access.ensureIsCustomer(service.findById(id).mandate)
        proposalService.reject(id, request)
    }

    // Re-matches a search mandate against its agent's inventory now, as on its approval
    @OpsEndpoint
    @PostMapping("/run")
    fun run(@RequestParam("mandate-id") mandateId: Long): RunMatchResponse {
        return RunMatchResponse(count = service.matchMandate(mandateId))
    }

    private companion object {
        val SEEN_BY_CUSTOMER = setOf(MatchStatus.PROPOSED, MatchStatus.ACCEPTED, MatchStatus.REJECTED)
    }
}
