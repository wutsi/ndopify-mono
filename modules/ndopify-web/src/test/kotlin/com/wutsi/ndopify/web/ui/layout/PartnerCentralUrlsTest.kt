package com.wutsi.ndopify.web.ui.layout

import com.wutsi.ndopify.refdata.dto.Tenant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartnerCentralUrlsTest {
    @Test
    fun `path on the tenant's partner central`() {
        assertEquals("https://partner.ndopify.cm/join", PartnerCentralUrls.of(Tenant(partnerCentralUrl = "https://partner.ndopify.cm"), "/join"))
    }

    @Test
    fun `trailing slash of the partner central URL`() {
        assertEquals("https://partner.ndopify.cm/join", PartnerCentralUrls.of(Tenant(partnerCentralUrl = "https://partner.ndopify.cm/"), "/join"))
    }

    @Test
    fun `null without partner central URL`() {
        assertNull(PartnerCentralUrls.of(Tenant(partnerCentralUrl = null), "/join"))
        assertNull(PartnerCentralUrls.of(Tenant(partnerCentralUrl = " "), "/join"))
    }
}
