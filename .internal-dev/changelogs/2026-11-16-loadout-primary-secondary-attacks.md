# Loadout and Primary-Secondary Attacks

## Date
2026-11-16

## Git Commit

c79e83059658be74f2e2324faf973dd8e9bf9aad

## Change Summary
Implemented phase 2 loadout, attack selection, shared asset loading, and animation validation.

## Files
- Core runtime: GameCore, Loadout, Animation2D, Combat, CombatWeapon, Controller, CharacterAssets, AnimationData, PlayerData, WeaponsData, InputAction, AnimationSystem, CombatSystem, ControllerSystem, EntityFactory.
- Tests: AssetServiceTest, CombatSystemTest, DataLoaderTest, MapSensorSystemTest.
- `raw_assets/data/player.json`, `raw_assets/data/weapons.json`, generated counterparts under `assets/data/`, `assets/.runtime-assets-owned`.
- `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/composite-sprite-assets.md`, existing movement-event bug record.

## Behavioral Impact
Initial loading queues sounds for all configured weapons, including unarmed. Runtime loadouts support equipment and weapon switching subject to combat state and loaded-asset validation. Primary and secondary attacks select their configured actions; bow projectile execution remains deferred.

## Specification Impact
Corrected the asset ownership decision to reflect loading all configured weapon sounds rather than only the initially equipped weapon. Composite rendering remains planned.

## Risks
Layer composition, world-depth ordering, and alpha handling are not yet implemented or validated. The unrelated movement-event baseline failure remains tracked in GitHub issue #6.

## Follow-up Items
Implement and validate phase 3 composite rendering.

## Validation
Independent phase validator returned PASS with no manual repairs. Generator, texturePackerToolTest, dataLoaderTest, combatSystemTest (1,595 checks), mapSensorSystemTest (1,696), assetServiceTest, and lwjgl3:compileJava passed. Raw/generated comparisons and diff checks passed. Root independently reran dataLoaderTest, combatSystemTest and lwjgl3:compileJava successfully.

inputSystemTest fails with the identical pre-existing controllerMovementEvent assertion at baseline c79e830 (expected 1, got 0), tracked in GitHub issue #6. It is not caused by the attack input changes.
