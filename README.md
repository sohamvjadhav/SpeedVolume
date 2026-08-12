# Speed Volume

[![License](https://img.shields.io/github/license/sohamvjadhav/SpeedVolume)](https://github.com/sohamvjadhav/SpeedVolume/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/sohamvjadhav/SpeedVolume)](https://github.com/sohamvjadhav/SpeedVolume/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/sohamvjadhav/SpeedVolume/build.yml)](https://github.com/sohamvjadhav/SpeedVolume/actions)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-orange.svg)](https://developer.android.com/studio)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/studio)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com/studio)
[![Kotlin](https://img.shields.io/badge/Kotlin-✓-purple)](https://kotlinlang.org)

Android app that reads the device's GPS speed and automatically adjusts media
volume to match — the faster you go, the louder the music. Built for the car
(and for runs, thanks to motion-sensor fallback).

- Standing still → low "idle" volume
- 80 km/h → max volume
- Everything between scales on a sqrt curve (fast, audible response at
  everyday speeds)
- Works as a Quick Settings tile: tap to start/stop while driving, no
  screen-peeking required

## Features

- **GPS-driven volume**: foreground service polls GPS (500 ms) and maps
  filtered speed to media volume
- **Motion-sensor fusion (v1.6)**: when GPS has no fix (indoor runs, tunnels,
  cold start), speed is estimated from step cadence × stride, so the
  speedometer and volume never sit at 0 while you're moving
- **Self-calibrating stride**: every GPS-verified walk/run re-measures your
  real stride length, so the sensor estimate converges on your actual gait
  (±10–15% accuracy, improving with use)
- **Accuracy-first filter pipeline**: deadband + spike rejection + median + EMA
  keep the raw GPS noise out while preserving real speed changes
- **Instant response (v1.7)**: volume approaches its target exponentially
  (~250 ms settle), speed changes pass through in 1–2 s
- **Quick Settings tile**: tap to start/stop, shows live speed + volume
  subtitle; long-press opens the app
- **Battery & OEM hardened**: START_STICKY restarts, 15 s watchdog re-registers
  location, battery-optimization whitelist prompt, volume-block detection
  (ColorOS/MIUI quirk) with an explicit UI warning
- **Zero dependencies**: pure Android platform APIs — no AndroidX, no Material,
  no third-party libraries

## How it works

```
GPS fix (500 ms) ──▶ filter pipeline ──▶ filtered speed ──▶ sqrt map ──▶ target level
                          │                                        │
step sensor (no fix) ─────┘                                        ▼
                                                    VolumeRamp: exponential approach
                                                       (halves gap every 50 ms)
```

1. `SpeedVolumeService` (a foreground service with
   `FOREGROUND_SERVICE_TYPE_LOCATION`) listens to `GPS_PROVIDER`.
2. `SpeedFilter` cleans the raw speed: samples < 3 km/h are deadbanded to 0,
   implausible accelerations (> 12 m/s²) are rejected, weak fixes are
   cross-checked against displacement-derived speed, then a median-of-3 and
   adaptive EMA smooth it (α 0.6 confident / 0.35 weak).
3. When no fix is fresh (`> 5 s`), step-detector cadence × calibrated stride
   (capped at 18 km/h) feeds the same filter, and the UI marks the source as
   "motion sensors".
4. The filtered speed maps to a target level via a sqrt curve between
   **standstill volume** (default 15% of max) and **full-volume speed**
   (default 80 km/h) — both configurable live in the app.
5. `VolumeRamp` moves the stream toward the target exponentially (halves the
   gap every 50 ms): large changes settle in ~250 ms, small corrections are
   inaudible. It also detects when the OEM silently refuses changes and
   flags a warning.

## Setup

### Permissions

| Permission | Why |
|---|---|
| Location (fine) | GPS speed |
| Location (always/"all the time") | Starting the service from the background (QS tile) — Android refuses background location-foreground-service starts without it |
| Notifications (Android 13+) | Required for the foreground service notification |
| Battery-optimization exemption | ColorOS/MIUI kill background services aggressively; whitelisting makes it reliable |

The app requests each on first use; grant "**Allow all the time**" for
location or the tile won't start the service from the background.

### Quick Settings tile (one-time)

Long-press a tile slot in the notification shade edit grid and pick
**Speed Volume**, or add via:

```bash
adb shell cmd statusbar add-tile com.example.speedvolume/com.example.speedvolume.SpeedVolumeTile
```

### Build & install

No AndroidX/Material dependencies — compiles with any modern AGP. The wrapper
pins Gradle 9.2.1 / AGP 9.0.1 (Java 21).

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on a connected device
```

Or open in Android Studio and Run. Use a physical device — an emulator has no
real GPS speed. Then grant location permission and press **Start**.

## In the app

- **Hero speed readout** — the filtered speed that drives the volume
- **Status pill** — `fix locked` / `motion sensors` / `looking for fix…` and
  GPS-toggle + volume-blocked warnings
- **Mapping settings** — full-volume speed (km/h) and standstill volume,
  applied live
- **Stop** — stops the service (or tap the tile, or the notification action)

## Troubleshooting

- **Volume doesn't move** → the "volume changes blocked" warning appears when
  the ROM silently refuses changes; check ColorOS "set media volume" /
  device-admin-like restrictions.
- **Works sometimes, not others** → battery killer. Whitelist the app on first
  start and don't force-stop it.
- **Speed stuck at 0 indoors** → GPS needs sky view; the step-sensor fallback
  covers walking/running without a fix, but vehicles always need real GPS.
- **Other apps change the volume too** → normal Android behavior (navigation,
  calls); the volume adjusted is the same `STREAM_MUSIC` stream that car
  Bluetooth uses.

## Project layout

```
app/src/main/java/com/example/speedvolume/
├── MainActivity.kt          # UI: status, start/stop, mapping settings
├── SpeedVolumeService.kt    # GPS + sensor fusion → volume foreground service
├── SpeedFilter.kt           # noise filtering (deadband, spikes, median, EMA)
├── VolumeRamp.kt            # exponential volume approach + block detection
├── SensorMotion.kt          # step cadence × self-calibrating stride estimate
├── SpeedVolumeTile.kt       # Quick Settings tile (start/stop, live subtitle)
└── ServiceState.kt          # listener-bridged state between service/UI/tile

app/src/main/res/
├── layout/activity_main.xml # card-based minimal UI (light + dark themes)
├── values/, values-night/   # colors & platform themes
├── drawable/                # cards, seekbars, tile & notification icons
└── mipmap-anydpi-v26/       # adaptive launcher icon
```

## Version history & reverting

The project is versioned with a tag per release:

```bash
git tag          # v1.0 … v1.7
git log --oneline --decorate
```

To try a previous version over the working tree:

```bash
git checkout v1.4 -- .    # restore v1.4 files, keep git history
./gradlew installDebug
```

To permanently go back (reflog keeps everything):

```bash
git revert <commit>   # or: git reset --hard v1.4
```

Every change is logged in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © 2026 Soham Jadhav