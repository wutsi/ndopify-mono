package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.SearchAssignmentRequest
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.mandate.server.mapper.AssignmentMapper
import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.endpoint.OpsEndpoint
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/assignments")
class AssignmentEndpoints(
    private val service: AssignmentService,
    private val mapper: AssignmentMapper,
) {
    // Ex: an agent's pending offers: GET /v1/assignments?agent-id=12&status=PENDING
    @GetMapping
    fun search(@ModelAttribute request: SearchAssignmentRequest): SearchAssignmentResponse {
        val assignments = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchAssignmentResponse(
            assignments = assignments.map { assignment -> mapper.toAssignmentSummary(assignment) }
        )
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetAssignmentResponse {
        val assignment = service.findById(id)
        return GetAssignmentResponse(assignment = mapper.toAssignment(assignment))
    }

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: Long) {
        service.accept(id)
    }

    @PostMapping("/{id}/decline")
    fun decline(@PathVariable id: Long) {
        service.decline(id)
    }

    // Expires the offer now, even before its TTL; the mandate goes to the next agent after commit
    @OpsEndpoint
    @PostMapping("/{id}/expire")
    fun expire(@PathVariable id: Long) {
        service.expire(id)
    }
}
