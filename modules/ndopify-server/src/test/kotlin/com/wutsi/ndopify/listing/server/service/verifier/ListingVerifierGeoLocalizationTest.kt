package com.wutsi.ndopify.listing.server.service.verifier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingVerifierGeoLocalizationTest {
    companion object {
        private const val CITY_ID = 10L
        private const val LAT = 3.8925
        private const val LONG = 11.5114

        // 1 km of latitude, in degrees
        private const val KM = 1.0 / 111.195
    }

    private val locationService = mock<LocationService>()
    private val verifier = ListingVerifierGeoLocalization(locationService)

    private val neighborhood = LocationEntity(
        id = 100L,
        parentId = CITY_ID,
        type = LocationType.NEIGHBORHOOD,
        name = "Bastos",
        latitude = LAT,
        longitude = LONG,
    )
    private val other = LocationEntity(id = 200L, parentId = CITY_ID, type = LocationType.NEIGHBORHOOD, name = "Ekoudou")

    @BeforeEach
    fun setUp() {
        doReturn(neighborhood).whenever(locationService).findByIdOrNull(neighborhood.id)
    }

    private fun listing(
        latitude: Double? = LAT,
        longitude: Double? = LONG,
        neighborhoodId: Long? = neighborhood.id,
    ) = ListingEntity(
        id = 1L,
        cityId = CITY_ID,
        neighborhoodId = neighborhoodId,
        latitude = latitude,
        longitude = longitude,
    )

    private fun mockNearest(location: LocationEntity, distance: Double) {
        doReturn(Pair(location, distance)).whenever(locationService)
            .findNearest(any(), any(), any(), any())
    }

    @Test
    fun `at the center of the neighborhood`() {
        mockNearest(neighborhood, 0.0)

        val result = verifier.verify(listing())

        assertTrue(result.success)
        assertNull(result.errorCode)
    }

    @Test
    fun `nearer to another neighborhood, within the slack`() {
        // 2 km from the claimed neighborhood, 1.5 km from the nearest
        mockNearest(other, 1.5)

        val result = verifier.verify(listing(latitude = LAT + 2 * KM))

        assertTrue(result.success)
    }

    @Test
    fun `nearer to another neighborhood, beyond the slack`() {
        // 2 km from the claimed neighborhood, 0.5 km from the nearest
        mockNearest(other, 0.5)

        val result = verifier.verify(listing(latitude = LAT + 2 * KM))

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD, result.errorCode)
    }

    @Test
    fun `beyond the max distance`() {
        // The claimed neighborhood is the nearest, but 6 km away
        mockNearest(neighborhood, 6.0)

        val result = verifier.verify(listing(latitude = LAT + 6 * KM))

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD, result.errorCode)
    }

    @Test
    fun `no nearest neighborhood`() {
        doReturn(null).whenever(locationService).findNearest(any(), any(), any(), any())

        val result = verifier.verify(listing(latitude = LAT + 2 * KM))

        assertTrue(result.success)
    }

    @Test
    fun `neighborhood without coordinates`() {
        doReturn(neighborhood.copy(latitude = null, longitude = null))
            .whenever(locationService).findByIdOrNull(neighborhood.id)

        val result = verifier.verify(listing())

        assertTrue(result.success)
    }

    @Test
    fun `missing latitude`() {
        val result = verifier.verify(listing(latitude = null))

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_MISSING_LAT_LONG, result.errorCode)
    }

    @Test
    fun `missing longitude`() {
        val result = verifier.verify(listing(longitude = null))

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_MISSING_LAT_LONG, result.errorCode)
    }

    @Test
    fun `missing neighborhood`() {
        val result = verifier.verify(listing(neighborhoodId = null))

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_MISSING_NEIGHBORHOOD, result.errorCode)
    }

    @Test
    fun `neighborhood not found`() {
        doReturn(null).whenever(locationService).findByIdOrNull(anyOrNull())

        val result = verifier.verify(listing())

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_INVALID_NEIGHBORHOOD, result.errorCode)
    }

    @Test
    fun `not a neighborhood`() {
        doReturn(neighborhood.copy(type = LocationType.CITY)).whenever(locationService).findByIdOrNull(neighborhood.id)

        val result = verifier.verify(listing())

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_INVALID_NEIGHBORHOOD, result.errorCode)
    }

    @Test
    fun `neighborhood of another city`() {
        doReturn(neighborhood.copy(parentId = 99L)).whenever(locationService).findByIdOrNull(neighborhood.id)

        val result = verifier.verify(listing())

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_INVALID_NEIGHBORHOOD, result.errorCode)
    }
}
