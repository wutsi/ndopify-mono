package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.mandate.dto.RequestMandateApprovalRequest
import com.wutsi.ndopify.refdata.dto.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MandateContractRulesTest {
    @Test
    fun `prefill - from the contract, or 6 months and no fee`() {
        assertEquals(
            MandateContractForm("3", "5000"),
            MandateContractRules.prefill(Contract(durationMonths = 3, visitFee = Money(5000, "XAF"))),
        )
        assertEquals(MandateContractForm("6", "0"), MandateContractRules.prefill(null))
        // A contract from before the visit fee
        assertEquals(MandateContractForm("6", "0"), MandateContractRules.prefill(Contract(durationMonths = 6)))
        // Set through the API, outside the page's range: kept as is
        assertEquals(MandateContractForm("24", "0"), MandateContractRules.prefill(Contract(durationMonths = 24)))
    }

    @Test
    fun `months - 1 to 12, plus the contract's own duration when it is outside`() {
        assertEquals((1..12).toList(), MandateContractRules.months(null))
        assertEquals((1..12).toList() + 24, MandateContractRules.months(Contract(durationMonths = 24)))
    }

    @Test
    fun `errors - duration 1 to 12 and a whole amount`() {
        assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("6", "5 000")))
        assertTrue(MandateContractRules.errors(MandateContractForm("0", "0")).containsKey("durationMonths"))
        assertTrue(MandateContractRules.errors(MandateContractForm("13", "0")).containsKey("durationMonths"))
        assertTrue(MandateContractRules.errors(MandateContractForm("abc", "0")).containsKey("durationMonths"))
        assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("24", "0"), currentDuration = 24))
        assertTrue(
            MandateContractRules.errors(MandateContractForm("25", "0"), currentDuration = 24)
                .containsKey("durationMonths"),
        )
        assertTrue(MandateContractRules.errors(MandateContractForm("6", "abc")).containsKey("visitFee"))
        assertTrue(MandateContractRules.errors(MandateContractForm("6", "-1")).containsKey("visitFee"))
        assertTrue(MandateContractRules.errors(MandateContractForm("6", "1.5")).containsKey("visitFee"))
        assertEquals(emptyMap(), MandateContractRules.errors(MandateContractForm("6", "")))
    }

    @Test
    fun `request - a blank fee is zero, in the tenant currency`() {
        assertEquals(
            RequestMandateApprovalRequest(3, Money(5000, "XAF")),
            MandateContractRules.request(MandateContractForm("3", "5 000"), "XAF"),
        )
        assertEquals(Money(0, "XAF"), MandateContractRules.request(MandateContractForm("6", ""), "XAF").visitFee)
    }
}
