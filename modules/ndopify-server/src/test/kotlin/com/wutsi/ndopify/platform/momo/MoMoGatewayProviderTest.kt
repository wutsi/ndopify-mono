package com.wutsi.ndopify.platform.momo

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import org.mockito.Mockito
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

    @Test
    fun `get by number MTN`() {
        val phoneNumber = "+237650000000"
        val countryCode = "CM"
        val prefix = "23765"

        // Mock the MTN gateway to return the prefix for the country code
        Mockito.`when`(mtnGateway.getPhoneNumberPrefixes(countryCode)).thenReturn(listOf(prefix))

        val gateway = provider.getByPhoneNumber(phoneNumber)
        assertEquals(mtnGateway, gateway)
    }

    @Test
    fun `get by number from prefix`() {
        val phoneNumber = "+237671234567"
        val prefix = "23767"

        // Mock the MTN gateway to return the prefix for the country code
        doReturn(listOf(prefix)).whenever(mtnGateway).getPhoneNumberPrefixes("CM")

        val gateway = provider.getByPhoneNumber(phoneNumber)
        assertEquals(mtnGateway, gateway)
    }

    @Test
    fun `get by number from prefix - not found`() {
        val phoneNumber = "+237671234567"
        val prefix = "0000"

        // Mock the MTN gateway to return the prefix for the country code
        doReturn(listOf(prefix)).whenever(mtnGateway).getPhoneNumberPrefixes("CM")

        val gateway = provider.getByPhoneNumber(phoneNumber)
        assertEquals(null, gateway)
    }

    @Test
    fun `get by number from prefix - country not supported`() {
        val phoneNumber = "+221771234567"

        val gateway = provider.getByPhoneNumber(phoneNumber)
        assertEquals(null, gateway)
    }
}
