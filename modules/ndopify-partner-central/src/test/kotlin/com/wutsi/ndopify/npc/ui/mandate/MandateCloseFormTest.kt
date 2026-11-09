package com.wutsi.ndopify.npc.ui.mandate

import kotlin.test.Test
import kotlin.test.assertNull

class MandateCloseFormTest {
    @Test
    fun `the forms start without an answer`() {
        assertNull(CounterpartForm().counterpart)
        assertNull(PriceForm().finalPrice)
    }
}
