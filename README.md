# Speed Volume

[![License](https://img.shields.io/github/license/sohamvjadhav/SpeedVolume)](https://github.com/sohamvjadhav/SpeedVolume/blob/main/LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/sohamvjadhav/SpeedVolume/build.yml)](https://github.com/sohamvjadhav/SpeedVolume/actions)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/studio)

Speed Volume is an Android app that adjusts `STREAM_MUSIC` volume from the
device's current speed. It is designed for driving and supports a walking or
running fallback when GPS temporarily has no fresh fix.

The app runs as a location foreground service. It is not a replacement for a
vehicle's safety systems, and it cannot make GPS work indoors, inside tunnels,
or when the phone manufacturer stops background execution.

## What it does

- Maps filtered speed to media volume with a responsive square-root curve.
- Uses GPS as the authoritative source for vehicle speed.
- Falls back to step cadence and a calibrated stride for walking/running when
  GPS has been stale for more than five seconds.
- Smooths noisy GPS using a standstill deadband, acceleration rejection,
  displacement cross-checking, median filtering, and adaptive EMA smoothing.
- Moves volume toward its target every 50 ms and detects OEMs that silently
  refuse volume changes.
- Provides an ongoing notification, a Quick Settings tile, and a live status
  screen.
- Recovers location requests after screen lock or Doze when callbacks become
  stale, subject to device and OEM policy.

## How the engine works

```text
GPS fixes (500 ms) ──┐
                     ├─ source-aware filter ── speed ── curve ── target volume
step detector ───────┘                              └─ exponential volume ramp
```

1. `SpeedVolumeService` promotes itself to a location foreground service and
   requests `GPS_PROVIDER` updates every 500 ms.
2. `SpeedFilter` treats speeds below 3 km/h as stationary, rejects samples
   implying more than 12 m/s² acceleration, cross-checks weak fixes against
   displacement speed, then applies median-of-3 and adaptive EMA smoothing.
3. If the last GPS fix is older than five seconds, `SensorMotion` estimates
   walking/running speed from recent step cadence. The estimate is capped at
   18 km/h and is not intended to replace GPS for driving.
4. GPS-verified walking or running calibrates stride length gradually. The
   filter history is reset whenever the source changes, so stale GPS speed does
   not bleed into a motion estimate.
5. Speed is mapped between the configured standstill volume and full volume.
   The default full-volume threshold is 80 km/h; the default standstill level
   is approximately 15% of the media stream's maximum.
6. `VolumeRamp` approaches the target exponentially and reports the actual
   stream level back to the activity, tile, and notification.

## Reliable setup for a locked screen

Android and phone manufacturers restrict background work. Complete all of the
following on the test device:

1. Open Speed Volume and grant precise location.
2. Grant **Allow all the time** location access. This app requires it for its
   Quick Settings and locked-screen workflow.
3. Grant notifications on Android 13 and newer. The ongoing notification is
   how Android recognizes and exposes the foreground service.
4. Set the app's battery usage to **Unrestricted** or disable battery
   optimization for it.
5. Enable the manufacturer's **Auto-start**, **Allow background activity**, or
   equivalent setting. Common battery killers include ColorOS, MIUI, EMUI,
   One UI power saving, and aggressive third-party task managers.
6. Start tracking once while the app is visibly open. Confirm that the
   ongoing **Speed Volume active** notification appears before locking the
   phone.
7. Lock the phone and test in an open-sky location. GPS speed needs a usable
   satellite fix; the step fallback is for pedestrians, not vehicles.

Android's foreground-service and background-location rules change across API
levels. See the official guidance for [foreground-service background starts](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
and [background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background).

If the service still stops only on one phone model, that is usually an OEM
power-management policy. The app can re-register its location request, but it
cannot override a manufacturer force-stop or task-killer policy.

## Permissions and device access

| Permission or setting | Purpose |
|---|---|
| Fine location | GPS speed and displacement |
| Background location | Locked-screen and Quick Settings operation |
| Notifications | Visible foreground-service notification on Android 13+ |
| Modify audio settings | Adjusts the `STREAM_MUSIC` volume |
| Battery optimization exemption | Prevents OEM/Doze termination where supported |
| Step detector hardware | Optional walking/running fallback; no permission is required |

## Quick Settings tile

Add **Speed Volume** from the notification shade's tile editor. Alternatively,
on a connected development device:

```bash
adb shell cmd statusbar add-tile \
  com.example.speedvolume/com.example.speedvolume.SpeedVolumeTile
```

The tile starts and stops the service. If required permissions are missing,
it tells you to open the app and complete setup. Long-pressing the tile opens
the app.

## Build, test, and install

The app has no runtime AndroidX, Material, or third-party dependencies. JUnit
is used for local engine tests. The project uses AGP 9.0.1, Gradle 9.2.1,
compile SDK 36, and Java 17 source compatibility.

```bash
./gradlew testDebugUnitTest   # filter and motion-engine tests
./gradlew lintDebug           # Android static analysis
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on a connected device
```

Use a physical device for functional testing. An emulator generally does not
provide realistic GPS speed, sensor cadence, Bluetooth audio, or OEM power
management.

## In-app controls

- **Full volume above speed**: speed at which the media stream reaches maximum.
- **Standstill volume level**: minimum volume used at 0–3 km/h.
- **Status pill**: whether the service is running.
- **Source row**: GPS fix, motion sensors, or looking for a fix.
- **Warnings**: GPS disabled, volume changes blocked, or service start failure.
- **Stop**: stops the foreground service and prevents a sticky restart.

## Troubleshooting

### It works only while the app is open

Check **Allow all the time**, notifications, unrestricted battery usage, and
the manufacturer's auto-start/background setting. Start the service while the
app is visible and verify the persistent notification before locking.

### It stops when the phone locks

The service may have been killed by Doze or an OEM task killer. Re-enable
unrestricted battery usage and auto-start. Do not force-stop the app; Android
does not reliably restart force-stopped applications.

### Speed stays at `--` or zero

Move outdoors and wait for a GPS fix. For walking/running, confirm that the
phone has a step-detector sensor and that recent steps are being detected. A
vehicle still requires GPS; motion fallback is intentionally limited to
pedestrian speeds.

### Volume does not change

The app adjusts the same media stream used by Bluetooth car audio. If the
warning appears, the ROM may be blocking programmatic media-volume changes.
Check device-admin, car-mode, audio-policy, or OEM restrictions.

### The tile does nothing

Open the app, grant background location, and try starting from the app once.
If the tile starts and then immediately becomes inactive, inspect the ongoing
notification and the device's background-execution settings.

## Project layout

```text
app/src/main/java/com/example/speedvolume/
├── MainActivity.kt          # setup UI, permissions, and controls
├── SpeedVolumeService.kt    # foreground service, GPS, sensors, recovery
├── SpeedFilter.kt           # GPS/displacement filtering
├── VolumeRamp.kt            # exponential volume approach and block detection
├── SensorMotion.kt          # cadence and stride calibration
├── SpeedVolumeTile.kt       # Quick Settings integration
└── ServiceState.kt          # activity/tile/service state bridge

app/src/test/java/com/example/speedvolume/
├── SpeedFilterTest.kt       # filter edge cases
└── SensorMotionTest.kt      # cadence and stale-sensor behavior
```

## Versioning and license

Release history is recorded in [CHANGELOG.md](CHANGELOG.md). Inspect tags with:

```bash
git tag
git log --oneline --decorate
```

The project is licensed under the [MIT License](LICENSE).
