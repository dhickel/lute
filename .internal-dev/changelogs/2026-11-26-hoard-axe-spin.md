## Date
2026-11-26

## Git Commit

d0f375d6bfe55e9e6f1a97e0620d84bf5dd5954d

## Change Summary
Added a clockwise 360-degree player axe secondary in Hoard and Hammer Demo.

## Files
- `CombatSystem.java`, `AnimationSystem.java`, and `PhysicSystem.java`
- `HoardHammerDemoTest.java`
- `.internal-dev/specifications/composite-characters.md`

## Behavioral Impact
Both axes retain the authored secondary animation, frame progression, hit timing, and cooldown as the player turns through all four directional sprites. The active hitbox follows facing and hits each target at most once. Axe primaries and attacks outside Hoard are unchanged.

## Specification Impact
Updated the Hoard attack exception without changing the data schema.

## Risks
The spin uses the four authored cardinal views rather than intermediate sprites.

## Follow-up Items
None. Hoard arena, Hammer Demo, and combat regressions passed.
