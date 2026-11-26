package com.wutsi.ndopify.npc.ui.document

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * The French words of what the AI read on a document, keyed by the data ndopify-server sends: `messages.properties`
 * holds one `document.field.<key>` per field key and one `document.problem.<code>` per problem code (#330, #333).
 * That bundle is the contract with the server: a key or a code this app does not know yet reads the bundle's generic
 * text (`document.field.unknown` / `document.problem.unknown`), so a new server field needs one line there and no
 * Kotlin change. `DOCUMENT_EXPIRED` has no entry on purpose — it is the expiry notice's warning, never an instruction.
 * The fixed page wording stays in the templates and views.
 */
@Component
class DocumentFieldLabels(private val messages: MessageSource) {
    companion object {
        private const val FIELD = "document.field."
        private const val PROBLEM = "document.problem."
        private const val UNKNOWN = "unknown"

        /** Last resorts only: the bundle's `document.field.unknown` / `document.problem.unknown` are the real texts. */
        private const val FIELD_UNKNOWN = "Autre donnée"
        private const val PROBLEM_UNKNOWN =
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture."
    }

    /** A field key's French label: the bundle's `document.field.<key>`, else its `document.field.unknown`. */
    fun label(key: String): String {
        val unknown = generic(FIELD + UNKNOWN, FIELD_UNKNOWN)
        return messages.getMessage(FIELD + key, null, unknown, Locale.FRENCH) ?: unknown
    }

    /**
     * A problem code's instruction: the bundle's `document.problem.<code>`, else its `document.problem.unknown`, which
     * a null or blank code reads directly (a `FAILED` document with no usable code still says what to do).
     */
    fun instruction(code: String?): String {
        val unknown = generic(PROBLEM + UNKNOWN, PROBLEM_UNKNOWN)
        val key = code?.trim()
        return if (key.isNullOrEmpty()) {
            unknown
        } else {
            messages.getMessage(PROBLEM + key, null, unknown, Locale.FRENCH) ?: unknown
        }
    }

    /** The bundle's generic text for a family, [fallback] only if the bundle itself is missing it. */
    private fun generic(key: String, fallback: String): String =
        messages.getMessage(key, null, fallback, Locale.FRENCH) ?: fallback
}
