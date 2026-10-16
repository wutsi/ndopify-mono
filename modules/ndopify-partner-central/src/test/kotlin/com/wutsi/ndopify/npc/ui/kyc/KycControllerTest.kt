package com.wutsi.ndopify.npc.ui.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KycControllerTest {
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

    private fun case(status: KycStatus = KycStatus.PENDING, images: List<KycImage> = emptyList(), errorCode: String? = null) =
        KycCase(id = "case-1", partyId = PARTY_ID, status = status, images = images, errorCode = errorCode)

    private fun given(case: KycCase?) {
        doReturn(case).whenever(kycClient).current(PARTY_ID)
    }

    @BeforeEach
    fun setUp() {
        doReturn(Party(id = PARTY_ID, firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"))
            .whenever(currentParty).get()
        whenever(kycClient.imageUrl(any())).thenAnswer { invocation -> "https://s3/${invocation.getArgument<String>(0)}.jpg" }

        // The view is named after its URL ("kyc" at /kyc); the default resolver would take that for a loop.
        mvc = MockMvcBuilders.standaloneSetup(KycController(kycClient, currentParty))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun `new case - first step`() {
        given(case())

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("kyc", result.modelAndView?.viewName)
        assertEquals("wizard", model(result)["view"])
        assertEquals("front", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(4, model(result)["stepCount"])
        assertEquals(false, model(result)["returning"])
        assertNull(model(result)["error"])
        assertEquals(emptyMap<String, String>(), model(result)["photoUrls"])
        assertEquals("RS", model(result)["initials"])
        assertNull(model(result)["userPhotoUrl"])
    }

    @Test
    fun `never skips a missing photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?step=review").session(session)).andReturn()

        assertEquals("back", model(result)["step"])
    }

    @Test
    fun `unknown step - first step`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?step=hack").session(session)).andReturn()

        assertEquals("front", model(result)["step"])
    }

    @Test
    fun `upload step shows its photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?step=front").session(session)).andReturn()

        assertEquals(mapOf("FRONT" to "https://s3/img-front.jpg"), model(result)["photoUrls"])
    }

    @Test
    fun `photo preview unavailable - step still shown without it`() {
        given(case(images = listOf(front)))
        whenever(kycClient.imageUrl(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/kyc?step=front").session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        assertEquals(emptyMap<String, String>(), model(result)["photoUrls"])
    }

    @Test
    fun review() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?step=review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(4, model(result)["stepNumber"])
        assertEquals(
            mapOf("FRONT" to "https://s3/img-front.jpg", "BACK" to "https://s3/img-back.jpg", "SELFIE" to "https://s3/img-selfie.jpg"),
            model(result)["photoUrls"],
        )
    }

    @Test
    fun `returning to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?step=back&returnTo=review").session(session)).andReturn()

        assertEquals("back", model(result)["step"])
        assertEquals(true, model(result)["returning"])
    }

    @Test
    fun `no case yet - first step, nothing created`() {
        given(null)

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        verify(kycClient, never()).create(any())
    }

    @Test
    fun `submitted - pending status`() {
        given(case(status = KycStatus.SUBMITTED, images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?step=front").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
        assertEquals("pending", model(result)["status"])
        verify(kycClient, never()).imageUrl(any())
    }

    @Test
    fun `verified status`() {
        given(case(status = KycStatus.VERIFIED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("verified", model(result)["status"])
    }

    @Test
    fun `rejected status with its reason`() {
        given(case(status = KycStatus.REJECTED, errorCode = KycErrorCode.IDENTIFICATION_REJECTED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("rejected", model(result)["status"])
        assertEquals("votre pièce d'identité n'a pas pu être validée.", model(result)["rejectionReason"])
    }

    @Test
    fun confirmation() {
        given(case(status = KycStatus.SUBMITTED))
        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = "case-1"))

        val result = mvc.perform(get("/kyc?step=confirmation").session(session)).andReturn()

        assertEquals("confirmation", model(result)["view"])
    }

    @Test
    fun `confirmation without a submission - status`() {
        given(case(status = KycStatus.SUBMITTED))

        val result = mvc.perform(get("/kyc?step=confirmation").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
    }

    @Test
    fun `no party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }

    // Magic numbers are enough for Tika to tell the type from the bytes.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) + ByteArray(64)
    private val pdf = "%PDF-1.4\n".toByteArray() + ByteArray(64)

    private fun photo(bytes: ByteArray, contentType: String = "image/jpeg") =
        MockMultipartFile("photo", "photo.jpg", contentType, bytes)

    private fun serverError(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.BAD_REQUEST)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    @Test
    fun `upload front - next step`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-1"), eq(KycImageType.FRONT), eq(jpeg), eq("image/jpeg"))
        assertEquals("redirect:/kyc?step=back", result.modelAndView?.viewName)
    }

    @Test
    fun `upload selfie - review`() {
        given(case(images = listOf(front, back)))

        val result = mvc.perform(multipart("/kyc/selfie").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-1"), eq(KycImageType.SELFIE), any(), eq("image/jpeg"))
        assertEquals("redirect:/kyc?step=review", result.modelAndView?.viewName)
    }

    @Test
    fun `replace a photo from the review - back to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).param("returnTo", "review").session(session)).andReturn()

        assertEquals("redirect:/kyc?step=review", result.modelAndView?.viewName)
    }

    @Test
    fun `no new file but the photo is already in - next step, nothing uploaded`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(multipart("/kyc/front").session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("redirect:/kyc?step=back", result.modelAndView?.viewName)
    }

    @Test
    fun `no file - error`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/front").session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        assertEquals("Ajoutez une photo pour continuer.", model(result)["error"])
    }

    @Test
    fun `pdf sent as a jpeg - format error, nothing uploaded`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/front").file(photo(pdf, "image/jpeg")).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", model(result)["error"])
    }

    @Test
    fun `photo over 5 MB - size error`() {
        given(case())
        val big = jpeg + ByteArray(KycRules.MAX_PHOTO_BYTES.toInt())

        val result = mvc.perform(multipart("/kyc/front").file(photo(big)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("Cette photo pèse 5,0 Mo. La taille maximale est de 5 Mo.", model(result)["error"])
    }

    @Test
    fun `upload a step ahead of a missing photo - back to the missing one`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/selfie").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `no case yet - one is opened`() {
        given(null)
        doReturn("case-new").whenever(kycClient).create(PARTY_ID)
        doReturn(KycCase(id = "case-new", status = KycStatus.PENDING)).whenever(kycClient).get("case-new")

        mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-new"), eq(KycImageType.FRONT), any(), any())
    }

    @Test
    fun `case already submitted - status, nothing uploaded`() {
        given(case(status = KycStatus.SUBMITTED))

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        verify(kycClient, never()).create(any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `case submitted meanwhile (other tab) - status`() {
        given(case())
        doThrow(serverError(ErrorCode.KYC_CASE_NOT_PENDING)).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `server error - generic error on the step`() {
        given(case())
        doThrow(serverError("unexpected")).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    }

    @Test
    fun `server failure (5xx) on upload - generic error on the step`() {
        given(case())
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    }

    @Test
    fun `server unreachable on upload - generic error on the step`() {
        given(case())
        doThrow(ResourceAccessException("timeout")).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("front", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    }

    @Test
    fun `server failure (5xx) on submit - generic error on the review, nothing submitted`() {
        given(case(images = listOf(front, back, selfie)))
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(kycClient).submit(any())

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
        assertNull(session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE))
    }

    @Test
    fun `upload without party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        val result = mvc.perform(multipart("/kyc/front").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }

    @Test
    fun submit() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        verify(kycClient).submit("case-1")
        assertEquals("redirect:/kyc?step=confirmation", result.modelAndView?.viewName)
        assertEquals(SubmittedKyc(caseId = "case-1"), session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE))
    }

    @Test
    fun `submit with a missing photo - back to it`() {
        given(case(images = listOf(front, back)))

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        verify(kycClient, never()).submit(any())
        assertEquals("redirect:/kyc?step=selfie", result.modelAndView?.viewName)
    }

    @Test
    fun `submit twice - status`() {
        given(case(images = listOf(front, back, selfie)))
        doThrow(serverError(ErrorCode.KYC_CASE_NOT_PENDING)).whenever(kycClient).submit(any())

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
        assertNull(session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE))
    }

    @Test
    fun `submit a case that is no longer pending - status`() {
        given(case(status = KycStatus.SUBMITTED, images = listOf(front, back, selfie)))

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        verify(kycClient, never()).submit(any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `server says a photo is missing - error on the review`() {
        given(case(images = listOf(front, back, selfie)))
        doThrow(serverError(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE)).whenever(kycClient).submit(any())

        val result = mvc.perform(post("/kyc/review").session(session)).andReturn()

        assertEquals("review", model(result)["step"])
        assertEquals(
            "Il manque une ou plusieurs photos. Ajoutez-les pour pouvoir envoyer votre dossier.",
            model(result)["error"],
        )
    }

    @Test
    fun `restart a rejected case - new case`() {
        given(case(status = KycStatus.REJECTED))

        val result = mvc.perform(post("/kyc/restart").session(session)).andReturn()

        verify(kycClient).create(PARTY_ID)
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `restart a pending case - nothing created`() {
        given(case(status = KycStatus.PENDING))

        mvc.perform(post("/kyc/restart").session(session)).andReturn()

        verify(kycClient, never()).create(any())
    }

    @Test
    fun `restart a verified case - nothing created`() {
        given(case(status = KycStatus.VERIFIED))

        mvc.perform(post("/kyc/restart").session(session)).andReturn()

        verify(kycClient, never()).create(any())
    }

    @Test
    fun `submit without party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        assertEquals("redirect:/logout", mvc.perform(post("/kyc/review").session(session)).andReturn().modelAndView?.viewName)
        assertEquals("redirect:/logout", mvc.perform(post("/kyc/restart").session(session)).andReturn().modelAndView?.viewName)
    }

    @Test
    fun `photo over the upload limit - size error on its step`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?step=back&error=too-large").session(session)).andReturn()

        assertEquals("back", model(result)["step"])
        assertEquals("Cette photo dépasse la taille maximale de 5 Mo.", model(result)["error"])
    }
}
