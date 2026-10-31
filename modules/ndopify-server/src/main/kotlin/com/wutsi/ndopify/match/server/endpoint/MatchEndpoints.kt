package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.mandate.server.service.MandateAccessService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.RunMatchResponse
import com.wutsi.ndopify.match.dto.SearchMatchRequest
import com.wutsi.ndopify.match.dto.SearchMatchResponse
import com.wutsi.ndopify.match.server.mapper.MatchMapper
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * A match is seen and dismissed by whoever may see / change its search mandate ([MandateAccessService]): its agent, or
 * an admin. The mandate (or match) is looked up first: 404, then 403.
 */
@RestController
@RequestMapping("/v1/matches")
class MatchEndpoints(
    private val service: MatchService,
    private val mapper: MatchMapper,
    private val mandateService: MandateService,
    private val access: MandateAccessService,
) {
    // Ex: the listings to propose for a search: GET /v1/matches?mandate-id=12
    @GetMapping
    fun search(@ModelAttribute request: SearchMatchRequest): SearchMatchResponse {
        access.ensureCanRead(mandateService.findById(request.mandateId))
        val matches = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchMatchResponse(matches = matches.map { match -> mapper.toMatch(match) })
    }

    // Digits only: with the ops endpoints off, POST /run must be a 404, not a 405 of this path
    @GetMapping("/{id:\\d+}")
    fun get(@PathVariable id: Long): GetMatchResponse {
        val match = service.findById(id)
        access.ensureCanRead(match.mandate)
        return GetMatchResponse(match = mapper.toMatch(match))
    }

    @PostMapping("/{id}/dismiss")
    fun dismiss(@PathVariable id: Long) {
        access.ensureCanUpdate(service.findById(id).mandate)
        service.dismiss(id)
    }

    // Re-matches a search mandate against its agent's inventory now, as on its approval
    @OpsEndpoint
    @PostMapping("/run")
    fun run(@RequestParam("mandate-id") mandateId: Long): RunMatchResponse {
        return RunMatchResponse(count = service.matchMandate(mandateId))
    }
}
