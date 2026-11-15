package com.wutsi.ndopify.npc.ui.telegram

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/telegram.html` through its controller with a real Thymeleaf engine. */
class TelegramTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(AGENT).whenever(currentAgent).get()

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
        mvc = MockMvcBuilders
            .standaloneSetup(
                TelegramController(
                    currentAgent,
                    mock<NdAgentClient>(),
                    "ndopify_bot",
                    Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC),
                    15,
                ),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    @Test
    fun `not connected - the three steps and the button`() {
        val page = render(get("/telegram"))

        assertEquals(3, page.select(".connect-steps > li").size)
        val botLink = page.selectFirst("a[href=https://t.me/ndopify_bot]")!!
        assertEquals("_blank", botLink.attr("target"))
        assertEquals("noopener", botLink.attr("rel"))

        val form = page.selectFirst("form[data-telegram-form]")!!
        assertEquals("/telegram/code", form.attr("action"))
        assertEquals("Recevoir mon code par e-mail", form.selectFirst("button")?.text())

        assertNull(page.selectFirst("[data-telegram-connected]"))
    }

    @Test
    fun `connected - the connected status and no form`() {
        doReturn(AGENT.copy(telegramConnected = true)).whenever(currentAgent).get()

        val page = render(get("/telegram"))

        assertEquals("Votre compte Telegram est connecté.", page.selectFirst("[data-telegram-connected]")?.text())
        assertNull(page.selectFirst("form[data-telegram-form]"))
    }

    @Test
    fun `the code was sent`() {
        val page = render(get("/telegram").flashAttr(TelegramRules.SENT, true))

        val alert = page.selectFirst("[data-telegram-sent]")!!
        assertEquals("status", alert.attr("role"))
        assertEquals("Code envoyé", alert.selectFirst(".alert__title")?.text())
        assertTrue(alert.text().contains("/connect"))
    }

    @Test
    fun `the code could not be sent`() {
        val page = render(get("/telegram").flashAttr(TelegramRules.ERROR, TelegramRules.SEND_FAILED))

        val alert = page.selectFirst("[data-telegram-error]")!!
        assertEquals("alert", alert.attr("role"))
        assertTrue(alert.text().contains(TelegramRules.SEND_FAILED))
        assertNotNull(page.selectFirst("[data-telegram-error] .alert__title"))
    }
}
