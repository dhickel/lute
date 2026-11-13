# Support Tiled point map objects

## Date
2026-11-12

## Git Commit

15375a97f581147935b10fe9a0745a869f873b4d

## Change Summary
Accept point objects in the existing Tiled object layers without creating entities for them. They remain accessible on the loaded map.

## Files
- `core/src/main/java/io/mindspice/lute/engine/systems/TiledSystem.java`

## Behavioral Impact
Point objects no longer cause an unsupported-object error; other unsupported types remain errors.

## Specification Impact
None; no existing specification governs Tiled point objects.

## Risks
Consumers must convert point coordinates to world units as appropriate.

## Follow-up Items
None.
