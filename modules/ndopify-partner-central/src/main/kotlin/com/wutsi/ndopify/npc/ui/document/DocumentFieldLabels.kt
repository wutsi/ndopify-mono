package com.wutsi.ndopify.npc.ui.document

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * The French words of what the AI read on a document, keyed by the data ndopify-server sends: `messages.properties`
 * holds one `document.field.<key>` per field key and one `document.problem.<code>` per problem code (#330, #333).
 * That bundle is the contract with the server: a key or a code this app does not know yet reads the generic text, so a
 * new server field needs one line there and no Kotlin change. `DOCUMENT_EXPIRED` has no entry on purpose — it is the
 * expiry notice's warning, never an instruction. The fixed page wording stays in the templates and views.
 */
@Component
class DocumentFieldLabels(private val messages: MessageSource) {
    companion object {
        private const val FIELD = "document.field."
        private const val FIELD_UNKNOWN = "Autre donnée"
        private const val PROBLEM = "document.problem."
        private val PROBLEM_UNKNOWN =
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture."
    }

    /** A field key's French label, [FIELD_UNKNOWN] when the bundle doesn't know it. */
    fun label(key: String): String = messages.getMessage(FIELD + key, null, FIELD_UNKNOWN, Locale.FRENCH) ?: FIELD_UNKNOWN

    /**
     * A problem code's instruction, [PROBLEM_UNKNOWN] for a code the bundle doesn't know and for a null or blank one
     * (a `FAILED` document with no usable code still says what to do).
     */
    fun instruction(code: String?): String {
        val key = code?.trim()
        return if (key.isNullOrEmpty()) {
            PROBLEM_UNKNOWN
        } else {
            messages.getMessage(PROBLEM + key, null, PROBLEM_UNKNOWN, Locale.FRENCH) ?: PROBLEM_UNKNOWN
        }
    }
}
