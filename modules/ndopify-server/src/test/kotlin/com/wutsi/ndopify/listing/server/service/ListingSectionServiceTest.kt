package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.dao.ListingSectionRepository
import com.wutsi.ndopify.listing.server.domain.ImageKind
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.domain.ListingSectionEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ListingSectionServiceTest {
    private val dao = mock<ListingSectionRepository>()
    private val imageDao = mock<ListingImageRepository>()
    private val service = ListingSectionService(dao, imageDao)

    private val listing = ListingEntity(id = 501L)

    private fun images(vararg kinds: ImageKind) = kinds.mapIndexed { index, kind ->
        ListingImageEntity(id = index + 1L, listingId = 501L, kind = kind, status = ImageStatus.PENDING_VERIFICATION)
    }

    private fun photos(): ListingSectionSummary =
        service.summaries(listing).first { summary -> summary.section == ListingSection.PHOTOS }

    private fun mockImages(images: List<ListingImageEntity>) {
        doReturn(emptyList<ListingSectionEntity>()).whenever(dao).findByListingId(501L)
        doReturn(images).whenever(imageDao).findByListingIdOrderById(501L)
    }

    @Test
    fun `five photos`() {
        mockImages(images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.VERIFIED, photos().status)
    }

    @Test
    fun `five plans and maps - not done`() {
        mockImages(images(ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP))

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `one photo among the plans and maps is enough`() {
        mockImages(images(ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.VERIFIED, photos().status)
    }

    @Test
    fun `four images are not enough`() {
        mockImages(images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `a rejected image leaves the section to verify`() {
        mockImages(
            images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.MAP).map {
                it.copy(status = ImageStatus.VERIFIED)
            } + ListingImageEntity(id = 9L, listingId = 501L, status = ImageStatus.REJECTED),
        )

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `a rejected photo does not count as the photo`() {
        mockImages(
            images(ImageKind.MAP, ImageKind.MAP, ImageKind.MAP, ImageKind.MAP).map {
                it.copy(status = ImageStatus.VERIFIED)
            } + ListingImageEntity(id = 9L, listingId = 501L, kind = ImageKind.PHOTO, status = ImageStatus.REJECTED),
        )

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }
}
