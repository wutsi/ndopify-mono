package com.wutsi.ndopify.listing.server.mail

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingUrlsTest {
    private val tenant = TenantEntity(id = 1L, partnerCentralUrl = "https://partner.ndopify.com/")
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `the fix URL is the listing page of Partner Central`() {
        assertEquals("https://partner.ndopify.com/listings/101", ListingUrls.fixUrl(tenant, listing))
        assertEquals(
            "https://partner.ndopify.com/listings/101",
            ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = "https://partner.ndopify.com"), listing),
        )
    }

    @Test
    fun `no partner central URL means no link`() {
        assertNull(ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = null), listing))
        assertNull(ListingUrls.fixUrl(tenant.copy(partnerCentralUrl = " "), listing))
    }

    @Test
    fun `a listing without an id means no link`() {
        assertNull(ListingUrls.fixUrl(tenant, ListingEntity()))
    }
}
