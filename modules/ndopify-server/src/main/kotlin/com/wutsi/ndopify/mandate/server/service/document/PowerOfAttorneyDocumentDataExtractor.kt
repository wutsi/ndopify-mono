package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock

/** Reads a power of attorney (*procuration*): who gave it, to whom, for which property and until when. */
@Service
class PowerOfAttorneyDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in legal documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from the document:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - grantor: the full name of the person giving the power
            - attorney: the full name of the person receiving the power
            - purpose: the purpose of the power of attorney, and the property it is about
            - notary: the notary who drew it up, when named
            - issueDate: the document's date in YYYY-MM-DD format
            - expiryDate: the date the power ends, in YYYY-MM-DD format, when it says so
            - valid: true if the document is readable and looks authentic, false otherwise
                - Do not consider an expired document as invalid, but provide its end date.
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            Return only the information in the following JSON format:
            {
                "detectedType": "POWER_OF_ATTORNEY",
                "grantor": "John Doe",
                "attorney": "Jane Doe",
                "purpose": "Sale of the plot TF-1234",
                "notary": "Me Jean Dupont",
                "issueDate": "2020-01-01",
                "expiryDate": "2021-01-01",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"grantor": 0.9, "attorney": 0.9, "purpose": 0.8}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.POWER_OF_ATTORNEY)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val instructions = documentPrompt(PROMPT, type, clock)
        return client.readDocument(instructions, images, PowerOfAttorney::class.java)
            ?: throw DocumentExtractionException("empty power of attorney extraction")
    }
}
