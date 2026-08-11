package com.example.speedvolume

object ServiceState {
    @Volatile var running: Boolean = false
    @Volatile var speedKmh: Float = 0f
    @Volatile var volume: Int = 0
    @Volatile var gpsEnabled: Boolean = true

    var listener: (() -> Unit)? = null

    fun changed() {
        listener?.invoke()
    }
}