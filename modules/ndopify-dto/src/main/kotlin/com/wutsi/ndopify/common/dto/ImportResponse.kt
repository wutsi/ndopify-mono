package com.wutsi.ndopify.common.dto

data class ImportResponse(
    val added: Int = 0,
    val updated: Int = 0,
    val errors: Int = 0,
    val errorMessages: List<ImportMessage> = emptyList()
)
