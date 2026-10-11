package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.service.identification.IdentificationInfoExtractorAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.mock
import kotlin.test.Test

class IdentificationInfoExtractorProviderTest {
    private val ai = mock(IdentificationInfoExtractorAi::class.java)
    private val provider = IdentificationInfoExtractorProvider(ai)

    @Test
    fun get() {
        // WHEN
        val extractor = provider.get()

        // THEN
        assertEquals(ai, extractor)
    }
}
