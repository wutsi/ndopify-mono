package com.wutsi.ndopify.platform.mail.filter

import com.wutsi.ndopify.platform.mail.MailFilter
import org.apache.commons.text.StringEscapeUtils
import org.springframework.stereotype.Service

/**
 * Filter that escape latin characters:
 * - à -> &agrave;
 * - ç -> &ccedil;
 * - etc...
 */
@Service
class HtmlEscapeFilter : MailFilter {
    override fun filter(html: String, tenantId: Long): String {
        return StringEscapeUtils.unescapeXml(
            StringEscapeUtils.escapeHtml4(html)
        )
    }
}
