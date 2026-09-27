package com.wutsi.ndopify.refdata.server.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "T_ROLE")
data class RoleEntity(
    @Id
    val id: Long? = null,
    val code: String = "",
    val active: Boolean = true,
)
