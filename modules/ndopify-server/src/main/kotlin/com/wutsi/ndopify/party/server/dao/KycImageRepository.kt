package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface KycImageRepository : CrudRepository<KycImageEntity, String> {
    fun findByKycCaseAndIdentificationTypeAndImageType(
        kycCase: KycCaseEntity,
        identificationType: IdentificationType,
        imageType: KycImageType
    ): KycImageEntity?

    fun findByKycCaseAndPath(kycCase: KycCaseEntity, path: String): List<KycImageEntity>

    /**
     * The step's image as the database has it, not the persistence context (which may hold a row read before the
     * storage I/O): see `KycService.saveImage`. Native, so not tenant-scoped: only for a case already loaded through
     * the tenant-scoped repository.
     */
    @Query(
        value = "SELECT id AS id, path AS path, storage_type AS storageType FROM T_KYC_IMAGE " +
            "WHERE case_id = :caseId AND identification_type = :identificationType AND image_type = :imageType",
        nativeQuery = true,
    )
    fun findRow(caseId: String, identificationType: String, imageType: String): KycImageRow?
}

/** A `T_KYC_IMAGE` row's id, path and storage type, read by [KycImageRepository.findRow]. */
interface KycImageRow {
    fun getId(): String

    fun getPath(): String?

    fun getStorageType(): String?
}
