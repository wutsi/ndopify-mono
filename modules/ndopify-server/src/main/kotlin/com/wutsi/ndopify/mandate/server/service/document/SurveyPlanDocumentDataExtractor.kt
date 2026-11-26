package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock

/** Reads a survey plan (*plan de bornage*): the parcel reference, where it is, its surface and the printed coordinates. */
@Service
class SurveyPlanDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        const val PROMPT = """
            You are an expert in land surveying documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from the document:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - parcelReference: the reference of the parcel or the survey plan
            - locality: the locality, city or village the parcel is in
            - surface: the parcel's surface in m², as a number
            - coordinates: the geographic coordinates as printed on the plan
            - valid: true if the document is readable and looks authentic, false otherwise
                - Do not consider an old document as invalid.
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            Return only the information in the following JSON format:
            {
                "detectedType": "SURVEY_PLAN",
                "parcelReference": "PB-1234",
                "locality": "Yaoundé",
                "surface": "500",
                "coordinates": "3.8480, 11.5021",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"parcelReference": 0.9, "locality": 0.85}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.SURVEY_PLAN)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val instructions = documentPrompt(PROMPT, type, clock)
        return client.readDocument(instructions, images, SurveyPlan::class.java)
            ?: throw DocumentExtractionException("empty survey plan extraction")
    }
}
