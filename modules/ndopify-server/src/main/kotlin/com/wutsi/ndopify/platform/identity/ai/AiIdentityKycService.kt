package com.wutsi.ndopify.platform.identity.ai

import com.wutsi.ndopify.platform.identity.IdentityKycService
import com.wutsi.ndopify.platform.identity.model.IdKycMatchRequest
import com.wutsi.ndopify.platform.identity.model.IdKycMatchResponse
import com.wutsi.ndopify.util.KycUtils
import org.springframework.stereotype.Service

@Service
class AiIdentityKycService(private val extractor: AiIdentityInfoExtractor) : IdentityKycService {
    override fun kycMatch(request: IdKycMatchRequest): IdKycMatchResponse {
        val info = extractor.extract(request.images)
        val holderName = "${info.firstName} ${info.lastName}".trim()
        return IdKycMatchResponse(
            holderName = holderName,
            status = info.status,
            holderNameScore = KycUtils.verifyName(holderName, request.holderName),
            countryCodeScore = if (request.countryCode.equals(info.countryCode, true)) 1.0 else 0.0,
            documentTypeScore = if (request.identityType == info.type) 1.0 else 0.0
        )
    }
}
