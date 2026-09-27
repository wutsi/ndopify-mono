package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.springframework.stereotype.Service

@Service
class MoMoGatewayFactory(
    private val mtn: MoMoGatewayMtn,
) {
    fun get(type: MoMoGatewayType): MoMoGateway? {
        return when (type) {
            MoMoGatewayType.MTN -> mtn
            else -> null
        }
    }
}
