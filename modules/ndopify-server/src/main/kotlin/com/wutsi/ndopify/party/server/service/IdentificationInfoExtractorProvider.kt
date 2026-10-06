package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.service.identification.IdentificationInfoExtractorAi
import org.springframework.stereotype.Service

@Service
class IdentificationInfoExtractorProvider(
    private val ai: IdentificationInfoExtractorAi,
) {
    fun get(): IdentificationInfoExtractor {
        return ai
    }
}
