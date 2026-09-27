package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class MoMoGatewayFactoryTest {
    val mtnGateway = mock<MoMoGatewayMtn>()
    val factory = MoMoGatewayFactory(mtnGateway)

    @Test
    fun mtn() {
        assertEquals(mtnGateway, factory.get(MoMoGatewayType.MTN))
    }

    @Test
    fun orange() {
        assertEquals(null, factory.get(MoMoGatewayType.ORANGE))
    }

    @Test
    fun unknown() {
        assertEquals(null, factory.get(MoMoGatewayType.UNKNOWN))
    }
}
