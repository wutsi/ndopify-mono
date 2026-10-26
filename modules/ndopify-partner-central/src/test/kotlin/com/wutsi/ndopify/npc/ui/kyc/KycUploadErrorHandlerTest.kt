package com.wutsi.ndopify.npc.ui.kyc

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KycUploadErrorHandlerTest {
    private val handler = KycUploadErrorHandler()
    private val ex = MaxUploadSizeExceededException(10L * 1024 * 1024)

    @Test
    fun `kyc photo step - back to it with the size error`() {
        assertEquals("redirect:/kyc?step=back&error=too-large", handler.tooLarge(MockHttpServletRequest("POST", "/kyc/back"), ex))
    }

    @Test
    fun `profile photo - back to its page with the size error`() {
        assertEquals(
            "redirect:/profile/photo?error=too-large",
            handler.tooLarge(MockHttpServletRequest("POST", "/profile/photo"), ex),
        )
    }

    @Test
    fun `listing photos - back to their page with the size error`() {
        assertEquals(
            "redirect:/listings/501/photos?error=too-large",
            handler.tooLarge(MockHttpServletRequest("POST", "/listings/501/photos"), ex),
        )
    }

    @Test
    fun `listing photos - hand-edited path not handled here`() {
        assertFailsWith<MaxUploadSizeExceededException> {
            handler.tooLarge(MockHttpServletRequest("POST", "/listings/abc/photos"), ex)
        }
    }

    @Test
    fun `any other upload - not handled here`() {
        assertFailsWith<MaxUploadSizeExceededException> {
            handler.tooLarge(MockHttpServletRequest("POST", "/somewhere/else"), ex)
        }
    }
}
