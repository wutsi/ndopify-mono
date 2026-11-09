package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.npc.ui.mandate.PendingMandateClose.Companion.STEP_CONFIRM
import com.wutsi.ndopify.npc.ui.mandate.PendingMandateClose.Companion.STEP_COUNTERPART
import com.wutsi.ndopify.npc.ui.mandate.PendingMandateClose.Companion.STEP_PRICE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingMandateCloseTest {
    @Test
    fun `counterpart steps with a match`() {
        val draft = PendingMandateClose(matchId = 100)

        assertTrue(draft.isComplete(STEP_COUNTERPART, true))
        assertEquals(STEP_PRICE, draft.firstIncompleteStep(true))
    }

    @Test
    fun `counterpart steps with outside`() {
        val draft = PendingMandateClose(outside = true)

        assertTrue(draft.isComplete(STEP_COUNTERPART, true))
    }

    @Test
    fun `counterpart is skipped without candidates`() {
        val draft = PendingMandateClose()

        assertFalse(draft.isComplete(STEP_COUNTERPART, true))
        assertTrue(draft.isComplete(STEP_COUNTERPART, false))
        assertEquals(STEP_PRICE, draft.firstIncompleteStep(false))
    }

    @Test
    fun `price is incomplete until answered`() {
        val draft = PendingMandateClose(matchId = 100)

        assertFalse(draft.isComplete(STEP_PRICE, true))
    }

    @Test
    fun `all answered`() {
        val draft = PendingMandateClose(outside = true, priceAmount = 240000)

        assertEquals(STEP_CONFIRM, draft.firstIncompleteStep(true))
        assertEquals(STEP_CONFIRM, draft.firstIncompleteStep(false))
    }

    @Test
    fun `a step without an answer, the confirm one included, is never complete`() {
        val draft = PendingMandateClose(outside = true, priceAmount = 240000)

        assertFalse(draft.isComplete(STEP_CONFIRM, true))
        assertFalse(draft.isComplete("unknown", true))
    }
}
