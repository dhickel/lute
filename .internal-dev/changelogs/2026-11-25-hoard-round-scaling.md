# Restore Hoard round scaling

## Date
2026-11-25

## Git Commit

28e008699f30e8772b7f9f01fac899203cfed03d

## Change Summary
Apply the shared linear +5% round bonus to later opening batches, timed refreshes, kill XP, and Dredge HP. A new visit still starts with exactly 10 enemies. Timed refreshes roll a base count of 4–8 every 30–50 simulation seconds.

## Files
`HoardRun.java`, `HoardSystem.java`, their focused tests, README, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
Enemy unlock tier replaces spawn area as the basis for XP in the existing Score counter. Dredge awards 100 base XP and has 375 HP in round 5 and 450 HP in round 10.

## Specification Impact
Updates the Hoard arena rules for spawning, scoring, and Dredge health.

## Risks
The scalar is uncapped; batch sizes and rewards grow at later rounds.

## Follow-up Items
None. Hoard run, awareness, combat, live arena, and map travel tests passed.
