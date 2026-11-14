# MVP Phase Index

**Product**: Dextop — XR glasses support (Viture Pro + XREAL Air 2 Pro)  
**Method**: `/task-1-plan` → `/task-2-execute` → `/task-3-complete`  
**Rule**: Only one task `InProgress` unless the human approves more.  
**INDEX Status**: use `✅` when complete (never the word `Done` in this column).  
**Close-out**: commits are local by default; push only with `--push`.

**Design context**: `/grill-me` session 2026-11-13 settled the design tree —
see task stubs' high-level notes and `.cursor/rules/xr-glasses.mdc`
("Settled design decisions") for the decision log.

| ID | Title | Status | Depends-on | Next | Layer | Notes |
| -- | ----- | ------ | ---------- | ---- | ----- | ----- |
| T01 | [XR pose provider abstraction + skeleton](./T01-xr-pose-provider-skeleton.md) | ✅ | — | T02 | L0 | Interface + fake provider; no hardware |
| T02 | [Glasses detection + XR settings card + profiles](./T02-glasses-detection-settings.md) | ✅ | T01 | T03 | L6 | Viture VID/PID, XREAL USB-NIC, display-name heuristics |
| T03 | [Viture Pro Gen1 HID adapter](./T03-viture-hid-adapter.md) | ✅ | T02 | T04 | L5 | Pro IMU 62.8 Hz, CRC XMODEM, no SDK blob |
| T04 | [XREAL Air 2 Pro HID pose adapter](./T04-xreal-tcp-adapter.md) | ✅ | T03 | T05 | L5 | Air IMU 991.4 Hz, timestamp ns, endpoints 0x84/0x05 |
| T05 | [3DoF fusion + stabilization modes + head-tracked viewport](./T05-fusion-stabilization-viewport.md) | ✅ | T04 | T06 | L4 | Crop the existing mirror; no second AHRS |
| T06 | [Geometry presets + pan + recenter](./T06-geometry-presets.md) | Pending | T05 | T07 | L6 | Single / triple (zones or drop) / ultrawide |
| T07 | [Input routing + onboarding + profile polish](./T07-input-onboarding-polish.md) | Pending | T06 | T08 | L7 | Trackpad role; Follow/Stabilizer hint is One-series only |
| T08 | [E2E hardware proof + generic fallback](./T08-e2e-hardware-proof.md) | Pending | T07 | — | L8 | Both glasses on hardware; unknown glasses degrade |

## Layer legend

| Layer | Meaning |
| ----- | ------- |
| L0 | Skeleton builds |
| L1 | Config / logging |
| L2 | Secrets / identity |
| L3 | Pure core |
| L4 | Integration surface |
| L5 | External processes / device protocols |
| L6 | Host integration |
| L7 | Operator UX |
| L8 | E2E proof |

## How to work

1. `/task-1-plan T01`
2. `/task-2-execute T01`
3. `/task-3-complete T01` → local commit (push only with `--push`) + Manual test → continues on `<T02-stub-stem>`
4. Repeat

Existing app (v2.0.0+39) predates this plan; these phases cover the XR glasses
feature only, built on the current display stack.

## Status History

| Timestamp | ID | Event | From | To |
| --------- | -- | ----- | ---- | -- |
| 2026-11-14 17:45 Africa/Addis_Ababa | T03 | /task-3-complete | InProgress | ✅ |
| 2026-11-14 18:38 Africa/Addis_Ababa | T04 | /task-3-complete | InProgress | ✅ |
| 2026-11-14 21:35 Africa/Addis_Ababa | T05 | /task-3-complete | InProgress | ✅ |
