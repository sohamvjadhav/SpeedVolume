# Changelog

## v1.7 (2026-08-12)
- Fix: ~2 s delay in volume response
  - Volume ramp now approaches the target exponentially (halves the gap every
    50 ms): large changes settle in ~250 ms instead of crawling one level per
    100 ms
  - Speed filter: median window 5 -> 3 samples and stronger smoothing weights
    (0.6/0.35) so real speed changes pass through in 1-2 s
  - GPS polling interval 1000 -> 500 ms (fresher fixes, still battery-light)
- Version bump: 1.7 (8)

## v1.6 (2026-08-12)
- Hardware: step-detector sensor fusion for accuracy-first motion tracking
  - GPS remains the authoritative speed source whenever a fix is fresh
  - When GPS has no fix (indoor runs, tunnels, cold start): speed is estimated
    from step cadence x stride, so the speedometer no longer sits at 0
  - Stride length self-calibrates against GPS-verified distance (4-16 km/h
    window), converging on the user's actual gait; estimate capped at 18 km/h
  - UI pill + tile/notification show the speed whenever it is nonzero and mark
    the source as "motion sensors" vs "fix locked"
  - No new permissions required (TYPE_STEP_DETECTOR needs none)
- Version bump: 1.6 (7)

## v1.5 (2026-08-12)
- Fix: speed no longer stuck at 0 while moving
  - Filter now falls back to displacement-derived speed when the GPS fix
    carries no speed value (common in background/low-power mode on ColorOS)
  - Snap-to-zero reduced 5 -> 3 km/h so starts register faster
  - EMA smoothing sped up (0.45 confident / 0.25 weak; accuracy bar 25 -> 40 m)
- Fix: volume scaled too slowly
  - Mapping now uses a sqrt curve with default max speed 120 -> 80 km/h, so
    everyday speeds (30-80 km/h) push volume up quickly
  - Volume ramp step 200 -> 100 ms
- Version bump: 1.5 (6)

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