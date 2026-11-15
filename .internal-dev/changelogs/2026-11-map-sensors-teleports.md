# Map sensors and same-map teleports

## Date

2026-11-15

## Git Commit

6d61831f522844d9952a5982ca8b1cbafc6cbe8f

## Change Summary

Implemented Tiled map sensors, spawn markers, and same-map teleports. Player contact produces typed sensor enter and exit events; combat and map sensors share the existing Box2D contact listener.

## Files

- `core/src/main/java/io/mindspice/lute/engine/` — added `Player`, `MapSensorSystem`, and `MapTriggers`; integrated events, map loading, physics, combat, entity creation, and debug rendering.
- `assets/game.tiled-project`, `assets/maps/test_tiled.tmx` — defined the `Sensor`, `Teleport`, `SpawnPoint`, and `SpawnArea` classes and enums, and added a same-map teleport example.
- `core/src/test/java/io/mindspice/lute/engine/` — added parser and native Box2D sensor tests; updated existing combat, trigger-debug, and Tiled animation regression tests.
- `core/build.gradle` — added test configuration.
- `docs/asset-service.md`, `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/ashley-entity-visibility.md` — documented authoring, decisions, and entity visibility behavior.

## Behavioral Impact

- Player physical fixtures generate one enter and exit per sensor visit, including across multiple fixtures. Events and teleports are processed outside the locked physics callback.
- A `PLAYER` spawn point creates the map-owned player. Other spawn points and spawn areas remain parsed metadata.
- A `THIS` teleport moves the player to a named `TELEPORT` spawn point, clears velocity, reconciles sensor events, and suppresses arrival-portal activation until the player exits and re-enters.
- Non-`THIS` targets log an unsupported-target message on entry; they do not transition maps.
- Trigger data is validated when loaded, including geometry, names, types, and local teleport destinations. Map replacement and cleanup remove owned sensor bodies.

## Specification Impact

Updated the asset documentation and recorded the implementation decisions and Ashley entity-visibility knowledge. The Tiled project schema replaces the obsolete `PlayerCharacter` authoring class with spawn markers.

## Risks

Cross-map teleportation is not implemented. No manual graphical run was performed, so visual placement and in-game interaction remain unverified.

## Follow-up Items

- Manually run the sample map and verify sensor and teleport behavior.
- Implement cross-map transitions if required.
