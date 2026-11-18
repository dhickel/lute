# One-Tile Bow Crosshair

## Date
2026-11-17

## Git Commit

7c8f97d8ae78f6bc98372153351588884b3e172b

## Change Summary
Changed the starting bow crosshair offset from two map tiles to one.

## Files
`BowAim.java`, `CombatSystemTest.java`, `CompositeGameTest.java`, and `.internal-dev/specifications/composite-characters.md`.

## Behavioral Impact
Aim starts one map tile from the player center in the facing direction.

## Specification Impact
Updated the composite-character aim contract.

## Risks
Focused core and desktop checks passed, including cardinal directions on rectangular tiles. The revised offset has not been manually playtested.

## Follow-up Items
Confirm the new starting distance on the test map.
