package com.example.speedvolume

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class SpeedVolumeService : Service(), LocationListener, SensorEventListener {

    private lateinit var audioManager: AudioManager
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    private var started = false
    private var updatesRequested = false

    private val filter = SpeedFilter()
    private val motion = SensorMotion()
    private var stepsAtLastFix = 0L
    private lateinit var ramp: VolumeRamp
    private val handler = Handler(Looper.getMainLooper())

    private var lastLocation: Location? = null
    private var lastFixMs = 0L

    companion object {
        private const val TAG = "SpeedVolumeService"
        private const val CHANNEL_ID = "speed_volume_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.speedvolume.STOP"
        const val PREFS_NAME = "speed_volume_settings"
        const val PREF_MAX_SPEED = "max_speed_kmh"
        const val PREF_IDLE_VOLUME = "idle_volume"
        const val DEFAULT_MAX_SPEED = 80
        private const val WATCHDOG_INTERVAL_MS = 15_000L
        private const val NO_FIX_TIMEOUT_MS = 5_000L
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ramp = VolumeRamp(audioManager)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        if (started) {
            Log.d(TAG, "already running, ignoring duplicate start")
            return START_STICKY
        }
        started = true

        val ok = try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "startForeground failed, permission lost: $e")
            started = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ok) return START_NOT_STICKY

        ServiceState.running = true
        ServiceState.volume = currentVolume()
        ServiceState.changed()

        ensureLocationUpdates()
        ensureSensors()
        ramp.start({ mapSpeedToVolume(ServiceState.speedKmh) }) { blocked ->
            if (ServiceState.volumeBlocked != blocked) {
                ServiceState.volumeBlocked = blocked
                ServiceState.changed()
            }
        }
        handler.post(watchdogRunnable)
        Log.i(TAG, "service started")
        return START_STICKY
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!started) return
            val lm = locationManager ?: return
            val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (gpsOn != ServiceState.gpsEnabled) {
                ServiceState.gpsEnabled = gpsOn
                ServiceState.changed()
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }
            if (gpsOn && !updatesRequested) ensureLocationUpdates()

            val noFix = lastFixMs == 0L || SystemClock.elapsedRealtime() - lastFixMs > NO_FIX_TIMEOUT_MS
            if (noFix != !ServiceState.hasFix) {
                ServiceState.hasFix = !noFix
                ServiceState.changed()
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun ensureLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ServiceState.gpsEnabled = false
            ServiceState.changed()
            return
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            updatesRequested = true
            ServiceState.gpsEnabled = true
            ServiceState.changed()
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission lost: $e")
            ServiceState.gpsEnabled = false
            ServiceState.changed()
        }
    }

    private fun ensureSensors() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        stepSensor = sensor
        Log.i(TAG, "step sensor active")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        val now = SystemClock.elapsedRealtime()
        motion.onStep(now)
        if (lastFixMs != 0L && now - lastFixMs <= NO_FIX_TIMEOUT_MS) return

        val estimate = motion.speedEstimateKmh(now)
        if (estimate.isNaN()) return
        val filtered = filter.process(estimate, Float.NaN, false, now)
        if (filtered.isNaN() || filtered == ServiceState.speedKmh) return
        ServiceState.speedKmh = filtered
        ServiceState.motionOnly = true
        ServiceState.changed()
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val prev = lastLocation
        var derivedKmh = Float.NaN
        if (prev != null && prev.latitude != location.latitude &&
            prev.longitude != location.longitude
        ) {
            val dtSec = (now - lastFixMs) / 1000f
            if (dtSec in 0.5f..10f) {
                val distanceM = prev.distanceTo(location)
                derivedKmh = distanceM / dtSec * 3.6f
                val stepsDelta = motion.stepsSince(stepsAtLastFix)
                if (stepsDelta > 0 && derivedKmh in 4f..16f) {
                    motion.calibrate(distanceM, stepsDelta.toInt())
                }
            }
        }
        stepsAtLastFix = motion.totalSteps()
        lastLocation = location
        lastFixMs = now

        val rawKmh = if (location.hasSpeed()) location.speed * 3.6f else Float.NaN
        val confident = location.hasSpeed() && location.accuracy > 0f && location.accuracy <= 40f

        val filtered = filter.process(rawKmh, derivedKmh, confident, now)
        if (filtered.isNaN().not()) {
            ServiceState.speedKmh = filtered
        }
        if (!ServiceState.hasFix) {
            ServiceState.hasFix = true
        }
        if (ServiceState.motionOnly) {
            ServiceState.motionOnly = false
        }
        ServiceState.changed()
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Maps filtered speed to a target volume level.
     * NaN speed (no fix / rejected sample) -> hold the current volume.
     */
    private fun mapSpeedToVolume(speedKmh: Float): Int {
        if (speedKmh.isNaN() || !ServiceState.hasFix && !ServiceState.motionOnly) return currentVolume()
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
                val ratio = kotlin.math.sqrt(speedKmh / maxSpeed)
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

    private fun buildNotification(): Notification {
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
        val speedText = if ((ServiceState.hasFix || ServiceState.speedKmh > 0f) &&
            !ServiceState.speedKmh.isNaN()
        )
            ServiceState.speedKmh.roundToInt().toString() else getString(R.string.no_fix)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body, speedText, ServiceState.volume))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        started = false
        updatesRequested = false
        handler.removeCallbacks(watchdogRunnable)
        ramp.stop()
        locationManager?.removeUpdates(this)
        stepSensor?.let { sensorManager?.unregisterListener(this, it) }
        ServiceState.running = false
        ServiceState.hasFix = false
        ServiceState.motionOnly = false
        ServiceState.changed()
        Log.i(TAG, "service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onProviderEnabled(provider: String) {
        Log.i(TAG, "provider enabled: $provider")
        ensureLocationUpdates()
    }

    override fun onProviderDisabled(provider: String) {
        Log.i(TAG, "provider disabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            updatesRequested = false
            ServiceState.gpsEnabled = false
            ServiceState.changed()
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) = Unit

    override fun onFlushComplete(requestCode: Int) = Unit
}