package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AttachMandateDocumentPageResponse
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient.UploadedPage
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.mandate.MandateViews
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A mandate's documents: the *Documents* section of the mandate page ([com.wutsi.ndopify.npc.ui.mandate.MandateController]
 * reads them with the page's other reads), the add form (`/mandates/{id}/documents/new`) and a document's own page
 * (`/mandates/{id}/documents/{documentId}`).
 *
 * The add and the replacement are multipart forms, so every action works without JavaScript; the pages show no image
 * of a page, only a *Voir* link to its [preview] route, which streams the watermarked JPEG behind the agent's session.
 * Each post is post-redirect-get back to the mandate
 * page (an add, a delete) or the document's page (a rename, a replacement) with a flash attribute, and the pages show
 * them: [OUTCOME] / [TITLE] on the mandate page, [RENAME_OUTCOME] and [PAGE] on the document's.
 *
 * The mandate is read on every request and only shown when it is in [MandateRules.SHOWN]: writes are only allowed
 * while it is in [MandateRules.ACTIVE] ([MandateDocumentRules.writable]), and a closed or cancelled one keeps its
 * documents and pages readable. Anything ndopify-server refuses because the page was stale (404: the mandate or the
 * document is gone, 403: no longer the agent's, 409: the mandate is closed or changed) goes back to the mandate page
 * with [CHANGED]: never the error page.
 */
@Controller
@RequestMapping("/mandates/{id}/documents")
class MandateDocumentController(
    private val currentAgent: CurrentAgent,
    private val mandateClient: NdMandateClient,
    private val documentClient: NdMandateDocumentClient,
    private val labels: DocumentFieldLabels,
    private val tenantContext: TenantContext,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentController::class.java)
        private val TIKA = Tika()

        const val VIEW_NEW = "mandate/document-new"
        const val VIEW_DOCUMENT = "mandate/document"

        /** The mandate page's flash after an add or a delete (and its title, for an add). */
        const val OUTCOME = "documentOutcome"
        const val TITLE = "documentTitle"
        const val ADDED = "added"
        const val DELETED = "deleted"

        /** The document page's flash after a rename or a replacement, and the page a replacement touched. */
        const val RENAME_OUTCOME = "outcome"
        const val PAGE = "page"
        const val RENAMED = "renamed"
        const val REPLACED = "replaced"

        /** The action couldn't run (a 5xx or an unreachable server), on the document page. */
        const val FAILED = "error"
        private const val OUTCOME_MESSAGE = "outcomeMessage"

        /** ndopify-server refused the action because the page was stale: the mandate page says so. */
        const val CHANGED = "changed"

        /** `?error=too-large` after a multipart post over `spring.servlet.multipart`'s limit (KycUploadErrorHandler). */
        const val ERROR_TOO_LARGE = "too-large"
        const val TOO_LARGE = "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère."

        /** `?replaceError=size&page={pageId}` after a replacement over the multipart limit. */
        const val REPLACE_ERROR = "replaceError"
        const val REPLACE_SIZE = "size"

        private const val ADD_FAILED =
            "Le document n'a pas pu être ajouté. Réessayez dans quelques instants : choisissez de nouveau ses pages."
        private const val RENAME_FAILED = "Le titre n'a pas pu être enregistré. Réessayez dans quelques instants."
        private const val REPLACE_FAILED = "La page n'a pas pu être remplacée. Réessayez dans quelques instants."

        private val CHANGED_STATUSES = setOf(403, 404, 409)
    }

    @GetMapping("/new")
    fun addForm(
        @PathVariable id: String,
        @RequestParam(name = "error", required = false) uploadError: String?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val errors = if (uploadError == ERROR_TOO_LARGE) mapOf("files" to TOO_LARGE) else emptyMap()
        return addPage(mandate, AddDocumentForm(), errors, null, model)
    }

    /**
     * The without-JS add: every page is sniffed with Tika (the browser's content type is a claim), checked by
     * [MandateDocumentRules.validateAdd], and sent to ndopify-server, which records the document and defaults a blank
     * title to the type's French name. An error shows the form again, the type and the title kept.
     */
    @PostMapping
    fun add(
        @PathVariable id: String,
        @ModelAttribute form: AddDocumentForm,
        @RequestParam(name = "files", required = false) files: List<MultipartFile>?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = writableMandate(id) ?: return changed(id, redirect)

        val pages = files.orEmpty().filterNot { it.isEmpty }.map { sniff(it) }
        val errors = MandateDocumentRules.validateAdd(form, pages)
        if (errors.isNotEmpty()) return addPage(mandate, form, errors, null, model)

        // validateAdd accepted it: the type is a known one (never UNKNOWN, which it refuses).
        val type = MandateDocumentRules.type(form.type)!!
        val title = form.title.trim().ifEmpty { null }
        return try {
            documentClient.upload(
                mandate.id,
                type,
                title,
                pages.map { UploadedPage(it.content, it.mimeType.orEmpty(), it.filename) },
            )
            redirect.addFlashAttribute(OUTCOME, ADDED)
            redirect.addFlashAttribute(TITLE, title ?: MandateDocumentLabels.type(type))
            back(mandate.id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Documents of mandate ${mandate.id} not added: ${ex.statusCode}")
            when {
                ex.statusCode.value() in CHANGED_STATUSES -> changed(mandate.id, redirect)
                ex.statusCode.value() == 400 -> addPage(mandate, form, serverErrors(ex), null, model)
                else -> addPage(mandate, form, emptyMap(), ADD_FAILED, model)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Documents of mandate ${mandate.id} not added", ex)
            addPage(mandate, form, emptyMap(), ADD_FAILED, model)
        }
    }

    /**
     * A document's page, with its pages in order and each linked to this app's authenticated preview; `?delete=1`
     * opens the confirmation panel without JS (a GET, so a delete is never forced by a link).
     * `?replaceError=size&page={pageId}` is the size error of a replacement over the multipart limit
     * ([com.wutsi.ndopify.npc.ui.kyc.KycUploadErrorHandler]).
     */
    @GetMapping("/{documentId}")
    fun document(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @RequestParam(name = "delete", required = false) delete: String?,
        @RequestParam(name = REPLACE_ERROR, required = false) replaceError: String?,
        @RequestParam(name = PAGE, required = false) page: String?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = shownMandate(id) ?: return changed(id, redirect)
        val document = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)
        val pageId = page?.toLongOrNull()
        val replaceErrors = if (replaceError == REPLACE_SIZE && pageId != null && document.pages.any { it.id == pageId }) {
            mapOf(pageId to TOO_LARGE)
        } else {
            emptyMap()
        }
        return documentPage(
            mandate,
            document,
            RenameDocumentForm(document.title, document.version),
            emptyMap(),
            replaceErrors,
            conflict = false,
            deleting = delete == "1",
            model,
        )
    }

    /**
     * The authenticated preview of one page: ndopify-server's resized, watermarked JPEG, streamed with the agent's
     * token. A copied URL opens nothing without the agent's session (the sign-in redirect). 403/404 keep the status
     * ndopify-server answered; a 5xx or an unreachable server is a 502. Never the original bytes nor a storage URL.
     */
    @GetMapping("/{documentId}/pages/{pageId}/preview")
    fun preview(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
    ): ResponseEntity<ByteArray> {
        currentAgent.get() ?: throw SignInRequiredException()
        val mandateId = id.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        // Read through the client so ndopify-server's 401 / 403 / 404 keep their status (a closed mandate stays shown)
        val mandate = mandateClient.get(mandateId)
        if (mandate.status !in MandateRules.SHOWN) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val document = documentClient.get(mandateId, documentId.toLongOrNull() ?: return notFoundResponse())
        val page = document.pages.sortedBy { it.index }
            .firstOrNull { it.id == pageId.toLongOrNull() }
            ?: return notFoundResponse()
        val number = document.pages.sortedBy { it.index }.indexOfFirst { it.id == page.id } + 1

        val bytes = try {
            documentClient.preview(mandateId, document.id, page.id)
        } catch (ex: HttpServerErrorException) {
            LOGGER.error("Preview of page ${page.id} of document ${document.id} of mandate $mandateId failed: ${ex.statusCode}")
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY)
        } catch (ex: ResourceAccessException) {
            LOGGER.error("Preview of page ${page.id} of document ${document.id} of mandate $mandateId unreachable")
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY)
        }
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"mandat-$mandateId-page-$number.jpg\"")
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
            .header("X-Content-Type-Options", "nosniff")
            .body(bytes)
    }

    private fun notFoundResponse(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND)

    /**
     * Renames a document; the version the page was loaded with is posted with the title, so a document changed since
     * (in another tab) comes back as a conflict with its current title, rather than being overwritten. A 409
     * `MANDATE_DOCUMENT_VERSION_CONFLICT` is that conflict; another 403 / 404 / 409 is [CHANGED].
     */
    @PostMapping("/{documentId}/rename")
    fun rename(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @ModelAttribute form: RenameDocumentForm,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val document = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)

        val errors = MandateDocumentRules.validateTitle(form.title)
        if (errors.isNotEmpty()) {
            return documentPage(mandate, document, form, errors, emptyMap(), false, false, model)
        }
        return try {
            documentClient.rename(mandate.id, document.id, UpdateMandateDocumentRequest(form.title.trim(), form.version))
            redirect.addFlashAttribute(RENAME_OUTCOME, RENAMED)
            documentPage(mandate.id, document.id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Document ${document.id} of mandate ${mandate.id} not renamed: ${ex.statusCode}")
            when {
                errorCode(ex) == ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT -> {
                    // The agent's title is lost on purpose: what they must check is the document's current one.
                    val current = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)
                    documentPage(
                        mandate,
                        current,
                        RenameDocumentForm(current.title, current.version),
                        emptyMap(),
                        emptyMap(),
                        conflict = true,
                        deleting = false,
                        model,
                    )
                }
                ex.statusCode.value() in CHANGED_STATUSES -> changed(mandate.id, redirect)
                ex.statusCode.value() == 400 ->
                    documentPage(mandate, document, form, serverErrors(ex), emptyMap(), false, false, model)
                else -> documentError(mandate.id, document.id, RENAME_FAILED, redirect)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Document ${document.id} of mandate ${mandate.id} not renamed", ex)
            documentError(mandate.id, document.id, RENAME_FAILED, redirect)
        }
    }

    /** Replaces one page, which keeps its rank; ndopify-server puts the document back to `UPLOADED` and reads it again. */
    @PostMapping("/{documentId}/pages/{pageId}")
    fun replacePage(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
        @RequestParam(name = "file", required = false) file: MultipartFile?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val agent = currentAgent.get() ?: throw SignInRequiredException()
        ProfileRules.header(agent, model)
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val document = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)
        val page = pageId.toLongOrNull()?.let { pageId -> document.pages.firstOrNull { it.id == pageId } }
            ?: return changed(mandate.id, redirect)

        val uploaded = file?.takeIf { !it.isEmpty }?.let { sniff(it) }
        val error = MandateDocumentRules.validatePage(uploaded)
        if (error != null || uploaded == null) {
            return documentPage(
                mandate,
                document,
                RenameDocumentForm(document.title, document.version),
                emptyMap(),
                mapOf(page.id to (error ?: MandateDocumentRules.IMAGE_REQUIRED)),
                false,
                false,
                model,
            )
        }
        val number = document.pages.sortedBy { it.index }.indexOfFirst { it.id == page.id } + 1
        return try {
            documentClient.uploadReplacementPage(
                mandate.id,
                document.id,
                page.id,
                UploadedPage(uploaded.content, uploaded.mimeType.orEmpty(), uploaded.filename),
            )
            redirect.addFlashAttribute(RENAME_OUTCOME, REPLACED)
            redirect.addFlashAttribute(PAGE, number)
            documentPage(mandate.id, document.id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Page ${page.id} of document ${document.id} not replaced: ${ex.statusCode}")
            when {
                ex.statusCode.value() == 400 -> documentPage(
                    mandate,
                    document,
                    RenameDocumentForm(document.title, document.version),
                    emptyMap(),
                    mapOf(page.id to (serverErrors(ex, "file")["file"] ?: REPLACE_FAILED)),
                    false,
                    false,
                    model,
                )
                ex.statusCode.value() in CHANGED_STATUSES -> changed(mandate.id, redirect)
                else -> documentError(mandate.id, document.id, REPLACE_FAILED, redirect)
            }
        } catch (ex: RestClientException) {
            LOGGER.error("Page ${page.id} of document ${document.id} not replaced", ex)
            documentError(mandate.id, document.id, REPLACE_FAILED, redirect)
        }
    }

    @PostMapping("/{documentId}/delete")
    fun delete(@PathVariable id: String, @PathVariable documentId: String, redirect: RedirectAttributes): String {
        currentAgent.get() ?: throw SignInRequiredException()
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val document = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)
        return try {
            documentClient.delete(mandate.id, document.id)
            redirect.addFlashAttribute(OUTCOME, DELETED)
            back(mandate.id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Document ${document.id} of mandate ${mandate.id} not deleted: ${ex.statusCode}")
            if (ex.statusCode.value() in CHANGED_STATUSES) changed(mandate.id, redirect) else failed(mandate.id, redirect)
        } catch (ex: RestClientException) {
            LOGGER.error("Document ${document.id} of mandate ${mandate.id} not deleted", ex)
            failed(mandate.id, redirect)
        }
    }

    /**
     * A short-lived URL to upload a page straight to the storage, for `mandate-document.js`. JSON: 401 without a
     * signed-in agent (never the sign-in redirect, which `fetch` would follow to a 200), 404 for an unknown or another
     * agent's mandate, 409 once it isn't ACTIVE any more; else the storage's answer (no URL with local storage), or
     * ndopify-server's status and error body when it refuses.
     */
    @PostMapping("/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun presign(
        @PathVariable id: String,
        @RequestBody request: PresignUploadRequest,
    ): ResponseEntity<*> = json(id) { mandate ->
        ResponseEntity.ok(documentClient.presign(mandate.id, request.contentType.orEmpty()))
    }

    /**
     * Records a document from the pages `mandate-document.js` uploaded, then the script opens [saved] as the without-JS
     * add does. A type, 1 to [MandateDocumentRules.MAX_PAGES] unique paths and a title of
     * [MandateDocumentRules.MAX_TITLE] characters at most are checked here first: 400 without calling ndopify-server.
     */
    @PostMapping("/create", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun create(
        @PathVariable id: String,
        @RequestBody request: CreateMandateDocumentRequest,
    ): ResponseEntity<*> = json(id) { mandate ->
        if (
            request.type == com.wutsi.ndopify.mandate.dto.DocumentType.UNKNOWN ||
            request.paths.isEmpty() ||
            request.paths.size > MandateDocumentRules.MAX_PAGES ||
            request.paths.distinct().size != request.paths.size ||
            (request.title ?: "").trim().length > MandateDocumentRules.MAX_TITLE
        ) {
            return@json ResponseEntity.badRequest().build<Any>()
        }
        ResponseEntity.ok(
            CreateMandateDocumentResponse(documentClient.create(mandate.id, request.copy(title = request.title?.trim()?.ifEmpty { null }))),
        )
    }

    /** After a direct add: the mandate page with the new document's title, as the without-JS add comes back. */
    @GetMapping("/saved")
    fun saved(
        @PathVariable id: String,
        @RequestParam(name = "documentId", required = false) documentId: String?,
        redirect: RedirectAttributes,
    ): String {
        currentAgent.get() ?: throw SignInRequiredException()
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val document = documentId?.let { load(mandate.id, it) } ?: return changed(mandate.id, redirect)
        redirect.addFlashAttribute(OUTCOME, ADDED)
        redirect.addFlashAttribute(TITLE, document.title.trim().ifEmpty { MandateDocumentLabels.type(document.type) })
        return back(mandate.id)
    }

    /** A short-lived URL to replace a page by a direct upload, for `mandate-document.js`; same JSON answers as [presign]. */
    @PostMapping("/{documentId}/pages/{pageId}/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun presignPage(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
        @RequestBody request: PresignUploadRequest,
    ): ResponseEntity<*> = json(id) { mandate ->
        ResponseEntity.ok(documentClient.presign(mandate.id, request.contentType.orEmpty()))
    }

    /**
     * Replaces a page by the file uploaded at [AttachUploadRequest.path], for `mandate-document.js`. 404 for a document
     * or page that isn't the mandate's, else the page's id (ndopify-server's status and error body when it refuses).
     */
    @PostMapping("/{documentId}/pages/{pageId}/attach", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun attach(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
        @RequestBody request: AttachUploadRequest,
    ): ResponseEntity<*> {
        currentAgent.get() ?: return unauthorized()
        val mandateId = id.toLongOrNull() ?: return notFound()
        val document = load(mandateId, documentId) ?: return notFound()
        val page = pageId.toLongOrNull()?.let { pageId -> document.pages.firstOrNull { it.id == pageId } }
            ?: return notFound()
        return try {
            ResponseEntity.ok(
                AttachMandateDocumentPageResponse(documentClient.replacePage(mandateId, document.id, page.id, request.path)),
            )
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Page ${page.id} of document ${document.id} not attached: ${ex.statusCode}")
            relayed(ex)
        } catch (ex: RestClientException) {
            LOGGER.error("Page ${page.id} of document ${document.id} not attached", ex)
            gateway()
        }
    }

    /** After a direct replacement: back to the document with the page it replaced, as the multipart form comes back. */
    @GetMapping("/{documentId}/pages/{pageId}/saved")
    fun savedPage(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
        redirect: RedirectAttributes,
    ): String {
        currentAgent.get() ?: throw SignInRequiredException()
        val mandate = writableMandate(id) ?: return changed(id, redirect)
        val document = load(mandate.id, documentId) ?: return changed(mandate.id, redirect)
        val page = pageId.toLongOrNull()?.let { pageId -> document.pages.firstOrNull { it.id == pageId } }
            ?: return changed(mandate.id, redirect)
        redirect.addFlashAttribute(RENAME_OUTCOME, REPLACED)
        redirect.addFlashAttribute(PAGE, document.pages.sortedBy { it.index }.indexOfFirst { it.id == page.id } + 1)
        return documentPage(mandate.id, document.id)
    }

    /**
     * The JSON proxies' frame: the signed-in agent's ACTIVE mandate, else 401 / 404 / 409, and a call that always
     * answers JSON — ndopify-server's status and body when it refuses, 502 when it fails or can't be reached, so
     * `fetch` never gets an HTML page.
     */
    private fun json(id: String, call: (Mandate) -> ResponseEntity<*>): ResponseEntity<*> {
        currentAgent.get() ?: return unauthorized()
        val mandateId = id.toLongOrNull() ?: return notFound()
        val mandate = try {
            mandateClient.get(mandateId)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No mandate $mandateId: ${ex.statusCode}")
            return notFound()
        } catch (ex: RestClientException) {
            LOGGER.error("No mandate $mandateId", ex)
            return gateway()
        }
        if (mandate.status !in MandateRules.SHOWN) return notFound()
        if (!MandateDocumentRules.writable(mandate)) return ResponseEntity.status(HttpStatus.CONFLICT).build<Any>()
        return try {
            call(mandate)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("Documents of mandate $mandateId refused: ${ex.statusCode}")
            relayed(ex)
        } catch (ex: RestClientException) {
            LOGGER.error("Documents of mandate $mandateId failed", ex)
            gateway()
        }
    }

    /** ndopify-server's refusal, status and error body, as `mandate-document.js` reads it. */
    private fun relayed(ex: HttpClientErrorException): ResponseEntity<Any> =
        ResponseEntity.status(ex.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ex.responseBodyAsString.ifBlank {
                    """{"error":{"code":"${errorCode(ex).orEmpty()}"}}"""
                },
            )

    private fun unauthorized(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    private fun notFound(): ResponseEntity<Any> = ResponseEntity.notFound().build()

    private fun gateway(): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()

    private fun addPage(
        mandate: Mandate,
        form: AddDocumentForm,
        errors: Map<String, String>,
        error: String?,
        model: Model,
    ): String {
        model.addAttribute("mandate", view(mandate))
        model.addAttribute("form", form)
        model.addAttribute("errors", errors)
        model.addAttribute("error", error)
        model.addAttribute("suggested", MandateDocumentLabels.suggested(mandate.mandateType))
        model.addAttribute("others", MandateDocumentLabels.others(mandate.mandateType))
        model.addAttribute("labels", MandateDocumentLabels)
        return VIEW_NEW
    }

    private fun documentPage(
        mandate: Mandate,
        document: MandateDocument,
        renameForm: RenameDocumentForm,
        renameErrors: Map<String, String>,
        replaceErrors: Map<Long, String>,
        conflict: Boolean,
        deleting: Boolean,
        model: Model,
    ): String {
        model.addAttribute("mandate", view(mandate))
        model.addAttribute("document", MandateDocumentViews.document(mandate, document, formatter()))
        model.addAttribute("reading", MandateDocumentReadingViews.of(mandate, document, formatter(), labels, today()))
        model.addAttribute("renameForm", renameForm)
        model.addAttribute("renameErrors", renameErrors)
        model.addAttribute("replaceErrors", replaceErrors)
        model.addAttribute("conflict", conflict)
        model.addAttribute("deleting", deleting)
        (model.getAttribute(RENAME_OUTCOME) as? String)?.let { outcome ->
            val message = model.getAttribute(OUTCOME_MESSAGE) as? String
            model.addAttribute("outcomeText", message ?: outcomeText(outcome, model.getAttribute(PAGE) as? Int))
            model.addAttribute("outcomeStyle", if (outcome == FAILED) "error" else "success")
        }
        return VIEW_DOCUMENT
    }

    private fun outcomeText(outcome: String, page: Int?): String? = when (outcome) {
        RENAMED -> "Titre du document enregistré."
        REPLACED -> if (page != null && page > 0) "Page $page remplacée." else "Page remplacée."
        FAILED -> RENAME_FAILED
        else -> null
    }

    /** Back to the document's page, with the failure the action couldn't report there. */
    private fun documentError(mandateId: Long, documentId: Long, message: String, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(RENAME_OUTCOME, FAILED)
        redirect.addFlashAttribute(OUTCOME_MESSAGE, message)
        return documentPage(mandateId, documentId)
    }

    private fun documentPage(mandateId: Long, documentId: Long): String =
        "redirect:/mandates/$mandateId/documents/$documentId"

    private fun back(id: Long): String = "redirect:/mandates/$id#documents-title"

    /** Stale page: the mandate page (or its locked view) says the document changed, never an error page. */
    private fun changed(id: String, redirect: RedirectAttributes): String {
        val mandateId = id.toLongOrNull() ?: return "redirect:/"
        return changed(mandateId, redirect)
    }

    private fun changed(id: Long, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(OUTCOME, CHANGED)
        return "redirect:/mandates/$id"
    }

    private fun failed(id: Long, redirect: RedirectAttributes): String {
        redirect.addFlashAttribute(OUTCOME, FAILED)
        return back(id)
    }

    /** The mandate, if the action still applies to it (the page may be stale); else null, and [changed] answers. */
    private fun writableMandate(id: String): Mandate? =
        shownMandate(id)?.takeIf { MandateDocumentRules.writable(it) }

    private fun shownMandate(id: String): Mandate? = id.toLongOrNull()?.let { shown(it) }

    private fun shown(mandateId: Long): Mandate? =
        try {
            mandateClient.get(mandateId).takeIf { it.status in MandateRules.SHOWN }
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No mandate $mandateId: ${ex.statusCode}")
            null
        } catch (ex: RestClientException) {
            LOGGER.error("No mandate $mandateId", ex)
            null
        }

    /** The document, null when it's gone, another mandate's or no longer the agent's. */
    private fun load(mandateId: Long, documentId: String): MandateDocument? {
        val id = documentId.toLongOrNull() ?: return null
        return try {
            documentClient.get(mandateId, id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No document $id of mandate $mandateId: ${ex.statusCode}")
            null
        } catch (ex: RestClientException) {
            LOGGER.error("No document $id of mandate $mandateId", ex)
            null
        }
    }

    private fun sniff(file: MultipartFile): SniffedPage {
        val content = file.bytes
        return SniffedPage(
            filename = file.originalFilename?.ifBlank { null } ?: "Cette image",
            size = file.size,
            mimeType = TIKA.detect(content),
            content = content,
        )
    }

    /** The server's 400 on its field (`error.parameter.name`), or on [fallback] when it names another one. */
    private fun serverErrors(ex: HttpClientErrorException, fallback: String = "files"): Map<String, String> {
        val error = try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error
        } catch (ignored: Exception) {
            null
        }
        val field = error?.parameter?.name?.takeIf { it == "type" || it == "title" || it == fallback } ?: fallback
        val message = error?.message?.takeIf { it.isNotBlank() } ?: when (fallback) {
            "file" -> TOO_LARGE
            else -> "Le document n'a pas pu être ajouté : vérifiez ses pages."
        }
        return mapOf(field to message)
    }

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }

    private fun view(mandate: Mandate) = MandateViews.of(
        mandate,
        null,
        null,
        null,
        emptyMap(),
        clock.millis(),
        formatter(),
    )

    private fun formatter(): TenantFormatter = TenantFormatter(tenantContext.getTenant())

    /** Today in the tenant's time zone. Like the rest of this controller, only reads `clock.millis()`. */
    private fun today(): LocalDate = formatter().today(Clock.fixed(Instant.ofEpochMilli(clock.millis()), ZoneOffset.UTC))
}
