package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.service.kyc.KycInfo
import java.io.File

interface KycInfoExtractor {
    fun extract(images: List<File>): KycInfo
}
