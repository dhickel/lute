# Complete point-based player spawning

## Date
2026-11-12

## Git Commit

6d23bba81528d8ef5efee8fc552b021a360de177

## Change Summary
Completed the in-progress EntityFactory player initialization and Triggers-layer integration. The new point-spawned player owns input and camera follow; the original tile player remains visible and passive. New creation resolves loaded atlas assets and uses a code-defined dynamic feet collider rather than a tile definition.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/TiledAshleyLoader.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PlayerCharProps.java`
- `assets/maps/test_tiled.tmx`
- `assets/game.tiled-project`
- `.internal-dev/knowledge/point-player-spawning.md`

## Behavioral Impact
PlayerCharacter points receive class defaults omitted from TMX. Spawn positions are converted once from map pixels. All player components and initial animation are ready before Ashley insertion. The old placement explicitly disables controller and camera. Core/desktop compilation and an independently rerun temporary desktop harness passed: two players, one controller/camera, movement and animation transitions, boundary collision, camera following, collider parity, and invalid-input rejection. Audio was disabled in the harness. No permanent tests added.

## Specification Impact
Specification Impact: none. Existing living specifications do not define player spawning; this completes the user's in-progress refactor without altering their documented contracts.

## Risks
Runtime class defaults must stay aligned with the Tiled project. Existing Teleport rectangle remains unsupported by the trigger handler and logs a warning; teleport implementation is outside scope. Map replacement persistence is unchanged.

## Follow-up Items
None required for initial game startup.
