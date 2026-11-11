# Correct camera map property names

## Date
2026-07-19

## Git Commit

4fa145f665ddcaa6bcc81405139069538e6616f8

## Change Summary
Changed the two Tiled map property lookup strings in CameraSystem from camelCase to lowercase: `tilewidth` and `tileheight`. Preserved the user's other in-progress changes.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/CameraSystem.java`

## Behavioral Impact
Tile dimensions can now be read using the property names supplied by LibGDX, allowing map boundary calculations to receive nonzero dimensions.

## Specification Impact
Specification Impact: none. This corrects property lookup spelling without changing the intended camera behavior.

## Risks
Runtime behavior has not been visually tested.

## Follow-up Items
`./gradlew core:compileJava` exited successfully. Changes are uncommitted because CameraSystem contains the user's staged and unstaged implementation work, which must not be included in an agent commit.
