# Speed Volume

Android app that reads the device's GPS speed and automatically adjusts the
media volume to match — the faster you go, the louder the music.

- High speed → max volume
- Low speed / standing still → low volume (linear scaling in between)

## How it works

`SpeedVolumeService` is a foreground service (required by Android for
continuous location tracking) that listens to the GPS provider, converts
`speed` (m/s) to km/h, maps it to a media volume level and applies it via
`AudioManager.setStreamVolume(STREAM_MUSIC)`. The mapping is configurable:

- **Full volume above speed** — speed (km/h) at which volume reaches max
  (default 120)
- **Standstill volume level** — volume applied while stopped (default 15% of max)

Everything between is interpolated linearly. Settings are shared with the
service via SharedPreferences, so changes apply live.

### Smoothing & GPS noise (v1.1)

Volume never jumps: a ramper moves the level one step at a time (5 levels/s),
so acceleration/deceleration sounds like a continuous swell, not step changes.
The GPS speed feeding it goes through a noise filter designed to keep accuracy:

- **Standstill deadband** — samples < 3 km/h read as 0, so GPS jitter while
  parked doesn't creep the volume up (post-filter snap < 5 km/h → 0)
- **Spike rejection** — any sample implying > 12 m/s² acceleration (beyond any
  car) is discarded; it is the median-of-5 that drives the output
- **Cross-check** — on weak fixes, samples that wildly disagree with the
  displacement-derived speed (distance/Δt between fixes) are rejected
- **Adaptive EMA** — heavy smoothing on low-confidence samples, light on good
  fixes, so real speed changes stay responsive
- **Fix loss** — when the GPS fix drops, volume holds steady instead of jumping

### Reliability (v1.1)

- Double-start guard prevents duplicate listeners on sticky restarts
- A 15 s watchdog re-registers location updates, detects GPS toggling and
  reports fix state; enabling GPS after start just works
- Graceful shutdown if the system revokes permissions mid-run
- **Battery killers**: on first Start the app asks to be whitelisted from
  battery optimization — ColorOS/MIUI aggressively kill background services,
  which was a common cause of "works sometimes, not others". Keep the app
  whitelisted and don't force-stop it.
- If the system silently blocks volume changes (some OEMs), the UI shows a
  warning instead of failing silently

## Build & run

Depends only on the Android platform (no AndroidX/Material libraries), so it
compiles with any modern AGP. Wrapper included — pinning Gradle 9.2.1 / AGP 9.0.1.

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on a connected device
```

Or open the project in Android Studio and run it.

1. Run on a physical device — an emulator has no real GPS speed.
2. Grant **Location** permission and tap **Start**.
3. Also enable **Notifications** (Android 13+), required for the foreground
   service.

## Notes

- Volume mapping is based on `Location.getSpeed()`, which the GPS computes from
  real position fixes. If the GPS hasn't locked yet, the last known volume is kept.
- In a car, audio usually plays over car Bluetooth; the media volume adjusted
  here is the same stream BT music uses.
- System volume could be overridden by other apps (navigation, calls) while
  this is running — that's Android's normal volume behavior.
- Running the service free of charge on battery: the GPS is polled once per
  second while active; stop the service when not driving.
- The speed shown in the app/notification is the *filtered* speed (what drives
  the volume), not the raw GPS value.

## Version history & reverting

The project is a git repository with a tag per release:

```bash
git tag          # v1.0, v1.1, ...
git log --oneline --decorate
```

To try a previous version (e.g. drop v1.1 changes):

```bash
git checkout v1.0 -- .    # restore v1.0 files over the working tree
./gradlew installDebug
```

To permanently go back (keeps a reflog so nothing is lost):

```bash
git revert <commit>  # or: git reset --hard v1.0
```

Changes are logged in CHANGELOG.md.

## Project layout

```
app/src/main/java/com/example/speedvolume/
├── MainActivity.kt          # UI: status, start/stop, mapping settings
├── SpeedVolumeService.kt    # GPS -> volume foreground service
├── SpeedFilter.kt           # GPS noise filtering (spike rejection, median, EMA)
├── VolumeRamp.kt            # smooth 1-level-per-tick volume changes
└── ServiceState.kt          # shared state bridge between service and UI
```