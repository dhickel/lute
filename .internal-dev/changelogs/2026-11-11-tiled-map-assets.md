# Tiled map asset references

## Date
2026-11-11

## Git Commit

34411686b2d04bed9121da6e14fcaa548a902bbc

## Change Summary
Refactored `TiledSystem` map asset handling so the load event carries manifest `MapAssets`, the system retains active assets for map unload, and `TiledAshleyLoader` resolves atlas IDs through those assets. `Animation2D` and `AnimationSystem` now consume `Asset.Atlas`. Existing screen startup uses the new atlas record and manifest.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/assets/tiled/TiledAshleyLoader.java`
- `core/src/main/java/io/mindspice/lute/engine/events/Event.java`
- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/AnimationSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/GameScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/screen/LoadingScreen.java`
- `core/src/main/java/io/mindspice/lute/engine/GameCore.java`

## Behavioral Impact
Map loading uses the manifest's active assets to resolve atlas IDs and retain asset information for unload.

## Specification Impact
None. No asset specification exists.

## Risks
Atlas preloading remains limited to `objects.atlas`. End-to-end map switching was not validated.

## Follow-up Items
Validate map switching end to end. Revisit the `objects.atlas` preload limitation if additional atlases are required.

## Validation
- `./gradlew :core:compileJava :core:manifestLoaderTest` passed.
- A temporary focused test checked default and missing atlas IDs.
