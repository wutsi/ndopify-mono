package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.server.service.KycInfoExtractor
import org.springframework.stereotype.Service
import java.io.File

@Service
class KycInfoExtractorNull : KycInfoExtractor {
    override fun extract(images: List<File>): KycInfo {
        return KycInfo(
            valid = false,
            invalidityReason = "KycInfoExtractorNull - Cannot extract information from the document",
        )
    }
}
