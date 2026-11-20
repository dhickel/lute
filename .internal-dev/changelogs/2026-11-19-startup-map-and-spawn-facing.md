# Startup map and spawn facing

## Date

2026-11-19

## Git Commit

1a56ec9b16d9977e04e2a13f109736792afcf123

## Change Summary

Document and implement the required startup map name and Tiled project filename, plus actor spawn-facing authoring and facing preservation during travel.

## Files

- `GameConsts.java` and `GameCore.java`: validate and select the configured startup map and Tiled project.
- `TexturePackerTool.java`: publish the configured project filename.
- `MapTriggers.java`, `TiledSystem.java`, and `EntityFactory.java`: apply the Tiled `facing` enum to newly spawned actors.
- README, spawn knowledge, decisions, and test fixtures document and exercise the updated contract. Local ignored settings and Tiled assets were updated.

## Behavioral Impact

New player sessions and map-owned actors use their authored spawn facing. Travel preserves the live player facing. Bow, clock-start, and audio behavior are unchanged.

## Specification Impact

`decisions.md` records configurable startup assets and the Tiled spawn-facing contract.

## Risks

Existing asset trees need `startupMap` and a `Facing` enum/default in their Tiled project.

## Follow-up Items

None.

## Validation

`./gradlew --offline :core:check :util:texturePackerToolTest :lwjgl3:mapTravelTest` passed, including 104 checks in the desktop travel test. Test-expected settings and projectile data were used temporarily; prior local values were restored.
