package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.IMAGES
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.SENT
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders `templates/listing/photos.html` through [ListingPhotoController] with a real Thymeleaf engine. */
class ListingPhotoTemplateTest {
    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(IMAGES).whenever(listingClient).images(501)

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
            .standaloneSetup(ListingPhotoController(currentAgent, listingClient, locationClient, tenantContext))
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: MockHttpServletRequestBuilder = get("/listings/501/photos")): Document =
        Jsoup.parse(mvc.perform(request).andReturn().response.contentAsString)

    @Test
    fun draft() {
        val page = render()

        assertEquals("Photos · Annonce · Appartement, Bonapriso — Ndopify Partner Central", page.title())
        assertEquals("listing-photos", page.body().attr("data-page"))
        assertEquals("Photos", page.selectFirst("h1")?.text())
        assertEquals("Au moins 5 photos nettes, prises à la lumière du jour.", page.selectFirst("[data-section-instruction]")?.text())
        assertEquals("/listings/501", page.selectFirst(".back-link a")?.attr("href"))
        assertNotNull(page.selectFirst(".photo-tips"))

        val upload = page.selectFirst("form[data-photo-form]")!!
        assertEquals("/listings/501/photos", upload.attr("action"))
        assertEquals("post", upload.attr("method"))
        assertEquals("multipart/form-data", upload.attr("enctype"))
        val inputs = upload.select("input[type=file][name=photo]")
        assertEquals(2, inputs.size)
        assertEquals("environment", inputs[0].attr("capture"))
        assertTrue(inputs[1].hasAttr("multiple"))
        // Without JS, the files are sent with this button
        assertEquals("Envoyer les photos", upload.selectFirst("button[type=submit][data-photo-submit]")?.text())

        assertEquals("2 photos · supprimez la photo refusée et reprenez-la", page.selectFirst("[data-photo-count]")?.text())
        assertEquals("Terminé", page.selectFirst(".form__actions a")?.text())
        assertTrue(page.selectFirst("#photos-error")!!.hasAttr("hidden"))
    }

    @Test
    fun photos() {
        val photos = render().select("[data-photos] > li.photo")

        assertEquals(3, photos.size)
        assertEquals("https://cdn/1.jpg", photos[0].selectFirst("img")?.attr("src"))
        assertEquals("Photo 1", photos[0].selectFirst("img")?.attr("alt"))
        assertEquals("Envoyée", photos[0].selectFirst("[data-photo-note]")?.text())
        assertEquals("Vérifiée", photos[1].selectFirst("[data-photo-note]")?.text())
        assertTrue(photos[2].hasClass("photo--rejected"))
        assertEquals("Floue", photos[2].selectFirst("[data-photo-note]")?.text())
        val delete = photos[2].selectFirst("form[data-photo-delete-form]")!!
        assertEquals("/listings/501/photos/3/delete", delete.attr("action"))
        assertEquals("post", delete.attr("method"))
        assertEquals("Supprimer la photo 3", delete.selectFirst("button[data-photo-delete]")?.attr("aria-label"))
    }

    @Test
    fun error() {
        val page = render(get("/listings/501/photos").flashAttr(ListingPhotoController.PHOTO_ERROR, "bail.jpg dépasse 5 Mo."))

        val error = page.selectFirst("#photos-error")!!
        assertFalse(error.hasAttr("hidden"))
        assertEquals("bail.jpg dépasse 5 Mo.", error.text())
        assertEquals("photos-error", page.selectFirst("input[type=file]")?.attr("aria-describedby"))
    }

    @Test
    fun full() {
        doReturn((1L..20L).map { ListingImage(id = it, url = "https://cdn/$it.jpg", status = ImageStatus.VERIFIED) })
            .whenever(listingClient).images(501)

        val page = render()

        assertTrue(page.select("input[type=file]").all { it.hasAttr("disabled") })
        assertTrue(page.selectFirst("button[data-photo-submit]")!!.hasAttr("disabled"))
    }

    @Test
    fun `read-only once sent`() {
        doReturn(LISTING.copy(status = ListingStatus.UNDER_VERIFICATION, modifiedAt = SENT)).whenever(listingClient).get(501)

        val page = render()

        assertNotNull(page.selectFirst("[data-section-readonly]"))
        assertNull(page.selectFirst("form[data-photo-form]"))
        assertNull(page.selectFirst(".photo-tips"))
        assertNull(page.selectFirst("form[data-photo-delete-form]"))
        assertEquals("2 photos", page.selectFirst("[data-photo-count]")?.text())
        assertEquals("Retour à l'annonce", page.selectFirst(".form__actions a")?.text())
    }

    @Test
    fun locked() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val page = render()

        assertEquals("Cette annonce n'est pas disponible", page.selectFirst("[data-section-locked] h1")?.text())
        assertNull(page.selectFirst("[data-photos]"))
    }
}
