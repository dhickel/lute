## Date
2026-11-26

## Git Commit

0b9e2a8960c0b77c9fd26791ce9cf5839351baa2

## Change Summary
The arena axe secondary uses the primary attack's hitbox geometry from frame zero through the last spinning tick.

## Files
- `CombatSystem.java`, `PhysicSystem.java`, and `HoardHammerDemoTest.java`
- `.internal-dev/specifications/composite-characters.md`

## Behavioral Impact
The hitbox follows the player’s facing and pushes overlapping enemies outward at a bounded speed, without launching them. Solid cover blocks the push. Secondary animation, damage, and cooldown remain unchanged, and each target takes damage at most once per spin.

## Specification Impact
Updates the arena axe secondary contract.

## Risks
Targets can now be hit before the original secondary hit frame, as intended for continuous coverage.

## Follow-up Items
None. Hammer Demo, Hoard arena, and combat tests passed.
