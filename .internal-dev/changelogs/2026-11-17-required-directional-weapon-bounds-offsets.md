# Required directional weapon bounds offsets

## Date
2026-11-17

## Git Commit

bdaa43aa5e5a5ef7c3460715cf1d201ac6efd4ff

## Change Summary
Added required per-facing X/Y offsets to weapon bounds and translated attack geometry after its existing rotation. All six authored attacks use explicit zero offsets.

## Files
- `core/src/main/java/io/mindspice/lute/engine/data/json/WeaponsData.java`
- `core/src/main/java/io/mindspice/lute/engine/components/CombatWeapon.java`
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- Focused data-loader, combat, map-sensor, and composite-render test fixtures.
- `.internal-dev/specifications/composite-characters.md`
- Local ignored `raw_assets/data/weapons.json` and matching generated `assets/data/weapons.json`.

## Behavioral Impact
`bounds` requires `offset_up`, `offset_down`, `offset_left`, and `offset_right`, each `{ "x": 0, "y": 0 }` in current definitions. Missing, null, incomplete, or nonfinite offsets fail at the JSON boundary. Offsets are logical-local pixels, selected by facing and applied after rotation; positive X is right and positive Y is up. Existing geometry is unchanged with zero offsets.

## Specification Impact
Updated the Composite Characters attacks contract with required fields, units, defaults, and transform order.

## Risks
Raw and generated asset trees are intentionally ignored by Git; external asset provisioning must carry the updated weapon JSON. No live visual tuning was performed; current zero offsets intentionally preserve placement.

## Follow-up Items
None required for this change. Focused validation passed: `./gradlew :core:dataLoaderTest :core:combatSystemTest :core:mapSensorSystemTest :core:assetServiceTest :lwjgl3:compileTestJava`. Combat checks exercise signed translations in all four directions for every bounds shape. Data checks exercise required offsets, required axes, nonfinite values, zero defaults, and raw/generated equality. Native-access warnings remain informational. `git diff --check` passed. An initial test compilation exposed two stale constructors, both migrated before successful validation.
