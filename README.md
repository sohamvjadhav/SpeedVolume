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

## Project layout

```
app/src/main/java/com/example/speedvolume/
├── MainActivity.kt          # UI: status, start/stop, mapping settings
├── SpeedVolumeService.kt    # GPS -> volume foreground service
└── ServiceState.kt          # shared state bridge between service and UI
```