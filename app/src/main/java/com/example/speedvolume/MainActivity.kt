package com.example.speedvolume

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var speedValue: TextView
    private lateinit var volumeValue: TextView
    private lateinit var gpsRow: View
    private lateinit var fixState: TextView
    private lateinit var gpsWarning: TextView
    private lateinit var volumeWarning: TextView
    private lateinit var toggleButton: Button
    private lateinit var speedForMaxValue: TextView
    private lateinit var idleVolumeValue: TextView
    private lateinit var audioManager: AudioManager

    private val prefs by lazy {
        getSharedPreferences(SpeedVolumeService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        audioManager = getSystemService(AudioManager::class.java)

        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        speedValue = findViewById(R.id.speedValue)
        volumeValue = findViewById(R.id.volumeValue)
        gpsRow = findViewById(R.id.gpsRow)
        fixState = findViewById(R.id.fixState)
        gpsWarning = findViewById(R.id.gpsWarning)
        volumeWarning = findViewById(R.id.volumeWarning)
        toggleButton = findViewById(R.id.toggleButton)
        speedForMaxValue = findViewById(R.id.speedForMaxValue)
        idleVolumeValue = findViewById(R.id.idleVolumeValue)

        setupUi()
    }

    override fun onStart() {
        super.onStart()
        ServiceState.listener = { runOnUiThread { refreshUi() } }
        refreshUi()
    }

    override fun onStop() {
        ServiceState.listener = null
        super.onStop()
    }

    private fun setupUi() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val speedSeek = findViewById<SeekBar>(R.id.speedForMaxSeek)
        speedSeek.progress = prefs.getInt(SpeedVolumeService.PREF_MAX_SPEED, SpeedVolumeService.DEFAULT_MAX_SPEED) - 20
        speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val kmh = progress + 20
                speedForMaxValue.text = getString(R.string.speed_for_max_value_fmt, kmh)
                if (fromUser) prefs.edit().putInt(SpeedVolumeService.PREF_MAX_SPEED, kmh).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        speedForMaxValue.text = getString(R.string.speed_for_max_value_fmt, speedSeek.progress + 20)

        val idleSeek = findViewById<SeekBar>(R.id.idleVolumeSeek)
        val defaultIdle = (maxVolume * 0.15f).toInt().coerceIn(1, maxVolume)
        idleSeek.max = maxVolume - 1
        idleSeek.progress = prefs.getInt(SpeedVolumeService.PREF_IDLE_VOLUME, defaultIdle).coerceIn(1, maxVolume) - 1
        idleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress + 1
                idleVolumeValue.text = getString(R.string.idle_volume_value_fmt, level)
                if (fromUser) prefs.edit().putInt(SpeedVolumeService.PREF_IDLE_VOLUME, level).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        idleVolumeValue.text = getString(R.string.idle_volume_value_fmt, idleSeek.progress + 1)

        toggleButton.setOnClickListener { onToggle() }
    }

    private fun refreshUi() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (ServiceState.running) {
            statusText.text = getString(R.string.status_running)
            statusDot.background = getDrawable(R.drawable.dot_active)
            toggleButton.setText(R.string.stop)
            speedValue.text = if (ServiceState.hasFix && !ServiceState.speedKmh.isNaN())
                getString(R.string.speed_value_fmt, ServiceState.speedKmh.toInt())
            else getString(R.string.no_fix)
            volumeValue.text = getString(R.string.volume_value_fmt, ServiceState.volume, maxVolume)
            gpsRow.visibility = android.view.View.VISIBLE
            fixState.text = getString(
                if (ServiceState.hasFix) R.string.gps_locked else R.string.gps_looking
            )
            gpsWarning.visibility = if (ServiceState.gpsEnabled)
                android.view.View.GONE else android.view.View.VISIBLE
            volumeWarning.visibility = if (ServiceState.volumeBlocked)
                android.view.View.VISIBLE else android.view.View.GONE
        } else {
            statusText.text = getString(R.string.status_stopped)
            statusDot.background = getDrawable(R.drawable.dot_idle)
            toggleButton.setText(R.string.start)
            speedValue.text = getString(R.string.no_fix)
            volumeValue.text = getString(R.string.volume_value_fmt, currentVolume(), maxVolume)
            gpsRow.visibility = android.view.View.GONE
            gpsWarning.visibility = android.view.View.GONE
            volumeWarning.visibility = android.view.View.GONE
        }
    }

    private fun currentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun onToggle() {
        if (ServiceState.running) {
            stopService(Intent(this, SpeedVolumeService::class.java))
            refreshUi()
        } else {
            maybeAskIgnoreBatteryOptimizations()
            val needed = permissionList()
            if (needed.isEmpty()) {
                startVolumeService()
            } else {
                requestPermissions(needed.toTypedArray(), REQUEST_PERMISSIONS)
            }
        }
    }

    private fun maybeAskIgnoreBatteryOptimizations() {
        if (prefs.getBoolean("battery_optimization_asked", false)) return
        prefs.edit().putBoolean("battery_optimization_asked", true).apply()
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            // Device policy may forbid it; harmless.
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (granted) startVolumeService()
        else statusText.text = getString(R.string.status_permission_denied)
    }

    private fun permissionList(): List<String> {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        return needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startVolumeService() {
        startForegroundService(Intent(this, SpeedVolumeService::class.java))
        refreshUi()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1
    }
}