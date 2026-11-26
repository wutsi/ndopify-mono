package com.wutsi.ndopify.platform.util

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.net.URLEncoder

object PhoneNumberUtils {
    fun normalize(phone: String): String {
        return phone.replace("+", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
    }

    fun extractCountryCode(phoneNumber: String): String {
        val util = PhoneNumberUtil.getInstance()
        val number = util.parse(phoneNumber, null)
        return util.getRegionCodeForNumber(number)
    }

    fun toWhatsappUrl(phone: String, text: String? = null): String {
        val url = "https://wa.me/" + normalize(phone)
        return if (text.isNullOrEmpty()) {
            url
        } else {
            "$url?text=" + URLEncoder.encode(text, "utf-8")
        }
    }

    /**
     * The `https://wa.me/<digits>` chat link of [phone], or null when there is nothing to open: a null, blank or
     * digit-less value. Only the digits are kept — a country code is never guessed — and no `?text=` is added.
     */
    fun whatsappUrlOrNull(phone: String?): String? =
        phone?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }?.let { toWhatsappUrl(it) }
}
