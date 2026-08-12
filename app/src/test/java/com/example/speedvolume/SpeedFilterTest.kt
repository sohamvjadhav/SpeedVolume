package com.example.speedvolume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFilterTest {

    @Test
    fun deadbandsStationaryGpsJitter() {
        val filter = SpeedFilter()

        val result = filter.process(2.9f, Float.NaN, true, 1_000L)

        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun fallsBackToDisplacementWhenGpsSpeedIsInvalid() {
        val filter = SpeedFilter()

        val result = filter.process(Float.POSITIVE_INFINITY, 12f, false, 1_000L)

        assertEquals(12f, result, 0.001f)
    }

    @Test
    fun resetDropsHistoryBeforeASourceChange() {
        val filter = SpeedFilter()
        filter.process(60f, Float.NaN, true, 1_000L)

        filter.reset()
        val result = filter.process(6f, Float.NaN, true, 2_000L)

        assertTrue(result in 5.9f..6.1f)
    }
}
