package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.dao.LocationRepository
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.text.Normalizer

@Service
class LocationService(
    private val dao: LocationRepository,
    private val em: EntityManager,
) {
    companion object {
        private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        const val MAX_RADIUS_KM = 100.0

        // Distance to the nearest origin. ST_Distance_Sphere returns meters; POINT takes (longitude, latitude).
        // id last in ORDER BY: paging by offset needs a total order
        private const val SQL_NEAREST = """
            SELECT l.id, MIN(ST_Distance_Sphere(POINT(l.longitude, l.latitude), POINT(o.longitude, o.latitude))) AS distance
            FROM T_LOCATION l
            JOIN T_LOCATION o ON o.id IN (:originIds) AND o.country = l.country
            WHERE l.type IN (:types)
              AND l.id NOT IN (:excludedIds)
              AND l.latitude IS NOT NULL AND l.longitude IS NOT NULL
              %s
            GROUP BY l.id
            HAVING distance <= :radius
            ORDER BY distance, l.id
        """
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

    /**
     * Returns the locations within [SearchNearestLocationRequest.radius] km of any of the locations
     * [SearchNearestLocationRequest.ids], in the same country, nearest first, with their distance in km to the nearest
     * of them. The locations searched around, and locations without coordinates, are excluded.
     */
    fun searchNearest(request: SearchNearestLocationRequest): List<Pair<LocationEntity, Double>> {
        val radius = request.radius.coerceAtMost(MAX_RADIUS_KM)
        val origins = if (request.ids.isEmpty() || radius <= 0) {
            emptyList()
        } else {
            dao.findAllById(request.ids).filter { it.latitude != null && it.longitude != null }
        }
        if (origins.isEmpty()) {
            return emptyList()
        }

        val types = request.type?.let { listOf(it) } ?: origins.map { it.type }.distinct()
        // The parent filter is only in the SQL when set
        val sql = SQL_NEAREST.format(if (request.parentId == null) "" else "AND l.parent_fk = :parentId")
        val query = em.createNativeQuery(sql)
            .setParameter("originIds", origins.map { it.id })
            .setParameter("excludedIds", request.ids)
            .setParameter("types", types.map { it.ordinal })
            .setParameter("radius", radius * 1000)
            .setFirstResult(request.offset.coerceAtLeast(0))
            .setMaxResults(OffsetLimitPageRequest.limit(request.limit))
        request.parentId?.let { parentId -> query.setParameter("parentId", parentId) }

        val distances = query.resultList
            .map { row -> row as Array<*> }
            .associate { row -> (row[0] as Number).toLong() to (row[1] as Number).toDouble() / 1000 }
        val locations = dao.findAllById(distances.keys).associateBy { it.id }
        return distances.mapNotNull { (locationId, distance) ->
            locations[locationId]?.let { it to distance }
        }
    }

    fun toAscii(str: String): String {
        val temp = Normalizer.normalize(str, Normalizer.Form.NFD)
        return REGEX_UNACCENT.replace(temp, "")
            .replace(" ", "-")
    }
}
