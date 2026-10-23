package com.wutsi.ndopify.npc.ui.profile

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdPartyClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.home.HomeController
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.BASTOS
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.DOUALA
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders `templates/index.html`, `templates/profile/edit.html` and `templates/profile/photo.html` through their
 * controllers with a real Thymeleaf engine, to catch template mistakes the controller tests can't see.
 */
class ProfileTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val kycClient = mock<NdKycClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(any())

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
                HomeController(
                    currentAgent,
                    kycClient,
                    assignmentClient,
                    mandateClient,
                    locationClient,
                    tenantContext,
                    clock,
                ),
                ProfileController(currentAgent, mock<NdAgentClient>(), mock<NdPartyClient>(), locationClient, tenantContext),
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

    // index.html

    @Test
    fun home() {
        val page = render(get("/"))

        assertEquals("Serge Ngono", page.selectFirst("[data-profile-name]")?.text())
        assertEquals(AGENT.party.photoUrl, page.selectFirst("[data-profile-photo] img")?.attr("src"))
        assertNull(page.selectFirst(".profile__initials"))
        assertEquals("Quartiers : Bonapriso, Akwa — Douala", page.selectFirst("[data-profile-area]")?.text())
        assertEquals("Démarcheur à Douala depuis 6 ans.", page.selectFirst("[data-profile-bio]")?.text())
        assertNull(page.selectFirst("[data-profile-incomplete]"))
        assertNull(page.selectFirst("[data-profile-saved]"))
        assertNull(page.selectFirst("[data-kyc-banner]"))
        assertEquals("/profile", page.select(".user-menu__item").first()?.attr("href"))
        assertNull(page.selectFirst("[data-user-email]"))
        assertEquals(AGENT.party.photoUrl, page.selectFirst("[data-user-photo]")?.attr("src"))
        assertNull(page.selectFirst("[data-user-initials]"))
    }

    @Test
    fun `home - signed out`() {
        SecurityContextHolder.clearContext()

        val page = render(get("/"))

        assertNull(page.selectFirst(".user-menu"))
    }

    @Test
    fun `home - incomplete profile`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = null), biography = null)).whenever(currentAgent).get()

        val page = render(get("/"))

        assertEquals("SN", page.selectFirst(".profile__initials")?.text())
        assertNull(page.selectFirst("[data-profile-photo] img"))
        assertEquals("SN", page.selectFirst("[data-user-initials]")?.text())
        assertNull(page.selectFirst("[data-user-photo]"))
        assertNotNull(page.selectFirst("[data-profile-bio-missing]"))
        assertEquals("/profile/photo", page.selectFirst("[data-profile-todo=photo] a")?.attr("href"))
        assertEquals("/profile", page.selectFirst("[data-profile-todo=bio] a")?.attr("href"))
    }

    @Test
    fun `home - only the biography is missing`() {
        doReturn(AGENT.copy(biography = "")).whenever(currentAgent).get()

        val page = render(get("/"))

        assertNull(page.selectFirst("[data-profile-todo=photo]"))
        assertNotNull(page.selectFirst("[data-profile-todo=bio]"))
    }

    @Test
    fun `home - after saving`() {
        assertEquals(
            "Votre profil a été enregistré.",
            render(get("/").flashAttr(ProfileRules.SAVED, ProfileRules.SAVED_PROFILE)).selectFirst("[data-profile-saved]")?.text(),
        )
        assertEquals(
            "Votre photo de profil a été enregistrée.",
            render(get("/").flashAttr(ProfileRules.SAVED, ProfileRules.SAVED_PHOTO)).selectFirst("[data-profile-saved]")?.text(),
        )
    }

    @Test
    fun `home - not verified yet - KYC banner`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()
        val banners = mapOf(
            null to ("todo" to "Vérifier mon identité"),
            KycStatus.SUBMITTED to ("pending" to "Voir le statut de mon dossier"),
            KycStatus.REJECTED to ("rejected" to "Voir le motif"),
        )
        banners.forEach { (status, banner) ->
            doReturn(status?.let { KycCase(id = "case-1", status = it) }).whenever(kycClient).current(100L)

            val page = render(get("/"))

            val shown = page.select("[data-kyc-banner]")
            assertEquals(listOf(banner.first), shown.map { it.attr("data-kyc-banner") })
            assertEquals(banner.second, shown.first()?.selectFirst("a")?.text())
            assertEquals("/kyc", shown.first()?.selectFirst("a")?.attr("href"))
            assertEquals("Serge Ngono", page.selectFirst("[data-profile-name]")?.text())
        }
    }

    // profile/edit.html

    @Test
    fun edit() {
        val page = render(get("/profile"))

        val form = page.selectFirst("form[data-join-form]")!!
        assertEquals("/profile", form.attr("action"))
        assertEquals("/locations", form.attr("data-locations-url"))
        assertEquals("Serge Ngono", page.selectFirst(".readonly")?.text())
        assertEquals("serge@gmail.com", page.selectFirst("#email")?.text())
        assertNull(page.selectFirst("input[name=email]"))
        assertEquals("+237670000001", page.selectFirst("#whatsappNumber")?.attr("value"))
        assertEquals("SENIOR", page.selectFirst("input[name=experienceLevel][checked]")?.attr("value"))
        assertEquals("INDEPENDANT", page.selectFirst("input[name=agentType][checked]")?.attr("value"))
        assertEquals(DOUALA.toString(), page.selectFirst("#cityId option[selected]")?.attr("value"))
        assertEquals(listOf("Bonapriso", "Akwa"), page.select("#neighborhoodIds option[selected]").map { it.text() })
        assertEquals("2 sur 3 sélectionnés", page.selectFirst("[data-neighborhood-counter]")?.text())
        assertEquals("Démarcheur à Douala depuis 6 ans.", page.selectFirst("#biography")?.text())
        assertEquals("500", page.selectFirst("#biography")?.attr("maxlength"))
        assertEquals("33 sur 500 caractères", page.selectFirst("#biography-counter")?.text())
        assertEquals("Changer la photo", page.selectFirst(".profile-photo-row a")?.text())
        assertTrue(page.select(".field__error[id]").all { it.hasAttr("hidden") })
        assertNull(page.selectFirst(".alert--error"))
        assertEquals("page", page.selectFirst(".user-menu__item[href=/profile]")?.attr("aria-current"))
    }

    @Test
    fun `edit - posts the version it was loaded with`() {
        doReturn(AGENT.copy(version = 3)).whenever(currentAgent).get()

        val page = render(get("/profile"))

        assertEquals("3", page.selectFirst("form input[type=hidden][name=version]")?.attr("value"))
    }

    @Test
    fun `edit - no e-mail`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(email = ""))).whenever(currentAgent).get()

        val page = render(get("/profile"))

        assertNull(page.selectFirst("#email"))
    }

    @Test
    fun `edit - new agent`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = null), cityId = null, neighborhoodIds = emptyList()))
            .whenever(currentAgent).get()

        val page = render(get("/profile"))

        assertEquals("Ajouter une photo", page.selectFirst(".profile-photo-row a")?.text())
        assertEquals("SN", page.selectFirst(".profile-photo__initials")?.text())
        assertNull(page.selectFirst("#cityId option[selected]"))
        assertFalse(page.selectFirst("[data-neighborhoods-empty]")!!.hasAttr("hidden"))
        assertTrue(page.selectFirst("[data-group=neighborhoodIds]")!!.hasAttr("hidden"))
    }

    @Test
    fun `edit - signed out`() {
        SecurityContextHolder.clearContext()

        val page = render(get("/profile"))

        assertNull(page.selectFirst(".user-menu"))
    }

    @Test
    fun `edit - with errors`() {
        val page = render(
            post("/profile")
                .param("whatsappNumber", "12")
                .param("experienceLevel", "JUNIOR")
                .param("agentType", "INDEPENDANT")
                .param("cityId", DOUALA.toString())
                .param("neighborhoodIds", BASTOS.toString()),
        )

        assertEquals("12", page.selectFirst("#whatsappNumber")?.attr("value"))
        assertFalse(page.selectFirst("#whatsappNumber-error")!!.hasAttr("hidden"))
        assertEquals("true", page.selectFirst("#whatsappNumber")?.attr("aria-invalid"))
        assertEquals("JUNIOR", page.selectFirst("input[name=experienceLevel][checked]")?.attr("value"))
    }

    // profile/photo.html

    @Test
    fun photo() {
        val page = render(get("/profile/photo"))

        val form = page.selectFirst("form[data-kyc-form]")!!
        assertEquals("/profile/photo", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("photo", page.selectFirst(".upload__input")?.attr("name"))
        assertEquals(AGENT.party.photoUrl, page.selectFirst("[data-preview] img")?.attr("src"))
        assertTrue(page.selectFirst("[data-placeholder]")!!.hasAttr("hidden"))
        assertEquals("Changer la photo", page.selectFirst("[data-upload-label]")?.text())
        assertTrue(page.selectFirst("#photo-error")!!.hasAttr("hidden"))
        assertNotNull(page.selectFirst("[data-step] [data-next]"))
    }

    @Test
    fun `photo - signed out`() {
        SecurityContextHolder.clearContext()

        val page = render(get("/profile/photo"))

        assertNull(page.selectFirst(".user-menu"))
    }

    @Test
    fun `photo - none yet, with an error`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = null))).whenever(currentAgent).get()

        val page = render(get("/profile/photo?error=too-large"))

        assertNull(page.selectFirst("[data-preview] img"))
        assertFalse(page.selectFirst("[data-placeholder]")!!.hasAttr("hidden"))
        assertEquals("Choisir une photo", page.selectFirst("[data-upload-label]")?.text())
        assertFalse(page.selectFirst("#photo-error")!!.hasAttr("hidden"))
        assertTrue(page.selectFirst("#photo-error")!!.text().contains("5 Mo"))
    }
}
