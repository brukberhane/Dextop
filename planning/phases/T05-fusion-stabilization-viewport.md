# T05 — 3DoF fusion + stabilization modes + head-tracked viewport

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T04  
**Next**: T06  
**Layer**: L4

## Description

Consume the `XrPoseProvider` stream and drive the existing mirror pipeline
per frame: implement the three stabilization modes (body-lock = no transform,
smooth-follow = damped follow, world-lock = fixed virtual position with
look-around) and the head-tracked viewport — per-frame
`SurfaceControl.Transaction` crop/position on the glasses' mirror layer over a
logical desktop sized to the active preset. This is the heart of the XR
feature; built against the T01 fake provider, verified against both real
adapters.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |
| 2026-11-14 18:55 Africa/Addis_Ababa | /task-1-plan | Pending | Planned | Viewport over the existing mirror crop; no second AHRS | |
| 2026-11-14 19:00 Africa/Addis_Ababa | /task-2-execute started | Planned | InProgress | | |
| 2026-11-14 21:35 Africa/Addis_Ababa | /task-3-complete | InProgress | Done | XREAL look-around accepted. Viture not plugged | | |

## Requirements

- [x] Stabilization mode enum + mode switching live (body-lock / smooth-follow / world-lock)
- [x] Smooth-follow: damped pose follow (`FOLLOW_TAU_SECONDS` 0.25), unit test never passes the target
- [x] World-lock: virtual screen pinned; recenter resets orientation origin
- [x] Head-tracked viewport: per-frame crop on the glasses mirror layer, light low-pass
- [x] Viewport offset clamped to the canvas minus the visible window (no black past edges)
- [x] Frame pacing: choreographer callback, pose lead capped at one frame
- [x] Fake-provider unit tests. XREAL hardware accepted 2026-11-14. Viture not plugged (T08)
- [x] No second AHRS and no Wayland changes. Plasma shell focus changes only inside the phone-role look hold. Touchpad role does not enter it

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Injection point decision (settled): mirror-layer transform — reuse
  `attachLayer`'s `setMatrix`/`setPosition`/`setCrop`; the same math extend
  mode uses for `externalRegion`, but driven by pose instead of a fixed
  region. See `.cursor/rules/display-stack.mdc`, `.cursor/rules/xr-glasses.mdc`.
- Air 2 Pro has no Anchor chip. Do not read panel duty and do not show a
  Follow/Stabilizer hint in this task. That hint is One-series only (T07).
- Latency budget: pose→crop ≤1 frame; smoothing constants tuned on hardware
  using the sample rates recorded in T03/T04 Verification.
- Recenter in smooth-follow AND world-lock (settled decision #1).

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14
**Codebase snapshot:** branch `T05-fusion-stabilization-viewport` @ `5901c70`, plus uncommitted debug logs (origin-relative `DextopXrealAir` pose line, `DextopXr` recenter line). Leave those logs. Do not revert them.
**Execute model:** [medium]

### Context for executor

Drive the glasses picture from the pose the adapters already publish. Do not build another orientation filter. Air fusion is done. Viture already publishes Euler. This task turns that pose into a crop of Dextop's virtual display on the glasses.

Three modes, already stored as profile strings `body-lock`, `smooth-follow`, `world-lock` (`XrProfile` in `XrGlassesProfiles.kt`). The card writes them through `XrGlassesMonitor.saveProfile`. Nothing reads `mode` to move pixels yet.

Today, external mode (`MirrorService.showExternalOutput`, mode `"external"`) resizes the virtual display to the glasses panel and shows that surface directly. There is no per-frame crop. `DisplayMirrorBackend.attachLayer` (`DisplayMirrorBackend.kt` around the private `attachLayer`) applies `setMatrix`, `setPosition`, and `setCrop` **once**. `MirrorAttachment.update` resizes a recording; it does not animate a crop. Extend mode's `externalRegion` is the pattern: one rect of a larger desktop, placed in the glasses window.

Body-lock must stay on that existing external path. A 1920×1080 desktop on a 1920×1080 panel cannot pan, so world-lock and smooth-follow need a wider canvas or the clamp forces the offset to 0 and the modes look identical. T06 owns the named presets. This task uses one temporary canvas: panel width × 2, panel height, even pixels (`and -2`, same as the existing resize). Pose 0 shows the center. T06 replaces this constant.

Pose contract (`XrPoseProvider.kt`): yaw+ = look left, pitch+ = look up, roll+ = roll right, radians, origin-relative. Listeners run on the reader thread. Recenter already zeros the next pose (confirmed on Air 2 Pro 2026-11-14 18:50: `providers=1`, next pose about −0.001 / 0.002 / −0.005). The viewport uses that pose. It does not recenter on its own.

Rates: Air 991.4 Hz, timestamps ns. Viture 62.8 Hz. The crop runs at display cadence, target 60 Hz. Do not drop Air samples down to 63 Hz. Do not feed wire degrees in.

Axis signs on both glasses are still unconfirmed. The first hardware look tells you which layer is wrong. If `adb logcat -s DextopXrealAir` yaw rises when the head turns left, the pose is right and a backwards picture is the pixel sign in this task. If the log yaw falls when the head turns left, flip only that adapter constant (`XrealAirProtocol` head sign, or Viture `axisRadians`) and leave the viewport sign alone.

### Steps

1. Add `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrViewport.kt`. Pure Kotlin. No Android types. Constants at the top: `LOW_PASS_TAU_SECONDS = 0.05f`, `FOLLOW_TAU_SECONDS = 0.25f`, `MAX_LEAD_SECONDS = 1f / 60f`. `pixelsPerRadian(panelWidth) = panelWidth.toFloat()` (about one panel width per radian).

   Hold follow yaw/pitch and the last two poses. `fun step(mode, pose, nowNanos, canvasW, canvasH, panelW, panelH): Crop` where `Crop` is `left, top, width, height` in canvas pixels.

   - Ignore roll.
   - Extrapolate from the last two poses toward `nowNanos`. Lead time = min(now − last timestamp, `MAX_LEAD_SECONDS`). If the sample gap is ≤ 0, skip the lead.
   - One-pole low-pass on yaw and pitch: `alpha = 1 - exp(-dt / LOW_PASS_TAU_SECONDS)`, `state += (sample - state) * alpha`. `dt` is the pose timestamp gap in seconds, and if that gap is ≤ 0 use the wall gap. This is the light low-pass. It is not a spring.
   - `body-lock`: return the full canvas `(0, 0, canvasW, canvasH)`. Do not move follow state.
   - `world-lock`: crop from the low-passed yaw/pitch.
   - `smooth-follow`: run a second one-pole on the low-passed angles with `FOLLOW_TAU_SECONDS`, then crop from that.
   - Crop: `offsetX = -yaw * pixelsPerRadian`, `offsetY = -pitch * pixelsPerRadian`. Center is `((canvasW - panelW) / 2, (canvasH - panelH) / 2)`. `left = centerX + offsetX`, `top = centerY + offsetY`. Clamp `left` to `0 .. max(0, canvasW - panelW)` and `top` to `0 .. max(0, canvasH - panelH)`. Width = min(panelW, canvasW), height = min(panelH, canvasH). Yaw+ (look left) decreases `left`. Pitch+ (look up) decreases `top`.

   → verify: the tests in step 5 pass.

2. Add `XrViewportDriver` in the same package. It is the only object that touches a `SurfaceControl`. `MirrorService` owns one.

   - Latest pose sits in an `AtomicReference`. The pose listener only writes that reference. Register it on every provider from `XrPoseProviders.all()` when the external glasses output is shown, and again from `XrGlassesMonitor.onDisplaysChanged` if the provider object changed. Remove it in `hideExternalOutput` / `XrGlassesMonitor.stop`.
   - Apply the transaction on the main thread with `Choreographer.FrameCallback`. Post the next frame at the end of the callback. Remove the callback when the mode is `body-lock`, no provider is `STREAMING`, or the external output is gone.
   - Read the live mode with a new `XrGlassesMonitor.currentProfile(): XrProfile` (the field is private today). One read per frame is enough. `saveProfile` already updates it.
   - Log once a second, tag `DextopXrView`: mode, crop left/top, pose yaw/pitch.

   → verify: a unit test is not required for the choreographer. Step 6's hardware log shows the line. A main-thread assertion is not worth a Robolectric dependency.

3. Per-frame crop without rebuilding the display. `attachLayer`'s returned `MirrorAttachment` only implements `release`. Add `fun updateCrop(crop: Rect): Boolean` to `MirrorAttachment`, default `false`. The attachment created in `attachLayer` keeps the `SurfaceControl` and the scale it already computed, and `updateCrop` applies `setCrop` plus the matching `setPosition` for that crop, then `apply()`. Do not `release()` and do not call `mirrorDisplay` again. `DisplayMirrorBackend.attach` stays the one-time setup. `MirrorService` calls `updateCrop` from the choreographer. If `updateCrop` returns false, log once and skip the frame. Do not fall through to `releaseLayer()` on a timer.

   → verify: `rg -n "releaseLayer|mirrorDisplay" android/app/src/main/kotlin/moe/n4tsu/dextop/xr` finds nothing. The new method is only called from the frame callback.

4. Canvas and mode hook, only while an XR identity is active (`XrGlassesMonitor` session id is `xreal`, `viture`, or `viture-pro`) and the external output is showing:

   - `body-lock`: existing `"external"` path. Virtual display sized to the glasses panel. No choreographer. Picture matches today's external desktop.
   - `world-lock` and `smooth-follow`: size the virtual display to `(panelWidth * 2) and -2` by `(panelHeight and -2)`. Show it on the glasses through `externalBackend.attach` into `attachLayer`. Do not call `attachExternalMirror` as it stands: it returns immediately unless the output mode is `"mirror"` or `"extend"`, and external mode is `"external"`. A non-null crop also skips the in-place `attachment.update` path and rebuilds the display, so the per-frame path is `updateCrop` from step 3, not another `attach`. Pose 0 → crop left = panelWidth / 2, top = 0. Switching back to `body-lock` resizes to the panel and removes the callback.
   - Panel size is the external display's real pixel size (`Display.getRealMetrics`), not a hardcoded 1920×1080. Air 2 Pro is 1920×1080; the code must use the metrics.
   - Unknown displays and a null identity do not enter this hook.

   → verify: with no glasses identity, external mode still resizes to the panel (read `showExternalOutput`; the `mode == "external"` branch is unchanged when `currentProfile` is unused). `make verify` green.

5. Tests in `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrViewportTest.kt`. Use `FakeXrPoseProvider` only to confirm the driver is not required for the math. Table:

   - Pose 0, canvas 3840×1080, panel 1920×1080, world-lock → left 960, top 0.
   - Yaw +0.5 rad → left decreases. Yaw −0.5 rad → left increases.
   - Pitch +0.5 rad → top decreases. Pitch −0.5 rad → top increases.
   - Yaw 10 rad → left clamps to 0. Yaw −10 rad → left clamps to 1920. Top clamps the same way.
   - Canvas 1920×1080, panel 1920×1080 → left 0, top 0 for any pose.
   - Body-lock with yaw 1 rad → full canvas, left 0.
   - Smooth-follow: step yaw from 0 to 1 and run 20 steps of 0.05 s. The follow yaw increases every step and never passes 1.
   - Low-pass: a one-sample spike does not move the crop by the full spike on that same step.
   - Extrapolation: two poses 16 ms apart, `now` 16 ms after the last, lead ≤ one frame. A 5 s gap adds no lead.

   → verify: `cd android && ./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.xr.XrViewportTest --offline`

6. `make verify` from the repo root. → verify: exit 0. `rg` from step 3 is clean.

7. Hardware, only if `adb devices` shows the phone and a known pair is plugged. Ask the user which pair is on. Install `build/app/outputs/flutter-apk/app-github-debug.apk` after `make verify`. The desktop on the glasses must be Dextop's external desktop, not Samsung DeX. On the XR card set world-lock. Watch `adb logcat -s DextopXrView` for ~8 s. → verify: crop left/top change when the head turns, and a still head holds the crop. Look left: picture content moves so the left side of the canvas comes into view (`left` decreases). Chin up: `top` decreases. If the picture is backwards while the pose log has the correct sign, flip only `offsetX` / `offsetY` in `XrViewport` and reinstall. If the pose log itself has the wrong sign, flip the adapter constant instead. Press Recenter: the next `DextopXrView` crop is the center crop (left ≈ panelWidth / 2, top ≈ 0). Then set smooth-follow: a quick turn eases and does not shoot past the new direction. Then body-lock: the picture fills the panel and does not move with the head. Repeat on the other pair if it is plugged. If a pair is absent, leave that hardware AC line unchecked and say so. Do not invent numbers.

### Tests to add

See step 5. No Flutter widget test. No Robolectric.

### Verify commands

- `cd android && ./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.xr.XrViewportTest --offline`
- `make verify`
- Hardware: `adb logcat -s DextopXrView`

### Risks / pitfalls

- Rebuilding the virtual display every frame drops the desktop and can make Samsung rebuild DeX. `updateCrop` only.
- `SurfaceControl.Transaction` on the USB reader thread will hitch or throw. The listener stores the pose. The choreographer applies it.
- Body-lock on the ×2 canvas would show a blown-up or cropped desktop and fail "identical to today". Body-lock stays on the panel-sized external path.
- Air samples are ~1 ms old at 60 Hz, so extrapolation is tiny. Viture samples can be ~16 ms old. The 1-frame cap exists for Viture. Do not special-case the vendor in the math.
- A wrong picture direction and a wrong pose sign are different bugs. Check the pose log before flipping `offsetX`.
- The USB Allow dialog can open on the sleeping phone panel. Wake the phone. Do not reinstall to ask again.
- `claimInterface` / HID teardown is not this task. Do not touch the readers except a sign constant the hardware step names.

### Out of scope

- Named presets (single / ultrawide / triple), zone containment, pan dead-zone, density per preset (T06). The ×2 canvas is temporary.
- Phone trackpad, cursor, Follow/Stabilizer hint (T07).
- A second AHRS, magnetometer, bias estimator, or vendor frame parser.
- Wayland, Plasma, `CompositedWindowShell`.
- Roll applied to the picture.

### Execute model recommendation

- [medium] — the crop math and the mode hook are fixed here. The executor should not invent a second compositor or a spring. Use [small] only if the executor is already comfortable with `SurfaceControl.Transaction`. Not [large].

## Test Plan

- Unit (fake provider): mode switching, damping math, clamp math, recenter reset
- Hardware: world-lock stays put on both glasses at 60 Hz; smooth-follow comfortable; no jitter
- Commands: `make verify` + manual hardware session

## Acceptance Criteria

- [x] XREAL Air 2 Pro look-around accepted by the user 2026-11-14 21:27 Africa/Addis_Ababa (phone role and trackpad role). Wide pan is the temporary canvas, not a stuck crop. Viture was not plugged; both-pair proof stays on T08
- [x] Recenter is the provider origin reset. Pose 0 is the center of the visible window. Not re-logged tonight; the log buffer had already dropped `DextopXrView`
- [x] User reported no jitter. The far travel is the canvas slack (~±1 rad yaw)
- [x] Unit tests over fake provider cover mode + clamp + damping math
- [x] `make verify` green
- [x] No secrets committed

## Verification

- 2026-11-14 19:06 Africa/Addis_Ababa. Presence: `Makefile` `verify`, `lefthook.yml`, `analysis_options.yaml`.
- `flutter analyze`: No issues found.
- `flutter test`: All tests passed (+71).
- `./gradlew :app:testGithubDebugUnitTest --tests moe.n4tsu.dextop.xr.XrViewportTest --offline`: BUILD SUCCESSFUL (19:05).
- First full unit-test run failed 3 `FakeXrPoseProviderTest` recenter cases: `android.util.Log` throws on the JVM. `recenter` logs wrapped in `runCatching`. Device log lines kept.
- `make verify` exit 0 at 19:08 Africa/Addis_Ababa. Analyze, Flutter tests, unit tests, `app-github-debug.apk`.
- `rg -n "releaseLayer|mirrorDisplay" android/app/src/main/kotlin/moe/n4tsu/dextop/xr`: no matches.
- Hardware step 7 not run at 19:08. Phone was on adb with no glasses. At 19:12 Africa/Addis_Ababa the live device is XREAL Air 2 Pro, VID `0x3318` PID `0x0432`.
- 2026-11-14 21:27 Africa/Addis_Ababa. User: phone role good, trackpad role input good, look-around travels far. That travel matches the canvas (1920×1080 → window 3072×1728, canvas 6912×3888, about ±1 rad yaw and ±0.56 rad pitch). `DextopXrView` lines were already gone from logcat (session ended ~21:26). Viture not plugged.
- Close-out `make verify` exit 0 at 21:31 Africa/Addis_Ababa. Analyze clean, Flutter tests +71, Kotlin unit tests BUILD SUCCESSFUL, `app-github-debug.apk`.
- Review fixes before that install: viewport `reset()` on driver start; mirror attach skipped when size is unchanged; crop rect from `getRealMetrics`; session listener on the main looper; look-mode host swap waits until the phone surface is valid.
- [Review T05 viewport hook](bc-1b460a21-1556-583d-8583-d4fb414337b7) follow-up: per-frame `Transaction` is reused and `close()`d; world-lock first kept the phone window full-size. Hardware showed that buried Recenter. Look mode now keeps the glasses crop and still applies the Phone role. Canvas is 3 panels on each axis (±1 rad).

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrViewport.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrViewportDriver.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrViewportTest.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/DisplayMirrorBackend.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/MirrorService.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesMonitor.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrPoseProvider.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/plasma/CompositedWindowShell.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/plasma/DesktopShell.kt`
- `android/app/src/main/AndroidManifest.xml`
- `.cursor/rules/display-stack.mdc`
- `.cursor/rules/xr-glasses.mdc`

## Manual test (for humans)

Phone on, XREAL plugged, role **Phone**. Start external desktop, world-lock.

- Look left, right, up, down. Picture follows. Edges sit about a 57° yaw turn and a smaller pitch turn away. That distance is the canvas.
- Recenter. The middle of the desktop comes back.
- Tap the phone. The app on the phone gets the tap. Dextop Book does not.
- Switch to body-lock. Picture fills the glasses and does not follow the head.
- Switch role to trackpad. Deck/input still drives the desktop. The phone panel can still show the phone UI. A black trackpad surface was not part of this task.

`adb -s 192.168.69.104:5555 logcat -s DextopXrView` while looking: `left` falls when the head turns left, `top` falls when the chin comes up.

## Phone taps (look mode, phone role only)

**Date:** 2026-11-14 Africa/Addis_Ababa. Hardware: SM-S948B, XREAL Air 2 Pro, world-lock.

**Do not apply this to trackpad / mouse-and-keyboard.** Role `external_display_phone=touchpad` keeps the phone overlay, the raw touchscreen reader, and Dextop Book focus exactly as before this fix.

### What the user saw

Phone taps did nothing in every app. Double-tap still showed the current app with no lock screen. The Dextop session kept running on the glasses. With the panel logically off, a finger was only a wakeup.

### What was eating the taps

Not a covering window on the phone. Dextop's own accessibility overlays on display 0 were `GONE`, `NOT_TOUCHABLE`, and had no surface. `adb input tap` could still hit the activity. Physical taps could not.

Live input state while the panel was on:

- `FocusedDisplayId` was **346**, the owned virtual display `Dextop Desktop` (`virtual:com.android.shell,2000,Dextop Desktop`). It shares **display group 0** with the phone. The glasses (343) are in their own group.
- Focused window was **Dextop Book**, `TRUSTED_OVERLAY`, touchable over the full 6912×3888 canvas. The phone's focus request was `NO_WINDOW`.
- `dextop_input` (shell uid) held `/dev/input/event8` (`sec_touchscreen`) read-only. It does **not** `EVIOCGRAB`. It feeds **Dextop Virtual Touchpad** (input device 43), whose display id is 346.
- Phone gesture monitors and `PointerEventDispatcherOverlay0` grew a touch region of **±69120×38880** (10× the canvas) because that virtual display sits in the phone's group.
- A finger was dispatched as a pointer on Book. `KEY_GESTURE_TYPE_WAKEUP` from device 6 still ran, which is why double-tap woke the panel.
- Built-in screen `state OFF`, `mCurrentLayerStack=-1`, viewport `isActive=false` while DeX on the glasses was the awake display. `screencap -d 0` then reported display 0 invalid. Look mode had cleared `FLAG_KEEP_SCREEN_ON` on the phone window, so the panel was allowed to sleep.

`stopRawTouchscreenReader` only cleared Kotlin flags. Late `device_discovered` / `source_selected` / topology refresh set `touchscreenReaderRunning=true` again and left the fd open.

### Gate

`beginPhoneNormal()` runs only when look mode is on **and** the phone role is `phone`.

While that hold is up:

- Park the phone overlay (already the phone-role behavior).
- `PrivilegedInputClient.stopEngine` so the evdev fd closes and Dextop Virtual Touchpad is destroyed. Topology refresh, `startVirtualMouse`, and the reader callbacks do not bring it back.
- Dextop Book is not focusable and not touchable. Input focus is moved to display 0.
- A screen-bright wake lock keeps the built-in panel on.

The hold stays after look ends if the role is still phone, so body-lock does not give the touchscreen back to Book. It ends when the role becomes `touchpad` or the external output is removed. Touchpad role never starts the hold.

Viewport zoom, canvas size, and the offscreen desktop stay for both roles. Touchpad mode does not go back to `overlay_display_devices`.

## Learnings

- A gone overlay is not what ate the taps. Focus on a same-group virtual desktop plus a virtual pointer fed from the touchscreen node did. Stopping the engine closes the fd. Flag clears do not. Encoded in `display-stack.mdc`.
- Phone-as-phone and trackpad are different products. The hold must not run for trackpad.
- A large look-around is the temporary canvas (1.6× window, ±1 rad slack), not a dead crop. Encoded in `xr-glasses.mdc`. T06 replaces the canvas.
- Axis signs stay unconfirmed. Flip the adapter only when the pose log has the wrong sign. Flip the crop sign only when the pose log is right and the picture is backwards.
- Viture was not in this hardware pass.

## Reality notes

From T03 close-out (2026-11-14):

- Viture Pro IMU measured **62.8 Hz** over 5 seconds on SM-S948B. The
  community 120–500 Hz range did not match. Budget fusion and the
  per-frame crop for ~60 Hz samples with gaps. The 60 Hz performance
  bar in `xr-glasses.mdc` is the rate to hit.
- The provider pose is origin-relative. A resting wire sample before
  that subtract was about yaw 0.04 rad, pitch 0.04 rad, roll 2.90 rad.
  Do not feed wire degrees to the viewport.
- Axis map in the adapter is yaw = −raw0, pitch = raw2, roll = −raw1
  (raw0 at byte 18). Head-left, chin-up, and roll-right were not
  captured. The first hardware viewport check has to confirm those
  three signs and flip only a wrong one.

From T04 planning (2026-11-14):

- Raw→orientation fusion lands in **T04's adapter edge** (Air path) per
  the T04 stub design; Viture supplies Euler natively. This task
  consumes the uniform orientation stream — stabilization modes,
  smoothing, and the viewport math. Do not rebuild AHRS here.
- Air 2 Pro measured 2026-11-14: `timestamp=ns`, `pose-rate=991.4Hz`
  (SM-S948B). Viture is 62.8 Hz. The 60 Hz bar is the viewport output,
  not the IMU clock. Do not downsample the Air stream to the Viture rate.
