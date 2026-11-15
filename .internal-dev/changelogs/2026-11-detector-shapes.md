# Filled detector shapes

## Date

2026-11-15

## Git Commit

db59ce7da4d3d27f2be7cd1a238c9cad1e72d189

## Change Summary

Implemented the approved Tiled detector shape contract for sensors and teleports, replacing rectangle-only geometry with filled, rotatable rectangles, circles, ellipses, and simple polygons.

## Files

- `core/src/main/java/io/mindspice/lute/engine/tiled/DetectorGeometryParser.java` — parses, transforms, validates, and decomposes detector geometry.
- `core/src/main/java/io/mindspice/lute/engine/tiled/MapTriggers.java` — stores detector geometry and retains separate spawn validation.
- `core/src/main/java/io/mindspice/lute/engine/systems/MapSensorSystem.java` — creates fixtures and reconciles logical contacts and teleports.
- `core/src/test/java/io/mindspice/lute/engine/tiled/MapTriggersTest.java` and `core/src/test/java/io/mindspice/lute/engine/systems/MapSensorSystemTest.java` — cover geometry, validation, contacts, seams, and teleport reconciliation.
- `docs/asset-service.md` and `.internal-dev/specifications/decisions.md` — document the authoring contract and decision.

## Behavioral Impact

Non-circular ellipses use a fixed eight-sided approximation. Concave polygons and polygons with more than eight vertices are decomposed into convex fixtures while remaining one logical detector: crossing fixture seams does not produce duplicate enter or exit events, and teleport reconciliation uses the fixture union.

Points and polylines are excluded as detector shapes, as are text and tile objects. `SpawnArea` remains an unrotated, positive-size rectangle. Invalid, self-intersecting, degenerate, non-finite, or Box2D-unsafe geometry is rejected rather than passed to physics.

## Specification Impact

Updated the sensor, teleport, and spawn decision and authoring documentation to reflect the approved shape contract and its limitations.

## Risks

Ellipse boundaries are approximations, and compound detector behavior depends on fixture-level overlap reconciliation. No graphical test was performed.

## Follow-up Items

Perform a graphical check of representative rotated and compound detectors if visual verification is needed.
