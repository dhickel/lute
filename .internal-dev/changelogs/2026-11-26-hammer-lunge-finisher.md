## Date
2026-11-26

## Git Commit

0c5fe66371e11629be734093202d5919dfb36c7d

## Change Summary
Halved the Hoard hammer lunge windup, extended travel to 48 logical pixels, and enlarged the hitbox 1.5× on the final movement tick.

## Files
- `DredgeLunge.java`, `CombatSystem.java`, and `HoardArenaTest.java`
- `.internal-dev/specifications/decisions.md` and `.internal-dev/specifications/composite-characters.md`

## Behavioral Impact
Applies to the player's hammer secondary and Dredge's lunge. The swing keeps its damage window, one hit per target, and cover blocking. Other attacks and demo spawning are unchanged.

## Specification Impact
Updated the Hoard lunge contract.

## Risks
The wider finish can catch targets that would previously have dodged.

## Follow-up Items
None. Hoard arena, Hammer Demo, and combat regressions passed.
