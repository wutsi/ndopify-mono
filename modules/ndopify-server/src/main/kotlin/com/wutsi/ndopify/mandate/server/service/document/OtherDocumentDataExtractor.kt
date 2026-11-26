package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock

/** Reads a document of no known type: what it could be titled and what it says, so the agent can still use it. */
@Service
class OtherDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in reading documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to describe the document:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}, when one fits
            - title: a short title for the document, in its own language
            - summary: what the document says, in a few sentences, in its own language
            - valid: true if the document is readable and looks authentic, false otherwise
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            Return only the information in the following JSON format:
            {
                "detectedType": "OTHER",
                "title": "Acte de vente",
                "summary": "Vente d'un terrain de 500 m² à Yaoundé.",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"title": 0.9, "summary": 0.7}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.OTHER)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val instructions = documentPrompt(PROMPT, type, clock)
        return client.readDocument(instructions, images, Other::class.java)
            ?: throw DocumentExtractionException("empty document extraction")
    }
}
