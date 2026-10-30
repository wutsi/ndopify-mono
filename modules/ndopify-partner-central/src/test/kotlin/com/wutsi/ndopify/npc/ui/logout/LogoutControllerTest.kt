package com.wutsi.ndopify.npc.ui.logout

import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogoutControllerTest {
    private lateinit var mvc: MockMvc
    private val accessTokenService = mock<AccessTokenService>()

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.standaloneSetup(LogoutController(accessTokenService)).build()
    }

    @Test
    fun logout() {
        val session = MockHttpSession()

        val result = mvc.perform(post("/logout").session(session)).andReturn()

        verify(accessTokenService).remove()
        assertEquals("redirect:/login", result.modelAndView?.viewName)
        assertTrue(session.isInvalid)
    }

    @Test
    fun `logout is a POST - another site can't sign the agent out with a link`() {
        val result = mvc.perform(get("/logout")).andReturn()

        assertEquals(405, result.response.status)
        verify(accessTokenService, never()).remove()
    }

    @Test
    fun logoutWithoutSession() {
        val result = mvc.perform(post("/logout")).andReturn()

        verify(accessTokenService).remove()
        assertEquals("redirect:/login", result.modelAndView?.viewName)
    }
}
