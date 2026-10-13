package com.wutsi.ndopify.refdata.dto

data class Money(
    val amount: Long = 0,
    val currency: String = "",
    val periodicity: PeriodicityType = PeriodicityType.UNKNOWN
)
