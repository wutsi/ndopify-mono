package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/SearchMandateEndpoint.sql"])
class SearchMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun all() {
        val mandates = search("")
        assertEquals(listOf(1L, 2L, 3L), mandates.map { it.id })

        val mandate = mandates[0]
        assertEquals(10L, mandate.customerId)
        assertNull(mandate.agentId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertEquals(MandateType.SEARCH_RENTAL, mandate.mandateType)
        assertEquals(ListingType.RENTAL, mandate.listingType)
        assertEquals(PropertyType.APARTMENT, mandate.propertyType)
        assertEquals(2370201L, mandate.cityId)
        assertEquals(listOf(111L, 222L), mandate.neighborhoodIds.sorted())
        assertEquals("2", mandate.rooms)
        assertEquals(150000L, mandate.price.amount)
        assertEquals("XAF", mandate.price.currency)
        assertEquals(PeriodicityType.MONTHLY, mandate.price.periodicity)
    }

    @Test
    fun `by ids`() {
        assertEquals(listOf(1L, 3L), search("id=1&id=3").map { it.id })
    }

    @Test
    fun `by agent ids`() {
        assertEquals(listOf(2L, 3L), search("agent-id=20").map { it.id })
    }

    @Test
    fun `by customer ids`() {
        assertEquals(listOf(3L), search("customer-id=11").map { it.id })
    }

    @Test
    fun `by city ids`() {
        assertEquals(listOf(3L), search("city-id=2220957").map { it.id })
    }

    @Test
    fun `by neighborhood ids`() {
        assertEquals(listOf(1L, 2L), search("neighborhood-id=111&neighborhood-id=222").map { it.id })
    }

    @Test
    fun `by mandate type`() {
        assertEquals(listOf(2L), search("mandate-type=RENTAL").map { it.id })
    }

    @Test
    fun `by listing type`() {
        assertEquals(listOf(1L, 3L), search("listing-type=RENTAL").map { it.id })
    }

    @Test
    fun `by statuses`() {
        assertEquals(listOf(2L, 3L), search("status=ASSIGNED&status=PUBLISHED").map { it.id })
    }

    @Test
    fun `no match`() {
        assertTrue(search("id=999").isEmpty())
    }

    @Test
    fun `limit and offset`() {
        assertEquals(listOf(2L), search("limit=1&offset=1").map { it.id })
    }

    @Test
    fun `mandate from another tenant is not returned`() {
        assertTrue(search("id=4").isEmpty())
    }

    @Test
    fun `switching tenant context reveals only that tenant's mandate`() {
        overrideTenantId = 2L

        assertEquals(listOf(4L), search("").map { it.id })
    }

    private fun search(query: String) =
        rest.getForEntity("/v1/mandates?$query", SearchMandateResponse::class.java).body!!.mandates
}
