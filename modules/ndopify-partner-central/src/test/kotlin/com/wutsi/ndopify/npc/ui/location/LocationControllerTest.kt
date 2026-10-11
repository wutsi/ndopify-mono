package com.wutsi.ndopify.npc.ui.location

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.refdata.dto.Tenant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationControllerTest {
    private val client = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        doReturn(Tenant(id = 1, countryCode = "CM")).whenever(tenantContext).getTenant()
        doReturn(
            SearchLocationResponse(
                locations = listOf(Location(id = 2370201, name = "Bafoussam", type = LocationType.CITY, country = "CM")),
            ),
        ).whenever(client).search(any())

        mvc = MockMvcBuilders.standaloneSetup(LocationController(client, tenantContext)).build()
    }

    @Test
    fun search() {
        val result = mvc.perform(get("/locations?q=baf&type=CITY&limit=10&offset=20")).andReturn()

        assertEquals(HttpStatus.OK.value(), result.response.status)
        assertTrue(result.response.contentAsString.contains("\"name\":\"Bafoussam\""))
        assertEquals("max-age=3600, public", result.response.getHeader(HttpHeaders.CACHE_CONTROL))
        assertEquals(
            SearchLocationRequest(
                keyword = "baf",
                types = listOf(LocationType.CITY),
                country = "CM",
                limit = 10,
                offset = 20,
            ),
            forwarded(),
        )
    }

    @Test
    fun searchNeighborhoodsByCity() {
        mvc.perform(get("/locations?type=NEIGHBORHOOD&parent-id=2370201")).andReturn()

        val request = forwarded()
        assertEquals(listOf(LocationType.NEIGHBORHOOD), request.types)
        assertEquals(2370201L, request.parentId)
    }

    @Test
    fun searchByIds() {
        mvc.perform(get("/locations?id=1&id=2&type=CITY&type=NEIGHBORHOOD")).andReturn()

        val request = forwarded()
        assertEquals(listOf(1L, 2L), request.ids)
        assertEquals(listOf(LocationType.CITY, LocationType.NEIGHBORHOOD), request.types)
    }

    @Test
    fun defaultsToCities() {
        mvc.perform(get("/locations")).andReturn()

        val request = forwarded()
        assertEquals(listOf(LocationType.CITY), request.types)
        assertEquals(20, request.limit)
        assertEquals(0, request.offset)
    }

    @Test
    fun typesOtherThanCityOrNeighborhoodAreDropped() {
        mvc.perform(get("/locations?type=COUNTRY&type=STATE&type=NEIGHBORHOOD")).andReturn()

        assertEquals(listOf(LocationType.NEIGHBORHOOD), forwarded().types)
    }

    @Test
    fun onlyDisallowedTypesFallBackToCities() {
        mvc.perform(get("/locations?type=COUNTRY")).andReturn()

        assertEquals(listOf(LocationType.CITY), forwarded().types)
    }

    @Test
    fun countryIsAlwaysTheTenantOne() {
        mvc.perform(get("/locations?country=CA")).andReturn()

        assertEquals("CM", forwarded().country)
    }

    @Test
    fun limitIsCapped() {
        mvc.perform(get("/locations?limit=10000")).andReturn()

        assertEquals(LocationController.MAX_LIMIT, forwarded().limit)
    }

    @Test
    fun limitAndOffsetAreKeptPositive() {
        mvc.perform(get("/locations?limit=0&offset=-5")).andReturn()

        val request = forwarded()
        assertEquals(1, request.limit)
        assertEquals(0, request.offset)
    }

    private fun forwarded(): SearchLocationRequest {
        val request = argumentCaptor<SearchLocationRequest>()
        verify(client).search(request.capture())
        return request.firstValue
    }
}
