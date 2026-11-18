# Bow Damage and Arrow Depth

## Date
2026-11-17

## Git Commit

d28a2299ae719d39656c8745b798603d7d9fff05

## Change Summary
Scaled bow damage by the draw frame sampled at release and moved arrow rendering behind world entities.

## Files
`CombatSystem.java`, `RenderSystem.java`, `CombatSystemTest.java`, and `.internal-dev/specifications/composite-characters.md`.

## Behavioral Impact
Draw frames 00, 01, and 02 deal 25%, 50%, and 75% damage; frame 03 onward deals full damage. Arrows render after the map background and before depth-sorted entities. Crosshair ordering is unchanged.

## Specification Impact
Updated the composite-character contract for release damage and render ordering.

## Risks
No targeted arrow/entity overlap assertion or new manual playtest was performed.

## Follow-up Items
Review arrow overlap and play feel in gameplay.
