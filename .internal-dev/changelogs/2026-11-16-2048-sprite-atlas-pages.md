# 2048 Sprite Atlas Page Defaults

## Date
2026-11-16

## Git Commit

47d777ac77d23d81aaf5cf7057f3f742d8929bb4

## Change Summary
Changed sprite packing defaults to 2048×2048 maximum page dimensions and regenerated the six sprite atlas families. The current output uses seven sprite pages instead of 27: one each for character, head, body, hands and feet, and two for weapons. Generation removed 20 stale pages; owned hashes decreased from 49 to 29.

## Files
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java`
- Generated atlas descriptors and texture pages in `assets/atlases/`, and `assets/.runtime-assets-owned`.
- `README.md`, `docs/asset-service.md`, `.internal-dev/specifications/composite-characters.md`, `.internal-dev/knowledge/composite-sprite-assets.md`.

## Behavioral Impact
Sprite `pack.json` settings inherit 2048 for omitted maximum dimensions and retain explicit overrides. Smaller explicit budgets and multipage atlases remain supported. Map packing settings are unchanged and already use 2048 maxima.

All 1,280 supported frames remain present; the 192 accessories export frames remain preserved but skipped. Source canvases remain 128×128 and logical character size remains 64×64. All raw inputs, including audio, maps and data, are hash-identical; player and weapon raw and runtime data remain byte-identical.

## Specification Impact
The composite-character asset contract now records the default maximum page dimensions and partial-override behavior. No canvas, geometry, weapon-stat or equipment-slot contract changed.

## Risks
An individual 2048×2048 RGBA page has four times the capacity of a 1024×1024 page, although the regenerated assets use fewer textures. Texture-size limits depend on the target driver; desktop rendering passed with the new pages.

## Follow-up Items
Live user review remains available. No push or PR update is performed for this local request.

## Validation
- `:util:texturePackerToolTest` passed, including a synthetic 1100×32 canvas, absent and partial `pack.json` settings, explicit small page budgets, paging and stale-page cleanup.
- `:lwjgl3:compositeRenderTest` and `:lwjgl3:compileJava` passed. The game test reported 15,814 checks and 480 native-frame comparisons across both weapon pages; synthetic rendering reported 11,807 GL checks, including helmet-order and pairwise-priority coverage.
- Repeated generation produced byte-identical owned outputs and manifest, covering 29 ownership hashes. A post-build repeat also matched every runtime file byte-for-byte.
- An initial broader comparison flagged only ignored, unowned `assets/assets.txt`, which `processResources` updates through `generateAssetList` after page filenames change. This was not generator nondeterminism; the owned atlas outputs were identical.
- Source hash comparisons, diff checks and global policy synchronization passed. The full suite was not run for this page-budget change; the known baseline input issue remains separate.
