# Hoard starter weapons

## Date
2026-11-24

## Git Commit

7ec8ff41704edaed2374a8196e4e8ad023bd0379

## Change Summary
Start each Hoard visit with `basic_sword` and `basic_bow` available to cycle.

## Files
- `core/src/main/java/io/mindspice/lute/engine/components/Controller.java`
- `lwjgl3/src/test/java/io/mindspice/lute/engine/systems/HoardArenaTest.java`
- `.internal-dev/specifications/decisions.md`

## Behavioral Impact
Both weapons are immediately available via R or controller Y, without changing the equipped weapon or the ordinary-map loadout.

## Specification Impact
Documents both starter weapons in the Hoard arena rules.

## Risks
The arena and input tests passed.

## Follow-up Items
None.
