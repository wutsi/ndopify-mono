package com.wutsi.ndopify.platform.momo.mtn

import com.wutsi.ndopify.platform.momo.MoMoError
import com.wutsi.ndopify.platform.momo.MoMoErrorCode
import com.wutsi.ndopify.platform.momo.MoMoException
import com.wutsi.ndopify.platform.momo.MoMoGateway
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUserStatus
import com.wutsi.ndopify.util.KycUtils
import com.wutsi.ndopify.util.PhoneNumberUtils

class MoMoGatewayMtn(
    val collection: MtnCollectionProduct,
) : MoMoGateway {
    override fun getPhoneNumberPrefixes(countryCode: String): List<String> {
        when (countryCode) {
            "CM" -> return listOf(
                "23767",
                "237650", "237651", "237652", "237653", "237654",
                "237680", "237681", "237682", "237683", "237684",
            )

            else -> throw MoMoException(
                error = MoMoError(code = MoMoErrorCode.UNSUPPORTED_COUNTRY),
                message = "Invalid country code: $countryCode"
            )
        }
    }

    override fun kycMatch(request: MoMoKycMatchRequest): MoMoKycMatchResponse {
        collection.authenticate()
        val info = collection.userBasicInfo(request.phoneNumber)
        val countryCode = PhoneNumberUtils.extractCountryCode(request.phoneNumber)
        val holderName = info.givenName + " " + info.familyName

        return MoMoKycMatchResponse(
            holderName = holderName,
            holderNameScore = KycUtils.verifyName(holderName, request.holderName),
            countryCodeScore = if (countryCode.equals(request.countryCode, true)) 1.0 else 0.0,
            active = info.status == MtnUserStatus.ACTIVE,
        )
    }
}
