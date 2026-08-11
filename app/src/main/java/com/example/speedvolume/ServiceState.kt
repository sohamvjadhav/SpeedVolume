package com.example.speedvolume

object ServiceState {
    @Volatile var running: Boolean = false
    @Volatile var speedKmh: Float = Float.NaN
    @Volatile var volume: Int = 0
    @Volatile var gpsEnabled: Boolean = true
    @Volatile var hasFix: Boolean = false
    @Volatile var volumeBlocked: Boolean = false

    var listener: (() -> Unit)? = null

    fun changed() {
        listener?.invoke()
    }
}