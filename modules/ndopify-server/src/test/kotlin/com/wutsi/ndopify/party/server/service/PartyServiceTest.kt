package com.wutsi.ndopify.party.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import org.mockito.Mockito.mock
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartyServiceTest {
    private val dao = mock<PartyRepository>()
    private val clock = mock<Clock>()
    private val service = PartyService(dao, mock(StorageServiceProvider::class.java), clock)

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
}
