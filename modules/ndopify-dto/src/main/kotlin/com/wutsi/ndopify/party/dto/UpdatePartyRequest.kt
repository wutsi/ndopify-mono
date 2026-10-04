package com.wutsi.ndopify.party.dto

data class CreatePartyRequest(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
)
