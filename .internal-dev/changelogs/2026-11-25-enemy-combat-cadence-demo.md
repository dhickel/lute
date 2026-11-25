# Prototype enemy attack cadence

## Date
2026-11-25

## Git Commit

3c514b9f3d7853fe913be43018a5f8c4d1b20e87

## Change Summary
Prototype delayed AI attack decisions, melee windup and recovery, and committed archer aim.

## Files
AI behavior, animation, combat and projectile systems; combat, awareness, travel and arena tests; `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
AI attacks have a sampled decision delay. Melee attacks have a readable windup and recovery; archers lock aim halfway through the draw and cancel if sight is lost before release. Player attacks are unchanged.

## Specification Impact
The map actor awareness decision documents this branch-specific experiment.

## Risks
Timing is prototype tuning; concurrent attacks remain uncapped.

## Follow-up Items
Playtest attack cadence and recovery.
