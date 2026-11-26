package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate

/**
 * Builds what was read on a document from ndopify-server's [MandateDocument]: its fields (labelled by
 * [DocumentFieldLabels] and formatted by [DocumentFieldValues], never the raw JSON nor a confidence), the hint of a
 * document still waiting for its reading, one instruction per problem code of a FAILED one, and the notice of a piece
 * expired before [today]. Only an EXTRACTED document shows field rows: a waiting or failed one never does (#331's
 * criteria 5 and 6), even when its payload still carries fields.
 *
 * `DOCUMENT_EXPIRED` is a warning, not an instruction: it tells the expiry notice the piece was already expired at
 * extraction, and never appears in [DocumentReadingView.problems].
 */
object MandateDocumentReadingViews {
    private const val WAITING_UPLOADED =
        "Le document est reçu : sa lecture va commencer. Rien à faire, elle se lance d'elle-même ; ses données " +
            "apparaîtront ici."
    private const val WAITING_EXTRACTING =
        "La lecture du document est en cours. Ses données apparaîtront ici dès qu'elle sera terminée : rien à faire."
    private const val ALREADY_EXPIRED = "Elle était déjà expirée lors de sa lecture."
    private const val EXPIRED_SINCE = "Elle a expiré depuis sa lecture."
    private const val FIND_A_VALID_ONE =
        "Ajoutez une pièce en cours de validité : remplacez ses pages par celles de la nouvelle pièce, ou " +
            "supprimez-la et ajoutez la nouvelle."
    private const val CLOSED = "Mandat fermé : cette pièce reste consultable telle quelle."
    private const val CANCELLED = "Mandat annulé : cette pièce reste consultable telle quelle."

    private const val EXPIRED = "DOCUMENT_EXPIRED"

    fun of(
        mandate: Mandate,
        document: MandateDocument,
        formatter: TenantFormatter,
        labels: DocumentFieldLabels,
        today: LocalDate,
    ): DocumentReadingView {
        val currency = document.fields.firstOrNull { it.key == DocumentFieldValues.CURRENCY }?.value
        val rows = if (document.status == DocumentStatus.EXTRACTED) {
            document.fields
                .filter { it.key !in DocumentFieldValues.HIDDEN && !it.value.isNullOrBlank() }
                .map {
                    DocumentFieldRowView(
                        label = labels.label(it.key),
                        value = DocumentFieldValues.of(it.key, it.value.orEmpty(), currency, formatter),
                    )
                }
        } else {
            emptyList()
        }
        val waiting = when (document.status) {
            DocumentStatus.UPLOADED -> WAITING_UPLOADED
            DocumentStatus.EXTRACTING -> WAITING_EXTRACTING
            else -> null
        }
        val expiry = document.expiresAt
            ?.takeIf { it.isBefore(today) }
            ?.let {
                DocumentExpiryView(
                    date = formatter.date(it),
                    since = if (EXPIRED in document.problemCodes) ALREADY_EXPIRED else EXPIRED_SINCE,
                    wayOut = wayOut(mandate),
                )
            }
        val problems = if (document.status == DocumentStatus.FAILED) {
            document.problemCodes
                .filter { it != EXPIRED }
                .map(labels::instruction)
                .ifEmpty { listOf(labels.instruction(null)) }
        } else {
            emptyList()
        }
        return DocumentReadingView(
            waiting = waiting,
            expiry = expiry,
            noData = rows.isEmpty() && problems.isEmpty() && waiting == null,
            problems = problems,
            rows = rows,
        )
    }

    /** What the agent can do about an expired piece; a closed or cancelled mandate offers no action of its own. */
    private fun wayOut(mandate: Mandate): String = when {
        MandateDocumentRules.writable(mandate) -> FIND_A_VALID_ONE
        mandate.status == MandateStatus.CANCELLED -> CANCELLED
        else -> CLOSED
    }
}
