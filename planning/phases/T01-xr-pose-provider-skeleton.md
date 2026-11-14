# T01 — XR pose provider abstraction + skeleton

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: —  
**Next**: T02  
**Layer**: L0

## Description

Land the compile-clean skeleton of the XR glasses feature: a Kotlin-side
`XrPoseProvider` interface (3DoF orientation stream + provider identity +
recenter hook), a deterministic fake provider for tests, and the seam where
XR mode will plug into the existing external-display pipeline. No hardware
protocols, no UI. After this task `make verify` passes with the new code in
the tree, and every later XR task has a stable interface to target.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] `XrPoseProvider` interface: stream of 3DoF orientation (yaw/pitch/roll or quaternion, timestamped), provider id/name, recenter callback, availability state
- [ ] Fake provider: scriptable pose sequence, injectable for tests
- [ ] Provider registry / lookup seam in the Kotlin service layer (empty except fake)
- [ ] Kotlin code follows existing package layout (`moe.n4tsu.dextop`)
- [ ] Unit tests for fake provider stream semantics (subscription, timestamps, recenter reset)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- Interface lives Kotlin-side: pose consumers (mirror transform, T05) are all
  in the service layer. Flutter never sees raw pose.
- Keep the interface transport-agnostic: Viture HID (T03) and XREAL TCP (T04)
  both implement it; generic fallback implements nothing (absent provider).
- Recenter is part of the interface because Viture has a native HID recenter
  command (`0x30`) while XREAL recenter is host-side yaw reset — consumers
  must not care which.
- Reference: `.cursor/rules/xr-glasses.mdc` (settled decisions),
  `.cursor/rules/display-stack.mdc` (pipeline invariants).
- Prior art: `one-xr` pose/calibration model (https://github.com/Skarian/one-xr).

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

- Unit tests over the fake provider (no hardware): subscription lifecycle, timestamp monotonicity, recenter reset semantics
- Commands: `make verify` (must include analyze + tests)

## Acceptance Criteria

- [ ] `XrPoseProvider` interface + fake provider exist Kotlin-side, transport-agnostic
- [ ] Provider registry seam resolves fake in tests, nothing in production yet
- [ ] Tests added for fake provider semantics
- [ ] Full analyze + test verify suite green (`make verify`)
- [ ] Verification commands recorded and passing
- [ ] No secrets committed

## Verification

*(Filled by `/task-2-execute`; re-confirmed by `/task-3-complete`)*

## Files Modified

*(Filled by `/task-2-execute`)*

## Manual test (for humans)

*(Filled by `/task-3-complete` — runnable commands + what to look for, or
`Nothing to test — <why>`)*

## Learnings

*(Filled by `/task-3-complete` / dialectic — link rule entries)*

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*
