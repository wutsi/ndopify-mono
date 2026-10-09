package com.wutsi.ndopify.security.server.service

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service

@Service
class PasswordEncryptor {
    fun encrypt(clear: String, salt: String?): String {
        return salt?.let { DigestUtils.md5Hex("$clear-$salt") } ?: DigestUtils.md5Hex(clear)
    }

    fun matches(clear: String, hashed: String, salt: String?): Boolean {
        return hashed.equals(encrypt(clear, salt))
    }
}
