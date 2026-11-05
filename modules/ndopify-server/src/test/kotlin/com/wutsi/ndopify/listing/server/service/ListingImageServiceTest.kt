package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import java.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingImageServiceTest {
    private val dao = mock<ListingImageRepository>()
    private val service = ListingImageService(dao, mock(), mock(), mock(), Clock.systemUTC())

    private val hero = ListingImageEntity(id = 7L, listingId = 101L, status = ImageStatus.VERIFIED)
    private val oldest = ListingImageEntity(id = 3L, listingId = 101L, status = ImageStatus.VERIFIED)
    private val listing = ListingEntity(id = 101L, heroImageId = 7L)

    @BeforeTest
    fun setUp() {
        doReturn(hero).whenever(dao).findByIdAndListingId(7L, 101L)
        doReturn(listOf(oldest, hero)).whenever(dao)
            .findByListingIdAndStatusInOrderById(101L, listOf(ImageStatus.VERIFIED))
    }

    @Test
    fun `cover - the hero image`() {
        assertEquals(hero, service.cover(listing))
    }

    @Test
    fun `cover - no hero, the oldest verified photo`() {
        assertEquals(oldest, service.cover(listing.copy(heroImageId = null)))
    }

    @Test
    fun `cover - hero not verified, the oldest verified photo`() {
        doReturn(hero.copy(status = ImageStatus.REJECTED)).whenever(dao).findByIdAndListingId(7L, 101L)

        assertEquals(oldest, service.cover(listing))
    }

    @Test
    fun `cover - no verified photo`() {
        doReturn(emptyList<ListingImageEntity>()).whenever(dao)
            .findByListingIdAndStatusInOrderById(101L, listOf(ImageStatus.VERIFIED))

        assertNull(service.cover(listing.copy(heroImageId = null)))
    }
}
