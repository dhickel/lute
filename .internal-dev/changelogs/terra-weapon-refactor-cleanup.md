# Terra review cleanup of internal validation and timing tests

## Date

2026-11-14

## Git Commit

06fe7654723cdfc4cf963e23fbcbd2fbb7d74629

## Change Summary

Terra reviewed the weapon refactor for redundant defensive code and correctness issues. Removed repeated null validation from `Animation2D.configureAttack`; root integration removed the equivalent check from `Combat.setSensor`. Assignment methods trust guarantees already established by runtime weapon construction and Box2D fixture creation.

Tests now follow authored sword timing and separately exercise nonzero JSON frame mapping. User edits to weapon JSON and `WeaponAssets` formatting remain uncommitted and unchanged.

## Files

- `core/src/main/java/io/mindspice/lute/engine/components/Animation2D.java`
- `core/src/main/java/io/mindspice/lute/engine/components/Combat.java`
- `core/src/test/java/io/mindspice/lute/engine/combat/CombatSystemTest.java`
- `core/src/test/java/io/mindspice/lute/engine/data/json/DataLoaderTest.java`
- `.internal-dev/reviews/terra-weapon-refactor-cleanup.md`
- `.internal-dev/knowledge/animation-initialization.md`

## Behavioral Impact

No intended gameplay change. External input validation, runtime construction invariants, optional-state handling, frame bounds, and Box2D safety remain intact. Internal null assignment no longer throws at the redundant assignment boundary.

Root independently passed focused asset, manifest, data, and native combat tests plus desktop compilation. The current zero-frame sword tuning results in 185 combat checks. Diff whitespace validation and global policy comparison passed.

## Specification Impact

Specification Impact: none. The accepted ownership and timing contracts remain unchanged; this cleanup trusts existing invariants and removes test dependence on a particular authored timing.

## Risks

Internal callers must continue supplying the validated attack data and newly created fixture. No graphical game launch or new full-suite run was performed. The known unrelated input movement-event failure remains tracked in issue #6.

## Follow-up Items

Review evidence is retained in `.internal-dev/reviews/terra-weapon-refactor-cleanup.md`. No additional fixes were identified within the reviewed scope.
