## Date
2026-11-27

## Git Commit

a12b77e5e13f53225203dfc2ced1735f91041bc3

## Change Summary
Scale terrain body origins using actual tile-layer dimensions and world scale.

## Files
`TiledSystem.java` and `MapSensorSystemTest.java`.

## Behavioral Impact
Collision aligns with non-square terrain tiles; existing 32-pixel maps are unchanged.

## Specification Impact
None. Fixes placement without changing tile-local shapes or supported layer transforms.

## Risks
No new layer-transform support.

## Follow-up Items
Native sensor, map travel, and Hoard tests passed. Tracks #53.
