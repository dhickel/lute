# Retain map actor maximum health

## Date

2026-11-22

## Git Commit

d91ac6480e5fd16f9ec7037aca781edf07a4f159

## Change Summary

Retained the existing `max_health` field for NPC and enemy definitions, using it for both initial and maximum health. The catalogue split and required names remain unchanged.

## Files

- `MapActorData.java` and `EntityFactory.java`: Retain the original field name and health initialization.
- `DataLoaderTest.java` and `MapTravelTest.java`: Verify `max_health` and reject `base_health`.
- `README.md`, `docs/asset-service.md`, and the composite-character and decisions specifications: Document the final migration.

## Behavioral Impact

Actor definitions in `characters.json` and `enemies.json` use `max_health` only, as requested. Local raw and runtime data were updated.

## Specification Impact

The composite-character and map actor authoring specifications now describe `max_health` as the sole health field.

## Risks

Anyone who used the draft `base_health` field from the initial PR must rename it back to `max_health` and regenerate their runtime assets.

## Follow-up Items

None. Asset generation, catalogue parity, and `./gradlew :core:dataLoaderTest :lwjgl3:mapTravelTest` passed, including all 222 map-travel checks.
