package com.wutsi.ndopify.util

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
}
