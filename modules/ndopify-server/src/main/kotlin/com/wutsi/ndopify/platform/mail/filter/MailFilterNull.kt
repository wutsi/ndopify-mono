package com.wutsi.ndopify.platform.mail.filter

import com.wutsi.ndopify.platform.mail.MailFilter

class MailFilterNull : MailFilter {
    override fun filter(html: String): String {
        return html
    }
}
