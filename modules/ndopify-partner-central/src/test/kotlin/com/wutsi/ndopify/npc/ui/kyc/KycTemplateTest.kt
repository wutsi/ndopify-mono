package com.wutsi.ndopify.npc.ui.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Renders `templates/kyc.html` through [KycController] with a real Thymeleaf engine, to catch template mistakes
 * (expressions, conditions, views) that [KycControllerTest] can't see.
 */
class KycTemplateTest {
    companion object {
        const val PARTY_ID = 100L
    }

    private val kycClient = mock<NdKycClient>()
    private val currentParty = mock<CurrentParty>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val front = KycImage(id = "img-front", imageType = KycImageType.FRONT)
    private val back = KycImage(id = "img-back", imageType = KycImageType.BACK)
    private val selfie = KycImage(id = "img-selfie", imageType = KycImageType.SELFIE)

    // Magic numbers are enough for Tika to tell the type from the bytes.
    private val pdf = "%PDF-1.4\n".toByteArray() + ByteArray(64)

    private fun case(status: KycStatus = KycStatus.PENDING, images: List<KycImage> = emptyList(), errorCode: String? = null) =
        KycCase(id = "case-1", partyId = PARTY_ID, status = status, images = images, errorCode = errorCode)

    private fun given(case: KycCase?) {
        doReturn(case).whenever(kycClient).current(PARTY_ID)
    }

    private fun photo(bytes: ByteArray, contentType: String = "image/jpeg") =
        MockMultipartFile("photo", "photo.jpg", contentType, bytes)

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        doReturn(Party(id = PARTY_ID, firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"))
            .whenever(currentParty).get()
        whenever(kycClient.imageUrl(any())).thenAnswer { invocation -> "https://s3/${invocation.getArgument<String>(0)}.jpg" }

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
        mvc = MockMvcBuilders.standaloneSetup(KycController(kycClient, currentParty))
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(viewResolver)
            .build()
        session = MockHttpSession()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun render(request: AbstractMockHttpServletRequestBuilder<*>): Document =
        Jsoup.parse(mvc.perform(request.session(session)).andReturn().response.contentAsString)

    @Test
    fun front() {
        given(case())

        val page = render(get("/kyc"))

        assertEquals("Étape 1 sur 4", page.selectFirst("[data-progress-label]")?.text())
        assertEquals(listOf("front"), page.select("[data-step]").map { it.attr("data-step") })
        val form = page.selectFirst("form[data-kyc-form]")!!
        assertEquals("/kyc/front", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("photo", page.selectFirst(".upload__input")?.attr("name"))
        assertNull(page.selectFirst("[data-preview] img"))
        assertNull(page.selectFirst("[data-prev]")) // the first step has nowhere to go back to
        assertEquals("Suivant", page.selectFirst("[data-next]")?.text())
        assertTrue(page.selectFirst(".field__error")!!.hasAttr("hidden"))
        assertNull(page.selectFirst("[data-user-email]"))
        assertEquals("RS", page.selectFirst("[data-user-initials]")?.text())
        assertNull(page.selectFirst("[data-user-photo]"))
        assertNull(page.selectFirst("[data-status]"))
    }

    @Test
    fun `signed out`() {
        given(case())
        SecurityContextHolder.clearContext()

        val page = render(get("/kyc"))

        assertNull(page.selectFirst(".user-menu"))
    }

    @Test
    fun `back with its photo`() {
        given(case(images = listOf(front, back)))

        val page = render(get("/kyc?step=back"))

        assertEquals("https://s3/img-back.jpg", page.selectFirst("[data-preview] img")?.attr("src"))
        assertTrue(page.selectFirst("[data-placeholder]")!!.hasAttr("hidden"))
        assertEquals("Changer la photo", page.selectFirst("[data-upload-label]")?.text())
        assertEquals("/kyc", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun `selfie works without JS`() {
        given(case(images = listOf(front, back)))

        val page = render(get("/kyc?step=selfie"))

        val input = page.selectFirst("#selfie-file")!!
        assertEquals("user", input.attr("capture"))
        assertTrue(!page.selectFirst("[data-camera-fallback]")!!.hasAttr("hidden")) // the file input is visible
        assertTrue(page.selectFirst("[data-camera-start]")!!.hasAttr("hidden")) // kyc.js reveals the camera
    }

    @Test
    fun `step with an error`() {
        given(case())

        val page = render(multipart("/kyc/front").file(photo(pdf)))

        val error = page.selectFirst(".field__error")!!
        assertTrue(!error.hasAttr("hidden"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", error.selectFirst("[data-error-text]")?.text())
        assertEquals("true", page.selectFirst(".upload__input")?.attr("aria-invalid"))
    }

    @Test
    fun review() {
        given(case(images = listOf(front, back, selfie)))

        val page = render(get("/kyc?step=review"))

        assertEquals("/kyc/review", page.selectFirst("form[data-kyc-form]")?.attr("action"))
        assertEquals(
            listOf("https://s3/img-front.jpg", "https://s3/img-back.jpg", "https://s3/img-selfie.jpg"),
            page.select("[data-review-image] img").map { it.attr("src") },
        )
        assertEquals("/kyc?step=back&returnTo=review", page.selectFirst("[data-edit=back]")?.attr("href"))
        assertEquals("Envoyer mon dossier", page.selectFirst("[data-submit]")?.text())
    }

    @Test
    fun `returning to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val page = render(get("/kyc?step=back&returnTo=review"))

        assertEquals("review", page.selectFirst("input[name=returnTo]")?.`val`())
        assertEquals("Revenir à la revue", page.selectFirst("[data-next]")?.text())
        assertEquals("/kyc?step=review", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun confirmation() {
        given(case(status = KycStatus.SUBMITTED))
        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = "case-1"))

        val page = render(get("/kyc?step=confirmation"))

        assertEquals("Dossier envoyé", page.selectFirst("#confirmation-title")?.text())
        assertEquals("/kyc", page.selectFirst("[data-show-status]")?.attr("href"))
        assertNull(page.selectFirst("form[data-kyc-form]"))
    }

    @Test
    fun `pending status`() {
        given(case(status = KycStatus.IN_PROGRESS))

        val page = render(get("/kyc"))

        assertNotNull(page.selectFirst("[data-status=pending]"))
        assertNull(page.selectFirst("[data-status=verified]"))
        assertNull(page.selectFirst("form[data-kyc-form]"))
        assertNull(page.selectFirst(".progress"))
    }

    @Test
    fun `verified status`() {
        given(case(status = KycStatus.VERIFIED))

        val page = render(get("/kyc"))

        assertEquals("/", page.selectFirst("[data-status=verified] a.btn")?.attr("href"))
    }

    @Test
    fun `rejected status`() {
        given(case(status = KycStatus.REJECTED, errorCode = KycErrorCode.SELFIE_REJECTED))

        val page = render(get("/kyc"))

        val panel = page.selectFirst("[data-status=rejected]")!!
        assertTrue(panel.text().contains("Motif : votre selfie ne correspond pas à la photo de votre pièce d'identité."))
        assertEquals("/kyc/restart", panel.selectFirst("form")?.attr("action"))
    }
}
