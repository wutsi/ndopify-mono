package com.wutsi.ndopify.platform.mail

/**
 * The `Message-ID` Amazon SES assigns to an accepted message, read from the final `250` reply of the SMTP conversation.
 * SES replaces the `Message-ID` we send with its own, so this id is the only one a mail client can thread against.
 *
 * Only SES is parsed: its SMTP host is `email-smtp.<region>.amazonaws.com`, and its reply is `250 Ok <id>` or
 * `250 Ok <id@domain>`. A relay that preserves our header (Mailpit, GreenMail) or an unrecognised reply yields `null`,
 * and no input throws.
 */
object ServerMessageIdParser {
    private val SES_HOST = Regex("^email-smtp\\.([a-z0-9-]+)\\.amazonaws\\.com$", RegexOption.IGNORE_CASE)
    private val ACCEPTED = Regex("^250[ -]Ok\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
    private val TOKEN = Regex("^<?([A-Za-z0-9._-]+)(?:@([A-Za-z0-9.-]+))?>?$")

    fun parse(reply: String?, host: String?): String? {
        val region = SES_HOST.find(host ?: return null)?.groupValues?.get(1) ?: return null
        val token = ACCEPTED.find(reply?.trim() ?: return null)?.groupValues?.get(1)?.trim() ?: return null
        val match = TOKEN.find(token) ?: return null
        val local = match.groupValues[1]
        val domain = match.groupValues[2].ifBlank {
            if (region.equals("us-east-1", ignoreCase = true)) "email.amazonses.com" else "$region.amazonses.com"
        }
        return "$local@$domain"
    }
}
