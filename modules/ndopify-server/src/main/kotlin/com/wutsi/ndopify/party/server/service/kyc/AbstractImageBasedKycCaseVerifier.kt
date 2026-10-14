package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycInfoExtractorProvider
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.MimeUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Clock
import java.util.Date
import java.util.UUID

abstract class AbstractImageBasedKycCaseVerifier(
    protected val clock: Clock,
    protected val storageProvider: StorageServiceProvider,
    protected val infoExtractorProvider: KycInfoExtractorProvider,
    logger: KVLogger,
) : AbstractKycCaseVerifier(logger) {
    abstract fun getImageTypes(): List<KycImageType>
    abstract fun match(verification: KycVerificationEntity, info: KycInfo): KycVerificationEntity

    override fun verify(case: KycCaseEntity): List<KycVerificationEntity> {
        val files = getFiles(case)
        if (files.isEmpty()) {
            return emptyList()
        }

        try {
            val info = extractInformations(files)

            val now = Date(clock.millis())
            val verification = KycVerificationEntity(
                id = UUID.randomUUID().toString(),
                case = case,
                type = getVerificationType(),
                createdAt = now,
                modifiedAt = now
            )
            val xverification = match(verification, info)
            addToLogger(xverification)

            return listOf(xverification)
        } finally {
            // Identity documents: don't leave them on disk
            files.forEach { file -> file.delete() }
        }
    }

    private fun extractInformations(files: List<File>): KycInfo {
        val infoExtractor = infoExtractorProvider.get(getVerificationType())
        val info = infoExtractor.extract(files)

        addToLogger(info)
        return info
    }

    private fun getFiles(case: KycCaseEntity): List<File> {
        val files = case.images
            .filter { img -> img.path != null }
            .filter { img -> getImageTypes().contains(img.imageType) }
            .map { img -> toFile(img) }
        return files
    }

    private fun toFile(img: KycImageEntity): File {
        val extension = MimeUtils.getExtensionFromMimeType(img.mimeType)
        val file = Files.createTempFile(img.id + "-${img.imageType}", ".$extension").toFile()
        val storage = storageProvider.get(img.storageType) // where the image was stored, not the default storage
        FileOutputStream(file).use { out ->
            storage.get(img.path!!, out)
        }
        return file
    }
}
