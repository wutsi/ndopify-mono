package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.server.dao.LocationRepository
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.text.Normalizer

@Service
class LocationService(
    private val dao: LocationRepository,
) {
    companion object {
        private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    }

    fun findById(id: Long): LocationEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.LOCATION_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
    }

    fun findByIdOrNull(id: Long): LocationEntity? {
        return dao.findById(id).orElse(null)
    }

    @Transactional
    fun save(location: LocationEntity): LocationEntity {
        location.asciiName = toAscii(location.name)
        return dao.save(location)
    }

    @Transactional
    fun link(parentId: Long, childId: Long): Boolean {
        val child = findByIdOrNull(childId) ?: return false
        val parent = findByIdOrNull(parentId) ?: return false

        child.parentId = parent.id
        dao.save(child)
        return true
    }

    fun search(request: SearchLocationRequest): List<LocationEntity> {
        val spec = Specification<LocationEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.keyword?.takeIf { it.isNotEmpty() }?.let { keyword ->
                predicates.add(cb.like(cb.upper(root.get("asciiName")), toAscii(keyword).uppercase() + "%"))
            }
            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            request.parentId?.let { parentId ->
                predicates.add(cb.equal(root.get<Long>("parentId"), parentId))
            }
            if (request.types.isNotEmpty()) {
                predicates.add(root.get<LocationType>("type").`in`(request.types))
            }
            request.country?.takeIf { it.isNotEmpty() }?.let { country ->
                predicates.add(cb.equal(cb.upper(root.get("country")), country.uppercase()))
            }

            cb.and(*predicates.toTypedArray())
        }

        // id last: paging by offset needs a total order, and names and populations repeat
        val sort = Sort.by(Sort.Order.asc("name"), Sort.Order.desc("population"), Sort.Order.asc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    fun toAscii(str: String): String {
        val temp = Normalizer.normalize(str, Normalizer.Form.NFD)
        return REGEX_UNACCENT.replace(temp, "")
            .replace(" ", "-")
    }
}
