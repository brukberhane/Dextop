# T07 — Input routing + onboarding + profile polish

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T06  
**Next**: T08  
**Layer**: L7

## Description

Make XR mode livable end-to-end: phone-as-trackpad+keyboard role lights up
automatically when the desktop moves to glasses (reusing the existing
external-only role), physical mouse/keyboard passthrough keeps working,
pointer cursor is visible on the head-tracked viewport, XREAL gets the
one-time "set Follow + Stabilizer off" onboarding hint when Anchor is
detected, and per-glasses profiles apply every setting on plug-in.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] Phone trackpad+keyboard role auto-activates for XR displays (existing 'touchpad' phone role)
- [ ] Physical mouse/keyboard passthrough verified on XR desktop
- [ ] Cursor visible and correctly positioned across pan/offsets on all presets
- [ ] XREAL One onboarding only: panel-duty heuristic (≈90% head-lock, ≤40% likely Anchor) drives a one-time hint card with one tap to XR settings. Air has no Anchor signal — do not block Air onboarding on this hint
- [ ] Profile auto-apply covers mode + preset + smoothing preference on plug-in
- [ ] All new strings across all 8 l10n sets
- [ ] Hot-unplug mid-session: clean teardown of pose provider, mirror, profiles; no crash; reconnect restores

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Input role + passthrough already exist for external-only mode — reuse,
  don't rebuild (`.cursor/rules/display-stack.mdc`).
- Onboarding heuristic numbers are community-measured, not official — treat
  thresholds as heuristics with copy saying "may be" (pocket-xr precedent).
- Hot-unplug interacts with Viture HPD churn on any residual mode changes.
  Both HID adapters must release cleanly (T03/T04 seam). Air has no TCP
  teardown.

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

- Unit: hint-card trigger logic (duty thresholds), profile apply order
- Hardware: full living session on each glasses pair — connect, work, recenter, unplug, reconnect
- Commands: `make verify` + manual hardware session

## Acceptance Criteria

- [ ] Trackpad + keyboard + physical input all work on XR desktop on hardware
- [ ] Cursor correct across pan on all shipped presets
- [ ] XREAL hint fires once when Anchor-like duty detected, per session
- [ ] Hot-unplug/reconnect clean on both glasses
- [ ] Unit tests green, `make verify` green
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

- HID teardown has to cancel the USB request and join the reader
  before releasing the interface. Closing first leaves the interface
  claimed and the next plug cannot start the stream. Both adapters
  share that order.

From T04 close-out (2026-11-14):

- A failed Air open must stay latched until the glasses detach. The
  display snapshot runs about every 2 s and would otherwise reclaim
  interface 3 in a loop.
- The Follow + Stabilizer hint is One-series only. Air 2 Pro has no
  glasses-side Anchor, so the hint cannot fire on the owned unit.
- The USB Allow dialog can open on the built-in panel while that panel
  is off and the glasses are the lit display. Wake the phone; the
  dialog is already there.

From T05 close-out (2026-11-14):

- Trackpad role input works and must stay off the phone-normal hold.
  The user still saw the phone's own UI, with no trackpad overlay and
  no black surface. They asked whether that surface should go black,
  like the old touchpad deck. Not done here. Do not bring back
  `overlay_display_devices` to hide it.
