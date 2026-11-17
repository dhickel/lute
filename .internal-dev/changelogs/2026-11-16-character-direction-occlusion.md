# Character Direction Occlusion Correction

## Date
2026-11-16

## Git Commit

1cb2ccca69d9a5b30e4edfb9bb68ae45d9b683cb

## Change Summary
Corrected composite character layer ordering so hands render behind the head in every direction, without changing the weapon's position in the order. Added independent pixel-level regressions instead of relying only on references that repeat the production order.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/CharacterCompositor.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeRenderTest.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/lwjgl3/CompositeGameTest.java`
- `.internal-dev/specifications/composite-characters.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/knowledge/composite-sprite-assets.md`
- `.internal-dev/reviews/2026-11-16-character-layer-occlusion.md`

## Behavioral Impact
Back-to-front order is weapon, base, feet, body, hands, head for UP; base, feet, body, hands, head, weapon for DOWN, LEFT and RIGHT. Unarmed characters omit the weapon. World-depth sorting, batching, canvas geometry, assets, animation timing, bounds and weapon placement remain unchanged.

## Specification Impact
Documents the corrected fixed direction-based order and the independent regression-testing lesson. No per-frame sorting system, new configuration, metadata API or accessory support is introduced.

## Risks
Alpha-mask overlap alone does not establish a visual defect. The corrected policy covers current tested poses, not every possible future equipment asset.

## Follow-up Items
Review current poses live after restarting the game. No push or PR update is performed as part of this local correction. The existing input issue #6 remains separate.

## Validation
- The independent actual-GPU regression failed before the fix on UP slash frame 2 at (61, 58), proving it detects the reported defect.
- Post-fix actual-game validation passed 15,814 checks, 480 composition/native-scale comparisons and all seven weapon pages.
- Synthetic rendering passed 11,807 real-GL checks, including isolated two-layer markers for all 15 pairs in every direction/frame and armed/unarmed actions.
- Exact source-page rank/batching assertions are retained: 10 characters, three targets and 18 draws.
- Root full check and desktop GL validation excluding only the known baseline `:core:inputSystemTest` passed. The unfiltered full gate fails only the unchanged movement-event assertion in issue #6.
- Source/runtime assets remain unchanged; all 49 ownership hashes, diff checks and global policy synchronization were verified. Full evidence is in the linked technical review.
