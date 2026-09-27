package com.wutsi.ndopify.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneNumberUtilsTest {
    @Test
    fun normalize() {
        assertEquals("15147580102", PhoneNumberUtils.normalize("+1514 758-01-02"))
        assertEquals("15147580102", PhoneNumberUtils.normalize("+1 (514) 758-0102"))
        assertEquals("15147580102", PhoneNumberUtils.normalize("+1-514-758-0102"))
        assertEquals("15147580102", PhoneNumberUtils.normalize("1 514 758 0102"))
        assertEquals("15147580102", PhoneNumberUtils.normalize("15147580102"))
        assertEquals("", PhoneNumberUtils.normalize(""))
    }

    @Test
    fun extractCountryCode() {
        assertEquals("US", PhoneNumberUtils.extractCountryCode("+14155552671"))
        assertEquals("CA", PhoneNumberUtils.extractCountryCode("+15145552671"))
        assertEquals("CM", PhoneNumberUtils.extractCountryCode("+237699000000"))
        assertEquals("FR", PhoneNumberUtils.extractCountryCode("+33612345678"))
    }

    @Test
    fun toWhatsappUrl() {
        assertEquals("https://wa.me/15147580102", PhoneNumberUtils.toWhatsappUrl("+1514 758-01-02"))
        assertEquals("https://wa.me/15147580102?text=hello", PhoneNumberUtils.toWhatsappUrl("+1514 758-01-02", "hello"))
        assertEquals(
            "https://wa.me/15147580102?text=I%27m+interested+in+your+car+for+sale",
            PhoneNumberUtils.toWhatsappUrl("+1514 758-01-02", "I'm interested in your car for sale")
        )
    }
}
