# Include local sword tuning and formatting before merge

## Date

2026-11-14

## Git Commit

fd5bc03ac01e50eb4ed343756a98a64b921d9343

## Change Summary

Include the user's remaining local changes in PR #9: sword hit and attack-sound frames are both 0; retain formatting changes in `WeaponAssets`, `AnimationSystem`, and `CombatSystem`. Remove trailing whitespace from a formatted blank line.

## Files

- `assets/data/weapons.json`
- `core/src/main/java/io/mindspice/lute/engine/assets/WeaponAssets.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/AnimationSystem.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/CombatSystem.java`

## Behavioral Impact

The tutorial sword activates its hitbox and emits its attack sound at animation start rather than later frames. Formatting changes have no behavioral impact. Existing event dispatch timing still applies.

## Specification Impact

Specification Impact: none. This is authored timing within the existing zero-based frame contract, not a contract change.

## Risks

The full `./gradlew check lwjgl3:classes --continue` run passed 185 native combat checks, asset service, data, manifest, Tiled animation, trigger debug, and desktop compilation, but still failed on the independently verified pre-existing movement-event assertion in issue #6. No new failures were observed. Diff whitespace and global policy synchronization checks passed.

## Follow-up Items

User requested inclusion of all local changes and merge of PR #9 into master. Issue #6 remains outside this refactor.
