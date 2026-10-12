package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier
import com.wutsi.ndopify.platform.logger.KVLogger

abstract class AbstractKycCaseVerifier(
    protected val logger: KVLogger,
) : KycCaseVerifier {
    abstract fun getVerificationType(): KycVerificationType

    protected fun addToLogger(verification: KycVerificationEntity, suffix: String? = null) {
        addToLogger("verif_status", verification.status, suffix)
        addToLogger("verif_score", verification.score, suffix)
        addToLogger("verif_error_code", verification.errorCode, suffix)
        addToLogger("verif_error_message", verification.errorMessage, suffix)
    }

    protected fun addToLogger(info: KycInfo, suffix: String? = null) {
        addToLogger("info_valid", info.valid, suffix)
        addToLogger("info_confidence", info.confidence, suffix)
        addToLogger("info_invalidity_reason", info.invalidityReason, suffix)
        addToLogger("info_type", info.type, suffix)
        addToLogger("info_number", info.number, suffix)
        addToLogger("info_first_name", info.firstName, suffix)
        addToLogger("info_last_name", info.lastName, suffix)
        addToLogger("info_country_code", info.countryCode, suffix)
        addToLogger("info_expiry_date", info.expiryDate, suffix)
    }

    protected fun addToLogger(name: String, value: Any?, suffix: String? = null) {
        if (value != null) {
            val key = "kyc_" + getVerificationType() + "_$name" + (suffix?.let { "_$it" } ?: "")
            logger.add(key.lowercase(), value)
        }
    }
}
