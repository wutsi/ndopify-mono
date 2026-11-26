package com.wutsi.ndopify.mandate.server.service.document

import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.platform.ai.ChatClients
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the financial documents of a mandate: a pay slip, a bank pre-approval and a proof of funds. Amounts are read as
 * plain numbers and the currency as its own ISO 4217 code (the tenant's for *FCFA* / *F CFA*), so the client formats
 * them through its `TenantFormatter`; today is the tenant's, not the server's.
 *
 * The model is asked for the last four digits of an account number only, and [FinancialData.sanitized] enforces it on
 * its answer before the data leaves the extractor: a prompt can ask, it can't be trusted.
 */
@Service
class FinancialDocumentDataExtractor(
    @Qualifier(ChatClients.VISION) private val client: ChatClient,
    private val tenantService: TenantService,
    private val clock: Clock,
) : DocumentDataExtractor {
    companion object {
        private const val COMMON_RULES = """
            - valid: true if the document is readable and looks authentic, false otherwise
                - Do not consider an expired or old document as invalid.
                - Consider the document invalid when it looks forged or tampered, the image is blurry, the information is not readable, a needed page is missing, or for another reason that makes it invalid.
            - invalidityReason: why the document is invalid; omit it when it is valid
            - confidence: a score between 0 and 1 for your overall assessment
            - fieldConfidences: a score between 0 and 1 for each extracted field, keyed by its JSON name

            ## Personal data
            - Never write a full account number, an IBAN or a card number: give only its last 4 digits.
            - An amount is a plain number, without spaces, separators or currency symbol.
            - A currency is an ISO 4217 code. FCFA and F CFA mean {{CURRENCY}}.
        """

        const val PAY_SLIP_PROMPT = """
            You are an expert in payroll documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from a pay slip:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - employerName: the name of the employer
            - employeeName: the full name of the employee
            - payMonth: the month the pay slip covers, in YYYY-MM format
            - grossSalary: the gross salary, as a plain number
            - netSalary: the net salary, as a plain number
            - currency: the ISO 4217 code of the salaries
            $COMMON_RULES

            Return only the information in the following JSON format:
            {
                "detectedType": "PAY_SLIP",
                "employerName": "ACME",
                "employeeName": "John Doe",
                "payMonth": "2026-10",
                "grossSalary": 400000,
                "netSalary": 350000,
                "currency": "{{CURRENCY}}",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"employeeName": 0.9, "payMonth": 0.9, "netSalary": 0.9, "currency": 0.9}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """

        const val BANK_PRE_APPROVAL_PROMPT = """
            You are an expert in banking documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from a bank pre-approval:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - bankName: the name of the bank
            - beneficiaries: the names of the beneficiaries, as a list of strings
            - amount: the approved amount, as a plain number
            - currency: the ISO 4217 code of the amount
            - issueDate: the document's issue date in YYYY-MM-DD format
            - validityDate: the date the pre-approval is valid until, in YYYY-MM-DD format
            $COMMON_RULES

            Return only the information in the following JSON format:
            {
                "detectedType": "BANK_PRE_APPROVAL",
                "bankName": "Afriland First Bank",
                "beneficiaries": ["John Doe", "Jane Doe"],
                "amount": 25000000,
                "currency": "{{CURRENCY}}",
                "issueDate": "2026-11-01",
                "validityDate": "2027-01-31",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"bankName": 0.9, "beneficiaries": 0.9, "amount": 0.9, "validityDate": 0.9}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """

        const val PROOF_OF_FUNDS_PROMPT = """
            You are an expert in banking documents.
            You will be provided images of a document declared as {{TYPE}}.
            Your task is to extract the following information from a bank statement or a bank attestation:
            - detectedType: the type of the document among {{DOCUMENT_TYPES}}
            - bankName: the name of the bank
            - accountHolder: the full name of the account holder
            - amount: the account's balance or the certified amount, as a plain number
            - amountKind: BALANCE for a statement, CERTIFIED for an attestation
            - currency: the ISO 4217 code of the amount
            - statementDate: the statement or attestation date in YYYY-MM-DD format
            - accountLast4: the last 4 digits of the account number, and nothing else; never write the full number or the IBAN
            $COMMON_RULES

            Return only the information in the following JSON format:
            {
                "detectedType": "PROOF_OF_FUNDS",
                "bankName": "Afriland First Bank",
                "accountHolder": "John Doe",
                "amount": 12000000,
                "amountKind": "BALANCE",
                "currency": "{{CURRENCY}}",
                "statementDate": "2026-11-20",
                "accountLast4": "0123",
                "valid": true,
                "confidence": 0.8,
                "fieldConfidences": {"accountHolder": 0.9, "amount": 0.9, "statementDate": 0.9, "accountLast4": 0.9}
            }

            ## Additional Information
            - Today's date is {{TODAY}}.
        """
    }

    override val types = setOf(DocumentType.PAY_SLIP, DocumentType.BANK_PRE_APPROVAL, DocumentType.PROOF_OF_FUNDS)

    override fun extract(type: DocumentType, images: List<File>): DocumentData {
        val tenant = tenant()
        val today = LocalDate.now(clock.withZone(zone(tenant.timeZone)))
        val template = when (type) {
            DocumentType.BANK_PRE_APPROVAL -> BANK_PRE_APPROVAL_PROMPT
            DocumentType.PROOF_OF_FUNDS -> PROOF_OF_FUNDS_PROMPT
            else -> PAY_SLIP_PROMPT
        }
        val instructions = documentPrompt(template, type, clock, today)
            .replace("{{CURRENCY}}", tenant.currencyCode)

        return when (type) {
            DocumentType.BANK_PRE_APPROVAL -> (
                client.readDocument(instructions, images, BankPreApprovalData::class.java)
                    ?: throw DocumentExtractionException("empty bank pre-approval extraction")
                ).sanitized()
            DocumentType.PROOF_OF_FUNDS -> (
                client.readDocument(instructions, images, ProofOfFundsData::class.java)
                    ?: throw DocumentExtractionException("empty proof of funds extraction")
                ).sanitized()
            else -> (
                client.readDocument(instructions, images, PaySlipData::class.java)
                    ?: throw DocumentExtractionException("empty pay slip extraction")
                ).sanitized()
        }
    }

    /** The current tenant: the extraction runs with it in [TenantContext] (the job, or the ops re-run's request). */
    private fun tenant(): TenantEntity {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant in context")
        return tenantService.findById(tenantId)
    }

    private fun zone(timeZone: String): ZoneId = try {
        ZoneId.of(timeZone)
    } catch (ex: Exception) {
        clock.zone
    }
}
