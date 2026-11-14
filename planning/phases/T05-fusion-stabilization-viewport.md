# T05 — 3DoF fusion + stabilization modes + head-tracked viewport

**Status**: Pending  
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

## Requirements

- [ ] Stabilization mode enum + mode switching live (body-lock / smooth-follow / world-lock)
- [ ] Smooth-follow: damped pose follow (tunable constant), no visible overshoot at 60 Hz
- [ ] World-lock: virtual screen pinned; recenter resets orientation origin
- [ ] Head-tracked viewport: per-frame crop/position on the glasses mirror layer (`DisplayMirrorBackend.attachLayer` path), pose smoothed with light low-pass
- [ ] Viewport offset clamped to canvas bounds minus panel size (no black past edges)
- [ ] Frame pacing: transform applied at display cadence (60 Hz target), pose extrapolated ≤1 frame
- [ ] Works with fake provider in unit tests; works on Viture + XREAL hardware
- [ ] No changes to native Wayland compositor; Plasma shell untouched

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Injection point decision (settled): mirror-layer transform — reuse
  `attachLayer`'s `setMatrix`/`setPosition`/`setCrop`; the same math extend
  mode uses for `externalRegion`, but driven by pose instead of a fixed
  region. See `.cursor/rules/display-stack.mdc`, `.cursor/rules/xr-glasses.mdc`.
- Double-transform guard: on XREAL, world-lock only works with glasses in
  Follow + Stabilizer off — card hint already exists from T02/T04; log the
  panel-duty reading on XR attach.
- Latency budget: pose→crop ≤1 frame; smoothing constants tuned on hardware
  using the sample rates recorded in T03/T04 Verification.
- Recenter in smooth-follow AND world-lock (settled decision #1).

## Execution plan (filled by /task-1-plan)

**Date:**  
**Codebase snapshot:**  
**Execute model:** small/default | large (only if justified)

### Context for executor
- …

### Steps
1. … → verify: …

### Tests to add
- …

### Verify commands
- …

### Risks / pitfalls
- …

### Out of scope
- …

### Execute model recommendation
- default (small/cheap) | large — rationale: …

## Test Plan

- Unit (fake provider): mode switching, damping math, clamp math, recenter reset
- Hardware: world-lock stays put on both glasses at 60 Hz; smooth-follow comfortable; no jitter
- Commands: `make verify` + manual hardware session

## Acceptance Criteria

- [ ] All three modes work on both glasses on hardware (world-lock pinned, smooth-follow damped, body-lock identical to today)
- [ ] Recenter works in world-lock and smooth-follow on both glasses
- [ ] Per-frame transform with no visible lag/jitter at 60 Hz on hardware
- [ ] Unit tests over fake provider cover mode + clamp + damping math
- [ ] `make verify` green
- [ ] No secrets committed

## Verification

*(Filled by `/task-2-execute`; re-confirmed by `/task-3-complete`)*

## Files Modified

*(Filled by `/task-2-execute`)*

## Manual test (for humans)

*(Filled by `/task-3-complete`)*

## Learnings

*(Filled by `/task-3-complete` / dialectic)*

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
