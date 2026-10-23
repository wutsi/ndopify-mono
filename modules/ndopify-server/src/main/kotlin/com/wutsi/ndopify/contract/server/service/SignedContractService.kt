package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.Date
import java.util.UUID

/**
 * Generates the signed PDF of a SIGNED contract (see [SignedContractRenderer]), with one signature block per party that
 * signed, whose QR code opens `{eSignaturePortalUrl}/verify/{contractId}/{signatureHash}`. Stored in the directory of
 * the contract's content: `{number}-{token}.pdf`, and the QR codes as `{number}-{token}-signature-{n}.png`. The
 * [token] is random, new for each run: the storage is public, and the directory and number are known to whoever has a
 * QR code; and two runs at once never overwrite each other's files. Never named after the contract party, whose id is
 * the secret of its sign link.
 *
 * Not transactional: nothing is locked while the content is downloaded, and the files rendered and uploaded. The PDF's
 * URL and hash are then saved under the contract's lock ([ContractService.setSignedContent], first one wins). Must
 * run with `TenantContext` set.
 */
@Service
class SignedContractService(
    private val contractService: ContractService,
    private val tenantService: TenantService,
    private val storageProvider: StorageServiceProvider,
    private val renderer: SignedContractRenderer,
    private val clock: Clock,
    @param:Value("\${ndopify.contract.signed-content.delay-minutes}") private val delayMinutes: Long,
    @param:Value("\${ndopify.contract.signed-content.window-days}") private val windowDays: Long,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SignedContractService::class.java)
    }

    /**
     * Generates the signed PDF of the contracts that still have none: the PDF of a last signature is generated once, by
     * `ContractEventListener.onSigned`, and is lost when it fails or the server stops before it runs. Only the contracts
     * signed more than [delayMinutes] ago, so that the listener's own run isn't raced (harmless: first one wins), and less
     * than [windowDays] ago, so that a contract that can't be generated (no content, hash mismatch...) isn't retried
     * forever. Each contract on its own, so that one failing doesn't stop the others. Returns how many were generated.
     */
    fun generateMissing(): Int {
        val now = clock.millis()
        val ids = contractService.findIdsWithoutSignedContent(
            from = Date(now - windowDays * 24 * 60 * 60 * 1000),
            to = Date(now - delayMinutes * 60 * 1000),
        )
        return ids.count { id ->
            try {
                generate(id) != null
            } catch (ex: Exception) {
                LOGGER.warn("Contract#$id - Unable to generate the signed content", ex)
                false
            }
        }
    }

    /**
     * Returns the signed PDF's URL, or null when none was generated: the contract isn't SIGNED or already has one, or
     * it can't be generated (logged) — no content in the storage, a content that isn't the one signed (its hash), a
     * signature without hash or salt, or a tenant without e-signature portal for the QR codes.
     */
    fun generate(id: String): String? {
        val contract = contractService.findById(id)
        if (contract.status != ContractStatus.SIGNED || contract.signedContentUrl != null) {
            return null
        }

        val tenant = tenantService.findById(TenantContext.get()!!)
        val portalUrl = tenant.eSignaturePortalUrl?.trim()?.removeSuffix("/")?.ifBlank { null }
            ?: return skip(id, "no eSignaturePortalUrl")
        val storage = storageProvider.get()
        val contentPath = contract.contentUrl?.let { url -> storage.pathOf(url) }
            ?: return skip(id, "content not in the storage")
        val dir = contentPath.substringBeforeLast('/', "")

        val content = ByteArrayOutputStream().also { out -> storage.get(contentPath, out) }.toByteArray()
        if (contract.contentHash != null && contract.contentHash != ContractHasher.contentHash(content)) {
            return skip(id, "content hash mismatch") // Not the content the parties signed
        }

        val signers = contractService.findParties(contract).filter { party -> party.signed }
        if (signers.any { party -> party.signatureHash == null || party.signatureSalt == null }) {
            return skip(id, "signature without hash or salt")
        }

        val name = join(dir, "${contract.number}-${token()}")
        val formatter = TenantFormatter(tenant)
        val blocks = signers.mapIndexed { i, party ->
            val qrCode = renderer.qrCode("$portalUrl/verify/${contract.id}/${party.signatureHash}")
            storage.store("$name-signature-${i + 1}.png", ByteArrayInputStream(qrCode), "image/png")
            SignatureBlock(
                legalName = party.legalName,
                signedAt = "${formatter.date(party.signedAt!!)} à ${formatter.time(party.signedAt)}",
                signatureHash = party.signatureHash!!,
                qrCodePng = qrCode,
            )
        }
        val pdf = renderer.render(String(content, Charsets.UTF_8), blocks)
        val pdfPath = "$name.pdf"
        storage.store(pdfPath, ByteArrayInputStream(pdf), "application/pdf")

        val url = storage.publicUrl(pdfPath).toString()
        return contractService.setSignedContent(id, url, ContractHasher.contentHash(pdf)).signedContentUrl
    }

    private fun skip(id: String, reason: String): String? {
        LOGGER.warn("Contract#$id - No signed content: $reason")
        return null
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "")

    private fun join(dir: String, name: String): String = if (dir.isEmpty()) name else "$dir/$name"
}
