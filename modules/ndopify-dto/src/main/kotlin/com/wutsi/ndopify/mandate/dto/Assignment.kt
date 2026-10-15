package com.wutsi.ndopify.mandate.dto

import java.util.Date

data class Assignment(
    val id: Long = -1L,
    val mandateId: Long = -1L,
    val agentId: Long = -1L,
    val status: AssignmentStatus = AssignmentStatus.UNKNOWN,
    val score: Double = 0.0,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val expiresAt: Date? = null,
)
