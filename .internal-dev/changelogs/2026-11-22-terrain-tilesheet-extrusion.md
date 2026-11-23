# Terrain tilesheet extrusion

## Date
2026-11-22

## Git Commit

3bff469e51ea1853430c441223b8f80c138e91cc

## Change Summary
The runtime asset generator now extrudes a one-pixel border around each tile in single-image tilesets and rewrites the generated Tiled definitions to describe the resulting texture layout.

## Files
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/main/java/io/mindspice/lute/engine/util/TilesheetExtrusion.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
- `README.md`, `docs/asset-service.md`, and `.internal-dev/specifications/decisions.md`

## Behavioral Impact
Tiled authoring in `raw_assets/` is unchanged. The generator checks the authored source grid, publishes padded PNGs with matching metadata, and leaves runtime rendering unchanged. Focused tests passed and generation succeeded against a temporary copy of the provisioned assets.

## Specification Impact
The terrain extrusion, grid validation, metadata, and ownership contract is recorded in `decisions.md`.

## Risks
Padding addresses texture bleed, not coverage gaps. Padded sheets use more texture memory.

## Follow-up Items
Confirm visually at the resolution and scale that showed the lines; automated tests verify asset generation, not the rendered seam.
