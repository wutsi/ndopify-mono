package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class MoMoGatewayProviderTest {
    val mtnGateway = mock<MoMoGatewayMtn>()
    val provider = MoMoGatewayProvider(mtnGateway)

    @Test
    fun mtn() {
        assertEquals(mtnGateway, provider.get(MoMoGatewayType.MTN))
    }

    @Test
    fun orange() {
        assertEquals(null, provider.get(MoMoGatewayType.ORANGE))
    }

    @Test
    fun unknown() {
        assertEquals(null, provider.get(MoMoGatewayType.UNKNOWN))
    }
}
