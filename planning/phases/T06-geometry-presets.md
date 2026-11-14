# T06 — Geometry presets + pan + recenter

**Status**: InProgress  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T05  
**Next**: T07  
**Layer**: L6

## Description

Implement the virtual display geometry presets over T05's viewport: Single
1920×1080 (fits FOV), Ultrawide 3840×1080 (world-locked, pan by head), and
Triple monitor — three discrete 1920×1080 zones in a 5760×1080 logical
desktop, shell-enforced zone containment with visible gaps and farther
apparent distance. Presets resize the logical desktop via the existing
`ResolutionRepository`/`requestDisplay` mechanism.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |
| 2026-11-14 21:40 Africa/Addis_Ababa | /task-1-plan | Pending | Planned | Zone containment = reuse of shell `outputs`; triple ships. Escalation rule did not fire |
| 2026-11-14 21:47 Africa/Addis_Ababa | /task-1-plan revision | Planned | Planned | User: rename triple→ultrawide, ultrawide→wide; no black bars ever; advanced collapsed single-upscale section |
| 2026-11-14 21:50 Africa/Addis_Ababa | /task-2-execute started | Planned | InProgress | Wide is 2 zones |
| 2026-11-14 22:40 Africa/Addis_Ababa | hardware follow-up | InProgress | InProgress | World-lock uses the full pose canvas. Body-lock and smooth-follow stay single, no pan. Wide and ultrawide are world-lock only | |

## Requirements

- [x] Preset enum: single / wide / ultrawide; switching resizes the logical desktop in place (owned display `resizeActiveDisplay`, not the overlay `requestDisplay` path)
- [x] Single: panel-sized, all three stabilization modes available; **advanced collapsed section upscales the render (1080p/1440p/2160p steps) at 16:9, same viewport, no panning, same apparent size (density scales with the step)**
- [x] Wide: `2·panel + gap × panel` with two shell-enforced panel-sized zones, head pan clamped to canvas bounds, no black bars
- [x] Ultrawide (renamed from "triple"): `3·panel + 2·48px gaps × panel` with three shell-enforced panel-sized zones — windows clamped/snapped within their zone, maximize fills the zone, black gap strips visible while panning — no black bars
- [x] Head-pan math: yaw/pitch → viewport offset, clamped, per-axis dead-zone-free
- [ ] Recenter snaps viewport to canvas/zone center (existing origin reset; hardware not re-logged this run)
- [x] **Escalation rule (resolved 2026-11-14):** zone containment is reuse of the shell `outputs` mechanism — not invasive. Triple SHIPS, renamed **ultrawide**, at native scale (user: no black bars ever)
- [x] Density/scale per preset sane on glasses (no ~30 dpi default; `density·k` clamped ≤ 640; base density snapshotted so a later step does not multiply again)

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Logical desktop sizes map to existing overlay/`requestDisplay` flows
  (`.cursor/rules/display-stack.mdc` invariant 5). Physical glasses mode stays
  within `supportedModes`.
- Triple-monitor zone containment touches `CompositedWindowShell` window
  placement — scope it in `/task-1-plan` FIRST and apply the escalation rule
  there if the blast radius is wrong (that is a plan-time decision point,
  recorded in the plan and reported to the user).
- Ultrawide 3840×1080 matches what both glasses advertise for UW mode;
  single 1920×1080 is the DP default.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-11-14
**Codebase snapshot:** branch `T06-geometry-presets` @ `9ac5961` (T05 close-out commit), clean tree.
**Execute model:** [medium]

### Reality check (what the stub assumed vs what is)

1. **The stub's "presets resize via `ResolutionRepository`/`requestDisplay`" is wrong for look mode.** Those are the overlay-display path (`DisplayMirrorBackend.kt:79`). Look mode's desktop is the owned virtual display created by `createXrOffscreenDisplay` (`MirrorService.kt:12517`) and it resizes **in place** via `resizeActiveDisplay` → `ManagedVirtualDisplay.update` → `resizeVirtualDisplay` (`MirrorService.kt:13688`, `DisplayMirrorBackend.kt:792`). No recreate, no `requestDisplay`. Presets therefore plug into `resizeActiveDisplay` with a preset-derived `Config`, exactly the way mode switching already does (`MirrorService.kt:9870-9876`).
2. **Preset plumbing already exists end-to-end.** `XrProfile(mode, preset)` with `presets = single/ultrawide/triple` (`XrGlassesProfiles.kt:5-9`), persisted per-glasses-id under prefs `"dextop_xr"` keys `"preset.$id"`. Channel `setXrGlassesProfile` already passes preset (`MainActivity.kt:739-747`). The Flutter card **already renders the geometry picker** with all three options (`lib/xr_glasses_card.dart:111-127`) and `xrGlassesState` already returns it. Tests cover the round-trip. **Nothing consumes `preset` for geometry yet** — that is this task.
3. **Preset-only changes don't propagate.** `syncXrLook` (`MirrorService.kt:9854`) early-returns when `look == xrLookOn` (`9858-9861`), and `xrCanvasSize()` (`9809-9811`) is panel-only. A preset flip mid-look must recompute canvas + window and re-attach (mirror attach already skips when size is unchanged — T05).
4. **Zone containment is reuse, not new work.** `CompositedWindowShell` already has `outputs: List<Rect>` with per-output clamping, work areas, tiling, maximize, and snap suppressed at seams (`CompositedWindowShell.kt:2748-2833`, drag `3024-3090`). `setOutputs` has **no two-screen limit** — extend mode just happens to feed 2. Three zones = feed 3 rects. **The escalation rule does not fire; triple ships.** One real fix: `clampFrame`'s horizontal keep uses the global display width (`2829`) — a drag could park a window's keep-slack into a gap; it must use the containing output's width.
5. **Density:** the look-mode canvas uses the session `density` field (`MirrorService.kt:1249`, set from the user's resolution profile, default 240, validated 80..640). No ~30 dpi path is involved. Presets keep that density.

### Geometry (revised 2026-11-14 21:47 per user)

Preset set becomes **single / wide / ultrawide**. The old stub names migrate: stub `ultrawide` (3840×1080) is now **wide**; stub `triple` (3 zones) is now **ultrawide**. The 3-zone layout ships — zone containment works — but at **native scale, no letterbox bars ever**. Panel = glasses real pixels (both pairs 1920×1080). All sizes derive from the panel. `px/rad = panelW` (unchanged).

| Preset | Canvas (logical desktop) | Crop window | Scale into panel | Pan |
| ------ | ----------------------- | ----------- | ---------------- | --- |
| single | `panelW·k × panelH·k`, `k` ∈ {1, 4/3, 2} (advanced) | whole canvas | `1/k` (downscale, 16:9 → no bars) | none — clamp pins, viewport always shows the whole desktop |
| wide | `2·panelW + gap × panelH`, `gap = 48` | `panelW × panelH` | 1.0 | yaw ±(canvasW−panelW)/2 px ≈ ±0.51 rad; no pitch |
| ultrawide | `3·panelW + 2·gap × panelH` | `panelW × panelH` | 1.0 | yaw ±(canvasW−panelW)/2 px ≈ ±1.03 rad; no pitch |

- **Window rule, all presets:** single shows the *whole* canvas (crop = full canvas, scaled into the lens — same shape as body-lock); wide/ultrawide pan a panel-sized crop. No 1.6× zoom window anywhere; `VIEW_ZOOM` stops driving the window in look presets.
- **Zones, wide and ultrawide:** panel-sized rects separated by `gap = 48` px. Wide: 2 rects at x = 0, `panelW+gap`. Ultrawide: 3 rects at x = 0, `panelW+gap`, `2·(panelW+gap)`. At rest you see one zone filling the whole lens at native size; panning reveals the black gap strip and the neighboring zone(s). Zone containment, maximize-fills-zone, and seam-suppressed snap come from the existing `outputs` mechanism.
- **Single upscale (advanced):** raising `k` renders the same logical desktop at more pixels and downscales it into the 1080p lens — supersampling; same aspect (16:9), same viewport, same apparent size. Density scales with `k` (`density·k`, clamped ≤ 640) so the desktop does not shrink. Experiment on single first; wide/ultrawide upscale stays out of scope until the user judges single.
- Recenter = provider origin reset (unchanged). Pose 0 = crop center: single = whole desktop; wide = middle; ultrawide = middle of zone 2.
- Mode availability: single allows all three modes; wide/ultrawide hide **body-lock** in the card (degenerate full-canvas squeeze). Picking wide/ultrawide while in body-lock sends world-lock in the same save.

### Context for executor

- Goal: one paragraph — make the existing geometry picker on the XR card change the look-mode desktop's size and, for triple, split it into three shell-enforced monitor zones. The T05 viewport (`XrViewport.kt`) is untouched except for what `XrGeometry` feeds it; all crop/clamp/lead machinery stays.
- Key files: `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGeometry.kt` (new, pure Kotlin), `XrViewport.kt` (no change), `MirrorService.kt` (`xrCanvasSize` 9809, crop window 9829-9830, `syncXrLook` 9854, `showExternalOutput` look branch 9762-9772, outputs feed `applyDesktopOutputs` 10075), `plasma/CompositedWindowShell.kt` (`clampFrame` 2821-2833 only), `lib/xr_glasses_card.dart` (picker filtering 97-127).
- Invariants: no Android types in `xr/XrGeometry.kt`; no second AHRS; no `overlay_display_devices` writes; phone-normal hold (T05) must not be touched by preset switching — a preset resize does not park/unpark anything; extend-mode `phoneRegion`/`externalRegion` flows must not be clobbered (look mode never uses them; keep the two feeds separate).

### Steps

1. **`android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGeometry.kt`** — pure Kotlin, no Android types. `const val ZONE_GAP_PX = 48`. `fun singleScaleSteps(): FloatArray = floatArrayOf(1f, 4f / 3f, 2f)` (1080p / 1440p / 2160p at a 1080p panel). Functions (all even-aligned with `and -2`):
   - `canvasWidth(preset: String, panelW: Int, singleScale: Float = 1f): Int` — single `(panelW * singleScale)`; wide `panelW * 2 + gap`; ultrawide `panelW * 3 + 2 * gap`; else `panelW`.
   - `canvasHeight(preset: String, panelH: Int, singleScale: Float = 1f): Int` — single `(panelH * singleScale)`; others `panelH`.
   - `windowWidth(preset: String, panelW: Int, canvasW: Int): Int` — single → `canvasW` (whole desktop always visible); others → `min(panelW, canvasW)`.
   - `windowHeight(preset: String, panelH: Int, canvasH: Int): Int` — single → `canvasH`; others → `min(panelH, canvasH)`.
   - `data class XrZoneRect(left, top, right, bottom)`; `fun zoneRects(preset: String, panelW: Int, panelH: Int): List<XrZoneRect>` — wide: 2 rects at x = 0, `panelW+gap`; ultrawide: 3 rects at x = 0, `panelW+gap`, `2*(panelW+gap)`; every other preset: one full-canvas rect.
   - Unknown preset strings fall back to single. → verify: `XrGeometryTest` (step 7) passes.

2. **`XrGlassesProfiles.kt` — rename + migrate + scale field.**
   (a) `presets` set becomes `setOf("single", "wide", "ultrawide")`. (b) **Migration on read**: map stored `"ultrawide"` → `"wide"` and `"triple"` → `"ultrawide"` (in `XrProfile.parse` or the store read — one place; existing saved profiles on the phone must not reset to single). (c) Add `singleScale: Float = 1f` to `XrProfile`; persist under prefs `"dextop_xr"` key `"single_scale.$id"`; validate against `singleScaleSteps()` (reject/clamp unknown). (d) `XrGlassesMonitor.snapshot` (88-106) returns `singleScale`; `saveProfile` (110-119) accepts it. → verify: extend `XrGlassesProfilesTest` — migration cases (`"triple"`→`"ultrawide"`, old `"ultrawide"`→`"wide"`) and scale round-trip.

3. **`MainActivity.kt` channel.** `setXrGlassesProfile` (739-747) gains an optional `singleScale` double arg (default 1.0, reject non-finite / ≤0; clamp to nearest step). Passes through to `saveProfile`. → verify: card test round-trips it via the existing test seam.

4. **`MirrorService.kt` — sizes.** Add `private fun xrPreset(): String = moe.n4tsu.dextop.xr.XrGlassesMonitor.currentProfile().preset` and `xrSingleScale()` the same way (read once per resize, not per frame). Replace `xrCanvasSize()` (9809-9811) with `XrGeometry.canvasWidth/Height(xrPreset(), panel, xrSingleScale())`. Replace the crop-window computation at 9829-9830 with `XrGeometry.windowWidth/Height` (pass the canvas). Density for the resize `Config`: `min(640, round(density * singleScale))` for single, plain `density` otherwise — the desktop keeps its apparent size at higher `k`. → verify: `compileGithubDebugKotlin` clean.

5. **`MirrorService.kt` — preset/scale switch mid-session.** Track `private var xrAppliedPreset: String? = null` and `xrAppliedScale: Float = 1f`. In `syncXrLook` (9854): the early-return at 9858-9861 also requires `preset == xrAppliedPreset && scale == xrAppliedScale`. When look is on and either changed: rerun the look-branch body (resize `Config`, `attachXrMirror()` — re-attaches only when size changed), then `applyXrZones()`. Update applied values on every look-on transition and in the external-attach look branch (9762-9772). Reset both on `hideExternalOutput` / look-off. → verify: hardware log shows canvas change per flip with no desktop restart.

6. **`MirrorService.kt` — zones.** New `private fun applyXrZones()`: if look on and preset is `wide` or `ultrawide`, `plasmaShell?.setOutputs(XrGeometry.zoneRects(...).map { Rect(...) })`; if look on and the preset is neither **and the previously applied preset was zoned**, `setOutputs(emptyList())`. Never touch extend mode's `phoneRegion`/`externalRegion` feed. Call only from the look branches. → verify: `rg -n "setOutputs" MirrorService.kt` shows the two extend sites plus the new zone sites only.

7. **`CompositedWindowShell.kt` — two surgical edits, nothing else.** (a) `clampFrame` (2821-2833): horizontal keep uses global `width` (2829); use the containing output's width when `outputs` is non-empty. (b) Paint the root view background black when `outputs.size > 1` (wallpaper already draws per output only, 4748-4752) so gap strips read as gaps. Do not touch drag/snap/tile/maximize — already per-output. `DextopAutoShell` early-return (2780) stays. → verify: extend mode unregressed; gaps black on hardware.

8. **`lib/xr_glasses_card.dart` — picker logic + advanced section.**
   (a) Rename the geometry picker options (111-127): `wide`, `ultrawide` (drop `triple`).
   (b) Mode picker (97-110): when preset != `single`, omit `body-lock`. In `save` (43-48): preset != single + current mode body-lock → send `world-lock`.
   (c) **Advanced section**: an `ExpansionTile` (collapsed by default) below the geometry picker, visible **only when preset == single**, with one dropdown: 1080p / 1440p / 2160p (labels, not raw floats). Selecting sends `setXrGlassesProfile` with the new `singleScale` (1, 4/3, 2). Body keeps rendering when collapsed; `XrGlassesCardBody` state map already carries `preset` — add `singleScale` through the same seam.
   → verify: card test below; `flutter analyze` clean.

9. **l10n — all 8 sets.** Rename the preset display labels (old `ultrawide` label → "Wide", old `triple` label → "Ultrawide") and add strings for the advanced section: section title ("Advanced"), the three resolution option labels. Files: `lib/l10n/app_en.arb`, `app_zh.arb`, `app_ru.arb`, `app_pt.arb`, `app_pt_BR.arb`, `app_ko.arb`, `app_ja.arb` (the pt file also holds pt-BR), plus `lib/l10n/inline_strings.g.dart` fallback map. Regenerate if the project does (`flutter gen-l10n`), matching how existing `uiXrGlasses*` keys are wired. → verify: `flutter analyze` + all l10n getters exist.

10. **Tests.**
    - `XrGeometryTest.kt` (new): canvas/window per preset at 1920×1080 (single 1920/2560/3840 wide with k=1/4-3/2; wide 3888; ultrawide 5856); single window == canvas, wide/ultrawide window == panel; wide zone rects = 2 at x 0/1968, ultrawide = 3 at x 0/1968/3936, gap 48; unknown preset → single fallback.
    - Extend `XrViewportTest.kt`: wide sizes — yaw +0.5 rad → `left` decreases, ±1.6 rad clamps to 0 / `canvasW−panelW`; single at k=2 (3840×2160 canvas, window = canvas) any pose → full-canvas crop, no pan; pitch never moves `top` for wide/ultrawide.
    - Extend `XrGlassesProfilesTest.kt`: migration + scale round-trip.
    - `test/xr_glasses_card_test.dart`: preset `wide` → mode picker without body-lock; preset `single` → advanced section visible with three scale options; preset `wide` → advanced section absent; save at 1440p → `singleScale` 4/3 sent.
    → verify: `cd android && ./gradlew :app:testGithubDebugUnitTest --tests 'moe.n4tsu.dextop.xr.*' --offline`; `flutter test test/xr_glasses_card_test.dart`.

11. **`make verify`** from repo root. → verify: exit 0.

12. **Hardware (XREAL plugged, phone role).** Install `build/app/outputs/flutter-apk/app-github-debug.apk`. World-lock per preset: single = whole desktop, no travel; 1440p = same picture, sharper/identical size, still no travel; wide = 2 zones, zone 1 fills the lens, pan reveals the gap and zone 2, windows stay in-zone, maximize fills the zone; ultrawide = 3 zones, same containment. Flip presets mid-session: in-place resize, no Overlay #, no restart. Recenter centers each time. → verify: `adb -s 192.168.69.104:5555 logcat -s DextopXrView` shows canvas per preset and clamps at the right edges. Viture unplugged → leave that line unchecked, do not invent.

### Tests to add

See step 10. `XrGeometryTest` (new), `XrViewportTest` (extend, new cases only), `XrGlassesProfilesTest` (migration + scale), `xr_glasses_card_test.dart` (extend).

### Verify commands

- `cd android && ./gradlew :app:testGithubDebugUnitTest --tests 'moe.n4tsu.dextop.xr.*' --offline`
- `flutter test test/xr_glasses_card_test.dart`
- `make verify`
- Hardware: `adb -s 192.168.69.104:5555 logcat -s DextopXrView`

### Risks / pitfalls

- **Do not resize through `requestDisplay`/`ResolutionRepository`** — that is the overlay path and would recreate the desktop and bring Overlay # back. Owned display + `resizeActiveDisplay` only.
- **Migration order matters**: a saved `"ultrawide"` on the phone means the OLD 3840 preset → new `wide`. Map on read; do not reset unknown values to single silently.
- **Re-attach on window/canvas change must update the mirror scale** (single k>1 downscales; wide/ultrawide are 1.0). `attachXrMirror` skips only when size is *unchanged* — the skip check must compare both canvas and crop-window sizes.
- **Density cap**: `density·k` clamped ≤ 640 (240·2 = 480 is fine; a user-picked 400 dpi × 2 would exceed — clamp and log, do not fail).
- Extend mode and look mode both call `setOutputs`; keep the two feeds separate. Zone rects must never reach `arrangeExtended`'s region math.
- A preset/scale resize must not unpark the phone overlay or end the T05 phone-normal hold; those keys are mode/role-driven only.
- Single upscale is supersampling, not more workspace: same dp content at more pixels. If the user wants more desktop area instead, that is a different knob — do not sneak it in.
- Pitch has no travel in any preset (canvas height == window height for wide/ultrawide; single shows the whole canvas). Intended; do not add vertical slack back.
- Body-lock + non-single preset from an old saved profile: UI prevents choosing it; the degenerate full-canvas squeeze is the mechanical fallback. No Kotlin-side mode coercion — it would fight the saved profile.
- l10n: every new user-visible string lands in all 8 sets in the same change; no hardcoded English in the card.

### Out of scope

- Upscale on wide/ultrawide (until the user judges single), zone-jump buttons / per-zone recenter, preset-specific smoothing, Viture hardware pass (T08), trackpad-role polish, T07 everything.
- Any change to drag/snap/tile/maximize internals beyond the `clampFrame` width fix.
- Touchpad role behavior (unchanged, per T05).

### Execute model recommendation

- [medium] — every change is small and anchored, but the preset-string migration, the `syncXrLook` early-return surgery, the two `setOutputs` feeds, the attach-scale re-check, and 8-set l10n need care a small model would fumble. Not [large]: no new architecture; zones reuse an existing mechanism.

## Test Plan

- Unit: preset → logical size mapping, pan clamp per preset, recenter target per preset
- Hardware: each preset visible + pannable on both glasses; triple zones hold windows
- Commands: `make verify` + manual hardware session

## Acceptance Criteria

- [ ] All shipped presets switch cleanly (no desktop restart surprises beyond documented HPD behavior)
- [ ] Pan + clamp + recenter correct per preset on hardware
- [ ] No black bars in any preset at any scale
- [ ] Ultrawide (3-zone) ships with zone containment (renamed from triple; escalation rule resolved in favor of shipping)
- [ ] Advanced single upscale: 1080p/1440p/2160p steps render sharper, same viewport, no panning, no apparent size change
- [x] Saved old preset names migrate (old `ultrawide` → `wide`, old `triple` → `ultrawide`)
- [x] Unit tests green, `make verify` green
- [x] No secrets committed

## Verification

Tooling present: root `Makefile` `verify` (analyze + flutter test + `:app:testGithubDebugUnitTest` + `flutter build apk`), `analysis_options.yaml`, `lefthook.yml` pre-commit → verify.

- `make verify` exit 0 at 22:05 Africa/Addis_Ababa (second run; includes `xrBaseDensity` snapshot, failed-resize not committing applied preset, and card load saving world-lock when a wide preset was stored with body-lock). `flutter analyze`: no issues. `flutter test`: +74 all passed. `:app:testGithubDebugUnitTest`: BUILD SUCCESSFUL. APK: `build/app/outputs/flutter-apk/app-github-debug.apk`.
- Earlier `make verify` at 21:51 exited 0 before those three fixes. Not the gate.
- `adb -s 192.168.69.104:5555 install -r` Success at 22:07 Africa/Addis_Ababa. No force-stop. User must stop the session and Start again.
- Hardware look at 22:18 Africa/Addis_Ababa: preset switch, yaw, and recenter worked. Pitch did not lock. Single world-lock filled the lens.
- Pose-canvas follow-up: `make verify` exit 0 at 22:42 Africa/Addis_Ababa. Analyze clean. Flutter tests +76. Kotlin unit tests `BUILD SUCCESSFUL`. APK rebuilt.
- Hardware AC for the pose canvas still open. Viture not plugged.

## Files Modified

- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGeometry.kt` (new)
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrGeometryTest.kt` (new)
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesProfiles.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrGlassesMonitor.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrViewport.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/xr/XrViewportDriver.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/MainActivity.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/MirrorService.kt`
- `android/app/src/main/kotlin/moe/n4tsu/dextop/plasma/CompositedWindowShell.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrViewportTest.kt`
- `android/app/src/test/kotlin/moe/n4tsu/dextop/xr/XrGlassesProfilesTest.kt`
- `lib/app_shell.dart`
- `lib/xr_glasses_card.dart`
- `test/xr_glasses_card_test.dart`
- `lib/l10n/app_*.arb`, `lib/l10n/inline/*.json`, generated `inline_strings.g.dart`, `app_localizations*.dart`, `android/app/src/main/resources/moe/n4tsu/dextop/l10n/inline_*.properties`
- `planning/phases/T06-geometry-presets.md`, `planning/phases/INDEX.md`

## Manual test (for humans)

*(Filled by `/task-3-complete`)*

## Learnings

*(Filled by `/task-3-complete` / dialectic)*

## Reality notes

From T03 close-out (2026-11-14):

- Presets consume the provider pose after the host origin subtract.
  The Viture wire roll sits near 2.90 rad at rest, so a preset that
  reads the wire angles starts far from center. Recenter stays the
  host origin reset. Do not send a glasses-side recenter.

From T04 close-out (2026-11-14):

- Air fused pose before the first Recenter sat near yaw −0.527,
  pitch 0.227, roll 2.711 rad. Recenter confirmed 18:50
  Africa/Addis_Ababa: the next logged pose was near 0 on all three
  axes. Presets read that origin-relative pose.

From T05 close-out (2026-11-14):

- The temporary look-mode canvas is not panel×2 by panel height.
  The glasses show a 1.6× panel window, scaled into the lens. The
  desktop is that window plus one radian of slack on each side. At
  1920×1080 that canvas is 6912×3888, and reaching an edge is about
  ±1 rad of yaw (~57°) and about ±0.56 rad of pitch. The user felt
  that as a very large screen. Presets replace this canvas. Do not
  treat the travel as a stuck crop.

From T06 planning (2026-11-14):

- The stub's "resize via `ResolutionRepository`/`requestDisplay`"
  assumption is wrong for look mode. That is the overlay-display
  path. Look mode's owned virtual display resizes in place via
  `resizeActiveDisplay`. Presets plug in there, like mode switching.
- Preset plumbing already exists end-to-end: `XrProfile(mode, preset)`,
  prefs keys, the `setXrGlassesProfile` channel, and the geometry
  picker on the card. Nothing consumed it for geometry until this task.
- Zone containment verdict: reuse of the shell's existing
  `outputs: List<Rect>` mechanism (per-output clamp, work area, tiling,
  maximize, seam-suppressed snap). No two-screen limit exists.
  The escalation rule did not fire; triple ships with one `clampFrame`
  width fix.

From the 2026-11-14 hardware look, before close-out:

- Yaw, recenter, and in-place preset switch worked. Pitch did not,
  because the wide and ultrawide canvases were only as tall as the
  lens. Single world-lock showed the whole desktop, so it could not
  sit in the room either.
- World-lock now uses one pose canvas for single, wide, and ultrawide:
  yaw ±180° and pitch ±90°, plus one lens so the crop can reach the
  pose limit. 1080p is 13984×7112. 1440p is 18644×9482. 2160p is
  27966×14224. That framebuffer is the documented world-lock
  performance limit. The device may stutter or reject the resize.
- Body-lock and smooth-follow stay single. Their canvas equals the
  window, so they do not pan and they do not allocate the pose buffer.
  Wide and ultrawide are not offered in those modes. A stored wide or
  ultrawide profile with another mode is saved back as world-lock.
