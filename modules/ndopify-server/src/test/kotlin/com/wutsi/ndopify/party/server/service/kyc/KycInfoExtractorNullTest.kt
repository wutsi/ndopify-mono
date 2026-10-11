package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.IdentificationType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KycInfoExtractorNullTest {
    private val extractor = KycInfoExtractorNull()

    @Test
    fun `extract - returns invalid info`() {
        val result = extractor.extract(listOf(File("front.jpg"), File("back.jpg")))

        assertFalse(result.valid)
        assertEquals(
            "KycInfoExtractorNull - Cannot extract information from the document",
            result.invalidityReason,
        )
        assertEquals(IdentificationType.UNKNOWN, result.type)
        assertEquals("", result.number)
        assertEquals("", result.firstName)
        assertEquals("", result.lastName)
        assertEquals("", result.countryCode)
        assertNull(result.expiryDate)
    }

    @Test
    fun `extract - no images - returns invalid info`() {
        val result = extractor.extract(emptyList())

        assertFalse(result.valid)
        assertEquals(
            "KycInfoExtractorNull - Cannot extract information from the document",
            result.invalidityReason,
        )
    }
}
