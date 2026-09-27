package com.wutsi.ndopify.platform.momo.mtn

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.mtn.model.MtnBasicUserInfoResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUserStatus
import org.mockito.Mockito.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoMoGatewayMtnTest {
    private val collection = mock<MtnCollectionProduct>()
    private val gateway = MoMoGatewayMtn(collection)

    @Test
    fun `active user with matching country code`() {
        doReturn(
            MtnBasicUserInfoResponse(
                givenName = "John",
                familyName = "Doe",
                status = MtnUserStatus.ACTIVE,
            ),
        ).whenever(collection).userBasicInfo("+237670101010")

        val response = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = "+237670101010",
                holderName = "John Doe",
                countryCode = "CM",
            ),
        )

        verify(collection).authenticate()
        assertEquals("John Doe", response.holderName)
        assertEquals(1.0, response.holderNameScore)
        assertEquals(1.0, response.countryCodeScore)
        assertTrue(response.active)
    }

    @Test
    fun `mismatching country code`() {
        doReturn(
            MtnBasicUserInfoResponse(
                givenName = "John",
                familyName = "Doe",
                status = MtnUserStatus.ACTIVE,
            ),
        ).whenever(collection).userBasicInfo("+237670101010")

        val response = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = "+237670101010",
                holderName = "John John",
                countryCode = "FR",
            ),
        )

        verify(collection).authenticate()
        assertEquals(0.0, response.countryCodeScore)
    }

    @Test
    fun `inactive user`() {
        doReturn(
            MtnBasicUserInfoResponse(
                givenName = "John",
                familyName = "Doe",
                status = MtnUserStatus.SUSPENDED,
            ),
        ).whenever(collection).userBasicInfo("+237670101010")

        val response = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = "+237670101010",
                holderName = "John John",
                countryCode = "CM",
            ),
        )

        verify(collection).authenticate()
        assertFalse(response.active)
    }

    @Test
    fun `holder name does not match`() {
        doReturn(
            MtnBasicUserInfoResponse(
                givenName = "John",
                familyName = "Doe",
                status = MtnUserStatus.ACTIVE,
            ),
        ).whenever(collection).userBasicInfo("+237670101010")

        val response = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = "+237670101010",
                holderName = "Xyz Qwerty",
                countryCode = "CM",
            ),
        )

        verify(collection).authenticate()
        assertTrue(response.holderNameScore < 0.5)
    }
}
