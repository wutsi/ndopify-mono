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
import com.wutsi.ndopify.security.server.dao.UserRepository
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.mockito.Mockito.mock
import java.time.Clock
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartyServiceTest {
    private val dao = mock<PartyRepository>()
    private val userDao = mock<UserRepository>()
    private val clock = mock<Clock>()
    private val service = PartyService(dao, userDao, clock)

    private val party = PartyEntity(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        email = "john.doe@wutsi.com",
    )

    @Test
    fun `update email with no linked user does not touch UserRepository`() {
        doReturn(null).whenever(dao).findByEmail(any())
        doReturn(null).whenever(userDao).findByPartyId(1L)
        doReturn(party.copy(email = "new@wutsi.com")).whenever(dao).save(any())

        service.update(party, UpdatePartyRequest(email = "new@wutsi.com"))

        verify(userDao, never()).save(any())
    }

    @Test
    fun `update email cascades to linked user`() {
        val user = UserEntity(id = 10L, email = "john.doe@wutsi.com")
        doReturn(null).whenever(dao).findByEmail("new@wutsi.com")
        doReturn(user).whenever(userDao).findByPartyId(1L)
        doReturn(Optional.empty<UserEntity>()).whenever(userDao).findByEmailIgnoreCase("new@wutsi.com")
        doReturn(party.copy(email = "new@wutsi.com")).whenever(dao).save(any())

        service.update(party, UpdatePartyRequest(email = "new@wutsi.com"))

        verify(userDao).save(user.copy(email = "new@wutsi.com"))
    }

    @Test
    fun `update without changing email does not cascade`() {
        val user = UserEntity(id = 10L, email = "john.doe@wutsi.com")
        doReturn(user).whenever(userDao).findByPartyId(1L)
        doReturn(party).whenever(dao).save(any())

        service.update(party, UpdatePartyRequest(firstName = "Jane"))

        verify(userDao, never()).save(any())
    }

    @Test
    fun `update email fails when a different user already owns it`() {
        val user = UserEntity(id = 10L, email = "john.doe@wutsi.com")
        val otherUser = UserEntity(id = 99L, email = "new@wutsi.com")
        doReturn(null).whenever(dao).findByEmail("new@wutsi.com")
        doReturn(user).whenever(userDao).findByPartyId(1L)
        doReturn(Optional.of(otherUser)).whenever(userDao).findByEmailIgnoreCase("new@wutsi.com")

        val ex = assertFailsWith<ConflictException> {
            service.update(party, UpdatePartyRequest(email = "new@wutsi.com"))
        }

        assertEquals(ErrorCode.USER_EMAIL_ALREADY_EXISTS, ex.error.code)
        verify(dao, never()).save(any())
        verify(userDao, never()).save(any())
    }
}
