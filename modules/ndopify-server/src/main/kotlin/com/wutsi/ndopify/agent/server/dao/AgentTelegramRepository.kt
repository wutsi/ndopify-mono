package com.wutsi.ndopify.agent.server.dao

import com.wutsi.ndopify.agent.server.domain.AgentTelegramEntity
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
interface AgentTelegramRepository : CrudRepository<AgentTelegramEntity, Long> {
    fun findByAgentId(agentId: Long): AgentTelegramEntity?

    fun findByCode(code: String): AgentTelegramEntity?

    fun findByChatId(chatId: Long): AgentTelegramEntity?

    /**
     * Links the row carrying the code, and clears it. The condition is in the update itself, so a code used
     * meanwhile (or expired) links nothing: returns 1 when it linked, 0 otherwise.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE AgentTelegramEntity e SET e.chatId = :chatId, e.code = NULL, e.codeExpiresAt = NULL, " +
            "e.linkedAt = :now, e.modifiedAt = :now " +
            "WHERE e.code = :code AND e.codeExpiresAt > :now AND e.chatId IS NULL"
    )
    fun link(
        @Param("code") code: String,
        @Param("chatId") chatId: Long,
        @Param("now") now: Date,
    ): Int
}
