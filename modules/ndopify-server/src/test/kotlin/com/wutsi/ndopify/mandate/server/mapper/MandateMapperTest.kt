package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.FinancingType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.TitleDocumentType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateMapperTest {
    private val mapper = MandateMapper()

    @Test
    fun `origin listing is returned`() {
        val mandate = mapper.toMandate(MandateEntity(id = 1, customerId = 2, cityId = 3, originListingId = 9))

        assertEquals(9, mandate.originListingId)
    }

    @Test
    fun `no origin listing`() {
        assertNull(mapper.toMandate(MandateEntity(id = 1, customerId = 2, cityId = 3)).originListingId)
    }

    @Test
    fun `maps the purchase answers`() {
        val entity = MandateEntity(
            id = 1L,
            listingType = ListingType.SALE,
            financingType = FinancingType.BANK_LOAN,
            titleDocuments = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN),
        )

        val mandate = mapper.toMandate(entity)

        assertEquals(FinancingType.BANK_LOAN, mandate.financingType)
        assertEquals(listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN), mandate.titleDocuments)
    }

    @Test
    fun `maps a rental with no purchase answers`() {
        val mandate = mapper.toMandate(MandateEntity(id = 1L, listingType = ListingType.RENTAL))

        assertNull(mandate.financingType)
        assertEquals(emptyList(), mandate.titleDocuments)
    }

    @Test
    fun `toMandate maps the deal the mandate was closed on`() {
        val entity = MandateEntity(
            id = 1L,
            customerId = 10L,
            contractId = "MNDT-2026-000001",
            status = MandateStatus.CLOSED,
            mandateType = MandateType.SEARCH,
            finalPrice = 140000L,
            dealMatchId = 42L,
            dealOutside = true,
            version = 3L,
        )

        val mandate = mapper.toMandate(entity)

        assertEquals(1L, mandate.id)
        assertEquals(10L, mandate.customerId)
        assertEquals("MNDT-2026-000001", mandate.contractId)
        assertEquals(MandateStatus.CLOSED, mandate.status)
        assertEquals(MandateType.SEARCH, mandate.mandateType)
        assertEquals(140000L, mandate.finalPrice)
        assertEquals(42L, mandate.dealMatchId)
        assertTrue(mandate.dealOutside)
        assertEquals(3L, mandate.version)
    }
}
