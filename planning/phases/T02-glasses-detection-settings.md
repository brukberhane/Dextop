# T02 — Glasses detection + XR settings card + profiles

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T01  
**Next**: T03  
**Layer**: L6

## Description

Detect that a connected external display is a supported XR glasses pair and
surface an XR settings card. Detection combines `DisplayListener` events with
USB signals: Viture VID 0x35CA (PID 0x101D on Pro) via `UsbManager`, XREAL
via the glasses' USB network interface, plus display-name heuristics. Unknown
glasses degrade to today's plain external-display behavior. Per-glasses
profiles (stabilization mode, geometry preset) persist and auto-apply on
plug-in. The card lives beside `ExternalDisplayModeCard` in display settings,
fully localized.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |
| 2026-11-14 | planned by /task-1-plan | Pending | Planned | seams from repo: display channel, USB, l10n, card slot | |
| 2026-11-14 | /task-2-execute started | Planned | InProgress | | |
| 2026-11-14 16:29 Africa/Addis_Ababa | /task-3-complete | InProgress | Done | review fixes + verify green; commit local | |

## Requirements

- [x] `XrGlassesDetector`: maps a connected physical display to a glasses identity (Viture Pro / XREAL One / unknown) using USB VID/PID + USB-NIC presence + display name
- [x] USB permission flow for Viture HID (runtime dialog + `device_filter.xml`); no permission flow for XREAL TCP
- [x] Per-glasses profile persistence (mode, preset) in shared preferences, auto-applied on reconnect
- [x] XR settings card in display settings: stabilization mode selector, geometry preset selector, recenter button, per-glasses profile; visible only when XR display detected
- [x] Every new user-facing string in all `app_*.arb` + inline json/properties l10n sets
- [x] Unknown glasses: existing external-display UX unchanged, no crash, no XR card
- [x] Unit tests for detection mapping (USB signal → identity → profile) with fake device inventory

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Wire `LinuxUsb.kt`-style USB attach/detach broadcasts into display
  detection — they exist today but are not linked to displays
  (`.cursor/rules/display-stack.mdc`).
- XREAL model identity is uncertain (One vs Air — user will verify on
  hardware). Keep identity resolution data-driven so an Air shows up as its
  own identity or unknown; do not hardcode "One" assumptions beyond display
  mode table.
- Viture Pro EDID product string is unpublished — identify by VID/PID first.
- Recenter button on the card: T01 interface hook; no-op until T05 wires
  the transform (card still ships).

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14
**Codebase snapshot:** branch `T02-glasses-detection-settings` @ `ceca29d` (T01). `moe.n4tsu.dextop.xr` exists. Display channel `app.freedextop/display`. No `device_filter.xml` yet. No `169.254` code yet. `INTERNET` + `ACCESS_NETWORK_STATE` already in the manifest.
**Execute model:** [medium]

### Context for executor

Detect XR glasses and remember a per-glasses profile. Show a settings card only when glasses are detected. Unknown displays stay on today's external-display UI.

Do **not** open HID or TCP. Do **not** move the desktop. Recenter calls `XrPoseProvider.recenter()` on whatever is already registered (empty until T03/T04 — that is a successful no-op).

**Identity rules** (`identifyXrGlasses`, pure, no Android types):

| Priority | Signal | Id |
| --- | --- | --- |
| 1 | USB VID `0x35CA` + PID `0x101D` | `viture-pro` |
| 2 | USB VID `0x35CA`, any other PID | `viture` |
| 3 | Any link address starting with `169.254.2.` | `xreal` |
| 4 | A connected external display name contains `viture` (ignore case) | `viture-pro` |
| 5 | Name contains `xreal` or `nreal` | `xreal` |
| else | | `null` (unknown — no card) |

USB beats name. `xreal` is the family id (One vs Air is still unconfirmed — do not split). If both a Viture USB device and an XREAL link are present, keep the one whose name matches; if neither name matches, keep `viture-pro` / `viture`.

**Profile** (per id): `mode` = `body-lock` (default) | `smooth-follow` | `world-lock`. `preset` = `single` (default) | `ultrawide` | `triple`. Stored in app prefs `dextop_xr` (not Flutter prefs). On identity change, load that id's profile into an in-memory session even if the card is closed. That is "auto-apply" for this task.

**Files**

- New `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesDetector.kt` — signals, identity, `identifyXrGlasses`
- New `.../xr/XrGlassesProfiles.kt` — `XrProfile`, `XrProfileStore`, `MemoryXrProfileStore`, `PrefsXrProfileStore` (`dextop_xr`)
- New `.../xr/XrGlassesMonitor.kt` — reads USB + networks + `ExternalDisplayDetector`, requests Viture permission, holds session
- New tests under `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/`
- New `android/app/src/main/res/xml/xr_usb_device_filter.xml` — vendor-id `13770` (`0x35CA`)
- Edit `AndroidManifest.xml` — USB device-attached meta-data on the existing main activity (do not add a second activity)
- Edit `MainActivity.kt` — three `when` branches before `else -> result.notImplemented()` (~line 738)
- Edit `lib/app_shell.dart` — three `NativeBridge` methods next to `externalDisplayState` (~line 390)
- New `lib/xr_glasses_card.dart` — `part of 'main.dart';`
- Edit `lib/main.dart` — `part 'xr_glasses_card.dart';` immediately after `part 'display_topology.dart';` (line 38)
- Edit `lib/display_topology.dart` `DisplaySettingsPage` — `belowArrangement` becomes a `Column` of `ExternalDisplayModeCard` then `XrGlassesSettingsCard`
- L10n: 7 locales, not 8. `en`, `ja`, `ko`, `zh`, `ru`, `pt`, `pt_BR`

**Channel maps** (strings, no enums over the channel)

`xrGlassesState` → `{ detected: bool, id: String, displayName: String, mode: String, preset: String, usbPermission: "granted"|"needed"|"not_required" }`. When nothing is detected, `detected: false` and the other strings may be empty.

`setXrGlassesProfile` args `{ id, mode, preset }` → same map shape as state, after saving. Reject unknown mode/preset with `result.error`.

`xrGlassesRecenter` → calls `recenter()` on every `XrPoseProviders.all()` entry, then `"ok"`.

**Invariants**

- Callbacks / `UsbManager`, not Flow (`.cursor/rules/flutter-dart.mdc`).
- Do not put a method named `setX` next to a `var x` setter.
- Card copy goes through `localText` (same as `ExternalDisplayModeCard`), keys below. Also add the same keys to every `app_*.arb` so the AC's ARB list is filled. Other locales get the **English** string (do not invent translations).
- `LinuxUsb.kt` stays untouched. Permission action string: `moe.n4tsu.dextop.xr.USB_PERMISSION` (not the Linux one).

**Inline / ARB keys** (English):

| Key | English |
| --- | --- |
| `uiXrGlassesTitle` | XR glasses |
| `uiXrGlassesStabilization` | Stabilization |
| `uiXrGlassesBodyLock` | Body lock |
| `uiXrGlassesSmoothFollow` | Smooth follow |
| `uiXrGlassesWorldLock` | World lock |
| `uiXrGlassesGeometry` | Display size |
| `uiXrGlassesPresetSingle` | Single screen |
| `uiXrGlassesPresetUltrawide` | Ultrawide |
| `uiXrGlassesPresetTriple` | Triple monitor |
| `uiXrGlassesRecenter` | Recenter |
| `uiXrGlassesUsbNeeded` | Allow USB access so stabilization can use the glasses sensors |

### Steps

1. Pure detector + memory profile store + unit tests (table below). No Android context in the detector. → verify: `make ktest` shows the new tests passing.
2. `PrefsXrProfileStore` + `XrGlassesMonitor`. USB: `UsbManager.deviceList` for VID/PID; `requestPermission` with the XR action when VID `0x35CA` lacks permission. NIC: `ConnectivityManager.allNetworks` → `LinkProperties.linkAddresses`; pass address strings into `identifyXrGlasses`. Displays: `ExternalDisplayDetector.snapshot().names`. Start/stop the monitor from `MirrorService` beside the existing `DisplayListener` registration (`MirrorService.kt` ~2113) and unregister on destroy. → verify: `make ktest` still green and `:app:compileGithubDebugKotlin` succeeds (ktest compiles it).
3. `device_filter.xml` + manifest meta-data `android.hardware.usb.action.USB_DEVICE_ATTACHED` on the main activity, resource `@xml/xr_usb_device_filter`. → verify: `make build` merges the manifest (no duplicate activity).
4. Channel methods in `MainActivity.kt` and `NativeBridge`. → verify: `flutter analyze` clean.
5. Card. `XrGlassesSettingsCard` polls `xrGlassesState` every 2s while mounted (same idea as `ExternalDisplayModeCard`). `detected != true` → `SizedBox.shrink()`. Dropdowns write `setXrGlassesProfile`. Recenter button calls `xrGlassesRecenter`. Extract a `XrGlassesCardBody` that takes the state map so widget tests do not need a channel. `DisplaySettingsPage.belowArrangement` = `Column(children: [ExternalDisplayModeCard(...), XrGlassesSettingsCard(...)])`. → verify: new widget test passes under `flutter test`.
6. L10n. Add the 11 keys to `lib/l10n/inline/{en,ja,ko,zh,ru,pt,pt_BR}.json` and the same keys to every `lib/l10n/app_*.arb` (English text in every locale). Run `python3 tool/generate_inline_strings.py` and `flutter gen-l10n`. Do not hand-edit `*.g.dart` or `app_localizations_*.dart`. → verify: `flutter analyze` clean.
7. Full gate. → verify: `make verify` exit 0.

### Tests to add

Kotlin `XrGlassesDetectorTest` / `XrGlassesProfilesTest` (`kotlin.test`):

| Case | Expect |
| --- | --- |
| USB `0x35CA` / `0x101D`, no name | `viture-pro` |
| USB `0x35CA` / `0x1100` | `viture` |
| Link `169.254.2.1/16`, no USB | `xreal` |
| Name `XREAL One`, no USB, no link | `xreal` |
| Name `VITURE Pro`, no USB | `viture-pro` |
| Name `Dell U2720Q` only | `null` |
| USB Viture Pro **and** name `Dell` | `viture-pro` (USB wins) |
| USB Viture Pro **and** link `169.254.2.1` **and** name `XREAL One` | `xreal` (name breaks the tie) |
| USB Viture Pro **and** link `169.254.2.1`, no name | `viture-pro` |
| Fresh store | mode `body-lock`, preset `single` |
| Write `world-lock` / `ultrawide` for `viture-pro` | read-back matches; `xreal` stays default |
| Unknown mode string | store rejects it (false / exception — pick one and test it) |

Dart `test/xr_glasses_card_test.dart`: `XrGlassesCardBody` with `detected: false` finds no "XR glasses" text; `detected: true`, `mode: world-lock` shows the title. Pump a `MaterialApp`.

### Verify commands

- `make ktest`
- `flutter test test/xr_glasses_card_test.dart`
- `make verify`

### Risks / pitfalls

- USB attach meta-data can bring the activity forward when glasses plug in. That is the requested permission path. Do not also register a second USB receiver that calls `LinuxUsb`'s permission action.
- `ExternalDisplayDetector` names are the Android display names, which may be generic (`HDMI` / empty). Identity must still work from USB or the link address alone.
- `belowArrangement` is one widget today. A `Column` is the whole change; do not edit `ExternalDisplayModeCard` behavior.
- Generating l10n: missing a locale file fails the build. All 7 files, every key.
- Do not claim XREAL One vs Air. Id is `xreal`.

### Out of scope

- HID pose stream, TCP sockets, IMU, stabilization math, viewport crop (T03–T05).
- Changing logical desktop resolution for presets (T06). The preset is stored only.
- Follow / Stabilizer-off onboarding card (T07).
- Editing `LinuxUsb.kt`.

### Execute model recommendation

- [medium] — channel + manifest + l10n + a pure function. Design above is closed. [small] can execute if it does not rename ids or skip a locale file.

## Test Plan

- Unit tests: detector mapping table (VID/PID, USB-NIC, name heuristics), profile round-trip
- Commands: `make verify`; manual: plug each glasses pair, card appears with remembered profile

## Acceptance Criteria

- [x] Detection maps Viture Pro, XREAL One, and unknown glasses correctly (unit-tested mapping)
- [x] XR card localized across all 7 locale files (`en`, `ja`, `ko`, `zh`, `ru`, `pt`, `pt_BR`) in both inline json and `app_*.arb`, appears only when glasses are detected
- [x] Profiles persist per glasses model and auto-apply on reconnect
- [x] Unknown glasses keep plain external-display behavior (regression-checked)
- [x] Tests added/updated; `make verify` green
- [x] No secrets committed

## Verification

**Date:** 2026-11-14 (Africa/Addis_Ababa)

`make verify` (Makefile `verify`, `lefthook.yml`, `analysis_options.yaml` present), exit 0:

- analyze: no issues (4.8s)
- flutter test: 71 passed (69 existing + 2 XR card tests)
- ktest: BUILD SUCCESSFUL (9s). `XrGlassesDetectorTest` 9/9, `XrGlassesProfilesTest` 3/3
- build: `app-github-debug.apk`

Review fixes (same day), then `/task-3-complete` re-verify, `make verify` exit 0:

- USB attach intent-filter on `MainActivity` so the device filter is live
- XREAL/nreal name wins the both-present tie even when a Viture name is listed first (`XrGlassesDetectorTest` 10)
- USB-needed tile calls `xrGlassesRequestUsbPermission` (clears still-denied ids, asks once per tap)
- `onDisplaysChanged` runs before `root?.post`, so a hotplug with no overlay still reloads the session
- close-out: analyze no issues (5.0s); flutter test 71; ktest BUILD SUCCESSFUL (7s); `app-github-debug.apk`

Non-English locales use the English source strings, as the plan specified.

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesDetector.kt` (new)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesProfiles.kt` (new)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesMonitor.kt` (new)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrGlassesDetectorTest.kt` (new)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrGlassesProfilesTest.kt` (new)
- `android/app/src/main/res/xml/xr_usb_device_filter.xml` (new)
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/MainActivity.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/MirrorService.kt`
- `lib/xr_glasses_card.dart` (new)
- `lib/main.dart`
- `lib/app_shell.dart`
- `lib/display_topology.dart`
- `lib/l10n/inline/*.json`, `lib/l10n/app_*.arb`, generated l10n outputs
- `test/xr_glasses_card_test.dart` (new)
- `planning/phases/T02-glasses-detection-settings.md`, `planning/phases/INDEX.md`
- `planning/phases/T03-viture-hid-adapter.md`, `planning/phases/T04-xreal-tcp-adapter.md` (Reality notes)
- `.cursor/rules/xr-glasses.mdc`, `.cursor/rules/viture-glasses.mdc` (dialectic)

## Manual test (for humans)

1. Build/install the github debug APK:
   `make build && adb install -r build/app/outputs/flutter-apk/app-github-debug.apk`
2. Open Dextop → Display settings (same place as the external-display mode card).
3. Plug Viture Pro (DP + USB). Expect:
   - XR glasses card appears with title “XR glasses”
   - If USB permission was never granted: tap the USB-needed tile → system dialog
   - After grant / if already granted: mode + preset dropdowns + Recenter work
   - Change mode/preset, unplug, replug → remembered values return
4. Plug XREAL (DP + USB NIC). Expect card with family id path working (title still
   “XR glasses”); no USB permission tile. Mode/preset persist across reconnect.
5. Plug a non-glasses HDMI/DP display. Expect: no XR card; existing external
   display mode card unchanged; no crash.
6. Recenter with no pose provider registered: button succeeds as a no-op (ok).

## Learnings

- `USB_DEVICE_ATTACHED` device-filter meta-data needs a matching intent-filter on
  the same activity or Android ignores the filter.
- Both-vendor signal ties: scan for any xreal/nreal name, do not stop at the
  first glasses-looking name.
- `permissionAsked` must not permanently block re-ask; clear still-denied ids
  only from an explicit UI action, never from the 2s poll.
- Glasses session refresh must run even when the overlay root is null.

## Reality notes

- T01 landed `moe.n4tsu.dextop.xr`: `XrPoseProvider`, `AbstractXrPoseProvider`,
  `FakeXrPoseProvider`, `XrPoseProviders` registry. Recenter on the settings
  card should call `XrPoseProvider.recenter()` when a provider is registered
  (still no production registration until T03/T04 — card can no-op safely).
- `make verify` now includes `make ktest` (`:app:testGithubDebugUnitTest`).
  New Kotlin under `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/` mirrors
  the main package.
- Identity ids shipped: `viture-pro` / `viture` / `xreal`. Prefs `dextop_xr`.
  USB action `moe.n4tsu.dextop.xr.USB_PERMISSION`. Channel methods
  `xrGlassesState` / `setXrGlassesProfile` / `xrGlassesRecenter` /
  `xrGlassesRequestUsbPermission`.
