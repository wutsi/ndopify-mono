package com.wutsi.ndopify.npc.ui.document

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
)

/** One image page of a document: its rank, and the short-lived URL *Voir* opens in a new tab. Never its path. */
data class DocumentPageView(
    val id: Long,
    /** "Page 1" for the first. */
    val number: Int,
    val url: String,
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
)
