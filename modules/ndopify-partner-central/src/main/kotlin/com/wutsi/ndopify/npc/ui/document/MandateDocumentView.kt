package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentSource
import com.wutsi.ndopify.npc.ui.mandate.MandateRules

/** One document of the mandate page's *Documents* section: the whole row links to the document's page. */
data class DocumentRowView(
    val id: Long,
    /** The type's French name, always shown. */
    val typeLabel: String,
    /** The agent's title, null when it's the type's name (the row's first line already says it). */
    val title: String?,
    val status: MandateRules.Tag,
    /** "Ajouté le 25/11/2026", in the tenant's date format. */
    val addedOn: String,
    val pageCount: Int,
    /** `expiresAt` before the tenant's today: the row carries the *Expirée* marker, the status doesn't say it. */
    val expired: Boolean = false,
    val href: String,
)

/**
 * The mandate page's *Documents* section. [unavailable] when the documents can't be read (the rest of the page is
 * unaffected), [writable] while the mandate is in [MandateRules.ACTIVE] (a closed one keeps them consultable only), and
 * [emptyHint] says what to add for this kind of mandate.
 */
data class DocumentSectionView(
    val rows: List<DocumentRowView>,
    val unavailable: Boolean,
    val writable: Boolean,
    val emptyHint: String,
    val intro: String,
    /** The documents currently stored, against [MandateDocumentRules.MAX_DOCUMENTS]; 0 when the list can't be read. */
    val count: Int,
    /** The cap is reached: the section hides *Ajouter un document* and shows the notice. */
    val atLimit: Boolean,
)

/**
 * One image page of a document: its rank, and the viewer page *Voir* opens in the same tab. Never its storage path
 * nor a storage URL.
 */
data class DocumentPageView(
    val id: Long,
    /** "Page 1" for the first. */
    val number: Int,
    /** The Partner Central viewer page of this page (`/mandates/{id}/documents/{documentId}/pages/{pageId}`). */
    val href: String,
)

/**
 * The viewer of one page of a document: its title, its rank and the document's page count, this app's authenticated
 * JPEG route, and the way back to the document page's *Pages* heading. Never a storage path nor a storage URL.
 */
data class DocumentPreviewView(
    val title: String,
    /** "Page [number] sur [count]". */
    val number: Int,
    val count: Int,
    /** This app's `/preview` route: the watermarked JPEG, behind the agent's session. */
    val imageHref: String,
    /** The document page's *Pages* heading. */
    val backHref: String,
    val alt: String,
)

/**
 * A document's own page: its title, its status, the type only when it differs from the title, one upload date for the
 * whole document, and its pages in order. [writable] while the mandate is ACTIVE: a closed one shows *Voir* only.
 */
data class DocumentView(
    val id: Long,
    val mandateId: Long,
    /** The type's French name, null when it's the document's own title (no need to repeat it). */
    val typeLabel: String?,
    val title: String,
    val status: MandateRules.Tag,
    /** "Ajouté le 25/11/2026", in the tenant's date format. */
    val addedOn: String,
    val version: Long,
    val pages: List<DocumentPageView>,
    val writable: Boolean,
    /** The kind the document was created from; the replacement form and its pill follow it. */
    val source: DocumentSource,
    /** "PDF", "Photos" or "Photos ou PDF". */
    val sourceLabel: String,
    /** The replacement file input's `accept`. */
    val replaceAccept: String,
    /** The replacement form's hint, by the document's kind. */
    val replaceHint: String,
    /** The replacement form's no-file error, by the document's kind. */
    val replaceRequired: String,
)
