package com.wutsi.ndopify.mandate.server.service.document

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.mandate.dto.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentDataExtractorProviderTest {
    private val identity = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT)
    }
    private val landTitle = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP)
    }
    private val survey = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.SURVEY_PLAN)
    }
    private val procuration = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.POWER_OF_ATTORNEY)
    }
    private val occupancy = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.LEASE_CONTRACT, DocumentType.UTILITY_BILL, DocumentType.TAX_RECEIPT)
    }
    private val other = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(DocumentType.OTHER)
    }
    private val financial = mock<DocumentDataExtractor> {
        on { types } doReturn setOf(
            DocumentType.PAY_SLIP,
            DocumentType.BANK_PRE_APPROVAL,
            DocumentType.PROOF_OF_FUNDS,
        )
    }

    private val provider = DocumentDataExtractorProvider(
        listOf(identity, landTitle, survey, procuration, occupancy, financial, other),
    )

    @Test
    fun `each handled type resolves to its extractor`() {
        assertEquals(identity, provider.get(DocumentType.NATIONAL_ID))
        assertEquals(identity, provider.get(DocumentType.PASSPORT))
        assertEquals(landTitle, provider.get(DocumentType.LAND_TITLE))
        assertEquals(landTitle, provider.get(DocumentType.CERTIFICATE_OF_OWNERSHIP))
        assertEquals(survey, provider.get(DocumentType.SURVEY_PLAN))
        assertEquals(procuration, provider.get(DocumentType.POWER_OF_ATTORNEY))
        assertEquals(occupancy, provider.get(DocumentType.LEASE_CONTRACT))
        assertEquals(occupancy, provider.get(DocumentType.UTILITY_BILL))
        assertEquals(occupancy, provider.get(DocumentType.TAX_RECEIPT))
        assertEquals(financial, provider.get(DocumentType.PAY_SLIP))
        assertEquals(financial, provider.get(DocumentType.BANK_PRE_APPROVAL))
        assertEquals(financial, provider.get(DocumentType.PROOF_OF_FUNDS))
        assertEquals(other, provider.get(DocumentType.OTHER))
    }

    @Test
    fun `the unknown type has no extractor`() {
        assertNull(provider.get(DocumentType.UNKNOWN))
        assertFalse(provider.supports(DocumentType.UNKNOWN))
    }

    @Test
    fun `supports the handled types`() {
        DocumentType.entries
            .filter { type -> type != DocumentType.UNKNOWN }
            .forEach { type -> assertTrue(provider.supports(type), type.name) }
    }
}
