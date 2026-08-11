# Changelog

## v1.4 (2026-08-11)
- Fix: QS tile now starts the service instantly from the background
  - Added ACCESS_BACKGROUND_LOCATION: the system refuses to start a location
    foreground service from the background without it, which made tile presses
    appear dead (or only work while the app was recently open)
  - App now prompts for "Allow all the time" after location is granted
  - Tile gives immediate "Starting…" feedback on press
- Version bump: 1.4 (5)

## v1.3.1 (2026-08-11)
- QS tile: long-press now opens the app instead of App Info
  (ACTION_QS_TILE_PREFERENCES on MainActivity, per platform docs)

## v1.3 (2026-08-11)
- Fix: content no longer renders under the status bar (edge-to-edge insets)
- Fix: duplicate "km/h" next to the big speed number
- Quick Settings tile: tap to start/stop the service, shows live speed + volume
  subtitle, active/inactive state and icons (add via QS edit menu)

## v1.2 (2026-08-11)
- Redesigned minimal UI:
  - card-based layout with hero speed display (large accent number)
  - header with live status pill (green/gray dot + Running/Stopped)
  - GPS fix state row (locked / looking for fix…)
  - custom-tinted seekbars and primary action button
  - dark theme (follows system night mode) — verified on device
- No functional changes; speed/volume logic identical to v1.1

## v1.1 (2026-08-11)
- Smooth volume changes: volume now ramps one level at a time (5 levels/s)
  instead of jumping, so speed changes sound continuous instead of stepwise
- GPS noise handling without losing responsiveness:
  - standstill deadband (< 3 km/h treated as 0) to kill jitter while parked
  - spike rejection via maximum plausible acceleration
  - cross-check against displacement-derived speed on weak fixes
  - median-of-5 filter + adaptive EMA (aggressive when the fix is weak,
    light when accuracy is good)
  - volume holds steady if the GPS fix is lost (no spurious jumps)
- Reliability fixes:
  - double-start guard (no duplicate listeners/ramps on sticky restarts)
  - 15 s watchdog re-registers location updates and detects GPS toggling
  - graceful failure if startForeground or location permission is revoked
  - system-blocked volume changes are now detected and reported in the UI
  - battery-optimization whitelist prompt on first start (aggressive OEM
    killers were killing the service)
- Launcher icon (adaptive speedometer icon)
- Version history via git (see README for reverting)

## v1.0
- Initial release: foreground service reads GPS speed once per second and
  sets the media volume (linear map, 0–maxSpeed km/h)
- Live speed/volume UI with configurable mapping sliders