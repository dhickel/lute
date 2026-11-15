# Validate external data once and simplify trusted runtime paths

## Date

2026-11-14

## Git Commit

cc022b31a898271e2948d88ad276aa690b832c48

## Change Summary

Update `AGENTS.md` with the user's boundary-only validation policy. Adopt existing local changes removing the `Animation2D.Attack` constructor guards and using `EventEmitter` for animation sound requests. Terra reviewed the recent merges and removed repeated internal validation; root integrated and validated the cleanup.

## Files

- `AGENTS.md`
- `AssetService`, `Animation2D`, `Combat`, `CombatWeapon`, `Health`, `PlayerData`, `WeaponsData`, `AnimationSystem`, `CombatSystem`, and `EntityFactory`.
- Asset service, combat, and JSON loading tests.
- `.internal-dev/specifications/decisions.md`, `.internal-dev/knowledge/animation-initialization.md`, `docs/asset-service.md`, and `.internal-dev/reviews/boundary-only-validation-review.md`.

## Behavioral Impact

Validate player/weapon values in JSON records. Validate native geometry and cue frame limits when entity configuration meets loaded resources. Internal records, component setters, health operations, and asset-key lookups trust validated inputs. Asset-service construction and queueing no longer repeat null checks on internally supplied dependencies and definitions.

Animation retains a minimum attack frame count and revalidates it only when an already observed atlas is replaced. Factory validation is not repeated on the first ordinary animation update. Optional-state, gameplay, missing-resource, lifecycle, type, and loader-failure handling remain.

Validation passed for 218 native combat checks and all focused asset, JSON, manifest, Tiled animation, trigger debug, and desktop compilation tasks. Full validation still reports only the independently confirmed baseline movement-event assertion in issue #6. No graphical game launch was performed.

## Specification Impact

Updates the attack validation ownership and boundary-validation contract in `.internal-dev/specifications/decisions.md`. Root project instructions now explicitly prohibit repeating bounds, size, empty, nullability, and related guards inside the validated closed system.

## Risks

Internal callers now rely on the external-data validation guarantees rather than duplicate runtime guards. Invalid externally loaded replacement atlases remain rejected at the reload boundary. No weapon switching, hit sounds, new validation framework, or event lifecycle cleanup is included.

## Follow-up Items

Open a pull request against master on `refactor/trust-validated-internal-data`, including previously local project-instruction commits. Leave it unmerged as requested. Existing input issue #6 remains outside scope.
