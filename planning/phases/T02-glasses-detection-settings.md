# T02 — Glasses detection + XR settings card + profiles

**Status**: Pending  
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

## Requirements

- [ ] `XrGlassesDetector`: maps a connected physical display to a glasses identity (Viture Pro / XREAL One / unknown) using USB VID/PID + USB-NIC presence + display name
- [ ] USB permission flow for Viture HID (runtime dialog + `device_filter.xml`); no permission flow for XREAL TCP
- [ ] Per-glasses profile persistence (mode, preset) in shared preferences, auto-applied on reconnect
- [ ] XR settings card in display settings: stabilization mode selector, geometry preset selector, recenter button, per-glasses profile; visible only when XR display detected
- [ ] Every new user-facing string in all `app_*.arb` + inline json/properties l10n sets
- [ ] Unknown glasses: existing external-display UX unchanged, no crash, no XR card
- [ ] Unit tests for detection mapping (USB signal → identity → profile) with fake device inventory

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

- Unit tests: detector mapping table (VID/PID, USB-NIC, name heuristics), profile round-trip
- Commands: `make verify`; manual: plug each glasses pair, card appears with remembered profile

## Acceptance Criteria

- [ ] Detection maps Viture Pro, XREAL One, and unknown glasses correctly (unit-tested mapping)
- [ ] XR card localized across all 8 l10n sets, appears only for detected glasses
- [ ] Profiles persist per glasses model and auto-apply on reconnect
- [ ] Unknown glasses keep plain external-display behavior (regression-checked)
- [ ] Tests added/updated; `make verify` green
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

*(Amended by upstream `/task-3-complete`)*
