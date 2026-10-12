package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdUserClient
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.dto.User
import org.junit.jupiter.api.AfterEach
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentPartyTest {
    private val userClient = mock<NdUserClient>()
    private val currentParty = CurrentParty(userClient)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun signIn(userId: Long) {
        val token = JWT.create().withIssuer(JwtDecoder.ISSUER).withClaim(JwtPrincipal.CLAIM_USER_ID, userId)
            .sign(Algorithm.none())
        SecurityContextHolder.setContext(SecurityContextImpl(JwtAuthentication(JwtPrincipal(JWT.decode(token)))))
    }

    @Test
    fun party() {
        signIn(11L)
        doReturn(User(id = 11L, party = Party(id = 100L))).whenever(userClient).get(11L)

        assertEquals(100L, currentParty.get()?.id)
    }

    @Test
    fun `user without party`() {
        signIn(11L)
        doReturn(User(id = 11L, party = null)).whenever(userClient).get(11L)

        assertNull(currentParty.get())
    }

    @Test
    fun `user not found`() {
        signIn(11L)
        doReturn(null).whenever(userClient).get(11L)

        assertNull(currentParty.get())
    }

    @Test
    fun anonymous() {
        assertNull(currentParty.get())
        verify(userClient, never()).get(any())
    }
}
