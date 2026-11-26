package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.npc.ui.mandate.MandateRules

/** One chosen page before it's sent: its name, the size the browser sent, the type Tika read on it and its bytes. */
data class SniffedPage(
    val filename: String,
    val size: Long,
    val mimeType: String?,
    val content: ByteArray,
)

/**
 * The rules of a mandate's documents, mirrored by `mandate-document.js` (which checks the type, the size and the count
 * in the browser first) and re-checked here on every post: the pages are JPEG, PNG or WEBP images or PDFs of 5 Mo at
 * most, [MAX_PAGES] files at most per add, and a title is [MAX_TITLE] characters at most after trimming.
 *
 * A PDF's password, readability and page count aren't the browser's to check: ndopify-server refuses them (400
 * `MANDATE_DOCUMENT_PDF_*`) and `MandateDocumentController` words each refusal in French.
 *
 * Writes are only allowed while the mandate is in [MandateRules.ACTIVE]: ndopify-server refuses a CLOSED or CANCELLED
 * one with 409 MANDATE_ALREADY_CLOSED, and the pages show it read-only.
 */
object MandateDocumentRules {
    const val MAX_PAGES = 10
    const val MAX_BYTES = 5 * 1024 * 1024L
    const val MAX_TITLE = 100

    const val PDF_MIME_TYPE = "application/pdf"

    /** What Tika may read on a photo page: the browser's content type is a claim, the bytes are the truth. */
    val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    /** What a page may be: an image, or a PDF the server renders to one image per page. */
    val MIME_TYPES = IMAGE_MIME_TYPES + PDF_MIME_TYPE

    const val TITLE_REQUIRED = "Indiquez un titre."
    const val TITLE_TOO_LONG = "100 caractères au plus."
    const val TYPE_REQUIRED = "Choisissez le type de document."
    const val PAGE_REQUIRED = "Ajoutez au moins une page."
    const val TOO_MANY_PAGES = "$MAX_PAGES pages au plus."
    const val FILE_REQUIRED = "Choisissez la nouvelle page : une photo ou un PDF."
    const val NOT_A_PAGE =
        "Ce fichier n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, ou un PDF."
    const val PDF_TOO_LARGE = "Ce PDF dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez une photo."
    private const val IMAGE_TOO_LARGE =
        "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."

    fun writable(mandate: Mandate): Boolean = mandate.status in MandateRules.ACTIVE

    /** Whether the page is a PDF, rendered to images by ndopify-server rather than stored as is. */
    fun isPdf(page: SniffedPage): Boolean = page.mimeType == PDF_MIME_TYPE

    /** The posted type, null when it's blank, unknown or [DocumentType.UNKNOWN] (never offered). */
    fun type(value: String?): DocumentType? =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { name -> DocumentType.entries.firstOrNull { it.name == name } }
            ?.takeIf { it != DocumentType.UNKNOWN }

    /**
     * The add form's errors, by field (`type`, `title`, `files`): a type, pages, at most [MAX_PAGES] of them, each an
     * image or a PDF of at most [MAX_BYTES] (the first page's problem is the one shown), and a title at most
     * [MAX_TITLE]. A PDF's own pages are counted by ndopify-server, never here.
     */
    fun validateAdd(form: AddDocumentForm, pages: List<SniffedPage>): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (type(form.type) == null) errors["type"] = TYPE_REQUIRED
        if (form.title.trim().length > MAX_TITLE) errors["title"] = TITLE_TOO_LONG
        val fileError = when {
            pages.isEmpty() -> PAGE_REQUIRED
            pages.size > MAX_PAGES -> TOO_MANY_PAGES
            else -> pages.firstNotNullOfOrNull { pageError(it, quoteName = true) }
        }
        if (fileError != null) errors["files"] = fileError
        return errors
    }

    /** The rename form's errors, by field: a title is required there, unlike the add form (where blank means the type). */
    fun validateTitle(title: String): Map<String, String> {
        val trimmed = title.trim()
        return when {
            trimmed.isEmpty() -> mapOf("title" to TITLE_REQUIRED)
            trimmed.length > MAX_TITLE -> mapOf("title" to TITLE_TOO_LONG)
            else -> emptyMap()
        }
    }

    /** A replacement page's error; null when it's a valid image or PDF. A multi-page PDF is ndopify-server's refusal. */
    fun validatePage(page: SniffedPage?): String? =
        if (page == null) FILE_REQUIRED else pageError(page, quoteName = false)

    private fun pageError(page: SniffedPage, quoteName: Boolean): String? {
        val pdf = isPdf(page)
        if (page.mimeType !in MIME_TYPES) {
            return if (quoteName) {
                "« ${page.filename} » n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, ou un PDF."
            } else {
                NOT_A_PAGE
            }
        }
        if (page.size > MAX_BYTES) {
            return when {
                pdf && quoteName ->
                    "« ${page.filename} » dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez ses pages en photos."
                pdf -> PDF_TOO_LARGE
                quoteName ->
                    "« ${page.filename} » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."
                else -> IMAGE_TOO_LARGE
            }
        }
        return null
    }
}
