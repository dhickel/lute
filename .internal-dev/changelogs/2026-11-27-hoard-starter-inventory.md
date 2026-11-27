## Date
2026-11-27

## Git Commit

a12b77e5e13f53225203dfc2ced1735f91041bc3

## Change Summary
Move Hoard's starter weapon selection out of Controller and pass the resulting cycle through TiledSystem.

## Files
Controller.java, HoardSystem.java, TiledSystem.java, GamepadInputTest.java, HoardRosterTest.java.

## Behavioral Impact
None. The equipped weapon remains first, with exact duplicate IDs omitted.

## Specification Impact
None. Existing starter inventory and input behavior are unchanged.

## Risks
Cycle ordering and ordinary map loadouts must remain unchanged.

## Follow-up Items
Hoard roster, run, arena, input, and map travel tests passed.
