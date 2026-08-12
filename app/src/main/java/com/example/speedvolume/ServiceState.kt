package com.example.speedvolume

import java.util.concurrent.CopyOnWriteArrayList

object ServiceState {
    @Volatile var running: Boolean = false
    @Volatile var speedKmh: Float = Float.NaN
    @Volatile var volume: Int = 0
    @Volatile var gpsEnabled: Boolean = true
    @Volatile var hasFix: Boolean = false
    @Volatile var motionOnly: Boolean = false
    @Volatile var volumeBlocked: Boolean = false

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun changed() {
        listeners.forEach { it() }
    }
}