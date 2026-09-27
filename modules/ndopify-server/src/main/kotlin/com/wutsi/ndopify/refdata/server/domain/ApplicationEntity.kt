package com.wutsi.ndopify.refdata.server.domain

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.util.jpa.AuthTypeListConverter
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "T_APPLICATION")
data class ApplicationEntity(
    @Id
    val id: Long = -1,
    val code: String = "",

    @OneToMany
    @JoinColumn(name = "application_id")
    val roles: List<RoleEntity> = emptyList(),

    @Convert(AuthTypeListConverter::class)
    val supportedAuthTypes: List<AuthType> = emptyList(),
)
