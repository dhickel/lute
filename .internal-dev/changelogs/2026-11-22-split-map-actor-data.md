# Split map actor data

## Date

2026-11-22

## Git Commit

733ea406a8456feb9dd1658669f0f578bfe6af9b

## Change Summary

Split the combined map actor catalogue into required `characters.json` and `enemies.json` files. Map actor definitions now require a nonblank `name` and positive integer `base_health`; spawned actors use base health for their initial and maximum health.

## Files

- `ActorsData.java`, `DataLoader.java`, and `MapActorData.java`: Load and validate the separate role catalogues and their fields.
- `EntityFactory.java`: Initialize map actor health from base health.
- `DataLoaderTest.java` and `MapTravelTest.java`: Cover the split catalogues, field validation, raw/runtime parity, and authored health on spawn and map travel.
- `lwjgl3/src/test/resources/fixtures/actors.json`: Remove the obsolete combined fixture.
- `README.md` and `docs/asset-service.md`: Document the data shape and migration.
- `.internal-dev/specifications/composite-characters.md` and `.internal-dev/specifications/decisions.md`: Record the new source and schema contracts.

## Behavioral Impact

NPCs load from `characters.json` and enemies from `enemies.json`; both files are required and may contain empty maps. There is no `actors.json` fallback. The `name` field is data and is not displayed in the UI. The player schema and actor awareness behavior are unchanged.

## Specification Impact

Updated the composite-character specification and map-actor awareness decision to describe the split sources, required fields, spawn health, and migration. Collision migration references now name the split files.

## Risks

Unmigrated or stale local assets will fail to load. Independently maintained asset trees may contain incorrect migrated health, graph keys, or detection radii; the focused checks do not validate every checkout.

## Validation

- `./generate-runtime-assets.sh` generated one map. Both split files match between raw and runtime assets; both trees lack `actors.json`.
- `./gradlew :core:dataLoaderTest :lwjgl3:mapTravelTest` passed, including 222 real GL and Box2D map-travel checks and sample actor respawn.
- The full test suite was not run.

## Follow-up Items

The usual checkout's ignored raw and runtime assets have been migrated. Other checkouts must follow the README migration instructions, preserving graph keys and detection radii, then regenerate their runtime assets.
