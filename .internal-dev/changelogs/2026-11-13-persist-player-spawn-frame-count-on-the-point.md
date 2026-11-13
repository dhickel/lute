# Persist player spawn frame count on the point

## Date
2026-11-13

## Git Commit

bf24bedf64d06e5a019c0ff851e7470938c0ff9a

## Change Summary
Reproduced the recurring keyFrames error and repaired the actual PlayerSpawn instance with an explicit integer keyFrames=8. The prior fix changed only a class default, which had returned to zero in the working project file. The source of that external rewrite was not established.

## Files
- `assets/maps/test_tiled.tmx`
- `core/src/test/java/io/mindspice/lute/engine/tiled/TiledAnimationPropertiesTest.java`
- `.internal-dev/knowledge/point-player-spawning.md`
- `.internal-dev/bugs/.archive/player-spawn-zero-keyframes/report.md`

## Behavioral Impact
PlayerSpawn id18 now overrides the PlayerCharacter class default. Production parsing and positive-value checks are unchanged; invalid explicit overrides still fail. The obsolete player tile's eight frames and the trees' ten frames are independent of the point. No loader precedence bug was found.

## Specification Impact
Specification Impact: none. This fixes the saved spawn configuration without changing the animation or property-precedence contract.

## Risks
A future explicit zero/negative value saved on the spawn point still intentionally fails. Other user-authored project, map, tileset, and PhysicObjectProps edits remain preserved and outside this commit.

## Follow-up Items
Focused loader regression verifies the saved point against class defaults zero/eight and eight loaded precedence cases (absent, zero, negative, positive explicit values); tree values, missing-required-field errors, and exact atlas/animation diagnostics also pass. Core/desktop compilation and manifest tests pass with rerun-tasks. No game smoke run. Reopened GitHub issue2 for recurrence; close it with this fix commit after validation.
