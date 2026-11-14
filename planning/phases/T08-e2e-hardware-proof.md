# T08 — E2E hardware proof + generic fallback

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T07  
**Next**: —  
**Layer**: L8

## Description

Holistic proof of the XR glasses feature on real hardware: a full living
session per supported glasses pair (plug in → profile auto-applies →
stabilized desktop → presets → pan → recenter → input → unplug), the generic
fallback path for unknown glasses, and regression proof that non-XR
external-display behavior is unchanged.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] Viture Pro: full session checklist passes (detect → 3 modes → presets → recenter → trackpad → unplug/replug)
- [ ] XREAL glasses: same checklist, plus Follow/Stabilizer-off onboarding path exercised
- [ ] Unknown glasses (any other DP display): plain external-display behavior, zero XR surface, no crash
- [ ] Non-XR external displays (monitor/TV): existing modes unchanged (regression)
- [ ] `make verify` green; session notes recorded in Verification with measured rates + firmware versions
- [ ] Human signs off on comfort (motion sickness spot check on smooth-follow)

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Record glasses firmware versions during the session — both protocols are
  firmware-sensitive (reverse-engineered); baseline them for future
  regression triage.
- Test matrix: each glasses pair × each shipped preset × world-lock +
  smooth-follow; body-lock is the control.
- If either adapter fails its checklist, task is Blocked with a diagnostic
  bundle (rates, logs, duty readings) — do not force-complete.

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

- Hardware session checklists (above); regression pass on plain external display
- Commands: `make verify` + recorded manual sessions

## Acceptance Criteria

- [ ] Both glasses pairs pass full session checklist on hardware
- [ ] Generic fallback proven on an unrecognized display
- [ ] Non-XR external display regression pass
- [ ] Session notes with firmware versions + measured rates recorded
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

- Viture Pro on SM-S948B streamed pose at 62.8 Hz. CRC locked to
  XMODEM little-endian. MCU acked IMU enable `0x15`.
- Still open on that unit: a head turn for axis signs (left / up /
  roll right), and a Recenter press whose next logged pose is near 0.
  Those two are the Viture half of the E2E pose check.
