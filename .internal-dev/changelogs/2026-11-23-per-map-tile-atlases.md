# Per-map used-tile atlases

## Date
2026-11-23

## Git Commit

afbcf1b3625b9577b996435ef4a4a28898bfadd3

## Change Summary
Pack used map terrain into the existing per-map atlas and load it through tileset-qualified region keys.

## Files
`README.md`, `docs/asset-service.md`, `.internal-dev/specifications/decisions.md`, `FixedTmxMapLoader.java`, `AssetServiceTest.java`, `TexturePackerTool.java`, `TilesheetExtrusion.java`, `MapTileAtlas.java`, `TexturePackerToolTest.java`, `lwjgl3/build.gradle`, `MapTileAtlasTest.java`, and `GameScreenLifecycleTest.java`.

## Behavioral Impact
Terrain and objects share atlas ownership; standalone maps retain padded-sheet loading. Qualified keys preserve overlapping animation and placement identities. Regenerate assets produced by the earlier GID-only format.

## Specification Impact
Updated the terrain generation and loading decision to describe qualified keys and migration.

## Risks
Packing may overflow onto multiple pages; the measured draw reduction is fixture-specific. Previously generated atlases require regeneration.

## Validation
Independent real-GL validation reproduced identical framebuffers with 8 → 1 draws on a four-sheet fixture. The overlapping-ID and sparse-collection reproducers passed, as did codec probes. The packaged game reached gameplay with generated assets.

## Follow-up Items
None required for delivery.
