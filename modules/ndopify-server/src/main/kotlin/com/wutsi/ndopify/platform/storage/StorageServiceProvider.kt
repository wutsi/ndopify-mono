package com.wutsi.ndopify.platform.storage

import com.wutsi.ndopify.refdata.dto.StorageType

class StorageServiceProvider(
    private val defaultType: StorageType,
    private val local: StorageService,
    private val s3: StorageService
) {
    fun get(): StorageService {
        return get(defaultType)
    }

    fun get(type: StorageType): StorageService {
        return when (type) {
            StorageType.S3 -> s3
            StorageType.LOCAL -> local
            StorageType.UNKNOWN -> throw IllegalArgumentException("Unsupported storage type $type")
        }
    }
}
