# Development Status

This document tracks the implementation status of each component. It is updated as work progresses.

---

## ✅ Fully Implemented

### Infrastructure
- Gradle multi-module build (11 modules, AGP 8.7.3, Gradle 8.11.1)
- Version catalog (`gradle/libs.versions.toml`) — all dependencies pinned
- Kotlin 2.1.0, Compose BOM 2025.02.00, Room 2.7.0, Media3 1.5.1, Hilt 2.54

### `:core-data`
- Room database v1 with 4 entities: `channels`, `guide_entries`, `recordings`, `scheduled_recordings`
- All DAOs with Flow-based observation and suspend one-shot queries
- `ChannelRepository`, `GuideRepository`, `RecordingRepository`
- DataStore `AppPreferences` (onboarding state, tuner selection, storage path, last channel)

### `:core-ui`
- Dark-first Material 3 theme (`TvBackground = #0A0A0F`)
- `ChannelLogoBadge`, `LoadingOverlay`, `ErrorState` shared components

### `:tuner-core`
- `TunerBackend` interface (discover, permission, open, tune, stream, scan, signal metrics)
- `TunerManager` — multibinding-based backend registry, selects/delegates to active backend
- `FakeTunerBackend` — simulates US ATSC scan (KTVU, KRON, KPIX, KGO, KNTV, KDTV), no hardware needed

### `:tuner-network`
- `HdhrTunerBackend` — full implementation:
  - UDP broadcast discovery (port 65001)
  - `/discover.json` + `/lineup.json` parsing
  - HTTP stream URL generation for ExoPlayer
  - Signal metrics from `/status.json`

### `:parser-atsc-psip`
- `TsPacket` — 188-byte MPEG-2 TS packet parser
- `SectionAssembler` — per-PID section reassembly with pointer_field handling
- `VctParser` — TVCT/CVCT (ATSC A/65) → callsign, major/minor channel, frequency, program numbers
- `EitParser` — EIT-0..3 → title, start time (GPS→Unix), duration, rating; Multiple String Structure decoder
- `PsipProcessor` — high-level processor, routes packets to parsers, emits `PsipEvent`
- Unit tests for all three parsers

### `:feature-live-tv`
- `PlaybackEngine` — ExoPlayer integration; HDHomeRun uses HTTP stream; demo uses test HLS
- `PlaybackService` — `MediaSessionService` for PiP continuity and lock-screen controls
- `LiveTvScreen` — full-screen Compose UI: video surface, auto-hide overlay, signal indicator, channel info
- `ScanViewModel` — channel scan state machine; persists results to Room on completion
- `LiveTvNavigation`

### `:feature-guide`
- `GuideViewModel` — 4-hour window from current time
- `GuideScreen` — time-based channel grid (LazyColumn + horizontal scroll); 4dp/minute layout

### `:feature-recordings`
- `RecordingService` — foreground service, TS byte stream → file capture
- `RecordingManager` — start/stop recording, storage path from DataStore
- `RecordingsViewModel` — collection CRUD, watch progress
- `RecordingsScreen` — "Continue Watching" row + "Recently Recorded" list

### `:feature-settings`
- `SettingsViewModel` — tuner backend selection, network URL, storage path, onboarding completion
- `SettingsScreen` — tuner source picker, network tuner URL field, "Scan for network tuners", recording path
- `OnboardingScreen` — 4-step wizard: Welcome → Find tuner → Channel scan → Done
- `SettingsNavigation`

### `:app`
- `TvTunerApplication` — `@HiltAndroidApp`, WorkManager Hilt integration
- `MainActivity` — edge-to-edge, USB attach intent handler, PiP hooks
- `TvTunerNavHost` — full navigation graph
- `OnboardingNavigation` — onboarding graph, pops on complete
- `PipManager` — 16:9 PiP params with auto-enter on Android S+
- `MetadataRefreshWorker` — OTA guide enrichment via WorkManager

### CI/CD
- GitHub Actions workflow (`.github/workflows/build.yml`) — debug APK on every push

---

## ⚙️ Partially Implemented

### `:tuner-usb-mygica` (MyGica PT682C / PadTV HD)
**Working:**
- USB device enumeration by Vendor ID
- USB permission request flow (broadcast receiver + `suspendCancellableCoroutine`)
- `TunerBackendType.USB_MYGICA` registration in Hilt multibinding

**Not working (returns `TunerError.NotImplemented`):**
- `tune()` — no RF tuning command implemented
- `readTransportStream()` — no TS byte stream from USB bulk endpoint
- `scanChannels()` — no scan loop
- Signal metrics

**Reason:** The MyGica PT682C uses a proprietary USB protocol that is not publicly documented. The Windows driver DLL would need to be reverse-engineered, or the vendor contacted for an Android SDK. The USB interface is likely a WinUSB/libusb-compatible bulk-transfer device — porting would require:
1. Identifying the correct interface/endpoint via `lsusb -v`
2. Implementing the USB bulk read loop via `UsbDeviceConnection.bulkTransfer()`
3. Sending the vendor-specific initialization + tune commands (device-specific)

---

## 🔮 Phase 2+ (Planned)

### Scheduled Recordings
- `ScheduledRecordingEntity` and DAO exist in Room
- Recording scheduling UI and background trigger not yet implemented
- Approach: WorkManager `OneTimeWorkRequest` timed to record start

### ATSC 3.0 (NEXTGEN TV) Support
- Would require a new tuner hardware target (e.g., SiliconDust HDHomeRun Flex 4K in ATSC 3.0 mode)
- ROUTE/DASH stream handling (not MPEG-2 TS)
- New parser module: `parser-atsc3-route`

### USB Tuner Streaming (Phase 2)
- Requires vendor cooperation or reverse engineering
- Once USB bulk endpoint is identified: implement `readTransportStream()` as a `callbackFlow` feeding `UsbRequest.queue()`

### Adaptive Streaming / Quality Selection
- ExoPlayer HLS track selection for HDHomeRun quality tiers

### Parental Controls / Channel Lock
- PIN-based lock per channel using existing `isEncrypted`/`isHidden` flags

### Subtitle / Caption Support
- EIA-608/708 closed caption extraction from TS
- ExoPlayer caption rendering overlay

### Export Recordings
- Share recording file via Android `FileProvider`

### Web Remote (Phase 3)
- Optional local HTTP server for phone-as-remote control
- Ktor embedded server

---

## Known Limitations

| Item | Notes |
|------|-------|
| USB VID/PID | Placeholder values in `usb_device_filter.xml` — verify with `lsusb` once hardware is in hand |
| EIT Huffman text | ATSC A/65 text compression via Huffman table is stubbed — most broadcasters use Latin-1 or UTF-16 which is fully decoded |
| HDHomeRun JSON parsing | Uses `String.split()` on JSON fields — should be replaced with a proper JSON parser if field order ever varies |
| Recording interruption | If the app is killed during recording, `RecordingEntity.status` is set to `INTERRUPTED` on next launch (not yet implemented) |
