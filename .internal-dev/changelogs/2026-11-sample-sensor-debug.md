# Sample sensor event visibility

## Date
2026-11-15

## Git Commit

5222020f6eee237a2690bcbd009250e1d576d75b

## Change Summary
Added a named general sensor near the player spawn and debug logging for dispatched sensor enter and exit events, including when no listener subscribes.

## Files
`assets/maps/test_tiled.tmx`, `EventSystem.java`, and existing map, sensor, and debug-render tests.

## Behavioral Impact
The sample map displays an additional sensor outline. Its enter and exit events appear in the debug log with the sensor name and type; teleports also produce sensor event logs.

## Specification Impact
None. Sensor event semantics and authoring remain unchanged; logging makes existing events observable.

## Risks
Sensor event logging adds debug output for all sensor transitions. No graphical gameplay run was performed.

## Follow-up Items
Manually walk through TestSensor and inspect enter and exit logs.
