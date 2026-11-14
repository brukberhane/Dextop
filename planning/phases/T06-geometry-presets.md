# T06 — Geometry presets + pan + recenter

**Status**: Pending  
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

## Requirements

- [ ] Preset enum: single / ultrawide / triple; switching resizes the logical desktop via existing resolution profile mechanism
- [ ] Single: 1920×1080, all three stabilization modes available
- [ ] Ultrawide: 3840×1080 world-locked, head pan clamped to canvas bounds
- [ ] Triple: 5760×1080 with three shell-enforced 1920×1080 zones — windows clamped/snapped within their zone, maximize fills the zone, visible gaps between regions, farther apparent scale
- [ ] Head-pan math: yaw/pitch → viewport offset, clamped, per-axis dead-zone-free
- [ ] Recenter snaps viewport to canvas/zone center
- [ ] **Escalation rule (human decision, 2026-11-13):** if zone enforcement in `CompositedWindowShell` proves too invasive during planning, DROP the triple preset and ship single + ultrawide — explicit user instruction, no silent degrade
- [ ] Density/scale per preset sane on glasses (no ~30 dpi default)

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

- Unit: preset → logical size mapping, pan clamp per preset, recenter target per preset
- Hardware: each preset visible + pannable on both glasses; triple zones hold windows
- Commands: `make verify` + manual hardware session

## Acceptance Criteria

- [ ] All shipped presets switch cleanly (no desktop restart surprises beyond documented HPD behavior)
- [ ] Pan + clamp + recenter correct per preset on hardware
- [ ] Triple preset either ships with zone containment or is explicitly dropped per escalation rule (user informed, INDEX/stubs updated)
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
