package com.wutsi.ndopify.platform.identity

import com.wutsi.ndopify.platform.identity.model.IdKycMatchRequest
import com.wutsi.ndopify.platform.identity.model.IdKycMatchResponse

interface IdentityKycService {
    fun kycMatch(request: IdKycMatchRequest): IdKycMatchResponse
}
