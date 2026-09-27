package com.wutsi.ndopify.platform.momo.mtn

import com.wutsi.ndopify.platform.momo.MoMoGateway
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchResponse
import com.wutsi.ndopify.platform.momo.mtn.model.MtnUserStatus
import com.wutsi.ndopify.util.KycUtils
import com.wutsi.ndopify.util.PhoneNumberUtils
import org.springframework.stereotype.Service

@Service
class MoMoGatewayMtn(
    val collection: MtnCollectionProduct,
) : MoMoGateway {
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
