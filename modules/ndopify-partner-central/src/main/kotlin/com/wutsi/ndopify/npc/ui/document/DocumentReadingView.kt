package com.wutsi.ndopify.npc.ui.document

/** What the AI read on a document, as `templates/mandate/document.html` renders it. */
data class DocumentReadingView(
    /** The hint of a document still being read (UPLOADED / EXTRACTING): nothing to do, it happens on its own. */
    val waiting: String? = null,
    /** The notice of a document past its expiry date on the tenant's clock, whatever its status says. */
    val expiry: DocumentExpiryView? = null,
    /** Nothing to show: an EXTRACTED document the AI read no field on (an older payload). */
    val noData: Boolean = false,
    /** One instruction per problem code of a FAILED document, in the server's order. */
    val problems: List<String> = emptyList(),
    /** The fields the AI read, labelled and formatted, in the server's order. */
    val rows: List<DocumentFieldRowView> = emptyList(),
)

/**
 * The expiry notice: [date] in the tenant's `dateFormat`, [since] saying whether the piece was already expired when
 * the AI read it (`DOCUMENT_EXPIRED`), and [wayOut] naming what the agent can do — nothing on a closed or cancelled
 * mandate, which stays readable as it is.
 */
data class DocumentExpiryView(val date: String, val since: String, val wayOut: String)

/** One field of the reading: its French label and its value formatted by kind. The confidence is never shown. */
data class DocumentFieldRowView(val label: String, val value: String)
