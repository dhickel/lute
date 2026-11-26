# Hoard progression and loot

## Date
2026-11-24

## Git Commit

619547512fe3d4a9393b8ecf97be0457f29b13d2

## Change Summary
Add tiered Hoard waves, authored health pickups, Dredge guards and loot, a run-scoped weapon inventory, and final run results.

## Files
- `core` runtime, input, and data records and tests
- `lwjgl3` Hoard arena integration tests
- `assets/input.setting.json` and local raw and regenerated Hoard assets
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
The round transition spawns 500 HP in the center rather than refilling the player. Player kills independently have a 10% chance to drop 50 HP. Dredge guards spawn every fifth round and drop a weapon bag. R and controller Y cycle collected weapons. Kill points follow the enemy's origin zone. Day/night runs at 4× in Hoard without speeding up rounds, and the final screen shows score, round, and active survival time.

## Specification Impact
Updates the Hoard arena rules, scoring, clock, and object behavior contracts.

## Risks
Focused Hoard, OpenGL/Box2D arena, input, object data, combat, time, trigger, and awareness checks passed. Existing shared-catalog and map-travel assertions also fail on baseline with the expanded local assets.

## Follow-up Items
The shared-catalog and map-travel assertions were not changed as part of this work.
