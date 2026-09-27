package com.wutsi.ndopify.platform.identity

import com.wutsi.ndopify.platform.identity.ai.AiIdentityKycService
import com.wutsi.ndopify.refdata.dto.IdentityType
import org.springframework.stereotype.Service

@Service
class IdentityKycServiceProvider(
    private val ai: AiIdentityKycService,
) {
    fun get(type: IdentityType): IdentityKycService? {
        return when (type) {
            IdentityType.UNKNOWN -> null
            else -> ai
        }
    }
}
