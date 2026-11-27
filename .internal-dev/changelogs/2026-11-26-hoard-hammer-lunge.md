## Date
2026-11-26

## Git Commit

cf3409da985928389f5e4f925ab18f11a97a955e

## Change Summary
Added the Hoard hammer lunge exceptions and main-menu Hoard Hammer Demo.

## Files
- `core/src/main/java/io/mindspice/lute/engine/` — arena entry, combat, physics, AI, and loadout wiring.
- `lwjgl3/src/test/java/io/mindspice/lute/engine/` — arena, demo, and menu regressions.
- `.internal-dev/specifications/decisions.md` — Hoard arena decision.
- `.internal-dev/specifications/composite-characters.md` — attack contract.

## Behavioral Impact
Hoard hammer secondary attacks and the boss lunge follow the arena-specific rules. The Hammer Demo uses the Hoard map with all weapons and an unscored ordinary-enemy swarm fighting one hammer Dredge. Normal scored progression and inventory remain unchanged.

## Specification Impact
The exceptions are runtime-only and introduce no data schema changes. Behavior outside Hoard remains unchanged.

## Risks
The moving sensor, bounded carry, and cover blocking interact with the existing combat and collision behavior.

## Follow-up Items
Playtest the carry speed and heavy-attack feel on keyboard and controller. The combat, awareness, Hoard arena, Hoard Hammer Demo, and menu regression tasks passed.
