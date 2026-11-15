# Align collision and detector ellipses

## Date
2026-11-15

## Git Commit

d54cb0c538f8c334224d4759ffa426d5c836ed39

## Change Summary
Align Tiled collision ellipse construction with detector geometry.

## Files
`core/src/main/java/io/mindspice/lute/engine/tiled/TiledPhysics.java`, `core/src/test/java/io/mindspice/lute/engine/tiled/MapTriggersTest.java`, and `.internal-dev/specifications/decisions.md`.

## Behavioral Impact
True circles use circle fixtures when collision scaling is uniform. Non-circular ellipses use eight-sided polygon fixtures, including near-circles previously approximated as circles. Tiled rotation uses the same anchor as detector geometry; collision scaling and relative offsets remain applied.

## Specification Impact
Updated the sensor and collision ellipse decision to document the shared shape and rotation convention.

## Risks
Existing near-circular or rotated collision shapes can have different fixture geometry. No graphical gameplay run was performed.

## Follow-up Items
None.
