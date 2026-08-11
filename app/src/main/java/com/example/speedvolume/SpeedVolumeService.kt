package com.example.speedvolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class SpeedVolumeService : Service(), LocationListener {

    private lateinit var audioManager: AudioManager
    private var locationManager: LocationManager? = null

    companion object {
        private const val CHANNEL_ID = "speed_volume_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.speedvolume.STOP"
        const val PREFS_NAME = "speed_volume_settings"
        const val PREF_MAX_SPEED = "max_speed_kmh"
        const val PREF_IDLE_VOLUME = "idle_volume"
        const val DEFAULT_MAX_SPEED = 120
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(currentVolume())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        ServiceState.running = true
        ServiceState.volume = currentVolume()
        ServiceState.changed()
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        val gpsOk = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        ServiceState.gpsEnabled = gpsOk
        ServiceState.changed()
        if (!gpsOk) return

        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        } catch (e: SecurityException) {
            ServiceState.gpsEnabled = false
            ServiceState.changed()
        }
    }

    override fun onLocationChanged(location: Location) {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val volume = mapSpeedToVolume(speedKmh)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)

        ServiceState.speedKmh = speedKmh
        ServiceState.volume = volume
        ServiceState.changed()

        notificationManager().notify(NOTIFICATION_ID, buildNotification(volume))
    }

    private fun mapSpeedToVolume(speedKmh: Float): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val maxSpeed = prefs.getInt(PREF_MAX_SPEED, DEFAULT_MAX_SPEED).coerceAtLeast(10).toFloat()
        val idleVolume = prefs
            .getInt(PREF_IDLE_VOLUME, (maxVolume * 0.15f).roundToInt().coerceIn(1, maxVolume))
            .coerceIn(1, maxVolume)

        return when {
            speedKmh >= maxSpeed -> maxVolume
            speedKmh <= 0f -> idleVolume
            else -> {
                val ratio = speedKmh / maxSpeed
                (idleVolume + ratio * (maxVolume - idleVolume)).roundToInt()
                    .coerceIn(idleVolume, maxVolume)
            }
        }
    }

    private fun currentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(volume: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, SpeedVolumeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(R.string.notification_body, ServiceState.speedKmh.roundToInt(), volume)
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(this)
        ServiceState.running = false
        ServiceState.changed()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onProviderEnabled(provider: String) {
        ServiceState.gpsEnabled = true
        ServiceState.changed()
    }

    override fun onProviderDisabled(provider: String) {
        ServiceState.gpsEnabled = false
        ServiceState.changed()
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) = Unit

    override fun onFlushComplete(requestCode: Int) = Unit
}