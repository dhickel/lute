# Fix axe reach in all four directions

## Date
2026-11-25

## Git Commit

4177852db16f266b3d1447cfffdf9022ee4f9341

## Change Summary
Move the up, left, and right hitboxes forward for both attacks on the basic and black axes. All 16 axe cases at 48 px landed after the fix; before, only down-facing attacks landed at that distance. The 224-case native probe and the focused combat, Hoard, and map travel tests passed.

## Files
Local Git-ignored `raw_assets/data/weapons.json` and generated `assets/data/weapons.json`.

## Behavioral Impact
The affected attacks now reach the tested target at 48 px in all four directions. Down-facing offsets, hitbox dimensions, damage, and attack timing are unchanged.

## Specification Impact
None. The fix uses the existing per-facing offset contract.

## Risks
Contact distance depends on the target hurtbox and alignment. The asset values are local and not carried by Git.

## Follow-up Items
None.
