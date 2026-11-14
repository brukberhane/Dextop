# T04 — XREAL One TCP adapter

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T03  
**Next**: T05  
**Layer**: L5

## Description

Implement the `XrPoseProvider` for XREAL One-series glasses over the glasses'
USB network interface: connect to `169.254.2.1:52998` (sensor stream) with
sockets bound via the USB NIC's `Network.socketFactory`, parse the 128-byte
gyro/accel/mag reports into a fused orientation, and expose recenter as a
host-side yaw/pitch origin reset. Read-only control use (panel duty `10065`
for mode heuristics) — never toggles SBS.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] `ConnectivityManager` enumeration finds the USB link-local network; sockets bound through its `Network.socketFactory`
- [ ] Sensor stream (`:52998`) parsed → orientation stream; control channel (`:52999`) used for panel-duty read only
- [ ] Host-side recenter (origin reset) + glasses-side state hint (Follow + Stabilizer off) surfaced to T02's card/onboarding
- [ ] First task action: **verify actual glasses model on hardware** (One vs Air uncertain). If Air: record Reality notes, implement Air transport (different protocol) in this task or split per Reality notes
- [ ] Measured IMU sample rate recorded (undocumented upstream)
- [ ] Firmware version-gated handshake; protocol failure degrades to generic display, diagnostics surfaced

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (bootstrap)

- Reference implementations to study (reimplement, do not vendor):
  https://github.com/Skarian/one-xr/blob/main/docs/android-library.md,
  https://github.com/georgehorrell/xreal-one-cli,
  https://github.com/0xcaff/xr-tools.
- All protocol facts + symptom table: `.cursor/rules/xreal-glasses.mdc`.
- Fusion from gyro/accel(/mag) happens here at the adapter edge so T05
  consumes a uniform 3DoF orientation stream regardless of vendor.
- 3DoF-only scope; 6DoF/plane detection does not exist on plain One.

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

- Unit: report parsing from recorded frames, network-failover fallback logic
- Hardware: XREAL glasses streaming pose; recenter resets origin; sample rate measured
- Commands: `make verify` + manual hardware session (owner has the XREAL pair)

## Acceptance Criteria

- [ ] Actual model verified on hardware and recorded (One vs Air)
- [ ] Pose streams into `XrPoseProvider` on hardware; sample rate measured and recorded
- [ ] Sockets provably bound to USB NIC (no default-network leakage)
- [ ] Protocol failure → generic display fallback, no crash
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

From T02 close-out (2026-11-14):

- Family identity id is `xreal` (not `xreal-one`). Link heuristic: any
  `LinkAddress` host starting with `169.254.2.`. Name heuristic: display name
  contains `xreal` or `nreal`. One vs Air still unverified — first T04 action.
- When both Viture USB and an XREAL link are present, any xreal/nreal display
  name wins the tie; otherwise Viture wins. USB still beats name alone.
- No USB permission dialog for XREAL TCP. Profiles use the same `dextop_xr`
  store under key `mode.xreal` / `preset.xreal`.
- Recenter is host-side via the registered `XrPoseProvider` (card already
  calls the registry). Do not send glasses-side Anchor/recenter APIs.
- Channel methods already exist: `xrGlassesState`, `setXrGlassesProfile`,
  `xrGlassesRecenter`. Onboarding Follow/Stabilizer hint stays T07.
