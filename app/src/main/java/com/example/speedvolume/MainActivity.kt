package com.example.speedvolume

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var speedValue: TextView
    private lateinit var volumeValue: TextView
    private lateinit var maxVolumeText: TextView
    private lateinit var gpsWarning: TextView
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
        speedValue = findViewById(R.id.speedValue)
        volumeValue = findViewById(R.id.volumeValue)
        maxVolumeText = findViewById(R.id.maxVolumeText)
        gpsWarning = findViewById(R.id.gpsWarning)
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
        maxVolumeText.text = getString(R.string.max_volume_fmt, maxVolume)

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
        if (ServiceState.running) {
            statusText.text = getString(R.string.status_running)
            toggleButton.setText(R.string.stop)
            speedValue.text = getString(R.string.speed_value_fmt, ServiceState.speedKmh.toInt())
            volumeValue.text = getString(R.string.volume_value_fmt, ServiceState.volume)
            gpsWarning.visibility = if (ServiceState.gpsEnabled)
                android.view.View.GONE else android.view.View.VISIBLE
        } else {
            statusText.text = getString(R.string.status_stopped)
            toggleButton.setText(R.string.start)
            speedValue.text = getString(R.string.speed_value_fmt, 0)
            volumeValue.text = getString(R.string.volume_value_fmt, currentVolume())
            gpsWarning.visibility = android.view.View.GONE
        }
    }

    private fun currentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun onToggle() {
        if (ServiceState.running) {
            stopService(Intent(this, SpeedVolumeService::class.java))
            refreshUi()
        } else {
            val needed = permissionList()
            if (needed.isEmpty()) {
                startVolumeService()
            } else {
                requestPermissions(needed.toTypedArray(), REQUEST_PERMISSIONS)
            }
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