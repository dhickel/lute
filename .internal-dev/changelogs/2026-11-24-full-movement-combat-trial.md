# Full movement combat trial

## Date
2026-11-24

## Git Commit

80be5e489bc28701aacc57d10f8bb1244342809e

## Change Summary
Player movement now continues at full speed during committed attacks while preserving attack facing and hitbox orientation.

## Files
Combat state, combat and movement systems, combat regression tests, and composite-character specification.

## Behavioral Impact
Directional input cancels recovery when permitted by cooldown or animation completion and the first melee hit step. Removed the one-step movement limit; bow aiming and NPC behavior remain unchanged.

## Specification Impact
Documented the trial's full movement and retained attack facing.

## Risks
Focused automated checks passed. Combat feel still requires user playtesting.

## Follow-up Items
Keep the trial local pending playtest.
