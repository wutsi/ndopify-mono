package com.wutsi.ndopify.security.server.domain

import com.wutsi.ndopify.refdata.dto.AuthType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Clock
import java.util.Date

@Entity
@Table(name = "T_AUTH_FACTOR")
data class AuthFactorEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity = UserEntity(),

    val authType: AuthType = AuthType.UNKNOWN,
    val data: String = "",
    val salt: String? = null,

    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
    val lastLoggedInAt: Date? = null,
    val expiresAt: Date? = null,
) {
    fun hasExpired(clock: Clock): Boolean {
        return expiresAt != null && expiresAt.time < clock.millis()
    }

    /**
     * Whether this one-time code was already used to log in: [AuthFactorService.consume] expires it as it's used, so
     * it expired no later than the last login. A code issued since then expires after that login, so is not consumed.
     */
    fun isConsumed(): Boolean {
        return lastLoggedInAt != null && expiresAt != null && !lastLoggedInAt.before(expiresAt)
    }
}
