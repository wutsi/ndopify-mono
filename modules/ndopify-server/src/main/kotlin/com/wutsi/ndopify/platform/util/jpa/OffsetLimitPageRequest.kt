package com.wutsi.ndopify.platform.util.jpa

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * A [Pageable] by offset and limit, so that searches page in SQL (`LIMIT ? OFFSET ?`).
 * Spring's `PageRequest` only knows page numbers: it can't express an offset that isn't a multiple of the limit.
 */
class OffsetLimitPageRequest(
    offset: Int,
    limit: Int,
    private val sort: Sort = Sort.unsorted(),
) : Pageable {
    companion object {
        /** Max number of rows a search endpoint returns. Not enforced here: internal callers may read more. */
        const val MAX_LIMIT = 100

        fun limit(limit: Int): Int = limit.coerceIn(1, MAX_LIMIT)
    }

    private val offset: Int = offset.coerceAtLeast(0)
    private val limit: Int = limit.coerceAtLeast(1)

    override fun getOffset(): Long = offset.toLong()

    override fun getPageSize(): Int = limit

    override fun getPageNumber(): Int = offset / limit

    override fun getSort(): Sort = sort

    override fun next(): Pageable = OffsetLimitPageRequest(offset + limit, limit, sort)

    override fun previousOrFirst(): Pageable =
        if (hasPrevious()) OffsetLimitPageRequest(offset - limit, limit, sort) else first()

    override fun first(): Pageable = OffsetLimitPageRequest(0, limit, sort)

    override fun withPage(pageNumber: Int): Pageable = OffsetLimitPageRequest(pageNumber * limit, limit, sort)

    override fun hasPrevious(): Boolean = offset >= limit
}
