package com.wutsi.ndopify.npc.ui.login

import java.io.Serializable

/**
 * The local page a signed-out agent asked for, kept in the HTTP session between the redirect to `/login` and the
 * successful verify. Its [fragment] is filled from the login page's hidden field, since a browser never sends a URL
 * fragment to the server.
 *
 * Nothing stored here is trusted: [of] and [redirectUrl] only accept a local, same-origin absolute path, and refuse
 * the sign-in and public pages, so a tampered session value can't become an open redirect.
 */
data class SignInTarget(
    val path: String,
    val query: String? = null,
    val fragment: String? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.sign-in-target"
        const val MAX_FRAGMENT_LENGTH = 200

        private const val FRAGMENT_PATTERN = "[A-Za-z0-9._~!$&'()*+,;=:@/?%-]{1,$MAX_FRAGMENT_LENGTH}"

        private val EXACT_EXCLUDED = setOf(
            "/login", "/logout", "/error", "/sw.js", "/offline.html", "/manifest.json", "/favicon.ico",
        )

        private val PREFIX_EXCLUDED = listOf(
            "/login/", "/error/", "/assets/", "/join", "/find-property", "/offer-property", "/locations",
        )

        /** The target for [path] and [query], or null when either isn't a safe, local page path. */
        fun of(path: String?, query: String? = null): SignInTarget? {
            if (path == null || !isSafePath(path)) return null
            if (EXACT_EXCLUDED.contains(path) || PREFIX_EXCLUDED.any { path.startsWith(it) }) return null
            val safeQuery = query?.takeIf { it.isNotEmpty() }
            if (safeQuery != null && !isSafeQuery(safeQuery)) return null
            return SignInTarget(path = path, query = safeQuery)
        }

        /** The fragment [raw] names (a leading `#` is dropped), or null when it is empty, too long or unsafe. */
        fun fragmentOf(raw: String?): String? {
            val fragment = raw?.removePrefix("#") ?: return null
            return fragment.takeIf { it.matches(Regex(FRAGMENT_PATTERN)) }
        }

        private fun isSafePath(path: String): Boolean =
            path.startsWith("/") &&
                !path.startsWith("//") &&
                path.none { it == '\\' || it == '#' || it == '?' || isControl(it) }

        private fun isSafeQuery(query: String): Boolean =
            query.none { it == '\\' || it == '#' || isControl(it) }

        private fun isControl(c: Char): Boolean = c.code < 0x20 || c.code == 0x7F
    }

    /** The redirect to send after a successful sign-in, or null when the stored value is no longer safe. */
    fun redirectUrl(): String? {
        val target = of(path, query) ?: return null
        return buildString {
            append(target.path)
            target.query?.let { append('?').append(it) }
            fragmentOf(fragment)?.let { append('#').append(it) }
        }
    }
}
