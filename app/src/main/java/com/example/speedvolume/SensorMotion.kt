package com.example.speedvolume

/**
 * Step-based motion estimate used only when GPS has no fresh fix.
 *
 * GPS stays the authoritative speed source whenever available; this class
 * fills the gap (indoor runs, tunnels, cold GPS). Accuracy comes from
 * self-calibration: every time GPS has a fix while the user is walking or
 * running (4-16 km/h), the stride length is re-measured against the real
 * travelled distance, so the estimate converges on the user's actual gait.
 */
class SensorMotion(
    private val baseStrideM: Float = 0.75f,
    private val calibAlpha: Float = 0.15f
) {

    private val stepTimes = ArrayDeque<Long>()
    private var totalSteps = 0L
    private var calibStrideM = baseStrideM
    private var calibHasData = false

    fun onStep(nowMs: Long) {
        stepTimes.addLast(nowMs)
        while (stepTimes.size > 2 && nowMs - stepTimes.first() > 60_000L) {
            stepTimes.removeFirst()
        }
        totalSteps++
    }

    fun totalSteps(): Long = totalSteps

    fun stepsSince(prevTotal: Long): Long = totalSteps - prevTotal

    private fun cadenceStepsPerMin(nowMs: Long): Float {
        val cutoff = nowMs - 10_000L
        var n = 0
        for (t in stepTimes) if (t > cutoff) n++
        return n * 6f
    }

    /** km/h estimate from cadence + calibrated stride; NaN when no recent steps. */
    fun speedEstimateKmh(nowMs: Long): Float {
        if (stepTimes.isEmpty()) return Float.NaN
        if (nowMs - stepTimes.last() > 30_000L) return Float.NaN
        val cadence = cadenceStepsPerMin(nowMs)
        if (cadence <= 0f) return Float.NaN
        val stride = if (calibHasData) calibStrideM else baseStrideM
        val kmh = cadence * stride * 60f / 1000f
        return kmh.coerceIn(1f, 18f)
    }

    /**
     * Re-measures stride from a GPS-verified distance and the steps counted
     * across the same interval. Ignores implausible values.
     */
    fun calibrate(distanceM: Float, steps: Int) {
        if (steps <= 0 || distanceM <= 0f) return
        val stride = distanceM / steps
        if (stride in 0.3f..1.3f) {
            calibStrideM = if (!calibHasData) stride
            else calibStrideM + calibAlpha * (stride - calibStrideM)
            calibHasData = true
        }
    }
}