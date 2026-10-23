package com.wutsi.ndopify.esignature.client

/** A contract's document as the sign page shows it: [html] sanitized, [hash] the SHA-256 of the bytes downloaded. */
data class ContractContent(
    val html: String,
    // Lowercase hex, as ndopify-server's ContractHasher.contentHash: sent with the signature, which it must match
    val hash: String,
)
