package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreateIdentificationRequest
import com.wutsi.ndopify.party.dto.IdentificationStatus
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.SearchIdentificationRequest
import com.wutsi.ndopify.party.server.dao.IdentificationImageRepository
import com.wutsi.ndopify.party.server.dao.IdentificationRepository
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
class IdentificationService(
    private val dao: IdentificationRepository,
    private val imageDao: IdentificationImageRepository,
    private val clock: Clock,
    private val partyService: PartyService,
) {
    fun findById(id: String): IdentificationEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.IDENTIFICATION_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
    }

    fun findByIdOrNull(id: String): IdentificationEntity? {
        return dao.findById(id).orElse(null)
    }

    fun search(request: SearchIdentificationRequest): List<IdentificationEntity> {
        val spec = Specification<IdentificationEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.partyId?.let { partyId ->
                predicates.add(cb.equal(root.get<PartyEntity>("party").get<Long>("id"), partyId))
            }
            if (request.types.isNotEmpty()) {
                predicates.add(root.get<IdentificationType>("type").`in`(request.types))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<IdentificationStatus>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    @Transactional
    fun create(request: CreateIdentificationRequest): IdentificationEntity {
        val party = partyService.findById(request.partyId)
        val now = Date(clock.millis())

        // ID
        val identification = dao.save(
            IdentificationEntity(
                party = party,
                type = request.type,
                issuingCountryCode = request.issuingCountryCode,
                createdAt = now,
                status = IdentificationStatus.PENDING_VERIFICATION,
            )
        )

        // IMAGES
        request.imageTypes.forEach { imageType ->
            val imageId = UUID.randomUUID().toString()
            imageDao.save(
                IdentificationImageEntity(
                    id = imageId,
                    identification = identification,
                    imageType = imageType,
                    createdAt = now,
                    uploaded = false,
                )
            )
        }

        return identification
    }
}
