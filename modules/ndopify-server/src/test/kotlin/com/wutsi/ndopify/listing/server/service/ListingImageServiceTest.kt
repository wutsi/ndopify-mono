package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import java.net.URL
import java.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ListingImageServiceTest {
    private val dao = mock<ListingImageRepository>()
    private val listingService = mock<ListingService>()
    private val accessService = mock<ListingAccessService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val directUpload = mock<DirectUploadService>()
    private val service = ListingImageService(
        dao,
        listingService,
        accessService,
        storageProvider,
        directUpload,
        Clock.systemUTC(),
    )

    private val hero = ListingImageEntity(id = 7L, listingId = 101L, status = ImageStatus.VERIFIED)
    private val oldest = ListingImageEntity(id = 3L, listingId = 101L, status = ImageStatus.VERIFIED)
    private val listing = ListingEntity(id = 101L, heroImageId = 7L)

    companion object {
        private const val PREFIX = "listings/101/images"
    }

    @BeforeTest
    fun setUp() {
        doReturn(hero).whenever(dao).findByIdAndListingId(7L, 101L)
        doReturn(listOf(oldest, hero)).whenever(dao)
            .findByListingIdAndStatusInOrderById(101L, listOf(ImageStatus.VERIFIED))
        doReturn(listing).whenever(listingService).findById(101L)
        doReturn(null).whenever(dao).findByPath(any())
        doReturn(storage).whenever(storageProvider).get()
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

    @Test
    fun `presign - under the listing's prefix`() {
        val expected = PresignUploadResponse("https://s3.example.com/put", "listings/101/images/x.jpg")
        doReturn(expected).whenever(directUpload).presign(PREFIX, "image/jpeg")

        assertEquals(expected, service.presign(101L, "image/jpeg"))
        verify(accessService).ensureCanUpdate(listing)
    }

    @Test
    fun `presign - access denied`() {
        doThrow(ForbiddenException(Error(ErrorCode.HTTP_ACCESS_DENIED))).whenever(accessService).ensureCanUpdate(listing)

        assertFailsWith<ForbiddenException> { service.presign(101L, "image/jpeg") }
        verify(directUpload, never()).presign(any(), any())
    }

    @Test
    fun `attach - records a pending image`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn("image/png").whenever(directUpload).verify(path, PREFIX)
        doReturn(URL("https://cdn.example.com/$path")).whenever(storage).publicUrl(path)
        doAnswer { it.arguments[0] }.whenever(dao).save(any())

        val image = service.attach(101L, path)

        assertEquals(path, image.path)
        assertEquals("image/png", image.mimeType)
        assertEquals(101L, image.listingId)
        assertEquals(ImageStatus.PENDING_VERIFICATION, image.status)
        assertEquals("https://cdn.example.com/$path", image.url)
        verify(accessService).ensureCanUpdate(listing)
    }

    @Test
    fun `attach - idempotent`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        val existing = ListingImageEntity(id = 9L, listingId = 101L, path = path)
        doReturn(existing).whenever(dao).findByPath(path)

        assertEquals(existing, service.attach(101L, path))
        verify(directUpload, never()).verify(any(), any())
        verify(dao, never()).save(any())
    }

    @Test
    fun `attach - rejected upload`() {
        val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doThrow(BadRequestException(Error(ErrorCode.HTTP_INVALID_PARAMETER)))
            .whenever(directUpload).verify(path, PREFIX)

        assertFailsWith<BadRequestException> { service.attach(101L, path) }
        verify(dao, never()).save(any())
    }
}
