# Diagnose inherited player animation frame default

## Date
2026-11-12

## Git Commit

0853965f1716937922052e8726ad99a6ccd975b6

## Change Summary
Reproduced the reported zero-keyFrames crash through the configured Tiled property loader without launching the game. Corrected the working PlayerCharacter project-class default from zero to eight; this restores the committed baseline value while preserving the user's other class edits. Improved animation errors to identify the atlas and animation key. Added a focused loader regression check following existing JavaExec test conventions. Corrected a stale ManifestLoader import left by the earlier package relocation so focused tests compile normally.

## Files
- `assets/game.tiled-project` (working-copy default restored; unrelated additions remain uncommitted)
- `core/src/main/java/io/mindspice/lute/engine/systems/EntityFactory.java`
- `core/src/main/java/io/mindspice/lute/engine/tiled/properties/PlayerCharProps.java`
- `core/src/test/java/io/mindspice/lute/engine/tiled/TiledAnimationPropertiesTest.java`
- `core/src/test/java/io/mindspice/lute/engine/assets/tiled/ManifestLoaderTest.java`
- `core/build.gradle`
- `.internal-dev/knowledge/point-player-spawning.md`
- `.internal-dev/knowledge/tiled-collision-properties.md`
- `.internal-dev/bugs/.archive/player-spawn-zero-keyframes/report.md`

## Behavioral Impact
Asset.Map supplies projectFilePath, so libGDX applies project-class defaults even when TMX omits the property. PlayerSpawn object 18 inherited zero; PlayerCharProps fallback8 did not replace a present value. All five trees correctly retained ten. Strict frame validation remains; error context now identifies the failing animation.

## Specification Impact
Specification Impact: none. This repairs configuration and diagnostics without changing the intended animation contract. Prior knowledge claiming the loader omitted class defaults was corrected.

## Risks
Explicit invalid placement overrides continue to fail intentionally. AnimatedPhysicObject still has a zero project default, so its placements/tiles must override it with a positive value.

## Follow-up Items
Core/desktop compilation and normal tiledAnimationPropertiesTest/manifestLoaderTest tasks passed. Regression coverage includes inherited player defaults, authored tree values, placement precedence, missing fields, and rejection of zero/negative frames with asset context. No game or desktop smoke run. GitHub mirror: https://github.com/dhickel/lute/issues/2 .
