package com.wutsi.ndopify.platform.momo

import com.wutsi.ndopify.platform.momo.mtn.MoMoGatewayMtn
import com.wutsi.ndopify.refdata.dto.MoMoGatewayType
import com.wutsi.ndopify.util.PhoneNumberUtils
import org.springframework.stereotype.Service

@Service
class MoMoGatewayProvider(
    private val mtn: MoMoGatewayMtn,
) {
    fun getByPhoneNumber(phoneNumber: String): MoMoGateway? {
        val countryCode = PhoneNumberUtils.extractCountryCode(phoneNumber)
        val normalizedPhoneNumber = PhoneNumberUtils.normalize(phoneNumber)
        return all().find { gateway -> supports(normalizedPhoneNumber, countryCode, gateway) }
    }

    private fun supports(normalizedPhoneNumber: String, countryCode: String, gateway: MoMoGateway): Boolean {
        return try {
            gateway
                .getPhoneNumberPrefixes(countryCode)
                .find { prefix -> normalizedPhoneNumber.startsWith(prefix) } != null
        } catch (_: MoMoException) {
            false
        }
    }

    private fun supports(phoneNumber: String, gateway: MoMoGateway): Boolean {
        val countryCode = PhoneNumberUtils.extractCountryCode(phoneNumber)
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        return try {
            gateway
                .getPhoneNumberPrefixes(countryCode)
                .find { prefix -> normalized.startsWith(prefix) } != null
        } catch (_: MoMoException) {
            false
        }
    }

    fun get(type: MoMoGatewayType): MoMoGateway? {
        return when (type) {
            MoMoGatewayType.MTN -> mtn
            else -> null
        }
    }

    private fun all(): List<MoMoGateway> = listOf(mtn)
}
