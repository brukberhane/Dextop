# T03 — Viture Pro Gen1 HID adapter

**Status**: Pending  
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

## Requirements

- [ ] HID interface claim + dedupe on Viture device (VID 0x35CA, Pro PID 0x101D); USB permission handled by T02 flow
- [ ] IMU stream enable (`0x15`), re-enable on re-attach
- [ ] `0xFFFC` report parsing → `XrPoseProvider` orientation stream (timestamps)
- [ ] Recenter via MCU command `0x30`; brightness `0x0122` optional passthrough
- [ ] CRC-16 framing with the XMODEM vs CCITT-FALSE variance resolved empirically (test both on hardware)
- [ ] Never toggles 2D/SBS or refresh (2D-only invariant)
- [ ] Protocol constants unit-tested against recorded sample frames

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

- Unit: report parsing (recorded frames), CRC variants, re-enable logic
- Hardware: Viture Pro streaming pose; recenter command visibly resets orientation
- Commands: `make verify` + manual hardware session (owner has Viture Pro)

## Acceptance Criteria

- [ ] Viture Pro pose streams into `XrPoseProvider` on hardware
- [ ] Recenter command works on hardware
- [ ] CRC variant empirically resolved and documented in Verification
- [ ] Measured pose rate recorded in Verification for T05
- [ ] No SDK blob added to tree (grep for `libglasses`, `.aar`, vendored zips)
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

*(Amended by upstream `/task-3-complete`)*
