package com.example.speedvolume

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlin.math.roundToInt

class SpeedVolumeTile : TileService() {

    private val uiListener = { refreshTile() }

    override fun onStartListening() {
        ServiceState.addListener(uiListener)
        refreshTile()
    }

    override fun onStopListening() {
        ServiceState.removeListener(uiListener)
    }

    override fun onClick() {
        if (ServiceState.running) {
            getSharedPreferences(SpeedVolumeService.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(SpeedVolumeService.PREF_SERVICE_ENABLED, false).apply()
            stopService(Intent(this, SpeedVolumeService::class.java))
            return
        }
        val tile = qsTile
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val backgroundGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !backgroundGranted) {
            setSubtitle(getString(R.string.tile_permission_hint))
            tile?.updateTile()
            return
        }
        setSubtitle(getString(R.string.tile_starting))
        tile?.updateTile()
        try {
            getSharedPreferences(SpeedVolumeService.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(SpeedVolumeService.PREF_SERVICE_ENABLED, true).apply()
            startForegroundService(Intent(this, SpeedVolumeService::class.java))
        } catch (e: Exception) {
            Log.w("SpeedVolumeTile", "foreground start blocked: $e")
            getSharedPreferences(SpeedVolumeService.PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(SpeedVolumeService.PREF_SERVICE_ENABLED, false).apply()
            setSubtitle(getString(R.string.tile_permission_hint))
            tile?.updateTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val running = ServiceState.running
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this, if (running) R.drawable.ic_speed_active else R.drawable.ic_speed_inactive
        )
        val subtitle = if (running) {
            val speed = if ((ServiceState.hasFix || ServiceState.speedKmh > 0f) &&
                !ServiceState.speedKmh.isNaN()
            )
                ServiceState.speedKmh.roundToInt().toString() else getString(R.string.no_fix)
            getString(R.string.tile_running, speed, ServiceState.volume)
        } else {
            getString(R.string.tile_off)
        }
        setSubtitle(subtitle)
        tile.updateTile()
    }

    private fun setSubtitle(value: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            qsTile?.subtitle = value
        }
    }
}
