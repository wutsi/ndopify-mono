package com.wutsi.koki.email.server.service.filter

import com.wutsi.koki.email.server.service.EmailFilter
import org.apache.commons.text.StringEscapeUtils
import org.springframework.stereotype.Service

/**
 * Filter that escape latin characters:
 * - à -> &agrave;
 * - ç -> &ccedil;
 * - etc...
 */
@Service
class HtmlEscapeFilter : EmailFilter {
    override fun filter(html: String, tenantId: Long): String {
        return StringEscapeUtils.unescapeXml(
            StringEscapeUtils.escapeHtml4(html)
        )
    }
}
