package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MandateMapperTest {
    private val mapper = MandateMapper()

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
