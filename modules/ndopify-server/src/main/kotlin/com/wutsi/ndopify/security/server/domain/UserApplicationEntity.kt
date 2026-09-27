package com.wutsi.ndopify.security.server.domain

import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.domain.RoleEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_USER_APPLICATION")
data class UserApplicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity = UserEntity(),

    @ManyToOne
    @JoinColumn(name = "application_id")
    val application: ApplicationEntity = ApplicationEntity(),

    @ManyToMany
    @JoinTable(
        name = "T_USER_APPLICATION_ROLE",
        joinColumns = arrayOf(JoinColumn(name = "user_application_id")),
        inverseJoinColumns = arrayOf(JoinColumn(name = "role_id")),
    )
    val roles: List<RoleEntity> = emptyList(),

    val createdAt: Date = Date(),
)
