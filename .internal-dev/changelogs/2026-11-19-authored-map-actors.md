# Authored map actors

## Date

2026-11-19

## Git Commit

da22f16c2eca536cd9db0a63c30d3998bd176665

## Change Summary

Replace fixed map actor defaults and `actors.json` with required ID-keyed NPC and enemy catalogues. Map spawn markers select definitions by `actorId`; construction uses authored geometry, health, animation, equipment and detection bounds. The README documents asset and map migration.

## Files

- `MapActorData.java`, `DataLoader.java`, `GameData.java`, and `PlayerData.java`: define and validate the new catalogues and share character bounds validation.
- `EntityFactory.java`, `TiledSystem.java`, and `MapTriggers.java`: resolve marker IDs and construct actors from their definitions.
- Core and desktop tests cover catalogue validation, marker propagation, combat compatibility and map travel.
- `README.md` and living specifications document migration. Local ignored raw/runtime actor data and `test_tiled` markers were updated but are not in the tracked diff.

## Behavioral Impact

NPC and enemy points require valid role-specific actor IDs. Malformed definitions and unknown weapon or actor IDs fail rather than falling back. NPC loadouts and nullable enemy weapons are authored; map actors remain stationary and do not attack.

## Specification Impact

`composite-characters.md` describes the actor catalogues, marker IDs and resulting actor properties. `decisions.md` updates the actor sensing and local asset data decisions.

## Risks

Externally provisioned asset trees and maps must be migrated before use.

## Follow-up Items

Full check and interactive visual review were not run.

## Validation

Passed `./gradlew --offline :core:dataLoaderTest :core:mapTriggersTest :core:mapSensorSystemTest :core:awarenessSystemTest :core:combatSystemTest :lwjgl3:mapTravelTest`; the desktop travel test passed 103 real GL/Box2D checks. The run used a temporary test-expected view width of 21.3 and source projectile JSON; originals were restored.
