package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/SearchMandateEndpoint.sql"])
class SearchMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Test
    fun all() {
        val mandates = search("")
        assertEquals(listOf(1L, 2L, 3L), mandates.map { it.id })

        val mandate = mandates[0]
        assertEquals(10L, mandate.customerId)
        assertNull(mandate.agentId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
        assertEquals(MandateType.SEARCH, mandate.mandateType)
        assertEquals(ListingType.RENTAL, mandate.listingType)
        assertEquals(PropertyType.APARTMENT, mandate.propertyType)
        assertEquals(2370201L, mandate.cityId)
        assertEquals(listOf(111L, 222L), mandate.neighborhoodIds.sorted())
        assertEquals("2", mandate.rooms)
        assertEquals(150000L, mandate.price.amount)
        assertEquals("XAF", mandate.price.currency)
        assertEquals(PeriodicityType.MONTHLY, mandate.price.periodicity)
        assertEquals(PropertyCategory.RESIDENTIAL, mandate.propertyCategory)
        assertEquals(FurnitureType.FULLY_FURNISHED, mandate.furnitureType)
        assertEquals("1", mandate.parkings)
        assertEquals(80, mandate.area)
        assertTrue(mandate.waterMeter)
        assertTrue(mandate.generatorSet)
        assertTrue(mandate.roadPaved)
        assertTrue(mandate.securityGuard)
        assertFalse(mandate.solarPanel)
        assertEquals(MovingPeriod.WITHIN_1_MONTH, mandate.movingPeriod)
        assertNull(mandate.contractId)
    }

    @Test
    fun `never its contract's signed PDF link`() {
        val json = rest.getForEntity("/v1/mandates?id=3", String::class.java).body!!

        assertFalse(json.contains("signedContentUrl"))
        assertFalse(json.contains("MNDT-2024-000003.pdf"))
    }

    @Test
    fun `listing's availability date`() {
        assertEquals(LocalDate.of(2024, 7, 1), search("id=2").single().availableFrom)
    }

    @Test
    fun `its contract's id only`() {
        val json = rest.getForEntity("/v1/mandates?id=2&id=3", String::class.java).body!!

        assertEquals(listOf("c-2", "c-3"), search("id=2&id=3").map { it.contractId })
        assertFalse(json.contains("MNDT-2024-000003"))
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
        assertEquals(listOf(2L), search("mandate-type=LISTING").map { it.id })
    }

    @Test
    fun `by listing type`() {
        assertEquals(listOf(1L, 3L), search("listing-type=RENTAL").map { it.id })
    }

    @Test
    fun `by statuses`() {
        assertEquals(listOf(2L, 3L), search("status=ASSIGNED&status=APPROVED").map { it.id })
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

    @Test
    fun `query count does not grow with the number of mandates`() {
        // One with a contract, whose id is the mandate's own column: no query for the contracts
        val one = countQueries { search("id=3") }
        val all = countQueries { search("") }

        assertEquals(one, all)
    }

    private fun search(query: String) =
        rest.getForEntity("/v1/mandates?$query", SearchMandateResponse::class.java).body!!.mandates
}
