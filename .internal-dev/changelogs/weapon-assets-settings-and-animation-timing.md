# Weapon assets, startup settings, and animation-driven attack timing

## Date

2026-11-14

## Git Commit

b35af15222fbf2de45b69b92cf4637192e051da0

## Change Summary

Move weapon asset ownership out of map manifests. Load the equipped weapon's sound definitions alongside initial map assets and retain combined tutorial sprites. Load game constants and asset-directory prefixes from `assets/game.settings.json` before runtime initialization.

Use animation progress instead of an independent combat duration. Combat supplies playback speed and the resolved attack-sound cue; animation emits that sound once. Combat owns hit-frame activation, confirmed damage, and sensor cleanup. Preserve the user's current sword tuning: hit frame 1 and sound frame 2.

## Files

- `assets/game.settings.json`, `assets/data/weapons.json`, and `assets/maps/test_tiled.manifest.json`.
- `GameConsts`, `GameCore`, `Asset`, `WeaponAssets`, `WeaponsData`, and `DataLoader`.
- `Animation2D`, `AnimationState`, `AnimationPlayerSystem`, `AnimationSystem`, `Combat`, `CombatWeapon`, `CombatSystem`, `EntityFactory`, and `GameScreen`.
- Asset, data, manifest, and native combat tests; `docs/asset-service.md`; living decisions and animation knowledge.

## Behavioral Impact

Only the equipped weapon adds sound loads. Runtime sound IDs are independent of filenames. Weapon JSON uses a shared atlas field and nested definitions; shared atlas and per-weapon region defaults are `NONE`. Selecting separate weapon visuals fails explicitly. Frame indices are zero-based, and playback speed scales both cues and attack lifetime. Hit sounds are loaded but not emitted.

Animation updates before combat, which updates before physics. Attack acceptance configures playback for the following update. Sensor lifetime starts at the configured hit frame and ends at animation completion, death, or component/entity/system removal. Animation objects without combat retain ordinary playback.

Validation passed: focused asset, data, manifest, and 193 native combat checks, plus desktop compilation. `check` excluding `core:inputSystemTest` passed. The full check reproduces issue #6's existing movement-event assertion; the identical failure was confirmed from baseline commit `a1d69f3` in an isolated source snapshot. No graphical game launch was performed.

## Specification Impact

Supersedes the explicit combat-duration decision with animation-driven timing. Adds settings initialization and weapon asset ownership contracts to `.internal-dev/specifications/decisions.md`; updates `docs/asset-service.md` accordingly.

## Risks

Frame thresholds must be within the actual attack animation. Settings are required at runtime; directory values must end with `/`. A large update or short active interval can pass without a fixed physics tick and therefore produce no hit. Sound dispatch retains its existing event-queue timing.

## Follow-up Items

No additional features are included. Weapon switching, separate weapon rendering, and hit-sound playback remain outside this change. Existing movement-event issue #6 and audio listener cleanup issue #8 remain open.
