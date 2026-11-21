# Wandering attacker pursuit

## Date

2026-11-21

## Git Commit

263c4480f46c9ad3bc65c4189f8d2d7db617acaa

## Change Summary

The `wander_attack` graph follows a living player detected by the awareness circle and line of sight. Seeing a target is now a separate decision from being able to make a melee attack.

## Files

- `Graphs.java`, `Action.java`, `Facing.java`, and `FacingSystem.java`: pursuit branch, steering, and dominant-axis facing for AI actors.
- `AwarenessSystemTest.java` and `MapTravelTest.java`: coverage of pursuit, sight and sensor limits, damage, and pursuit after retreat.
- `README.md`, actor specifications, and actor decision graph knowledge note.

## Behavioral Impact

The attacker uses authored movement speed to close the gap, stops within 32 logical pixels for primary melee, and resumes following a detectable player when they retreat beyond melee range. A committed attack finishes first. If there is no detectable player, it wanders. The wander-only NPC and player-control facing rules are unchanged.

## Specification Impact

Updated the map-actor behavior contract from attack-only to sensor- and sight-driven pursuit.

## Risks

Pursuit is direct steering: the attacker does not remember a lost target or navigate around obstacles.

## Follow-up Items

No changes required for the current behavior. Native awareness and combat tests and the desktop map travel and composite rendering tests passed.
