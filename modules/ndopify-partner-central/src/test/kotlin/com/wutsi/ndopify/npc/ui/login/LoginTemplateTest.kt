package com.wutsi.ndopify.npc.ui.login

import com.nhaarman.mockitokotlin2.mock
import com.wutsi.ndopify.npc.client.ndopify.NdAuthClient
import com.wutsi.ndopify.npc.client.ndopify.NdOtpClient
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Renders `templates/login.html` through [LoginController] with a real Thymeleaf engine, to catch template mistakes
 * (expressions, conditions, hidden fields) that [LoginControllerTest] can't see.
 */
class LoginTemplateTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val pending = PendingLogin(email = "ray@gmail.com", expiresAt = now.toEpochMilli() + 300_000)
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val engine = SpringTemplateEngine().apply {
            setTemplateResolver(
                ClassLoaderTemplateResolver().apply {
                    prefix = "templates/"
                    suffix = ".html"
                    characterEncoding = "UTF-8"
                },
            )
        }
        val viewResolver = ThymeleafViewResolver().apply {
            templateEngine = engine
            characterEncoding = "UTF-8"
        }
        val controller = LoginController(
            mock<NdOtpClient>(),
            mock<NdAuthClient>(),
            mock<AccessTokenService>(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(viewResolver)
            .build()
        session = MockHttpSession()
    }

    @Test
    fun emailStepRendersTheRememberedFragment() {
        session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42", fragment = "match-7"))

        val page = render(get("/login"))

        assertEquals("match-7", page.selectFirst("form[data-login-form] input[name=fragment]")?.`val`())
    }

    @Test
    fun codeStepHasNoFragmentField() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
        session.setAttribute(SignInTarget.SESSION_ATTRIBUTE, SignInTarget("/mandates/42", fragment = "match-7"))

        val page = render(get("/login?step=code"))

        assertNull(page.selectFirst("input[name=fragment]"))
    }

    private fun render(request: MockHttpServletRequestBuilder): Document {
        val response = mvc.perform(request.session(session)).andReturn().response
        assertEquals(200, response.status)
        return Jsoup.parse(response.getContentAsString(Charsets.UTF_8))
    }
}
