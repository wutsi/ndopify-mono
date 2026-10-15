package com.wutsi.ndopify.mandate.dto

data class SearchAssignmentResponse(
    val assignments: List<AssignmentSummary> = emptyList(),
)
