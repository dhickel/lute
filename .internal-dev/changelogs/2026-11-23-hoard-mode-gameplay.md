# Hoard mode alpha gameplay

## Date
2026-11-23

## Git Commit

1be04de4f211218be4e6b7d0cc35b0a97f691cb1

## Change Summary
Add map-scoped hoard rounds, reinforcement spawning, kill scoring, clock-driven lighting, and a compact HUD. Arena player death is terminal; ordinary-map respawn remains unchanged.

## Files
- `core/`: hoard run rules, Tiled spawn metadata, placement, combat, lifecycle, HUD, and tests.
- `lwjgl3/`: native desktop integration tests.
- `README.md`, `docs/`, and `.internal-dev/specifications/`: authoring and behavior contracts.
- Local ignored assets: `raw_assets/game.tiled-project`, `raw_assets/maps/hoard_mode/hoard_mode.tmx`, `raw_assets/data/weapons.json`, and generated counterparts.

## Behavioral Impact
The arena starts with five enemies, adds reinforcements at randomized 20–40 second intervals, and refreshes the living player to 1,000 HP each three-minute round without deleting survivors. Each player-attributed kill adds five points. The existing world clock drives night rendering and a 15% spawn-budget bonus. Spawns participate in combat consistently when consecutive physics ticks run in the same frame, even before Ashley registration; lethal contact takes precedence over a round rollover.

## Specification Impact
Updated spawn-area authoring, clock-driven arena lighting, weapon damage bounds, and terminal arena-player death contracts.

## Risks
Night uses the interpreted overnight interval 18:00 through 10:00, exclusive at 10:00. The local map and balance data remain Git-ignored; the provisioning recipe is in `docs/asset-service.md`. Independent validation passed offline Gradle `check`, desktop arena and map travel tests, and native Box2D regression probes.

## Follow-up Items
Playtest spawn density and combat balance using the documented alpha values.
