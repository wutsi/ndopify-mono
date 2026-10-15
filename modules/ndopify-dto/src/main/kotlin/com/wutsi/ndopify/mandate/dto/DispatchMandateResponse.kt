package com.wutsi.ndopify.mandate.dto

data class DispatchMandateResponse(
    val assignmentId: Long? = null, // null: nothing offered (night, no candidate, MANUAL_ASSIGNMENT, already offered)
)
