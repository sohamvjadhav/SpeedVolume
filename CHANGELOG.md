# Changelog

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