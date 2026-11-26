package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentRequirementsTest {
    @Test
    fun `national id`() {
        val requirements = DocumentRequirements.of(DocumentType.NATIONAL_ID)!!

        assertEquals(setOf("number", "firstName", "lastName"), requirements.required)
        assertEquals(listOf(setOf("address", "locality")), DocumentRequirements.of(DocumentType.UTILITY_BILL)!!.oneOf)
        assertEquals(true, requirements.requiresExpiry)
        assertEquals(setOf("FRONT", "BACK"), requirements.sides)
        assertEquals(emptyList(), requirements.oneOf)
    }

    @Test
    fun `passport`() {
        val requirements = DocumentRequirements.of(DocumentType.PASSPORT)!!

        assertEquals(setOf("number", "firstName", "lastName"), requirements.required)
        assertEquals(true, requirements.requiresExpiry)
        assertEquals(setOf("FRONT"), requirements.sides)
    }

    @Test
    fun `land title and certificate of ownership`() {
        listOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP).forEach { type ->
            val requirements = DocumentRequirements.of(type)!!

            assertEquals(setOf("titleNumber", "owners", "locality"), requirements.required, type.name)
            assertEquals(false, requirements.requiresExpiry, type.name)
            assertEquals(emptySet(), requirements.sides, type.name)
        }
    }

    @Test
    fun `survey plan`() {
        val requirements = DocumentRequirements.of(DocumentType.SURVEY_PLAN)!!

        assertEquals(setOf("parcelReference", "locality"), requirements.required)
    }

    @Test
    fun `power of attorney`() {
        val requirements = DocumentRequirements.of(DocumentType.POWER_OF_ATTORNEY)!!

        assertEquals(setOf("grantor", "attorney", "purpose"), requirements.required)
        assertEquals(false, requirements.requiresExpiry)
    }

    @Test
    fun `occupancy documents`() {
        listOf(DocumentType.LEASE_CONTRACT, DocumentType.UTILITY_BILL, DocumentType.TAX_RECEIPT).forEach { type ->
            val requirements = DocumentRequirements.of(type)!!

            assertEquals(setOf("holderName", "issueDate"), requirements.required, type.name)
            assertEquals(listOf(setOf("address", "locality")), requirements.oneOf, type.name)
        }
    }

    @Test
    fun `other`() {
        assertEquals(setOf("summary"), DocumentRequirements.of(DocumentType.OTHER)!!.required)
    }

    @Test
    fun `the financial and unknown types have no requirements`() {
        listOf(
            DocumentType.UNKNOWN,
            DocumentType.PAY_SLIP,
            DocumentType.BANK_PRE_APPROVAL,
            DocumentType.PROOF_OF_FUNDS,
        ).forEach { type -> assertNull(DocumentRequirements.of(type), type.name) }
    }
}
