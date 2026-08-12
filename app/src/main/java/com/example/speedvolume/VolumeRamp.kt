package com.example.speedvolume

import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Smooth volume changer.
 *
 * Instead of jumping straight to the target level (which sounds abrupt and
 * jittery at 1 Hz location updates), it moves one level per tick, producing a
 * continuous ramp. It also detects when the system silently refuses the
 * volume change (some OEMs/ColorOS restrict it) and reports it via
 * onBlockedChanged.
 */
class VolumeRamp(
    private val audioManager: AudioManager,
    private val stepIntervalMs: Long = 100L
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var targetProvider: (() -> Int)? = null
    private var mismatches = 0
    private var onBlockedChanged: ((Boolean) -> Unit)? = null

    fun start(targetProvider: () -> Int, onBlockedChanged: (Boolean) -> Unit) {
        if (running) return
        running = true
        this.targetProvider = targetProvider
        this.onBlockedChanged = onBlockedChanged
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (targetProvider?.invoke() ?: 0).coerceIn(0, maxVolume)
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (current != target) {
                val next = if (target > current) current + 1 else current - 1
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                val after = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (after == current) {
                    mismatches++
                    if (mismatches == 10) onBlockedChanged?.invoke(true)
                } else {
                    mismatches = 0
                    onBlockedChanged?.invoke(false)
                }
            } else if (mismatches > 0) {
                mismatches = 0
                onBlockedChanged?.invoke(false)
            }
            handler.postDelayed(this, stepIntervalMs)
        }
    }
}