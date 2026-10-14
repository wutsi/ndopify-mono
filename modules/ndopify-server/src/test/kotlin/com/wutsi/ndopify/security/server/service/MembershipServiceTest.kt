package com.wutsi.ndopify.security.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.server.dao.MembershipRepository
import com.wutsi.ndopify.security.server.dao.UserApplicationRepository
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MembershipServiceTest {
    private val dao = mock<MembershipRepository>()
    private val userApplicationDao = mock<UserApplicationRepository>()
    private val applicationService = mock<ApplicationService>()
    private val service = MembershipService(dao, userApplicationDao, applicationService)

    private val user = UserEntity(id = 1L, email = "ray.sponsible@gmail.com")
    private val party = PartyEntity(id = 100L)
    private val membership = MembershipEntity(id = 10L, user = user)
    private val application = ApplicationEntity(id = 3L, code = "partner-central")

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `current tenant`() {
        TenantContext.set(2L)

        assertEquals(2L, service.currentTenantId())
    }

    @Test
    fun `no current tenant`() {
        val ex = assertFailsWith<BadRequestException> { service.currentTenantId() }

        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, ex.error.code)
        assertEquals(HttpHeader.TENANT_ID, ex.error.parameter?.name)
    }

    @Test
    fun `deleted membership is not found`() {
        doReturn(membership.copy(deleted = true)).whenever(dao).findByUser(user)
        doReturn(membership.copy(deleted = true)).whenever(dao).findByPartyId(100L)

        assertNull(service.findByUserOrNull(user))
        assertNull(service.findByPartyIdOrNull(100L))
    }

    @Test
    fun `create membership`() {
        doReturn(null).whenever(dao).findByUser(user)
        doReturn(membership).whenever(dao).save(any())

        service.findOrCreate(user, party)

        val saved = argumentCaptor<MembershipEntity>()
        verify(dao).save(saved.capture())
        assertNull(saved.firstValue.id)
        assertNull(saved.firstValue.tenantId) // set by Hibernate from TenantContext
        assertEquals(user, saved.firstValue.user)
        assertEquals(party, saved.firstValue.party)
    }

    @Test
    fun `existing membership without party is linked to the party`() {
        doReturn(membership).whenever(dao).findByUser(user)
        doReturn(membership).whenever(dao).save(any())

        service.findOrCreate(user, party)

        verify(dao).save(membership.copy(party = party))
    }

    @Test
    fun `existing membership with a party is left untouched`() {
        val existing = membership.copy(party = PartyEntity(id = 999L))
        doReturn(existing).whenever(dao).findByUser(user)

        assertEquals(existing, service.findOrCreate(user, party))
        verify(dao, never()).save(any())
    }

    @Test
    fun `grant access twice does not duplicate`() {
        doReturn(application).whenever(applicationService).findByCode("partner-central")
        doReturn(Optional.of(UserApplicationEntity(id = 1L))).whenever(userApplicationDao)
            .findByMembershipAndApplication(membership, application)

        service.grantAccess(membership, "partner-central")

        verify(userApplicationDao, never()).save(any())
    }
}
