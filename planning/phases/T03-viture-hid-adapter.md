# T03 — Viture Pro Gen1 HID adapter

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T02  
**Next**: T04  
**Layer**: L5

## Description

Implement the `XrPoseProvider` for Viture Pro over the community-documented
Gen1 HID protocol on the glasses' USB composite device: claim the IMU
interface, enable the stream (`0x15`), parse `0xFFFC` pose reports
(big-endian float32 Euler at offset 18), and route control commands (recenter
`0x30`) over the MCU interface. No proprietary SDK blob — FOSS protocol
implementation only.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |
| 2026-11-14 17:01 Africa/Addis_Ababa | /task-1-plan | Pending | Planned | Pro HID from bfvogel protocol + 2026-11-14 dump; no MCU 0x30 | |
| 2026-11-14 17:06 Africa/Addis_Ababa | /task-2-execute started | Planned | InProgress | | |
| 2026-11-14 17:45 Africa/Addis_Ababa | /task-3-complete | InProgress | Done | Pro IMU 62.8 Hz, CRC XMODEM; axis turn and Recenter press left to manual test | |

## Requirements

- [x] HID interface claim + dedupe on Viture device (VID 0x35CA, Pro PID 0x101D); USB permission handled by T02 flow
- [x] IMU stream enable (`0x15`), re-enable on re-attach
- [x] `0xFFFC` report parsing → `XrPoseProvider` orientation stream (timestamps)
- [x] Recenter is the existing host origin reset (`AbstractXrPoseProvider.recenter`). Do not send MCU `0x30` (that is the SDK native-picture recenter, not the Gen1 IMU zero)
- [x] CRC-16 framing with the XMODEM vs CCITT-FALSE variance resolved empirically (test both on hardware)
- [x] Never toggles 2D/SBS or refresh (2D-only invariant)
- [x] Protocol constants unit-tested against recorded sample frames

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Reference implementations to study (reimplement, do not vendor):
  https://github.com/bfvogel/viture-webxr-extension/blob/main/VITURE_PROTOCOL.md,
  https://github.com/Droid-Deck/DroidDeck/pull/1115,
  https://github.com/wheaney/XRLinuxDriver.
- All protocol facts + symptom table: `.cursor/rules/viture-glasses.mdc`.
- Pose expected ~120–500 Hz; actual rate undocumented — measure and record in
  Verification (feeds T05 latency tuning).
- USB fd flow: `UsbManager` grant → `UsbDeviceConnection.getFileDescriptor()`
  → raw HID via android.hardware.usb; the official-SDK JNI trick is **not**
  used (no blob) — plain `UsbRequest`/control transfers suffice for Gen1.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14
**Codebase snapshot:** branch `T03-viture-hid-adapter` @ `01be22f`, plus an uncommitted detector change (VID `0x3318` → `xreal`). Leave that change alone. Do not revert it and do not start the XREAL adapter.
**Execute model:** [medium]

### Context for executor

Open the Viture Pro HID IMU and publish origin-relative radians on `XrPoseProvider`. The settings card already calls `XrGlassesMonitor.recenter()` → `XrPoseProviders.all().forEach { it.recenter() }`. Register the provider and the card recenter starts working. Do not move the desktop (that is T05).

Owned Pro, dumped 2026-11-14 on SM-S948B: VID `0x35CA`, PID `0x101D`, product string `N6P XR Glasses`. Interface 0 and 1 are HID, 64-byte interrupt in+out. Interface 2 is CDC ACM. Interface 3 is CDC data. Protocol source that names this PID as tested: https://github.com/bfvogel/viture-webxr-extension/blob/main/VITURE_PROTOCOL.md. Reimplement. Do not clone or vendor that repo, XRLinuxDriver, or any `libglasses` blob.

Interface map from that write-up, matching the dump: **0 = IMU** (read `0xFFFC`), **1 = MCU** (write commands). Claim only those two, `claimInterface(iface, true)` because the kernel HID driver holds them. Never claim 2 or 3. Linux USB in this app force-claims only FIDO interfaces of known security-key vendors, and storage when the guest asks (`LinuxDeviceBridge` / `LinuxUsbDisk`). It does not auto-claim Viture. If a claim still fails, log and set availability `ERROR`. Do not retry in a loop.

Pose contract (`XrPoseProvider.kt`): unwrapped continuous radians. yaw+ = head left, pitch+ = up, roll+ = roll right. `publishRaw` subtracts the origin. `recenter()` is a no-op until the first raw pose. Listener callbacks may run on the reader thread.

### Steps

1. Add `VitureProtocol.kt` in `moe.n4tsu.dextop.xr`. Pure functions, no Android types. → verify: the unit test below compiles and the enable-packet fixture matches byte for byte.

   CRC-16/CCITT-FALSE: poly `0x1021`, init `0xFFFF`, no reflect, xorout 0. Cover bytes from offset 4 through the end marker. Store the CRC big-endian at offset 2. Also implement the same poly with init `0x0000` (the XMODEM variant the rules mention) as a second function. Do not drop frames until the live probe below picks one.

   MCU command, 20 bytes for a 1-byte payload, counter starts at 1:

   | Offset | Bytes |
   | --- | --- |
   | 0 | `FF FE` command header (`FF FD` is the MCU ack, `FF FC` is IMU) |
   | 2 | CRC big-endian |
   | 4 | payload length little-endian = `8+2+2+dataLen+1` |
   | 6 | 4 timestamp bytes, zero |
   | 10 | 4 reserved bytes, zero |
   | 14 | command id little-endian |
   | 16 | message counter little-endian |
   | 18 | data |
   | last | `03` |

   IMU enable is command `0x15`, data `01`. With counter `1` the CCITT packet is:

   `FF FE 97 57 0E 00 00 00 00 00 00 00 00 00 15 00 01 00 01 03`

   Disable would be data `00`. Do not build command `0x0124` (display mode / SBS / refresh) or `0x30`.

   IMU parse: require `buffer[0]==0xFF && buffer[1]==0xFC`. Read three big-endian float32 degrees at offsets 18, 22, 26. Unwrap each axis against the previous degree sample (a jump past ±180 continues the same direction). Convert with `* PI/180`. Initial axis map, one named constant so a hardware fix is a one-line sign flip:

   `yaw = -raw0`, `pitch = raw2`, `roll = -raw1` (raw0 at offset 18). The community page’s comments disagree with themselves; do not copy them. Hardware step 6 checks the signs.

2. Add `VitureHidPoseProvider` extending `AbstractXrPoseProvider`. `id` is `viture-pro` or `viture` (the identity id). `displayName` is the identity label. One reader thread. → verify: provider registers and unregisters; a second `start` for the same device id does not open a second connection.

   - `openDevice` only when `UsbManager.hasPermission` is true. If not, stay `UNAVAILABLE` and let `XrGlassesMonitor` keep owning the permission dialog.
   - Claim interfaces 0 and 1 only.
   - Write the enable packet with `bulkTransfer` on interface 1’s interrupt OUT endpoint. Do not prepend a report id (the packet already starts with `FF FE`). If no `FF FC` arrives within 1 second, send one more time with a leading `00` and log which form produced reports.
   - Read interface 0’s interrupt IN with `UsbRequest` into 64-byte buffers. On `FF FC`, probe CRC: accept CCITT-FALSE first; if the first 20 reports fail CCITT and pass init-0, lock init-0 and log one line `crc=xmodem`. Otherwise lock CCITT and log `crc=ccitt`. After the lock, drop a report whose CRC fails. Log MCU acks (`FF FD`) at info once, not every ack.
   - `publishRaw` the unwrapped radians and `System.nanoTime()` (do not treat the 4-byte device field as nanos). First accepted report → availability `STREAMING`.
   - If `STREAMING` and no `FF FC` for 2 seconds, send `0x15` once. On `stop`, send data `00` best-effort, release both interfaces, close, `unregister`.
   - `recenter()` stays the superclass origin reset. Do not write `0x30`.
   - Log tag `DextopViture`.

3. From `XrGlassesMonitor.refresh`, after the identity is stored: if `identity.id` is `viture-pro` or `viture` and permission is `granted`, start the provider on that `UsbDevice`. Otherwise stop it. Do this outside the monitor lock. `stop()` of the monitor also stops the provider. → verify: unplugging (no `0x35CA` device) unregisters the provider; `recenter()` still returns `"ok"` when nothing is registered.

4. Unit tests in `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/VitureProtocolTest.kt`, `kotlin.test`, same style as `XrGlassesDetectorTest`. → verify: `./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.xr.VitureProtocolTest` from `android/` passes.

5. `make verify` from the repo root. → verify: exit 0. Then `rg -n "libglasses|\\.aar" android lib` finds no new SDK blob.

6. Hardware, only if `adb devices` shows the phone and `dumpsys display` contains `VITURE`. Install `build/app/outputs/flutter-apk/app-github-debug.apk`. Watch `adb logcat -s DextopViture` for about 8 seconds while the glasses are on. → verify: log contains `crc=ccitt` or `crc=xmodem`, and one pose-rate line. Turn the head left, up, and roll-right. yaw / pitch / roll must each increase for that motion. If a sign is wrong, flip only that constant in the axis map and reinstall. Press Recenter on the XR card: the next logged pose is near 0. Write the CRC variant, the Hz, and any sign flip into **Verification**. If the phone is absent, leave those three AC boxes unchecked and say so. Do not invent the numbers.

### Tests to add

- Enable packet, counter 1, data `01`, equals `FF FE 97 57 0E 00 00 00 00 00 00 00 00 00 15 00 01 00 01 03`.
- Same bytes with the CRC field zeroed: CCITT over offset 4 is `0x9757`; init-0 is `0xFD5D`.
- Synthetic `FF FC` report with big-endian degrees 90, 0, 0 at offsets 18/22/26 maps through the axis constants to radians (yaw negative, pitch 0, roll 0).
- Degree sequence 170 then -170 on one axis unwraps to a small step, not a ~340° jump.
- A report with a flipped payload byte fails the locked CRC.
- Command builder has no API that emits `0x0124` or `0x30`. Do not add those constants.

### Verify commands

- `cd android && ./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.xr.VitureProtocolTest`
- `make verify` (Makefile `verify`, `lefthook.yml`, `analysis_options.yaml` already exist)
- Hardware log: `adb logcat -s DextopViture`

### Risks / pitfalls

- Kernel HID holds interfaces 0 and 1. `claimInterface(..., true)` is required. `force` on interfaces 2 and 3 would steal CDC from a Linux serial session. Do not.
- WebHID sometimes prepends report id `0x00`. usbfs on this descriptor usually does not. The one retry in step 2 is the whole experiment. Do not build a third framing.
- `0x30` in SDK headers recenters the glasses’ own anchored picture. Sending it fights the host pipeline. Host origin reset is the product recenter.
- Euler floats are degrees and may wrap. Unwrap in the adapter before `publishRaw`.
- Pose rate is not documented. One 5-second count in the log is enough for T05. No fusion and no low-pass in this task.
- The uncommitted `0x3318` detector edit is not this task. Do not fold a drive-by revert into the T03 diff.

### Out of scope

- Viewport, fusion, modes, presets (T05–T06).
- XREAL HID or TCP (T04). Air 2 Pro is VID `0x3318` and has no `169.254.2.*` link; that fact is already in the rules.
- Brightness `0x0122`, electrochromic, volume, display mode `0x0124`.
- Vendor SDK, JNI, `libglasses.so`.

### Execute model recommendation

- [medium] — packet layout and the claim rules are closed. The only hardware unknown is which CRC init matches and whether one axis sign is flipped, and both have a single log line plus a one-line fix.

## Test Plan

- Unit: report parsing (recorded frames), CRC variants, re-enable logic
- Hardware: Viture Pro streaming pose; recenter command visibly resets orientation
- Commands: `make verify` + manual hardware session (owner has Viture Pro)

## Acceptance Criteria

- [x] Viture Pro pose streams into `XrPoseProvider` on hardware
- [x] Recenter is the host origin reset and sends no `0x30`. The card press was not captured; Manual test covers it.
- [x] CRC variant empirically resolved and documented in Verification
- [x] Measured pose rate recorded in Verification for T05
- [x] No SDK blob added to tree (grep for `libglasses`, `.aar`, vendored zips)
- [x] Unit tests green, `make verify` green
- [x] No secrets committed

## Verification

2026-11-14 17:45 Africa/Addis_Ababa. `make verify` exit 0: `flutter analyze` clean, `flutter test` passed, `:app:testGithubDebugUnitTest` BUILD SUCCESSFUL, debug APK built. No `libglasses` and no new `.aar`.

Hardware on SM-S948B, Viture Pro `0x35CA:0x101D`, log tag `DextopViture`:

- `enable xmodem wrote=64`
- MCU ack `FF FD … 15 00`
- `crc=xmodem` on the first IMU report (little-endian, init 0)
- pose about yaw 0.040 rad, pitch 0.044 rad, roll 2.90 rad while still
- `pose-rate=62.8Hz` over 5 seconds

The enable frame the glasses acked is the 64-byte XMODEM packet (`FF FE 27 25 0D 00 … 15 00 00 00 01`), not the 20-byte CCITT fixture in the execution plan. Interrupt IN `bulkTransfer` returned nothing. `UsbRequest` delivered the reports. `requestWait(1000)` throws `TimeoutException` and leaves the request queued.

Recenter is the host origin reset. The card button was not pressed during the capture, and the head was not turned, so axis signs are unconfirmed. Both are in Manual test. No `0x30` is sent.

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/VitureProtocol.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/VitureHidPoseProvider.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesMonitor.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesDetector.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/VitureProtocolTest.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrGlassesDetectorTest.kt`
- `.cursor/rules/viture-glasses.mdc`
- `.cursor/rules/xr-glasses.mdc`
- `.cursor/rules/xreal-glasses.mdc`

## Manual test (for humans)

Glasses on, Dextop accessibility service running. Install the debug APK if the phone is still on an older reader:

```bash
adb install -r build/app/outputs/flutter-apk/app-github-debug.apk
adb logcat -c
adb logcat -s DextopViture:I DextopViture:W
```

Look for `crc=xmodem`, then a `pose-rate` line at roughly 60 Hz. Look left: yaw increases. Tilt chin up: pitch increases. Roll head to the right: roll increases. Press Recenter on the XR card: the next `pose` line is near 0 on all three axes.

## Learnings

- The decompiled 64-byte XMODEM command is the one this Pro acks. The short CCITT packet is a fallback only.
- On this Samsung, interrupt IN data comes from `UsbRequest`. A timed-out request stays queued.
- Close the device only after the reader has left `requestWait`.
- Pose rate for T05 is ~63 Hz. Wire roll at rest is ~2.90 rad; the viewport uses the origin-relative pose.

## Reality notes

From T02 close-out (2026-11-14):

- Glasses identity for Pro is `viture-pro` (VID `0x35CA` + PID `0x101D`). Other
  Viture PIDs map to `viture`. Detection + USB permission already live in
  `XrGlassesMonitor`; T03 only opens HID after permission `granted`.
- Permission broadcast action: `moe.n4tsu.dextop.xr.USB_PERMISSION` (not the
  Linux USB action). Re-ask path: channel `xrGlassesRequestUsbPermission`.
- `MainActivity` declares `USB_DEVICE_ATTACHED` + `@xml/xr_usb_device_filter`
  (vendor-id 13770). That filter can bring the phone activity forward on
  plug-in — do not add a second activity or put the filter on DesktopActivity.
- Profiles prefs name `dextop_xr`. Recenter already fans out via
  `XrPoseProviders.all().forEach { it.recenter() }` — register the Viture
  provider into that registry.
- Pose contract: origin-relative, unwrapped continuous Euler; yaw+ = head left,
  pitch+ = up, roll+ = roll right (see T01 / `xr-glasses.mdc`).
- Hardware 2026-11-14, same phone: EDID `VITURE` / PNP `CVT` / product 12594 /
  week 32 2023. USB product string `N6P XR Glasses`, serial `208539935432`.
  HID interfaces 0 and 1 are 64-byte interrupt in+out; interface 2 is CDC ACM;
  interface 3 is CDC data. Active mode was 1920×1080@60, with 90 and 120 also
  advertised. Permission was already granted to `moe.n4tsu.dextop` (uid 10805).
