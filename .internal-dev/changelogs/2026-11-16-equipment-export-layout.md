# Equipment Export Directory Layout

## Date
2026-11-16

## Git Commit

e8cb3163b231b3450638e6e945cb054e90f7206e

## Change Summary
Reorganized equipment source frames by set and slot while retaining separate runtime atlases. Updated the generator, tests, documentation, specification, and asset knowledge.

## Files
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
- 768 raw PNG renames from slot-first directories to `raw_assets/sprites/equipment/basic/<slot>/`.
- `README.md`, `docs/asset-service.md`, `.internal-dev/specifications/composite-characters.md`, `.internal-dev/knowledge/composite-sprite-assets.md`.

## Behavioral Impact
Equipment source paths and packing inputs changed. Runtime atlas names, region keys, generated assets, data, sounds, bounds, and tuning remain unchanged.

## Specification Impact
Updated the composite-character asset contract to describe set-first equipment sources, slot-based atlas generation, shared packing settings, and validation.

## Risks
Additional sets and packing settings must preserve the region names and frame properties expected by runtime loading. Generator validation and focused tests cover these constraints.

## Follow-up Items
Live user review of the previously fitted character bounds remains pending. No push or PR update was performed.

## Validation
- `:util:texturePackerToolTest` passed, including multiple sets, multipage and stale output handling, invalid slots, missing basic slots, nested overrides, and transaction guards.
- Regeneration succeeded. All runtime asset hashes matched before and after, including 49 owned files; the weapons atlas retained seven pages.
- All 1,280 raw PNG hashes were preserved.
- `:lwjgl3:compositeRenderTest` passed: 13,613 gameplay checks, 480 native-frame comparisons across seven weapon pages, and 6,880 synthetic GL checks.
- Full suite not run for this pipeline-only change; the known baseline input issue remains unchanged.
