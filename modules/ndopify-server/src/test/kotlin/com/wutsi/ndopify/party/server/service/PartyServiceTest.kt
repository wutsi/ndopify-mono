package com.wutsi.ndopify.party.server.service

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
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import java.net.URL
import java.time.Clock
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartyServiceTest {
    private val dao = mock<PartyRepository>()
    private val accessService = mock<PartyAccessService>()
    private val storage = mock<StorageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val directUpload = mock<DirectUploadService>()
    private val clock = mock<Clock>()
    private val service = PartyService(dao, accessService, storageProvider, directUpload, clock)

    private val party = PartyEntity(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@wutsi.com",
    )

    @Test
    fun `update email`() {
        doReturn(null).whenever(dao).findByEmail("new@wutsi.com")
        doReturn(party.copy(email = "new@wutsi.com")).whenever(dao).save(any())

        val updated = service.update(party, UpdatePartyRequest(email = "New@Wutsi.com"))

        assertEquals("new@wutsi.com", updated.email)
    }

    @Test
    fun `update email fails when another party of the tenant already has it`() {
        doReturn(PartyEntity(id = 99L, email = "new@wutsi.com")).whenever(dao).findByEmail("new@wutsi.com")

        val ex = assertFailsWith<ConflictException> {
            service.update(party, UpdatePartyRequest(email = "new@wutsi.com"))
        }

        assertEquals(ErrorCode.PARTY_EMAIL_ALREADY_EXISTS, ex.error.code)
        verify(dao, never()).save(any())
    }

    @Test
    fun `upload photo - another party's photo`() {
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        doReturn(storage).whenever(storageProvider).get()
        doThrow(ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED)))
            .whenever(accessService).ensureIsParty(party)

        assertFailsWith<ForbiddenException> { service.uploadPhoto(1L, mock()) }

        verify(accessService).ensureIsParty(party)
        verify(storage, never()).store(any(), any(), any())
        verify(dao, never()).save(any())
    }

    @Test
    fun `presign - under the party's prefix`() {
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        val expected = PresignUploadResponse("https://s3.example.com/put", "party/1/photo/x.jpg")
        doReturn(expected).whenever(directUpload).presign("party/1/photo", "image/jpeg")

        assertEquals(expected, service.presign(1L, "image/jpeg"))
        verify(accessService).ensureIsParty(party)
    }

    @Test
    fun `presign - another party's photo`() {
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        doThrow(ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED)))
            .whenever(accessService).ensureIsParty(party)

        assertFailsWith<ForbiddenException> { service.presign(1L, "image/jpeg") }
        verify(directUpload, never()).presign(any(), any())
    }

    @Test
    fun `attach - records the new photo url`() {
        val path = "party/1/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        doReturn("image/png").whenever(directUpload).verify(path, "party/1/photo")
        doReturn(storage).whenever(storageProvider).get()
        doReturn(URL("https://cdn.example.com/$path")).whenever(storage).publicUrl(path)
        doAnswer { it.arguments[0] }.whenever(dao).save(any<PartyEntity>())

        val attached = service.attach(1L, path)

        assertEquals("https://cdn.example.com/$path", attached.photoUrl)
        verify(accessService).ensureIsParty(party)
    }

    @Test
    fun `attach - rejected upload leaves the photo unchanged`() {
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        doThrow(BadRequestException(error = Error(code = ErrorCode.HTTP_INVALID_PARAMETER)))
            .whenever(directUpload).verify(any(), any())

        assertFailsWith<BadRequestException> { service.attach(1L, "party/1/photo/x.png") }
        verify(dao, never()).save(any())
    }

    @Test
    fun `attach - another party's photo`() {
        doReturn(Optional.of(party)).whenever(dao).findById(1L)
        doThrow(ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED)))
            .whenever(accessService).ensureIsParty(party)

        assertFailsWith<ForbiddenException> { service.attach(1L, "party/1/photo/x.png") }
        verify(directUpload, never()).verify(any(), any())
        verify(dao, never()).save(any())
    }
}
