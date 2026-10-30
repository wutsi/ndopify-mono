package com.wutsi.ndopify.esignature.ui.verify

import com.wutsi.ndopify.esignature.client.ndopify.NdContractClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.TenantFormatter
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * The page the QR code printed next to a signature in the signed contract opens,
 * `/verify/{contractId}/{signatureHash}`. Public: no session, no cookie, nothing but the contract's number, the signer's
 * name, the date and time of the signature, its hash and the signed PDF.
 *
 * Valid when ndopify-server finds the signature (whatever the contract's status: it decides); a malformed hash is
 * invalid without calling it. Any other error than a 404 (5xx, I/O) is thrown, so that it shows as an error page,
 * never as an invalid signature. Both states are an HTTP 200.
 */
@Controller
class VerifyController(
    private val contractClient: NdContractClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        const val VIEW = "verify"

        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }

    @GetMapping("/verify/{contractId}/{signatureHash}")
    fun verify(
        @PathVariable contractId: String,
        @PathVariable signatureHash: String,
        model: Model,
    ): String {
        model.addAttribute("view", view(contractId, signatureHash.trim().lowercase()))
        return VIEW
    }

    private fun view(contractId: String, hash: String): VerifyView {
        if (!SHA256.matches(hash)) return VerifyView(valid = false)

        val signature = contractClient.getSignature(contractId, hash) ?: return VerifyView(valid = false)
        val formatter = TenantFormatter(tenantContext.getTenant())
        return VerifyView(
            valid = true,
            contractNumber = signature.contractNumber,
            signerName = signature.legalName,
            signedAt = "${formatter.date(signature.signedAt)} à ${formatter.time(signature.signedAt)}",
            signatureHash = hash,
        )
    }
}
