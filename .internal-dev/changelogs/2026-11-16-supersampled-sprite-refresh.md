# Supersampled Sprite Refresh

## Date
2026-11-16

## Git Commit

a6088bf4c82894b38ad186a1d13d76828de1c596

## Change Summary
Refreshed the composite character sprite sources and generated runtime atlases from supersampled exports. The generator now reports and skips accessories export folders without modifying them, as explicitly approved by the user.

## Files
- `raw_assets/sprites/`: refreshed character, equipment, and weapon sources; preserved new `equipment/basic/accessories/` exports.
- `assets/atlases/` and `assets/.runtime-assets-owned`: regenerated runtime outputs and ownership hashes.
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`: skip accessories exports.
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`: verify source preservation and absence of an accessories atlas.
- `README.md`, `docs/asset-service.md`, `.internal-dev/specifications/composite-characters.md`, `.internal-dev/knowledge/composite-sprite-assets.md`: document the export exception and inventory.

## Behavioral Impact
The 128×128 sources retain the existing 64×64 logical character size and are not cropped or resized. The four equipment slots, weapons, and character retain their existing atlas names, region keys, and multipage behavior. Accessories are not packed or equipable; other unknown slots remain errors. All 1,472 source frames are preserved; 1,280 supported frames are imported and 192 accessory frames are skipped.

## Specification Impact
The composite character specification now explicitly permits the reported accessories-export skip while preserving source files and excluding an accessories runtime layer.

## Risks
Visual appearance still requires live user review. Accessories remain unavailable for equipment.

## Follow-up Items
Maintain the live-review hold; do not push or update PR #15 until the user authorizes it.

## Validation
- `:util:texturePackerToolTest` passed, including source-byte preservation, no accessories atlas, and rejection of other unknown slots.
- Generation completed; 49 ownership hashes and player/weapon raw-to-runtime parity were verified.
- Focused core data, combat (1,803 checks), maps (1,732 checks), actual-game GL (13,613 checks and 480 native comparisons across seven weapon pages), synthetic rendering (6,880 checks), and LWJGL compilation passed.
- Bounds review image was inspected.
- Repeated generation was byte-identical; all 1,472 source PNG hashes matched the pre-import snapshot.
- Diff checks and global policy synchronization passed. The full suite was not run for this small asset refresh; the known baseline input issue remains.
