package com.wutsi.ndopify.listing.server.mail

/**
 * The wording of a listing sent back to DRAFT for correction, shared by the Telegram message and the e-mail.
 *
 * The message is generic: it names the property and points at its Partner Central page, where the `À corriger` panel
 * lists what to fix. The server never restates the error codes as reasons, so the wording stays in one place,
 * Partner Central, and doesn't drift.
 */
internal object ListingSentBackCopy {
    const val HEADLINE = "Votre annonce"
    const val STATUS = "a été renvoyée pour correction"
    const val NEXT_STEP = "Ouvrez-la dans Partner Central pour voir ce qui est à corriger, puis renvoyez-la."
    const val BUTTON = "Corriger l'annonce"
    const val SUBJECT = "{{tenant_name}}: Votre annonce a été renvoyée pour correction"

    /**
     * The property's label: `n° <id>` followed, when known, by its place — neighbourhood then city, no dangling
     * separator when one of them is missing or blank.
     */
    fun property(id: Long, city: String?, neighbourhood: String?): String {
        val place = listOfNotNull(neighbourhood?.ifBlank { null }, city?.ifBlank { null }).joinToString(", ")
        return if (place.isEmpty()) "n° $id" else "n° $id — $place"
    }
}
