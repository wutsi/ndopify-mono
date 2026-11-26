package com.wutsi.ndopify.npc.ui.document

/** The add form (`/mandates/{id}/documents/new`): the type's `name` and the optional title, kept when the form comes back. */
data class AddDocumentForm(val type: String = "", val title: String = "")

/** The rename form: the new title and the document's `version` the agent read (`null` to not check it). */
data class RenameDocumentForm(val title: String = "", val version: Long? = null)
