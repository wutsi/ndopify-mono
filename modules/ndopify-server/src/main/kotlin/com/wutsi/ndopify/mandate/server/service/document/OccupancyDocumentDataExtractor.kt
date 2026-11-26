package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock

/**
 * Reads the documents that prove where a person lives: a lease contract, a utility bill or a tax receipt. The holder's
 * name, its address or its locality, the provider and the issue date.
 */
@Service
class OccupancyDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in housing and utility documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from the document:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - holderName: the full name of the person the document is addressed to
            - address: the postal address printed on the document, when any
            - locality: the city, town or village of the address, when the address is not complete
            - provider: the company, landlord or administration that issued the document
            - issueDate: the document's issue date in YYYY-MM-DD format
            - valid: true if the document is readable and looks authentic, false otherwise
                - Do not consider an old document as invalid.
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            Return only the information in the following JSON format:
            {
                "detectedType": "UTILITY_BILL",
                "holderName": "John Doe",
                "address": "Rue 1, Bastos",
                "locality": "Yaoundé",
                "provider": "ENEO",
                "issueDate": "2020-01-01",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"holderName": 0.9, "address": 0.85, "issueDate": 0.9}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.LEASE_CONTRACT, DocumentType.UTILITY_BILL, DocumentType.TAX_RECEIPT)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val instructions = documentPrompt(PROMPT, type, clock)
        return when (type) {
            DocumentType.LEASE_CONTRACT -> client.readDocument(instructions, images, LeaseContract::class.java)
                ?: throw DocumentExtractionException("empty lease contract extraction")
            DocumentType.TAX_RECEIPT -> client.readDocument(instructions, images, TaxReceipt::class.java)
                ?: throw DocumentExtractionException("empty tax receipt extraction")
            else -> client.readDocument(instructions, images, UtilityBill::class.java)
                ?: throw DocumentExtractionException("empty utility bill extraction")
        }
    }
}
