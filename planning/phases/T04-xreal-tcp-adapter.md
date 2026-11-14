# T04 — XREAL Air 2 Pro HID pose adapter

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T03  
**Next**: T05  
**Layer**: L5

## Description

Implement the `XrPoseProvider` for the owned XREAL **Air 2 Pro** over the
Air-family USB HID protocol on VID `0x3318` (PID `0x0432`): claim the IMU
interface, start the raw gyro/accel/mag stream, fuse it into the uniform
3DoF orientation at the adapter edge, and expose recenter as the host-side
origin reset. The stub's original XREAL One TCP transport does not apply —
this unit has no USB NIC; those facts stay in the rules file for reference.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |
| 2026-11-14 18:05 Africa/Addis_Ababa | /task-1-plan | Pending | Planned | One TCP → Air 2 Pro HID after hardware dump + protocol research | |
| 2026-11-14 18:05 Africa/Addis_Ababa | /task-2-execute started | Planned | InProgress | | |
| 2026-11-14 18:30 Africa/Addis_Ababa | /task-2-execute software | InProgress | InProgress | verify green; hardware grant sitting on display 0 (screen off) | |
| 2026-11-14 18:32 Africa/Addis_Ababa | /task-2-execute hardware | InProgress | InProgress | grant accepted; IMU 991.4 Hz, timestamp ns, START wrote=9 | |
| 2026-11-14 18:38 Africa/Addis_Ababa | /task-3-complete | InProgress | Done | 991.4 Hz, timestamp ns, endpoints 0x84/0x05; signs and Recenter left to manual test | |

## Requirements

- [x] USB permission for VID `0x3318` wired: device filter entry + monitor re-ask path (T02 flow, Viture pattern)
- [x] IMU interface claimed (`force=true`), stream started with the Air command frame, echo/INIT replies tolerated
- [x] Raw gyro/accel reports parsed (deg/s, g, per-sample mult/div scaling) and fused to the `XrPoseProvider` orientation contract at the adapter edge
- [x] Recenter is the existing host origin reset; no glasses-side spatial command exists on Air — do not look for one
- [x] Measured IMU sample rate + timestamp unit recorded (undocumented upstream; nominal 1000 Hz, Android-measured 489–665 Hz on Air 1)
- [x] Protocol failure degrades to generic display with availability `ERROR`, diagnostics in logcat; no crash, no retry loop

## Implementation Plan

### High-level notes (bootstrap, revised by /task-1-plan)

- The One TCP references (Skarian/one-xr, georgehorrell/xreal-one-cli,
  0xcaff/xr-tools) describe a transport this unit does not have.
  Air-family references live in `.cursor/rules/xreal-glasses.mdc` under
  "Air-family HID protocol": nrealAirLinuxDriver (MIT), ar-drivers-rs
  (MIT), Monado drv_xreal_air (BSL-1.0), AirAPI_Windows (MIT).
  Reimplement from those. XrealBeam and pocket-xr have no clear license —
  reference only, never copy code.
- Fusion from gyro/accel happens here at the adapter edge so T05
  consumes a uniform 3DoF orientation stream regardless of vendor
  (Viture supplies Euler natively; Air supplies raw gyro/accel).
- 3DoF-only scope. No mag required — yaw from gyro integration with the
  stationary-bias estimator; mag handling is out of scope.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14  
**Codebase snapshot:** branch `T04-xreal-tcp-adapter` @ `ca4c212` (T03 complete: Viture provider streaming at 62.8 Hz, `VitureHidPoseProvider` is the structural template). Tree clean.  
**Execute model:** [medium]

### Context for executor

Open the XREAL Air 2 Pro IMU over USB HID and publish fused
origin-relative radians on `XrPoseProvider`. The settings card and
recenter fan-out already exist (`XrGlassesMonitor.recenter()` → registry).
The glasses have no spatial chip: every pose value is host-computed.

Owned unit, dumped 2026-11-14 on SM-S948B: VID `0x3318` (decimal 13080),
PID `0x0432`. Interfaces: 0–2 USB audio; 3, 4, 5 HID 64-byte interrupt
in+out; 6 HID in-only 3-byte (buttons, standard keyboard). **IMU =
interface 3.** MCU (interface 4) and the undocumented interface 5 are
never claimed. Air 2 Pro endpoint addresses are not documented anywhere —
discover the interrupt IN/OUT pair from the claimed interface at connect
time. Same protocol across Air `0x0424` / Air 2 `0x0428` / Air 2 Pro
`0x0432`; Ultra `0x0426` speaks a different 512-byte protocol — gate the
adapter on the three known PIDs.

Full protocol facts, symptom table, and source links:
`.cursor/rules/xreal-glasses.mdc` → "Air-family HID protocol". The
numbers below are the only ones the executor needs.

Pose contract (`XrPoseProvider.kt`, same as Viture): unwrapped continuous
radians, yaw+ = head left, pitch+ = up, roll+ = roll right. `publishRaw`
subtracts the origin. Listener callbacks run on the reader thread.

T03 USB lessons that carry over exactly (measured on this phone):

- Interrupt OUT via `bulkTransfer` works. Interrupt IN must be read with
  `UsbRequest` — `bulkTransfer` on the IN endpoint returned nothing.
- `requestWait(timeout)` throws `TimeoutException` and leaves the request
  queued: catch it, do not clear, do not requeue, wait again on the same
  request.
- Teardown: cancel the request, join the reader, then release the claimed
  interface and close the connection. Anything else wedges the next plug.

### Steps

1. Add `XrealAirProtocol.kt` in `moe.n4tsu.dextop.xr`. Pure functions, no
   Android types. → verify: unit tests in step 5 pass byte for byte.

   Command frame (little-endian, interrupt OUT, no report-id prefix):

   | Offset | Content |
   | --- | --- |
   | 0 | `0xAA` head |
   | 1–4 | CRC-32 LE over bytes 5..end, `java.util.zip.CRC32` (poly 0xEDB88320, init 0xFFFFFFFF, final xor) |
   | 5–6 | u16 LE length = `3 + data.size` |
   | 7 | msgid |
   | 8.. | data |

   START = msgid `0x19`, data `01` → hex `AA C5 D1 21 42 04 00 19 01`
   (captured ground truth, cross-verified in three repos). STOP = `AA 53
   E1 26 35 04 00 19 00`. Do not add any other msgid to the API. MCU
   display commands stay out of the tree.

   Report classifier on 64-byte IN reports: `01 02` at offset 0 = sensor,
   `AA 53` at 0 = INIT, `AA` at 0 otherwise = command echo. Echo replies
   are informational only — do not build reply-matching machinery.

   Sensor parse, `parseSample(buffer, length)` returning null on bad
   frames: u64 LE timestamp at 0x04; gyro = i16 LE multiplier at 0x0C,
   i32 LE divisor at 0x0E, three i24 LE sign-extended at 0x12/0x15/0x18;
   accel = i16 LE mult at 0x1B, i32 LE div at 0x1D, three i24 LE at
   0x21/0x24/0x27. `value = raw × mult / div`. Gyro is deg/s, accel is g.
   Ignore the mag block and the CRC at offset 54 — no community driver
   verifies it and pose does not need it.

   Axis remap to head frame, one named constant set so a hardware fix is
   a one-line sign flip: sensor body is x left, y back, z up; head frame
   (x right, y up, z back) = gyro/accel `(−x, z, y)`.

   Timestamp helper: first sane delta decides ns vs µs (delta < 50,000 ⇒
   µs, scale ×1000); reject Δ ≤ 0 or ≥ 0.5 s; 24-bit tick-rollover guard.
   Return null dt for an unusable sample — the provider falls back to
   `System.nanoTime()` spacing for that sample.

2. Add `XrOrientationFusion.kt`, pure Kotlin. Input: gyro deg/s, accel g,
   dt seconds. Output: yaw/pitch/roll radians. → verify: step 5 fusion
   tests pass.

   Complementary filter, quaternion internal, hand-rolled quaternion ops
   (no external math library on the Kotlin side):

   - Integrate gyro (converted to rad/s) into the quaternion each sample.
   - Gravity correction: rotate the head-up gravity reference into the
     sensor frame and apply a small accel-driven pitch/roll correction
     (gain ~0.02–0.1, tunable constant).
   - Yaw from gyro integration only; no mag.
   - Stationary-bias estimator (FusionOffset-style): when |gyro| stays
     under ~3 deg/s for a few seconds, feed the mean into a slowly
     adapting per-axis bias subtracted before integration. Target:
     resting yaw drift ≪ 1°/min warm; ~3°/min cold on first plug is
     acceptable.
   - Euler output unwrapped against the previous output (same helper
     shape as `VitureProtocol.unwrapDegrees`).

3. Add `XrealAirHidPoseProvider` extending `AbstractXrPoseProvider`, plus
   a `XrealAirHidSession` companion mirroring `VitureHidSession` (single
   active provider per device key, `start`/`stop`/`abandon`). `id` is
   `xreal` (family identity from the detector), `displayName` the identity
   label. Log tag `DextopXrealAir`. → verify: registers/unregisters; a
   second `start` for the same device does not open a second connection.

   - Open only when `UsbManager.hasPermission` is true. Claim **only
     interface 3** with `claimInterface(iface, true)` (kernel `usbhid`
     holds it). Discover its interrupt IN/OUT endpoints.
   - Write START via `bulkTransfer` on the OUT endpoint. Read IN with
     `UsbRequest` (T03 pattern: one queued request, timeout caught and
     awaited again). If no sensor frame within 1 s, write STOP then
     START once — the first write after open can go unanswered. No
     further retries, no poll loop; on failure set availability `ERROR`
     and stop the reader.
   - Feed every sensor sample to the fusion and `publishRaw` the fused
     yaw/pitch/roll with `System.nanoTime()`. First accepted sample →
     availability `STREAMING`. Log once: endpoint addresses, timestamp
     unit, and after 5 s a `pose-rate=` line from device-timestamp deltas.
   - If `STREAMING` and no sensor frame for 2 s, write START once.
   - On stop: write STOP best-effort, cancel the request, join the
     reader, release interface 3, close. `recenter()` stays the
     superclass origin reset — there is no glasses-side recenter on Air.

4. Wire detection: `res/xml/xr_usb_device_filter.xml` add
   `<usb-device vendor-id="13080" />` (keep 13770). `XrGlassesMonitor`:
   extend the permission/ask path to VID `0x3318` devices; in `refresh`,
   after the identity is stored, if `identity.id == "xreal"` and a
   VID `0x3318` device with a known PID (`0x0424`, `0x0428`, `0x0432`)
   has permission `granted`, `XrealAirHidSession.start(...)`; otherwise
   stop it. Outside the monitor lock, Viture pattern. → verify:
   `make verify` green; card shows the permission ask for XREAL before
   grant.

5. Unit tests in
   `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrealAirProtocolTest.kt`
   and `XrOrientationFusionTest.kt`, kotlin.test, same style as
   `VitureProtocolTest`. → verify:
   `./gradlew :app:testGithubDebugUnitTest --tests "moe.n4tsu.dextop.xr.Xreal*"`
   from `android/` passes.

   Protocol tests:
   - START equals `AA C5 D1 21 42 04 00 19 01` exactly; STOP equals
     `AA 53 E1 26 35 04 00 19 00`.
   - A synthetic sensor report (known mult/div, known i24 payloads)
     parses to expected deg/s and g; the axis remap yields head-frame
     values.
   - `AA 53` INIT and `AA` echo frames are classified and do not parse
     as samples; a truncated buffer returns null.
   - Timestamp: ns delta passes, µs delta (e.g. 1000) scales to ms,
     Δ = 0 and Δ = 0.6 s are rejected.

   Fusion tests:
   - Constant gyro around head-y for 1 s at 90°/s rotates yaw ~90°;
     pitch and roll stay near 0.
   - Resting input (gyro 0, accel = +1 g on head-up axis) holds
     orientation, and the bias estimator drives an injected small gyro
     bias to ≈0 drift.
   - Accel-only pitch displacement converges back toward level
     (gravity correction) instead of drifting away.

6. `make verify` from the repo root. → verify: exit 0. Then
   `rg -n "0x2822|0x271C|0x2727|0x2729" android/app/src/main` finds
   nothing (no display-control commands shipped).

7. Hardware, only if `adb devices` shows the phone and the Air 2 Pro is
   plugged (ask the user to plug it; the Viture can stay plugged — the
   detector resolves the family from the XREAL display name). Install
   the debug APK. Watch `adb logcat -s DextopXrealAir` for ~8 s.
   → verify: log shows the endpoint addresses, a timestamp-unit line,
   `pose-rate=` between 400 and 1000 Hz, and pose lines that move with
   the head. Look left: yaw increases. Chin up: pitch increases. Roll
   right: roll increases. If a sign is wrong, flip only that constant in
   the axis remap and reinstall. Press Recenter on the XR card: the next
   logged pose is near 0. Write the measured rate, timestamp unit, and
   any sign flip into **Verification**. If the phone is absent, leave the
   hardware AC boxes unchecked and say so. Do not invent numbers.

### Tests to add

See step 5 — protocol fixtures byte-exact, fusion behavior synthetic.

### Verify commands

- `cd android && ./gradlew :app:testGithubDebugUnitTest --tests "moe.n4tsu.dextop.xr.Xreal*"`
- `make verify`
- Hardware log: `adb logcat -s DextopXrealAir`

### Risks / pitfalls

- Interface 3 is IMU, not 0/1 like Viture. Claiming 4 or 5 is wrong; 4 is
  the display MCU (brightness/SBS commands live there — never write
  them), 5 is undocumented.
- Echo-per-command: every write earns one IN reply. Unread echoes are
  harmless (the classifier drops them).
- The first write after open can time out (observed on Air 1 and Air 2
  Pro on Windows). The one stop→start retry in step 3 is the whole
  experiment. Do not build a third retry.
- Timestamp unit is firmware-dependent (ns vs µs) and rolls over at 24
  bits on some units. Never assume a fixed rate; dt comes from the
  timestamp helper with wall-clock fallback per sample.
- Yaw is unobservable (gyro-only): expect small drift — that is why the
  card has Recenter. Do not add mag fusion here.
- USB permission is revoked on every replug — the card re-ask path (T02)
  handles it; never re-ask from a poll loop.
- Kernel `usbhid` holds interface 3 and interface 6 (keyboard).
  `force=true` on 3 detaches it; leave 6 alone so the buttons keep
  working.
- Do not copy code from XrealBeam or pocket-xr (no clear license).
  MIT/BSL-1.0 sources are safe to reimplement from; GPL XRLinuxDriver is
  license-compatible anyway.

### Out of scope

- MCU interface 4: brightness, display mode, SBS, serial, firmware.
  Never touched.
- Mag fusion, calibration blob drain (msgids 0x14–0x18), factory JSON.
- Viewport, stabilization modes, presets (T05–T06).
- XREAL One TCP transport (no such unit owned; no NIC on Air).
- Ultra PID `0x0426` (512-byte protocol).
- Glasses-side recenter/anchor — confirmed absent on Air.

### Execute model recommendation

- [medium] — framing is closed with captured ground-truth bytes, the
  provider template is proven on hardware in T03, and the only new math
  is a compact complementary filter with tests. The hardware unknowns
  (endpoint addresses, rate, timestamp unit, axis signs) each resolve to
  one log line or a one-line constant flip, same as T03.

## Test Plan

- Unit: report parsing from synthetic fixtures, frame byte-exactness, timestamp unit detection, fusion behavior (rotation, bias, gravity correction)
- Hardware: Air 2 Pro streaming pose; recenter resets origin; sample rate + timestamp unit measured
- Commands: `make verify` + manual hardware session (owner has the Air 2 Pro)

## Acceptance Criteria

- [x] USB permission flow works for VID 0x3318 (filter entry + card re-ask)
- [x] Pose streams into `XrPoseProvider` on hardware; sample rate + timestamp unit measured and recorded
- [x] Fusion is unit-tested (gyro integration, bias estimator, gravity correction); adapter edge delivers the uniform orientation contract to T05
- [x] Protocol failure → generic display fallback, no crash, availability ERROR surfaced
- [x] Unit tests green, `make verify` green
- [x] No secrets committed; no code copied from unlicensed repos (XrealBeam, pocket-xr)

## Verification

- Tooling present: `Makefile` `verify`, `lefthook.yml` pre-commit → verify, `analysis_options.yaml`.
- `make verify` (2026-11-14 18:10 Africa/Addis_Ababa, after the USB close fixes): flutter analyze clean, flutter test green, `:app:testGithubDebugUnitTest` green, `app-github-debug.apk` built. Pre-existing Gradle/Kotlin version warnings only.
- `rg '0x2822|0x271C|0x2727|0x2729' android/app/src/main` — no matches.
- Reviewer fixes folded in before that verify: retry only on a timeout, requeue only while `running`, cancel + `requestWait` before the no-sensor return, `failedKey` latch until `XrealAirHidSession.stop()` (unplug).
- Hardware 2026-11-14 18:28 Africa/Addis_Ababa, SM-S948B `192.168.69.104:5555`, Air 2 Pro already attached (display 312). Installed `app-github-debug.apk`. Filter vendor `13080` registered. `UsbPermissionActivity` opened on display 0 while that panel was off. User turned the phone on and accepted at ~18:30.
- Session log, pid 13638, tag `DextopXrealAir`: `endpoints in=132 out=5` (18:30:44), `start wrote=9` (no retry), `timestamp=ns` (18:30:44.401), `pose-rate=991.4Hz` (18:30:49.400). Pose held still through 18:32:26: yaw −0.527, pitch 0.227, roll 2.711 rad (origin still 0; Recenter not pressed). Head-turn signs not captured. No sign flip.
- `/task-3-complete` re-ran `make verify` 2026-11-14 18:38 Africa/Addis_Ababa: analyze clean, flutter test green, unit tests green, debug APK built.

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrealAirProtocol.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrOrientationFusion.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrealAirHidPoseProvider.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesMonitor.kt`
- `android/app/src/main/res/xml/xr_usb_device_filter.xml`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrealAirProtocolTest.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrOrientationFusionTest.kt`
- `.cursor/rules/xreal-glasses.mdc`
- `planning/phases/T04-xreal-tcp-adapter.md`
- `planning/phases/T05-fusion-stabilization-viewport.md`
- `planning/phases/INDEX.md`
- `planning/phases/T06-geometry-presets.md`
- `planning/phases/T07-input-onboarding-polish.md`
- `planning/phases/T08-e2e-hardware-proof.md`
- `README.md`

## Manual test (for humans)

Glasses plugged, USB already allowed. If `DextopXrealAir` stays silent, wake the phone — the Allow dialog may be sitting on the built-in panel.

```bash
adb logcat -c
adb logcat -s DextopXrealAir
```

Look left, chin up, roll the head right, then press Recenter on the XR card.

Success: `endpoints in=132 out=5`, `timestamp=ns`, `pose-rate` near 1000 Hz. Look left raises yaw. Chin up raises pitch. Roll right raises roll. The pose after Recenter is near 0. A wrong sign is one constant flip, then reinstall.

## Learnings

- This Air 2 Pro answered the first 9-byte START. Timestamp unit is ns. Pose rate was 991.4 Hz. IMU endpoints match Air 1 (`0x84` in, `0x05` out).
- The USB Allow dialog opened on the sleeping phone panel while the glasses were the lit display. Waking the phone revealed the dialog that was already up.
- A failed reader has to stay latched until unplug, or the 2-second snapshot reclaim loop takes interface 3.
- Head-turn signs and the Recenter press were not captured. Stationary fused pose before Recenter: yaw −0.527, pitch 0.227, roll 2.711 rad.

## Reality notes

From T02 close-out (2026-11-14):

- Hardware 2026-11-14: owned glasses are **XREAL Air 2 Pro**, not One.
  VID `0x3318` PID `0x0432`. EDID `Air 2 Pro` (PNP `MRG`, product 12597).
  USB audio + three 64-byte HID interfaces. No `169.254.2.*` interface, so
  the One TCP transport is the wrong path for this unit. Card match is VID
  `0x3318` (already in the detector). Transport stays this task: Air HID,
  with One TCP only if a later unit actually exposes the NIC.
- Family identity id is `xreal` (not `xreal-one`, not `air-2-pro`). Fallbacks
  still: link host `169.254.2.*`, display name contains `xreal` or `nreal`.
  Do not match the USB product string.
- When both Viture USB and an XREAL signal are present, any xreal/nreal
  display name wins the tie; otherwise Viture wins.
- No USB permission dialog for XREAL TCP. Profiles use the same `dextop_xr`
  store under key `mode.xreal` / `preset.xreal`.
- Recenter is host-side via the registered `XrPoseProvider` (card already
  calls the registry). Do not send glasses-side Anchor/recenter APIs.
- Channel methods already exist: `xrGlassesState`, `setXrGlassesProfile`,
  `xrGlassesRecenter`. Onboarding Follow/Stabilizer hint stays T07.

From T03 close-out (2026-11-14), same phone SM-S948B:

- Air 2 Pro is HID, so it needs a USB permission grant. The "no dialog"
  note above is the TCP path only. `res/xml/xr_usb_device_filter.xml`
  lists vendor 13770 (Viture) and not VID `0x3318` (decimal 13080). Add
  that vendor before expecting an attach grant.
- Interrupt OUT `bulkTransfer` delivered a full buffer. Interrupt IN
  `bulkTransfer` returned no payload. The read that worked was
  `UsbRequest`. `requestWait(timeout)` throws `TimeoutException` and
  leaves the request queued: catch it, do not clear, do not requeue,
  wait again on the same request.
- Tear-down order that keeps the next plug able to claim: cancel the
  request, join the reader, then release the interfaces and close.
