package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import jakarta.validation.constraints.NotEmpty

data class UpdateMobileMoneyRequest(
    val gateway: MoMoGatewayType = MoMoGatewayType.UNKNOWN,

    @get:NotEmpty val mobileNumber: String = "",
)
