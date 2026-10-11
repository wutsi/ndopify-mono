package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.service.identification.IdentificationInfo
import java.io.File

interface IdentificationInfoExtractor {
    fun extract(images: List<File>): IdentificationInfo
}
