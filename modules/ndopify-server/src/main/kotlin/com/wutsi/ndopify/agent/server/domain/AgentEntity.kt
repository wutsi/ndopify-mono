package com.wutsi.ndopify.agent.server.domain

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.Date

@Entity
@Table(name = "T_AGENT")
data class AgentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne
    @JoinColumn(name = "party_id")
    val party: PartyEntity = PartyEntity(),

    @ElementCollection
    @CollectionTable(
        name = "T_AGENT_NEIGHBORHOOD",
        joinColumns = [JoinColumn(name = "agent_id")]
    )
    @Column(name = "neighborhood_id")
    val neighborhoodIds: List<Long> = emptyList(),

    val cityId: Long? = null,
    val whatsappNumber: String? = null,
    val agentType: AgentType = AgentType.UNKNOWN,
    val experienceLevel: ExperienceLevel = ExperienceLevel.UNKNOWN,
    val biography: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
