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
            MandateCloseRules.closeMatch(draft, "XAF", sale = false),
        )
    }

    @Test
    fun `close mandate`() {
        val draft = PendingMandateClose(outside = true, priceAmount = 240000)

        assertEquals(
            CloseMandateRequest(Money(amount = 240000, currency = "XAF", periodicity = PeriodicityType.MONTHLY)),
            MandateCloseRules.closeMandate(draft, "XAF", sale = false),
        )
    }

    @Test
    fun `a sale's close match carries no periodicity`() {
        val draft = PendingMandateClose(matchId = 100, priceAmount = 15_000_000)

        assertEquals(
            CloseMatchRequest(Money(amount = 15_000_000, currency = "XAF", periodicity = PeriodicityType.NONE)),
            MandateCloseRules.closeMatch(draft, "XAF", sale = true),
        )
    }

    @Test
    fun `a sale's outside close carries no periodicity`() {
        val draft = PendingMandateClose(outside = true, priceAmount = 15_000_000)

        assertEquals(
            CloseMandateRequest(Money(amount = 15_000_000, currency = "XAF", periodicity = PeriodicityType.NONE)),
            MandateCloseRules.closeMandate(draft, "XAF", sale = true),
        )
    }

    @Test
    fun `a rental's requests keep the monthly periodicity`() {
        val draft = PendingMandateClose(matchId = 100, priceAmount = 240000)

        assertEquals(PeriodicityType.MONTHLY, MandateCloseRules.closeMatch(draft, "XAF", sale = false).finalPrice.periodicity)
        assertEquals(PeriodicityType.MONTHLY, MandateCloseRules.closeMandate(draft, "XAF", sale = false).finalPrice.periodicity)
    }
}
