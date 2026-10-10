package com.wutsi.ndopify.platform.mail.filter

import com.wutsi.ndopify.platform.mail.MailFilter
import org.apache.commons.text.StringEscapeUtils

/**
 * Filter that escape latin characters:
 * - à -> &agrave;
 * - ç -> &ccedil;
 * - etc...
 */
class HtmlEscapeFilter : MailFilter {
    override fun filter(html: String): String {
        return StringEscapeUtils.unescapeXml(
            StringEscapeUtils.escapeHtml4(html)
        )
    }
}
