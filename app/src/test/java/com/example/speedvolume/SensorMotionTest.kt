package com.example.speedvolume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorMotionTest {

    @Test
    fun estimatesSpeedFromRecentCadence() {
        val motion = SensorMotion()
        for (step in 1..20) motion.onStep(step * 500L)

        val estimate = motion.speedEstimateKmh(10_000L)

        assertEquals(5.4f, estimate, 0.01f)
    }

    @Test
    fun stopsEstimatingAfterStepsGoStale() {
        val motion = SensorMotion()
        motion.onStep(1_000L)

        assertTrue(motion.speedEstimateKmh(31_001L).isNaN())
    }
}
