# Per-Attack Sounds

## Date
2026-11-16

## Git Commit

c7586cc96ced47b32725bcaa0638f2982e751f42

## Change Summary
Implemented per-attack weapon sound ownership and updated the composite-character specifications and asset documentation.

## Files
- `raw_assets/data/weapons.json`, `assets/data/weapons.json`, `assets/.runtime-assets-owned`
- Core WeaponAssets, WeaponsData, EntityFactory and asset/combat/data tests.
- `README.md`, `docs/asset-service.md`
- Composite-character specification, decisions, index and asset knowledge.

## Behavioral Impact
Primary and secondary attacks independently define attack and hit sound paths and use distinct `weapon/<id>/<primary|secondary>/<attack|hit>` IDs. Both sounds are loaded for every configured attack, including unarmed. Existing WAV files are reused; hit sounds are not played.

## Specification Impact
Updated the accepted sound ownership and loading contract and removed stale phase 4 implementation status. Refreshed authoring documentation for composed atlases, loadouts and attacks.

## Risks
Shared filenames can conceal incorrect slot selection unless tests verify logical IDs and distinct paths.

## Follow-up Items
Complete final integration validation.

## Validation
Independent phase validation returned PASS with no edits. DataLoaderTest, AssetServiceTest and CombatSystemTest (1,619 checks) passed. CompositeGameTest (9,917 checks and 384 framebuffer comparisons) and CompositeRenderTest (1,402 real-GL checks) passed. Raw/generated weapons JSON is byte-identical; ownership audit checked 29 entries with no hash mismatches. Diff check and README/asset documentation consistency passed. Full suite remains for the final integration gate; known unrelated baseline input issue #6 remains.
