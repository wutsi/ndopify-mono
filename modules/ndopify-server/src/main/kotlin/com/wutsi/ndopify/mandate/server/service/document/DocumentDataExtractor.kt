package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.util.MimeUtils
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import java.io.File
import java.time.Clock
import java.time.LocalDate

/** Reads one family of mandate documents with the vision model, returning its typed [DocumentData]. */
interface DocumentDataExtractor {
    /** The [DocumentType]s this extractor handles; an unhandled type is never sent to the model. */
    val types: Set<DocumentType>

    /**
     * Reads [type]'s [images] (one derived JPEG per page, never the stored original) and returns the typed data,
     * whatever its validity: [DocumentData.valid] and the problem codes are checked by the caller.
     */
    fun extract(type: DocumentType, images: List<File>): DocumentData
}

/**
 * Sends [prompt] and [images] to the vision [client] and reads its answer as a [clazz]. It is the request the KYC
 * identification extractor makes: the text, then each page as a file resource with its own MIME type.
 */
internal fun <T : Any> ChatClient.readDocument(prompt: String, images: List<File>, clazz: Class<T>): T? = prompt()
    .user { user ->
        val spec = user.text(prompt)
        images.forEach { file ->
            val mediaType = MediaType.parseMediaType(MimeUtils.getMimeTypeFromExtension(file.extension))
            spec.media(mediaType, FileSystemResource(file))
        }
        spec
    }
    .call()
    .entity(clazz)

/**
 * Fills a document prompt's placeholders: the declared type, every type name and [today], the calendar date a document's
 * own date is read against ([LocalDate.now] in the clock's zone, or the tenant's today when the caller has one).
 */
internal fun documentPrompt(
    template: String,
    type: DocumentType,
    clock: Clock,
    today: LocalDate = LocalDate.now(clock),
): String = template
    .replace("{{TYPE}}", type.name)
    .replace("{{DOCUMENT_TYPES}}", DocumentType.entries.joinToString(", ") { it.name })
    .replace("{{TODAY}}", today.toString())
