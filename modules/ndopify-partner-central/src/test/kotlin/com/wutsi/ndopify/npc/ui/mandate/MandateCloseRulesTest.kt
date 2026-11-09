package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.match.dto.CloseMatchRequest
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateCloseRulesTest {
    @Test
    fun `amount`() {
        assertEquals(240000L, MandateCloseRules.amount("240 000"))
        assertEquals(240000L, MandateCloseRules.amount("240.000"))
        assertNull(MandateCloseRules.amount("0"))
        assertNull(MandateCloseRules.amount(""))
        assertNull(MandateCloseRules.amount("240k"))
        assertNull(MandateCloseRules.amount(null))
    }

    @Test
    fun `close match`() {
        val draft = PendingMandateClose(matchId = 100, priceAmount = 240000)

        assertEquals(
            CloseMatchRequest(Money(amount = 240000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
            MandateCloseRules.closeMatch(draft, "XAF"),
        )
    }

    @Test
    fun `close mandate`() {
        val draft = PendingMandateClose(outside = true, priceAmount = 240000)

        assertEquals(
            CloseMandateRequest(Money(amount = 240000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
            MandateCloseRules.closeMandate(draft, "XAF"),
        )
    }
}
