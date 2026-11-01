package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.domain.RoleEntity
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import com.wutsi.ndopify.security.server.service.AccessTokenProperties
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchLinkTokenServiceTest {
    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val applicationService = mock<ApplicationService>()
    private val userApplicationService = mock<UserApplicationService>()
    private val accessTokenService = mock<AccessTokenService>()
    private val properties = AccessTokenProperties(ttl = mapOf(MatchLinkTokenService.TTL_KEY to Duration.ofDays(2)))
    private val service = MatchLinkTokenService(
        userService,
        membershipService,
        applicationService,
        userApplicationService,
        accessTokenService,
        properties,
    )

    private val party = PartyEntity(id = 100L, email = "ray.sponsible@gmail.com")
    private val customer = CustomerEntity(id = 10L, party = party)
    private val user = UserEntity(id = 13L, email = "ray.sponsible@gmail.com")
    private val membership = MembershipEntity(id = 1L, tenantId = 1L, user = user, party = party)
    private val publicPortal = ApplicationEntity(id = 1L, code = ApplicationCode.PUBLIC_PORTAL)

    @BeforeTest
    fun setUp() {
        doReturn(publicPortal).whenever(applicationService).findByCode(ApplicationCode.PUBLIC_PORTAL)
        doReturn("xxx.yyy.zzz").whenever(accessTokenService).create(any(), any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `member - its token`() {
        doReturn(membership).whenever(membershipService).findByPartyIdOrNull(100L)
        doReturn(UserApplicationEntity(membership = membership, roles = listOf(RoleEntity(code = "customer"))))
            .whenever(userApplicationService).findByMembershipAndApplicationOrNull(membership, publicPortal)

        assertEquals("xxx.yyy.zzz", service.create(customer))

        verify(membershipService).grantAccess(membership, ApplicationCode.PUBLIC_PORTAL)
        verify(accessTokenService).create(
            application = ApplicationCode.PUBLIC_PORTAL,
            userId = 13L,
            roles = arrayOf("customer"),
            tenantId = 1L,
            ttlSeconds = 2 * 24 * 3600,
            partyId = 100L,
        )
        verify(userService, never()).findByEmailOrCreate(any())
    }

    @Test
    fun `not a member - one from its e-mail`() {
        doReturn(user).whenever(userService).findByEmailOrCreate("ray.sponsible@gmail.com")
        doReturn(membership).whenever(membershipService).findOrCreate(user, party)

        assertEquals("xxx.yyy.zzz", service.create(customer))

        verify(membershipService).grantAccess(membership, ApplicationCode.PUBLIC_PORTAL)
        verify(accessTokenService).create(
            eq(ApplicationCode.PUBLIC_PORTAL),
            eq(13L),
            eq(emptyArray()),
            eq(1L),
            eq(2 * 24 * 3600),
            eq(100L),
        )
    }

    @Test
    fun `no e-mail - no token`() {
        assertNull(service.create(customer.copy(party = party.copy(email = ""))))

        verify(userService, never()).findByEmailOrCreate(any())
        verify(membershipService, never()).grantAccess(any(), any())
    }

    @Test
    fun `e-mail of another party's member - no token`() {
        doReturn(user).whenever(userService).findByEmailOrCreate("ray.sponsible@gmail.com")
        doReturn(membership.copy(party = PartyEntity(id = 999L))).whenever(membershipService).findOrCreate(user, party)

        assertNull(service.create(customer))

        verify(membershipService, never()).grantAccess(any(), any())
    }
}
