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
            stopService(Intent(this, SpeedVolumeService::class.java))
            return
        }
        val tile = qsTile
        val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            tile?.subtitle = getString(R.string.tile_permission_hint)
            tile?.updateTile()
            return
        }
        tile?.subtitle = getString(R.string.tile_starting)
        tile?.updateTile()
        try {
            startForegroundService(Intent(this, SpeedVolumeService::class.java))
        } catch (e: Exception) {
            Log.w("SpeedVolumeTile", "foreground start blocked: $e")
            tile?.subtitle = getString(R.string.tile_permission_hint)
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
        tile.subtitle = if (running) {
            val speed = if (ServiceState.hasFix && !ServiceState.speedKmh.isNaN())
                ServiceState.speedKmh.roundToInt().toString() else getString(R.string.no_fix)
            getString(R.string.tile_running, speed, ServiceState.volume)
        } else {
            getString(R.string.tile_off)
        }
        tile.updateTile()
    }
}