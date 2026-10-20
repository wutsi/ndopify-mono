package com.wutsi.ndopify.esignature.ui.verify

/** What `verify.html` shows. Everything but [valid] is null when the signature isn't valid. */
data class VerifyView(
    val valid: Boolean,
    val contractNumber: String? = null,
    val signerName: String? = null,
    val signedAt: String? = null, // "18 octobre 2026 à 14 h 32", by TenantFormatter
    val signatureHash: String? = null, // the URL's, normalized: only set when the server matched it
)
