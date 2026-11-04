package com.wutsi.ndopify.visit.server.service

import kotlin.test.Test
import kotlin.test.assertEquals

class GeoDistanceTest {
    @Test
    fun `two Douala points about 1112 m apart`() {
        val distance = GeoDistance.meters(4.051100, 9.767900, 4.061100, 9.767900)

        assertEquals(1111.9, distance.toDouble(), 1.0)
    }

    @Test
    fun `the same point is 0 m away`() {
        assertEquals(0, GeoDistance.meters(4.051100, 9.767900, 4.051100, 9.767900))
    }

    @Test
    fun `about 50 m`() {
        val distance = GeoDistance.meters(4.051100, 9.767900, 4.051550, 9.767900)

        assertEquals(50.0, distance.toDouble(), 1.0)
    }
}
