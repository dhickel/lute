# Composite Atlas Pipeline

## Date
2026-11-16

## Git Commit

bba356304251ac519ed880df406a970a8f60d202

## Change Summary
Generated the character, head, body, hands, feet, and weapons atlases, including `weapons2.png`, with ownership recorded. Preserved all 1,440 raw frames.

Recorded the accepted composite-character runtime contract and asset knowledge, and updated the decisions, index, deferred, and horizon records.

## Files
- `util/src/main/java/io/mindspice/lute/engine/util/TexturePackerTool.java`
- `util/src/test/java/io/mindspice/lute/engine/util/TexturePackerToolTest.java`
- Generated character, head, body, hands, feet, and weapons atlas assets under `assets/atlases/`
- `assets/.runtime-assets-owned`
- `.internal-dev/specifications/composite-characters.md`
- `.internal-dev/knowledge/composite-sprite-assets.md`
- Decisions, index, deferred, and horizon records

## Behavioral Impact
Atlas generation is available through the utility. The accepted runtime contract is documented, but runtime implementation was not delivered in Phase 1. Cast animations are optional; no magic capability is implied.

## Specification Impact
Added the composite-character specification and recorded the relevant decisions and future work.

## Risks
The documented runtime contract is not yet implemented.

## Follow-up Items
Implement the accepted runtime contract in the dependent phases.

## Validation
Independent validator returned PASS. `./gradlew :util:texturePackerToolTest --no-daemon` and `./gradlew :util:generateRuntimeAssets --no-daemon` passed. Independent pixel audit checked all 1,440 regions, including 132,714 visible and 5,765,526 transparent pixels. Multipage, collision, ownership, stale-output and rollback checks passed. No validator edits.
