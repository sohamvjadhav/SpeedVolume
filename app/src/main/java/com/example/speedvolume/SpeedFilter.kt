package com.example.speedvolume

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * GPS speed noise filter.
 *
 * Strategy (accuracy-preserving):
 *  - deadband: samples < 3 km/h are treated as 0 (GPS jitter at standstill)
 *  - spike rejection: any sample implying acceleration beyond maxAccelMs2
 *    (a car's maximum plausible acceleration) is discarded
 *  - cross-check: when the fix is low-confidence, a sample that wildly
 *    disagrees with displacement-derived speed is discarded
 *  - median-of-3: removes isolated outliers while keeping sharp real changes
 *  - adaptive EMA: aggressive smoothing on low-confidence samples, light
 *    smoothing on good fixes, so real speed changes stay responsive
 */
class SpeedFilter(private val maxAccelMs2: Float = 12f) {

    private val window = ArrayDeque<Float>()
    private var smoothed = 0f
    private var initialized = false
    private var prevValidKmh = -1f
    private var lastSampleMs = 0L

    /**
     * @param rawKmh     GPS-reported speed in km/h, or Float.NaN if unknown
     * @param derivedKmh speed computed from consecutive fixes, or Float.NaN
     * @param confident  whether the fix quality is high (good accuracy + speed)
     * @param nowMs      sample timestamp (elapsed realtime)
     * @return filtered speed in km/h, or Float.NaN if the sample was rejected
     */
    fun process(rawKmh: Float, derivedKmh: Float, confident: Boolean, nowMs: Long): Float {
        val sample = when {
            rawKmh.isFinite() -> rawKmh.coerceAtLeast(0f).let { if (it < 3f) 0f else it }
            derivedKmh.isFinite() -> derivedKmh.coerceAtLeast(0f)
            else -> return Float.NaN
        }

        val dt = if (lastSampleMs > 0L) (nowMs - lastSampleMs) / 1000f else -1f

        if (prevValidKmh >= 0f && dt in 0.2f..5f) {
            val accel = abs(sample - prevValidKmh) / 3.6f / dt
            if (accel > maxAccelMs2) return if (initialized) smoothed else 0f
        }

        if (derivedKmh.isNaN().not() && abs(sample - derivedKmh) > 40f && !confident) {
            return if (initialized) smoothed else 0f
        }

        window.addLast(sample)
        if (window.size > 3) window.removeFirst()
        val median = window.sorted()[window.size / 2]

        if (!initialized) {
            smoothed = median
            initialized = true
        } else {
            val alpha = if (confident) 0.6f else 0.35f
            smoothed += alpha * (median - smoothed)
        }
        if (smoothed < 3f) smoothed = 0f

        prevValidKmh = sample
        lastSampleMs = nowMs
        return smoothed
    }

    /** Clears history when switching between GPS and motion-derived sources. */
    fun reset() {
        window.clear()
        smoothed = 0f
        initialized = false
        prevValidKmh = -1f
        lastSampleMs = 0L
    }
}
