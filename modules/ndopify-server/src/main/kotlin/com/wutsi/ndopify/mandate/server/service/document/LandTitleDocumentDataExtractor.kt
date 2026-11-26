package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock

/**
 * Reads a land title or a customary certificate of ownership: the title number, its owners and where the plot is.
 * A document the model recognizes as another type is kept as [DocumentData.detectedType]; the caller reports it.
 */
@Service
class LandTitleDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in real estate and land ownership documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from the document:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - titleNumber: the number or reference of the title, certificate or deed
            - owners: the names of the owners, as a list of strings
            - department: the département the plot is in
            - arrondissement: the arrondissement the plot is in
            - locality: the locality, city or village the plot is in
            - surface: the plot's surface in m², as a number
            - issueDate: the document's issue date in YYYY-MM-DD format
            - valid: true if the document is readable and looks authentic, false otherwise
                - Do not consider an expired or old document as invalid.
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            Return only the information in the following JSON format:
            {
                "detectedType": "LAND_TITLE",
                "titleNumber": "TF-1234",
                "owners": ["John Doe", "Jane Doe"],
                "department": "Centre",
                "arrondissement": "Mfoundi",
                "locality": "Yaoundé",
                "surface": "500",
                "issueDate": "2020-01-01",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"titleNumber": 0.9, "owners": 0.8, "locality": 0.85}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val instructions = documentPrompt(PROMPT, type, clock)
        return if (type == DocumentType.CERTIFICATE_OF_OWNERSHIP) {
            client.readDocument(instructions, images, CertificateOfOwnership::class.java)
                ?: throw DocumentExtractionException("empty certificate of ownership extraction")
        } else {
            client.readDocument(instructions, images, LandTitle::class.java)
                ?: throw DocumentExtractionException("empty land title extraction")
        }
    }
}
