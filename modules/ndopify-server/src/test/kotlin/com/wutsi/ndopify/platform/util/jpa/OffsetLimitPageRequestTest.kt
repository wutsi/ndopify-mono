package com.wutsi.ndopify.platform.util.jpa

import org.springframework.data.domain.Sort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffsetLimitPageRequestTest {
    private val sort = Sort.by("id")

    @Test
    fun `offset is not a multiple of the limit`() {
        val page = OffsetLimitPageRequest(30, 20, sort)

        assertEquals(30L, page.offset)
        assertEquals(20, page.pageSize)
        assertEquals(1, page.pageNumber)
        assertEquals(sort, page.sort)
    }

    @Test
    fun `negative offset and empty limit are corrected`() {
        val page = OffsetLimitPageRequest(-5, 0)

        assertEquals(0L, page.offset)
        assertEquals(1, page.pageSize)
        assertEquals(Sort.unsorted(), page.sort)
    }

    @Test
    fun `large limit is kept for internal callers`() {
        assertEquals(Int.MAX_VALUE, OffsetLimitPageRequest(0, Int.MAX_VALUE).pageSize)
    }

    @Test
    fun navigation() {
        val page = OffsetLimitPageRequest(30, 20, sort)

        assertEquals(50L, page.next().offset)
        assertEquals(10L, page.previousOrFirst().offset)
        assertEquals(0L, page.first().offset)
        assertEquals(60L, page.withPage(3).offset)
        assertEquals(sort, page.next().sort)
        assertTrue(page.hasPrevious())
        assertFalse(OffsetLimitPageRequest(10, 20).hasPrevious())
        assertEquals(0L, OffsetLimitPageRequest(10, 20).previousOrFirst().offset)
    }

    @Test
    fun `limit is capped for endpoints`() {
        assertEquals(OffsetLimitPageRequest.MAX_LIMIT, OffsetLimitPageRequest.limit(1000))
        assertEquals(20, OffsetLimitPageRequest.limit(20))
        assertEquals(1, OffsetLimitPageRequest.limit(0))
    }
}
